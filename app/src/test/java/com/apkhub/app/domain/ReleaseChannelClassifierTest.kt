package com.apkhub.app.domain

import com.apkhub.app.domain.logic.ReleaseChannelClassifier
import com.apkhub.app.domain.model.ReleaseChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChannelClassifierTest {

    @Test
    fun classifiesStableRelease() {
        val channel = ReleaseChannelClassifier.classify(
            tagName = "v2.4.1",
            releaseName = "Release 2.4.1",
            isPrerelease = false
        )
        assertEquals(ReleaseChannel.STABLE, channel)
    }

    @Test
    fun classifiesBetaAndRcReleases() {
        assertEquals(
            ReleaseChannel.BETA,
            ReleaseChannelClassifier.classify("v2.5.0-beta.2", "v2.5.0 Beta 2", true)
        )
        assertEquals(
            ReleaseChannel.BETA,
            ReleaseChannelClassifier.classify("v2.5.0-rc1", "Release Candidate 1", false)
        )
        assertEquals(
            ReleaseChannel.BETA,
            ReleaseChannelClassifier.classify("v2.5.0", "Preview Build", true)
        )
    }

    @Test
    fun classifiesAlphaNightlyCanaryTestReleases() {
        assertEquals(
            ReleaseChannel.TEST,
            ReleaseChannelClassifier.classify("v3.0.0-alpha.1", "Alpha 1", true)
        )
        assertEquals(
            ReleaseChannel.TEST,
            ReleaseChannelClassifier.classify("nightly-20261121", "Nightly build", true)
        )
        assertEquals(
            ReleaseChannel.TEST,
            ReleaseChannelClassifier.classify("v3.0.0-dev", "Dev snapshot", false)
        )
    }

    @Test
    fun verifiesChannelHierarchyInclusion() {
        assertTrue(ReleaseChannel.STABLE.includes(ReleaseChannel.STABLE))
        assertFalse(ReleaseChannel.STABLE.includes(ReleaseChannel.BETA))
        assertFalse(ReleaseChannel.STABLE.includes(ReleaseChannel.TEST))

        assertTrue(ReleaseChannel.BETA.includes(ReleaseChannel.STABLE))
        assertTrue(ReleaseChannel.BETA.includes(ReleaseChannel.BETA))
        assertFalse(ReleaseChannel.BETA.includes(ReleaseChannel.TEST))

        assertTrue(ReleaseChannel.TEST.includes(ReleaseChannel.STABLE))
        assertTrue(ReleaseChannel.TEST.includes(ReleaseChannel.BETA))
        assertTrue(ReleaseChannel.TEST.includes(ReleaseChannel.TEST))
    }
}
