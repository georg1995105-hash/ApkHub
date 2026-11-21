package com.apkhub.app.data.system

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class ActiveDownloadProgress(
    val taskId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

sealed interface DownloadOutcome {
    data class Success(
        val file: File,
        val sizeBytes: Long,
        val sha256: String
    ) : DownloadOutcome

    data object Cancelled : DownloadOutcome

    data class Failure(val message: String) : DownloadOutcome
}

class ApkDownloadManager(
    private val context: Context,
    private val httpClient: OkHttpClient
) {
    private val activeCalls = ConcurrentHashMap<Long, Call>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<Long>()

    private val _progressMap = MutableStateFlow<Map<Long, ActiveDownloadProgress>>(emptyMap())
    val progressMap: StateFlow<Map<Long, ActiveDownloadProgress>> = _progressMap.asStateFlow()

    fun getApkDirectory(): File {
        val dir = File(context.filesDir, "apks")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCacheSizeBytes(): Long {
        val dir = getApkDirectory()
        return dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    fun clearAllApkFiles(): Long {
        val dir = getApkDirectory()
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile && !file.name.endsWith(".part")) {
                val len = file.length()
                if (file.delete()) freed += len
            }
        }
        return freed
    }

    fun deleteFileIfExists(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            File(path).takeIf { it.exists() }?.delete()
        } catch (_: Exception) {
        }
    }

    fun cancelDownload(taskId: Long) {
        cancelledIds.add(taskId)
        activeCalls.remove(taskId)?.cancel()
        _progressMap.update { it - taskId }
    }

    suspend fun downloadApk(
        taskId: Long,
        url: String,
        targetFileName: String,
        expectedSizeBytes: Long,
        token: String?
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        cancelledIds.remove(taskId)
        val safeName = sanitizeFileName(targetFileName)
        val finalFile = File(getApkDirectory(), "${taskId}_$safeName")
        val partFile = File(getApkDirectory(), "${taskId}_$safeName.part")

        if (partFile.exists()) partFile.delete()
        if (finalFile.exists()) finalFile.delete()

        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "APK-HUB-Android/1.0")
        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${token.trim()}")
        }

        val call = httpClient.newCall(reqBuilder.build())
        activeCalls[taskId] = call

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadOutcome.Failure(
                        "Не удалось скачать APK (HTTP ${response.code})."
                    )
                }

                val body = response.body
                    ?: return@withContext DownloadOutcome.Failure("Сервер вернул пустой ответ.")

                val contentLength = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)

                var downloaded = 0L
                var lastReportTime = System.currentTimeMillis()
                var lastReportBytes = 0L

                _progressMap.update {
                    it + (taskId to ActiveDownloadProgress(taskId, 0L, contentLength, 0L))
                }

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            if (cancelledIds.contains(taskId)) {
                                throw CancellationException("Cancelled by user")
                            }

                            val read = input.read(buffer)
                            if (read == -1) break

                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastReportTime
                            if (elapsed >= 200L) {
                                val deltaBytes = downloaded - lastReportBytes
                                val speed = if (elapsed > 0L) (deltaBytes * 1000L) / elapsed else 0L
                                _progressMap.update {
                                    it + (taskId to ActiveDownloadProgress(taskId, downloaded, contentLength, speed))
                                }
                                lastReportTime = now
                                lastReportBytes = downloaded
                            }
                        }
                        output.flush()
                    }
                }

                if (cancelledIds.contains(taskId)) {
                    partFile.delete()
                    return@withContext DownloadOutcome.Cancelled
                }

                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                DownloadOutcome.Success(
                    file = finalFile,
                    sizeBytes = downloaded,
                    sha256 = sha256
                )
            }
        } catch (_: CancellationException) {
            partFile.delete()
            finalFile.delete()
            DownloadOutcome.Cancelled
        } catch (e: IOException) {
            partFile.delete()
            finalFile.delete()
            if (cancelledIds.contains(taskId)) {
                DownloadOutcome.Cancelled
            } else {
                DownloadOutcome.Failure(
                    "Сбой загрузки: ${e.localizedMessage ?: "потеряно соединение"}."
                )
            }
        } catch (e: Exception) {
            partFile.delete()
            finalFile.delete()
            DownloadOutcome.Failure(
                "Ошибка при загрузке: ${e.localizedMessage ?: "неизвестная ошибка"}."
            )
        } finally {
            activeCalls.remove(taskId)
            cancelledIds.remove(taskId)
            _progressMap.update { it - taskId }
        }
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (cleaned.lowercase().endsWith(".apk")) cleaned else "$cleaned.apk"
    }
}
