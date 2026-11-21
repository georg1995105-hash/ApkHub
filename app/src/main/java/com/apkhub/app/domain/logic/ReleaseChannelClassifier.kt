package com.apkhub.app.domain.logic

import com.apkhub.app.domain.model.ReleaseChannel

object ReleaseChannelClassifier {
    private val testPatterns = listOf(
        Regex("""(?i)(^|[._\-\s+])(alpha|a\d+|nightly|canary|dev|snapshot|test|testing|experimental|debug|wip|preview|ci)([._\-\s+\d]|$)"""),
        Regex("""(?i)-a\.?\d+""")
    )

    private val betaPatterns = listOf(
        Regex("""(?i)(^|[._\-\s+])(beta|b\d+|rc\d*|release[-_]?candidate|pre|prerelease|pre[-_]?release)([._\-\s+\d]|$)"""),
        Regex("""(?i)-b\.?\d+""")
    )

    fun classify(
        tagName: String,
        releaseName: String,
        isPrerelease: Boolean
    ): ReleaseChannel {
        val combined = "$tagName $releaseName".trim()

        if (testPatterns.any { it.containsMatchIn(combined) }) {
            return ReleaseChannel.TEST
        }

        if (betaPatterns.any { it.containsMatchIn(combined) }) {
            return ReleaseChannel.BETA
        }

        return if (isPrerelease) {
            ReleaseChannel.BETA
        } else {
            ReleaseChannel.STABLE
        }
    }
}
