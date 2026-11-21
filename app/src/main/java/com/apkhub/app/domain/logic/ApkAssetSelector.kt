package com.apkhub.app.domain.logic

import com.apkhub.app.domain.model.AssetSelectionResult
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.ScoredAsset
import com.apkhub.app.domain.model.UnsupportedPackageFormat

object ApkAssetSelector {

    private data class AbiRule(
        val abi: String,
        val regex: Regex
    )

    private val abiRules = listOf(
        AbiRule("arm64-v8a", Regex("""(?i)(^|[._\-\s])(arm64[-_]?v8a|arm64|aarch64|v8a)([._\-\s]|$)""")),
        AbiRule("armeabi-v7a", Regex("""(?i)(^|[._\-\s])(armeabi[-_]?v7a|armv7a?|armeabi|arm32|v7a)([._\-\s]|$)""")),
        AbiRule("x86_64", Regex("""(?i)(^|[._\-\s])(x86[-_]?64|amd64|x64)([._\-\s]|$)""")),
        AbiRule("x86", Regex("""(?i)(^|[._\-\s])(x86|i686|i386|ia32)([._\-\s]|$)"""))
    )

    private val universalRegex = Regex("""(?i)(^|[._\-\s])(universal|all|fat|multi[-_]?arch|noarch)([._\-\s]|$)""")
    private val tvRegex = Regex("""(?i)(^|[._\-\s])(android[-_]?tv|leanback|firetv|tv)([._\-\s]|$)""")
    private val wearRegex = Regex("""(?i)(^|[._\-\s])(wear|wearos|watch)([._\-\s]|$)""")
    private val debugRegex = Regex("""(?i)(^|[._\-\s])(debug|unsigned|unaligned|testonly)([._\-\s]|$)""")
    private val releaseRegex = Regex("""(?i)(^|[._\-\s])(release|signed|foss|github|standard)([._\-\s]|$)""")
    private val minSdkRegex = Regex("""(?i)(?:api|sdk|minsdk|android)[-_]?(\d{2})""")

