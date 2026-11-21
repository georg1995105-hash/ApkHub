package com.apkhub.app.domain.logic

/**
 * Extracts expected SHA-256 hashes for a given APK filename from GitHub release notes
 * or checksum file contents (e.g., sha256sums.txt or app.apk.sha256).
 */
object ChecksumExtractor {
    private val sha256Regex = Regex("""(?i)\b([a-f0-9]{64})\b""")

    fun extractExpectedSha256(
        text: String?,
        assetFileName: String
    ): String? {
        if (text.isNullOrBlank() || assetFileName.isBlank()) return null

        val lines = text.lines()
        val lowerFileName = assetFileName.lowercase()

        // Pass 1: Look for a line containing both the exact asset filename and a 64-hex SHA-256 hash.
        for (line in lines) {
            val lowerLine = line.lowercase()
            if (lowerLine.contains(lowerFileName)) {
                val match = sha256Regex.find(line)
                if (match != null) {
                    return match.groupValues[1].lowercase()
                }
            }
        }

        // Pass 2: Look for filename on line N and hash on line N+1 (common in Markdown lists/tables).
        for (i in 0 until lines.size - 1) {
            if (lines[i].lowercase().contains(lowerFileName)) {
                val nextMatch = sha256Regex.find(lines[i + 1])
                if (nextMatch != null) {
                    return nextMatch.groupValues[1].lowercase()
                }
            }
        }

        // Pass 3: If the text is a dedicated single-hash checksum file (e.g., <asset>.sha256)
        val trimmed = text.trim()
        val allMatches = sha256Regex.findAll(trimmed).toList()
        if (allMatches.size == 1 && trimmed.length <= 260 && !trimmed.contains("\n")) {
            return allMatches.first().groupValues[1].lowercase()
        }

        return null
    }

    fun isChecksumAssetFor(checksumAssetName: String, apkAssetName: String): Boolean {
        val lowerChecksum = checksumAssetName.lowercase()
        val lowerApk = apkAssetName.lowercase()
        return lowerChecksum == "$lowerApk.sha256" ||
            lowerChecksum == "$lowerApk.sha256sum" ||
            lowerChecksum == "sha256sums" ||
            lowerChecksum == "sha256sums.txt" ||
            lowerChecksum == "checksums.txt" ||
            lowerChecksum == "sha256.txt"
    }
}
