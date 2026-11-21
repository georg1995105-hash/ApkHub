package com.apkhub.app.domain.model

data class ApkInspectionResult(
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val nativeAbis: List<String>,
    val signingCertSha256: List<String>,
    val appLabel: String?
)

sealed interface VerificationOutcome {
    data class Verified(
        val inspection: ApkInspectionResult,
        val warnings: List<String> = emptyList(),
        val checksumVerified: Boolean = false
    ) : VerificationOutcome

    data class Blocked(
        val reason: String,
        val code: BlockReasonCode,
        val inspection: ApkInspectionResult? = null
    ) : VerificationOutcome
}

enum class BlockReasonCode {
    FILE_MISSING_OR_EMPTY,
    SIZE_MISMATCH,
    CHECKSUM_MISMATCH,
    CORRUPTED_ARCHIVE,
    INVALID_APK_MANIFEST,
    INCOMPATIBLE_SDK,
    INCOMPATIBLE_ABI,
    PACKAGE_NAME_MISMATCH,
    SIGNATURE_MISMATCH,
    VERSION_DOWNGRADE
}
