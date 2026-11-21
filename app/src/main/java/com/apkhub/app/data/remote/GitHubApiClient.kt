package com.apkhub.app.data.remote

import com.apkhub.app.domain.logic.ReleaseChannelClassifier
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.ReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface GitHubResult<out T> {
    data class Success<T>(
        val data: T,
        val etag: String? = null,
        val rateLimit: RateLimitInfo? = null
    ) : GitHubResult<T>

    data class NotModified(
        val etag: String?,
        val rateLimit: RateLimitInfo?
    ) : GitHubResult<Nothing>

    data class Error(
        val message: String,
        val kind: ApiErrorKind,
        val httpCode: Int? = null,
        val rateLimit: RateLimitInfo? = null
    ) : GitHubResult<Nothing>
}

enum class ApiErrorKind {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    NOT_FOUND,
    UNAUTHORIZED,
    SERVER_ERROR,
    PARSE_ERROR,
    UNKNOWN
}

class GitHubApiClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.github.com",
    private val userAgent: String = "APK-HUB-Android/1.0"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchRepository(
        owner: String,
        repo: String,
        token: String? = null
    ): GitHubResult<GitHubRepoDto> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/repos/$owner/$repo"
        executeGet(url = url, token = token, etag = null) { body ->
            json.decodeFromString<GitHubRepoDto>(body)
        }
    }

    suspend fun fetchReleases(
        owner: String,
        repo: String,
        token: String? = null,
        etag: String? = null,
        perPage: Int = 30
    ): GitHubResult<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/repos/$owner/$repo/releases?per_page=$perPage"
        executeGet(url = url, token = token, etag = etag) { body ->
            val dtos = json.decodeFromString<List<GitHubReleaseDto>>(body)
            dtos
                .filter { !it.draft && it.tagName.isNotBlank() }
                .map { dto ->
                    val displayName = dto.name?.takeIf { it.isNotBlank() } ?: dto.tagName
                    ReleaseInfo(
                        id = dto.id,
                        tagName = dto.tagName,
                        name = displayName,
                        body = dto.body.orEmpty(),
                        htmlUrl = dto.htmlUrl,
                        publishedAt = dto.publishedAt ?: dto.createdAt.orEmpty(),
                        isPrerelease = dto.prerelease,
                        isDraft = dto.draft,
                        channel = ReleaseChannelClassifier.classify(
                            tagName = dto.tagName,
                            releaseName = displayName,
                            isPrerelease = dto.prerelease
                        ),
                        assets = dto.assets
                            .filter { it.name.isNotBlank() && it.browserDownloadUrl.isNotBlank() }
                            .map { asset ->
                                ReleaseAsset(
                                    id = asset.id,
                                    name = asset.name,
                                    downloadUrl = asset.browserDownloadUrl,
                                    sizeBytes = asset.size,
                                    contentType = asset.contentType.orEmpty(),
                                    updatedAt = asset.updatedAt.orEmpty()
                                )
                            }
                    )
                }
        }
    }

    suspend fun fetchTextAsset(
        downloadUrl: String,
        token: String? = null,
        maxBytes: Int = 64 * 1024
    ): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", userAgent)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val source = body.source()
                source.request(maxBytes.toLong())
                val bytes = source.buffer.clone().readByteArray(
                    minOf(source.buffer.size, maxBytes.toLong())
                )
                bytes.toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> executeGet(
        url: String,
        token: String?,
        etag: String?,
        parser: (String) -> T
    ): GitHubResult<T> {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)

        if (!token.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer ${token.trim()}")
        }
        if (!etag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", etag)
        }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rateLimit = parseRateLimit(response)
                when (response.code) {
                    200 -> {
                        val rawBody = response.body?.string().orEmpty()
                        try {
                            val parsed = parser(rawBody)
                            GitHubResult.Success(
                                data = parsed,
                                etag = response.header("ETag"),
                                rateLimit = rateLimit
                            )
                        } catch (e: SerializationException) {
                            GitHubResult.Error(
                                message = "Некорректный формат ответа GitHub API: ${e.message ?: "ошибка JSON"}",
                                kind = ApiErrorKind.PARSE_ERROR,
                                httpCode = 200,
                                rateLimit = rateLimit
                            )
                        }
                    }
                    304 -> GitHubResult.NotModified(
                        etag = response.header("ETag") ?: etag,
                        rateLimit = rateLimit
                    )
                    401 -> GitHubResult.Error(
                        message = "Неверный или просроченный GitHub Token (HTTP 401).",
                        kind = ApiErrorKind.UNAUTHORIZED,
                        httpCode = 401,
                        rateLimit = rateLimit
                    )
                    403, 429 -> {
                        val isLimit = response.code == 429 ||
                            (rateLimit != null && rateLimit.remaining <= 0) ||
                            response.header("Retry-After") != null
                        if (isLimit) {
                            GitHubResult.Error(
                                message = "Превышен лимит запросов GitHub API. Используются кешированные данные; добавьте GitHub Token в Настройках для лимита 5000/час.",
                                kind = ApiErrorKind.RATE_LIMITED,
                                httpCode = response.code,
                                rateLimit = rateLimit?.copy(isRateLimited = true)
                                    ?: RateLimitInfo(remaining = 0, isRateLimited = true)
                            )
                        } else {
                            GitHubResult.Error(
                                message = "Доступ к репозиторию запрещён (HTTP ${response.code}).",
                                kind = ApiErrorKind.UNAUTHORIZED,
                                httpCode = response.code,
                                rateLimit = rateLimit
                            )
                        }
                    }
                    404 -> GitHubResult.Error(
                        message = "Репозиторий или релизы не найдены (HTTP 404). Проверьте ссылку и публичность репозитория.",
                        kind = ApiErrorKind.NOT_FOUND,
                        httpCode = 404,
                        rateLimit = rateLimit
                    )
                    in 500..599 -> GitHubResult.Error(
                        message = "Временная ошибка сервера GitHub (HTTP ${response.code}).",
                        kind = ApiErrorKind.SERVER_ERROR,
                        httpCode = response.code,
                        rateLimit = rateLimit
                    )
                    else -> GitHubResult.Error(
                        message = "Неожиданный ответ GitHub (HTTP ${response.code}).",
                        kind = ApiErrorKind.UNKNOWN,
                        httpCode = response.code,
                        rateLimit = rateLimit
                    )
                }
            }
        } catch (_: UnknownHostException) {
            GitHubResult.Error(
                message = "Нет подключения к интернету. Показаны сохранённые данные.",
                kind = ApiErrorKind.OFFLINE
            )
        } catch (_: SocketTimeoutException) {
            GitHubResult.Error(
                message = "Превышено время ожидания ответа от GitHub.",
                kind = ApiErrorKind.TIMEOUT
            )
        } catch (e: IOException) {
            GitHubResult.Error(
                message = "Сетевая ошибка: ${e.localizedMessage ?: "нет связи с сервером"}.",
                kind = ApiErrorKind.OFFLINE
            )
        }
    }

    private fun parseRateLimit(response: Response): RateLimitInfo? {
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull() ?: return null
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: return null
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull() ?: 0L
        return RateLimitInfo(
            limit = limit,
            remaining = remaining,
            resetEpochSeconds = reset,
            isRateLimited = remaining <= 0
        )
    }
}
