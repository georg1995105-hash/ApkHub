package com.apkhub.app.data.system

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.graphics.drawable.Drawable
import android.os.Build
import com.apkhub.app.domain.model.InstalledPackageInfo
import java.security.MessageDigest

class InstalledAppsInspector(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun getInstalledPackageInfo(packageName: String): InstalledPackageInfo? {
        val pkgInfo = queryPackageInfo(packageName) ?: return null
        val versionName = pkgInfo.versionName ?: "0"
        val versionCode = extractVersionCode(pkgInfo)
        val certs = extractSigningCertSha256(pkgInfo)
        val label = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        return InstalledPackageInfo(
            packageName = pkgInfo.packageName,
            versionName = versionName,
            versionCode = versionCode,
            signingCertSha256 = certs,
            appLabel = label
        )
    }

    fun loadAppIcon(packageName: String): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun getSelfPackageInfo(): InstalledPackageInfo? {
        return getInstalledPackageInfo(context.packageName)
    }

    @Suppress("DEPRECATION")
    private fun queryPackageInfo(packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun extractVersionCode(pkgInfo: PackageInfo): Long {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
        }

        @Suppress("DEPRECATION")
        fun extractSigningCertSha256(pkgInfo: PackageInfo): List<String> {
            val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = pkgInfo.signingInfo
                when {
                    signingInfo == null -> null
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                    else -> signingInfo.signingCertificateHistory
                }
            } else {
                pkgInfo.signatures
            }
            return signatures.orEmpty().map { sig ->
                sha256Hex(sig.toByteArray())
            }.distinct()
        }

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