    fun selectBestApk(
        assets: List<ReleaseAsset>,
        device: DeviceProfile
    ): AssetSelectionResult {
        if (assets.isEmpty()) {
            return AssetSelectionResult(
                recommended = null,
                compatibleCandidates = emptyList(),
                unsupportedFormatsFound = emptyList(),
                reason = "В релизе отсутствуют прикреплённые файлы."
            )
        }

        val unsupportedFormats = assets
            .mapNotNull { UnsupportedPackageFormat.detect(it.name) }
            .distinct()

        // Strictly accept standard .apk files only. Never treat .xapk, .apks, .aab as compatible.
        val apkAssets = assets.filter { asset ->
            asset.name.lowercase().endsWith(".apk") &&
                UnsupportedPackageFormat.detect(asset.name) == null
        }

        if (apkAssets.isEmpty()) {
            val reason = if (unsupportedFormats.isNotEmpty()) {
                val names = unsupportedFormats.joinToString(", ") { it.displayName }
                "Найдены только неподдерживаемые форматы ($names). APK HUB v1 поддерживает только стандартные .apk."
            } else {
                "В релизе не найдено файлов .apk."
            }
            return AssetSelectionResult(
                recommended = null,
                compatibleCandidates = emptyList(),
                unsupportedFormatsFound = unsupportedFormats,
                reason = reason
            )
        }

        val scored = mutableListOf<ScoredAsset>()
        var rejectedByAbiCount = 0
        var rejectedBySdkCount = 0

        for (asset in apkAssets) {
            val baseName = asset.name.removeSuffix(".apk").removeSuffix(".APK")
            val detectedMinSdk = extractMinSdkHint(baseName)
            if (detectedMinSdk != null && detectedMinSdk > device.sdkInt) {
                rejectedBySdkCount++
                continue
            }

            val detectedAbis = detectAbisInFilename(baseName)
            val isExplicitUniversal = universalRegex.containsMatchIn(baseName)

            val matchedAbi: String?
            var score = 0
            val notes = mutableListOf<String>()

            when {
                isExplicitUniversal -> {
                    matchedAbi = "universal"
                    score += 80
                    notes.add("Universal APK")
                }
                detectedAbis.isNotEmpty() -> {
                    // Check if any detected ABI matches the device supported ABIs
                    val bestIndex = device.supportedAbis.indexOfFirst { supported ->
                        detectedAbis.any { it.equals(supported, ignoreCase = true) }
                    }
                    if (bestIndex == -1) {
                        rejectedByAbiCount++
                        continue
                    }
                    matchedAbi = device.supportedAbis[bestIndex]
                    // Highest priority for primary device ABI (index 0 -> +120, index 1 -> +100, etc.)
                    score += (120 - bestIndex * 20).coerceAtLeast(50)
                    notes.add("ABI: $matchedAbi")
                }
                else -> {
                    // No explicit ABI in filename — treat as universal/unspecified
                    matchedAbi = "universal"
                    score += 75
                    notes.add("Общая сборка")
                }
            }

            // Form-factor adjustments
            val isTvBuild = tvRegex.containsMatchIn(baseName)
            val isWearBuild = wearRegex.containsMatchIn(baseName)

            if (isWearBuild) {
                score -= 90
                notes.add("Wear OS")
            }

            if (device.isAndroidTv) {
                if (isTvBuild) {
                    score += 45
                    notes.add("Android TV")
                }
            } else {
                if (isTvBuild) {
                    score -= 60
                    notes.add("TV-сборка")
                }
            }

            // Build flavor quality adjustments
            if (debugRegex.containsMatchIn(baseName)) {
                score -= 50
                notes.add("Debug/Unsigned")
            }
            if (releaseRegex.containsMatchIn(baseName)) {
                score += 10
            }

            // Slightly prefer smaller targeted ABI over huge universal when tied
            if (asset.sizeBytes <= 0L) {
                score -= 30
            }

            scored.add(
                ScoredAsset(
                    asset = asset,
                    score = score,
                    matchedAbi = matchedAbi,
                    notes = notes.joinToString(" • ")
                )
            )
        }

        val sorted = scored.sortedWith(
            compareByDescending<ScoredAsset> { it.score }
                .thenBy { it.asset.name.length }
                .thenBy { it.asset.name }
        )

        val recommended = sorted.firstOrNull()?.asset
        val reason = when {
            recommended != null -> sorted.first().notes.ifBlank { "Совместимый APK" }
            rejectedByAbiCount > 0 ->
                "Найденные APK не подходят под архитектуру устройства (${device.supportedAbis.joinToString(", ")})."
            rejectedBySdkCount > 0 ->
                "Найденные APK требуют более новую версию Android (текущая API ${device.sdkInt})."
            else -> "Подходящий APK не найден."
        }

        return AssetSelectionResult(
            recommended = recommended,
            compatibleCandidates = sorted,
            unsupportedFormatsFound = unsupportedFormats,
            reason = reason
        )
    }

    internal fun detectAbisInFilename(baseName: String): List<String> {
        val found = mutableListOf<String>()
        for (rule in abiRules) {
            if (rule.regex.containsMatchIn(baseName)) {
                // Avoid false match of "x86" inside "x86_64"
                if (rule.abi == "x86" && found.contains("x86_64")) {
                    val without64 = baseName.replace(Regex("""(?i)x86[-_]?64"""), "")
                    if (!rule.regex.containsMatchIn(without64)) continue
                }
                found.add(rule.abi)
            }
        }
        return found
    }

    private fun extractMinSdkHint(baseName: String): Int? {
        val match = minSdkRegex.find(baseName) ?: return null
        val value = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return if (value in 21..40) value else null
    }
}
