package com.apkhub.app.data.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.apkhub.app.domain.model.ApkInspectionResult
import com.apkhub.app.domain.model.BlockReasonCode
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.VerificationOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkMetadataVerifier(
    private val context: Context,
    private val installedAppsInspector: InstalledAppsInspector,
    private val deviceInfoProvider: DeviceInfoProvider
) {
    private val pm: PackageManager = context.packageManager

    suspend fun verify(
        apkFile: File,
        expectedSizeBytes: Long,
        expectedSha256: String?,
        expectedPackageName: String?,
        precomputedSha256: String? = null
    ): VerificationOutcome = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || !apkFile.isFile || apkFile.length() <= 0L) {
            return@withContext VerificationOutcome.Blocked(
                reason = "Файл APK отсутствует или пуст.",
                code = BlockReasonCode.FILE_MISSING_OR_EMPTY
            )
        }

        val actualSize = apkFile.length()
        if (expectedSizeBytes > 0L && actualSize != expectedSizeBytes) {
            return@withContext VerificationOutcome.Blocked(
                reason = "Размер файла ($actualSize байт) не совпадает с метаданными GitHub ($expectedSizeBytes байт). Возможно, загрузка повреждена.",
                code = BlockReasonCode.SIZE_MISMATCH
            )
        }

        val actualSha256 = (precomputedSha256 ?: computeFileSha256(apkFile)).lowercase()
        var checksumVerified = false
        if (!expectedSha256.isNullOrBlank()) {
            if (!actualSha256.equals(expectedSha256.trim(), ignoreCase = true)) {
                return@withContext VerificationOutcome.Blocked(
                    reason = "Контрольная сумма SHA-256 не совпадает! Файл мог быть повреждён или подменён.",
                    code = BlockReasonCode.CHECKSUM_MISMATCH
                )
            }
            checksumVerified = true
        }

        // Validate ZIP integrity and inspect native libraries inside lib/<abi>/
        val nativeAbis = try {
            ZipFile(apkFile).use { zip ->
                val manifestEntry = zip.getEntry("AndroidManifest.xml")
                if (manifestEntry == null) {
                    return@withContext VerificationOutcome.Blocked(
                        reason = "Архив не содержит AndroidManifest.xml — это не валидный APK.",
                        code = BlockReasonCode.INVALID_APK_MANIFEST
                    )
                }
                val abis = mutableSetOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        val parts = name.split("/")
                        if (parts.size >= 3 && parts[1].isNotBlank()) {
                            abis.add(parts[1])
                        }
                    }
                }
                abis.toList().sorted()
            }
        } catch (e: Exception) {
            return@withContext VerificationOutcome.Blocked(
                reason = "APK-архив повреждён и не может быть прочитан: ${e.localizedMessage ?: "ошибка ZIP"}.",
                code = BlockReasonCode.CORRUPTED_ARCHIVE
            )
        }

        val archiveInfo = parsePackageArchiveInfo(apkFile.absolutePath)
            ?: return@withContext VerificationOutcome.Blocked(
                reason = "Android не смог разобрать метаданные пакета APK. Файл повреждён или несовместим.",
                code = BlockReasonCode.INVALID_APK_MANIFEST
            )

        val appInfo = archiveInfo.applicationInfo
        val packageName = archiveInfo.packageName
        if (packageName.isNullOrBlank()) {
            return@withContext VerificationOutcome.Blocked(
                reason = "Не удалось определить packageName внутри APK.",
                code = BlockReasonCode.INVALID_APK_MANIFEST
            )
        }

        appInfo?.sourceDir = apkFile.absolutePath
        appInfo?.publicSourceDir = apkFile.absolutePath

        val versionName = archiveInfo.versionName ?: "0"
        val versionCode = InstalledAppsInspector.extractVersionCode(archiveInfo)
        val minSdk = appInfo?.minSdkVersion ?: 1
        val targetSdk = appInfo?.targetSdkVersion ?: 0
        val signingCerts = InstalledAppsInspector.extractSigningCertSha256(archiveInfo)
        val label = appInfo?.loadLabel(pm)?.toString()

        val inspection = ApkInspectionResult(
            filePath = apkFile.absolutePath,
            fileSizeBytes = actualSize,
            sha256 = actualSha256,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            nativeAbis = nativeAbis,
            signingCertSha256 = signingCerts,
            appLabel = label
        )

        val device: DeviceProfile = deviceInfoProvider.getDeviceProfile()

        // 1. Android SDK compatibility check
        if (minSdk > device.sdkInt) {
            return@withContext VerificationOutcome.Blocked(
                reason = "APK требует Android API $minSdk, но на устройстве установлен API ${device.sdkInt}.",
                code = BlockReasonCode.INCOMPATIBLE_SDK,
                inspection = inspection
            )
        }

        // 2. Native ABI compatibility check (if APK ships native .so libraries)
        if (nativeAbis.isNotEmpty()) {
            val hasCompatibleAbi = nativeAbis.any { apkAbi ->
                device.supportedAbis.any { it.equals(apkAbi, ignoreCase = true) }
            }
            if (!hasCompatibleAbi) {
                return@withContext VerificationOutcome.Blocked(
                    reason = "APK содержит нативные библиотеки только для [${nativeAbis.joinToString()}], а устройство поддерживает [${device.supportedAbis.joinToString()}].",
                    code = BlockReasonCode.INCOMPATIBLE_ABI,
                    inspection = inspection
                )
            }
        }

        // 3. Expected packageName match check (protects against wrong APK / repo hijacking)
        if (!expectedPackageName.isNullOrBlank() &&
            !expectedPackageName.equals(packageName, ignoreCase = false)
        ) {
            return@withContext VerificationOutcome.Blocked(
                reason = "Несовпадение packageName: ожидался $expectedPackageName, но в APK указан $packageName.",
                code = BlockReasonCode.PACKAGE_NAME_MISMATCH,
                inspection = inspection
            )
        }

        val warnings = mutableListOf<String>()

        // 4. Cross-check against currently installed package: signature & versionCode downgrade
        val installed = installedAppsInspector.getInstalledPackageInfo(packageName)
        if (installed != null) {
            if (signingCerts.isNotEmpty() && installed.signingCertSha256.isNotEmpty()) {
                val sharesSigner = signingCerts.any { cert ->
                    installed.signingCertSha256.any { it.equals(cert, ignoreCase = true) }
                }
                if (!sharesSigner) {
                    return@withContext VerificationOutcome.Blocked(
                        reason = "Подпись APK не совпадает с подписью установленного приложения ($packageName). Установка заблокирована для защиты от подмены и конфликта подписи.",
                        code = BlockReasonCode.SIGNATURE_MISMATCH,
                        inspection = inspection
                    )
                }
            }

            if (versionCode < installed.versionCode) {
                return@withContext VerificationOutcome.Blocked(
                    reason = "Попытка понижения версии (downgrade): в APK versionCode=$versionCode, а установлена более новая версия ${installed.versionName} (versionCode=${installed.versionCode}).",
                    code = BlockReasonCode.VERSION_DOWNGRADE,
                    inspection = inspection
                )
            } else if (versionCode == installed.versionCode) {
                warnings.add("Версия в APK совпадает с уже установленной (${installed.versionName}).")
            }
        }

        if (signingCerts.isEmpty()) {
            warnings.add("Не удалось прочитать сертификат подписи до установки — Android выполнит проверку при установке.")
        }

        VerificationOutcome.Verified(
            inspection = inspection,
            warnings = warnings,
            checksumVerified = checksumVerified
        )
    }

    @Suppress("DEPRECATION")
    private fun parsePackageArchiveInfo(apkPath: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    apkPath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNATURES)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeFileSha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer)
            while (read != -1) {
                md.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
