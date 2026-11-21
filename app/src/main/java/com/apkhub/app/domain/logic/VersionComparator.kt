package com.apkhub.app.domain.logic

object VersionComparator {
    private val leadingPrefixRegex = Regex("""^(?i)(?:version|ver|rel|release|android|app|v)[._\-\s]*""")
    private val tokenSplitRegex = Regex("""[._\-+\s]+""")
    private val mixedTokenRegex = Regex("""([0-9]+)|([A-Za-z]+)""")

    data class ParsedVersion(
        val numericParts: List<Long>,
        val preReleaseRank: Int,
        val preReleaseNumber: Long,
        val rawNormalized: String
    ) : Comparable<ParsedVersion> {
        override fun compareTo(other: ParsedVersion): Int {
            val maxLen = maxOf(numericParts.size, other.numericParts.size)
            for (i in 0 until maxLen) {
                val left = numericParts.getOrElse(i) { 0L }
                val right = other.numericParts.getOrElse(i) { 0L }
                if (left != right) return left.compareTo(right)
            }
            if (preReleaseRank != other.preReleaseRank) {
                return preReleaseRank.compareTo(other.preReleaseRank)
            }
            if (preReleaseNumber != other.preReleaseNumber) {
                return preReleaseNumber.compareTo(other.preReleaseNumber)
            }
            return rawNormalized.compareTo(other.rawNormalized, ignoreCase = true)
        }
    }

    fun normalize(raw: String): String {
        var result = raw.trim()
        // Strip leading non-digit prefix like "v", "release-", "app-v"
        val stripped = leadingPrefixRegex.replace(result, "")
        if (stripped.isNotEmpty() && stripped.first().isDigit()) {
            result = stripped
        } else {
            val firstDigitIdx = result.indexOfFirst { it.isDigit() }
            if (firstDigitIdx > 0 && firstDigitIdx <= 12) {
                result = result.substring(firstDigitIdx)
            }
        }
        return result.trim()
    }

    fun parse(raw: String): ParsedVersion {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) {
            return ParsedVersion(emptyList(), STABLE_RANK, 0L, "")
        }

        // Remove build metadata after '+'
        val withoutBuild = normalized.substringBefore('+')
        val rawTokens = tokenSplitRegex.split(withoutBuild).filter { it.isNotEmpty() }

        val numericParts = mutableListOf<Long>()
        var preReleaseRank = STABLE_RANK
        var preReleaseNumber = 0L
        var enteredPreRelease = false

        for (token in rawTokens) {
            val subTokens = mixedTokenRegex.findAll(token).map { it.value }.toList()
            for (sub in subTokens) {
                val num = sub.toLongOrNull()
                if (num != null) {
                    if (!enteredPreRelease) {
                        numericParts.add(num)
                    } else if (preReleaseNumber == 0L) {
                        preReleaseNumber = num
                    }
                } else {
                    val rank = rankForLabel(sub)
                    if (rank != null) {
                        enteredPreRelease = true
                        if (rank < preReleaseRank) {
                            preReleaseRank = rank
                        }
                    } else if (numericParts.isNotEmpty()) {
                        enteredPreRelease = true
                        if (preReleaseRank == STABLE_RANK) {
                            preReleaseRank = OTHER_PRE_RANK
                        }
                    }
                }
            }
        }

        return ParsedVersion(
            numericParts = numericParts,
            preReleaseRank = preReleaseRank,
            preReleaseNumber = preReleaseNumber,
            rawNormalized = normalized
        )
    }

    /**
     * Returns > 0 if [candidate] is newer than [current], 0 if equal, < 0 if older.
     */
    fun compare(candidate: String, current: String): Int {
        val left = parse(candidate)
        val right = parse(current)
        if (left.numericParts.isEmpty() && right.numericParts.isEmpty()) {
            return left.rawNormalized.compareTo(right.rawNormalized, ignoreCase = true)
        }
        return left.compareTo(right)
    }

    fun isNewer(candidateVersion: String, currentVersion: String): Boolean {
        if (candidateVersion.isBlank()) return false
        if (currentVersion.isBlank()) return true
        return compare(candidateVersion, currentVersion) > 0
    }

    private fun rankForLabel(label: String): Int? = when (label.lowercase()) {
        "dev", "snapshot", "ci", "wip" -> 10
        "nightly", "canary" -> 20
        "alpha", "a" -> 30
        "test", "testing", "experimental", "preview", "debug" -> 35
        "beta", "b" -> 50
        "pre", "prerelease" -> 60
        "rc" -> 70
        "release", "final", "stable", "hotfix", "patch" -> STABLE_RANK
        else -> null
    }

    private const val OTHER_PRE_RANK = 40
    private const val STABLE_RANK = 100
}
