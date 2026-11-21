package com.apkhub.app.domain

import com.apkhub.app.domain.logic.GitHubUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubUrlParserTest {

    @Test
    fun parsesStandardHttpsUrl() {
        val ref = GitHubUrlParser.parse("https://github.com/Revanced/revanced-manager")
        assertEquals("Revanced", ref?.owner)
        assertEquals("revanced-manager", ref?.repo)
    }

    @Test
    fun parsesReleasesSubpathAndStripsGitSuffix() {
        val ref = GitHubUrlParser.parse("https://www.github.com/owner-1/my.app.git/releases/latest?tab=1")
        assertEquals("owner-1", ref?.owner)
        assertEquals("my.app", ref?.repo)
    }

    @Test
    fun parsesShortOwnerRepoFormat() {
        val ref = GitHubUrlParser.parse("  apkhub-android/apk-hub  ")
        assertEquals("apkhub-android", ref?.owner)
        assertEquals("apk-hub", ref?.repo)
    }

    @Test
    fun rejectsReservedGitHubPathsAndInvalidUrls() {
        assertNull(GitHubUrlParser.parse("https://github.com/topics/android"))
        assertNull(GitHubUrlParser.parse("https://gitlab.com/owner/repo"))
        assertNull(GitHubUrlParser.parse("just-one-word"))
        assertNull(GitHubUrlParser.parse(""))
    }
}
