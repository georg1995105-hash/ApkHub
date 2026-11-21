package com.apkhub.app.domain

import com.apkhub.app.domain.logic.ApkAssetSelector
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.UnsupportedPackageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkAssetSelectorTest {

    private val arm64Phone = DeviceProfile(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        sdkInt = 34,
        isAndroidTv = false
    )

    private val tvArm32 = DeviceProfile(
        supportedAbis = listOf("armeabi-v7a"),
        sdkInt = 31,
        isAndroidTv = true
    )

    @Test
    fun prefersPrimaryDeviceAbiOverUniversalAndSecondaryAbi() {
        val assets = listOf(
            ReleaseAsset(1, "app-v1.2-universal-release.apk", "https://example.com/u.apk", 25_000_000),
            ReleaseAsset(2, "app-v1.2-armeabi-v7a-release.apk", "https://example.com/v7.apk", 14_000_000),
            ReleaseAsset(3, "app-v1.2-arm64-v8a-release.apk", "https://example.com/v8.apk", 15_000_000),
            ReleaseAsset(4, "app-v1.2-x86_64-release.apk", "https://example.com/x64.apk", 16_000_000)
        )

        val result = ApkAssetSelector.selectBestApk(assets, arm64Phone)
        assertEquals("app-v1.2-arm64-v8a-release.apk", result.recommended?.name)
        assertEquals(3, result.compatibleCandidates.size)
    }

    @Test
    fun rejectsUnsupportedBundleFormatsAndNeverMarksThemCompatible() {
        val assets = listOf(
            ReleaseAsset(1, "app-v2.0.xapk", "https://example.com/a.xapk", 40_000_000),
            ReleaseAsset(2, "app-v2.0.apks", "https://example.com/a.apks", 38_000_000),
            ReleaseAsset(3, "app-v2.0.aab", "https://example.com/a.aab", 35_000_000)
        )

        val result = ApkAssetSelector.selectBestApk(assets, arm64Phone)
        assertNull(result.recommended)
        assertTrue(result.compatibleCandidates.isEmpty())
        assertTrue(result.unsupportedFormatsFound.contains(UnsupportedPackageFormat.XAPK))
        assertTrue(result.unsupportedFormatsFound.contains(UnsupportedPackageFormat.APKS))
        assertTrue(result.unsupportedFormatsFound.contains(UnsupportedPackageFormat.AAB))
    }

    @Test
    fun prefersTvApkOnAndroidTvAndAvoidsIncompatibleAbi() {
        val assets = listOf(
            ReleaseAsset(1, "player-arm64-v8a.apk", "https://example.com/v8.apk", 12_000_000),
            ReleaseAsset(2, "player-armeabi-v7a.apk", "https://example.com/v7.apk", 11_000_000),
            ReleaseAsset(3, "player-tv-armeabi-v7a.apk", "https://example.com/tv.apk", 11_500_000)
        )

        val result = ApkAssetSelector.selectBestApk(assets, tvArm32)
        assertEquals("player-tv-armeabi-v7a.apk", result.recommended?.name)
    }

    @Test
    fun filtersOutApkRequiringHigherApiLevel() {
        val oldPhone = DeviceProfile(
            supportedAbis = listOf("arm64-v8a"),
            sdkInt = 28,
            isAndroidTv = false
        )
        val assets = listOf(
            ReleaseAsset(1, "tool-api33-arm64-v8a.apk", "https://example.com/33.apk", 9_000_000)
        )
        val result = ApkAssetSelector.selectBestApk(assets, oldPhone)
        assertNull(result.recommended)
    }
}
