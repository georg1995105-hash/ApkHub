package com.apkhub.app.domain

import com.apkhub.app.domain.logic.VersionComparator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun comparesSemanticVersionsWithAndWithoutPrefix() {
        assertTrue(VersionComparator.isNewer("v1.10.0", "1.2.9"))
        assertTrue(VersionComparator.isNewer("2.0.0", "v1.9.9"))
        assertFalse(VersionComparator.isNewer("v1.2.3", "1.2.3"))
        assertFalse(VersionComparator.isNewer("1.2.0", "v1.2.1"))
    }

    @Test
    fun ordersPrereleaseLowerThanStable() {
        assertTrue(VersionComparator.isNewer("1.5.0", "1.5.0-rc.1"))
        assertTrue(VersionComparator.isNewer("1.5.0-rc.1", "1.5.0-beta.3"))
        assertTrue(VersionComparator.isNewer("1.5.0-beta.2", "1.5.0-beta.1"))
        assertTrue(VersionComparator.isNewer("1.5.0-beta.1", "1.5.0-alpha.4"))
    }

    @Test
    fun comparesDateBasedVersions() {
        assertTrue(VersionComparator.isNewer("2026.11.21", "2026.11.05"))
        assertFalse(VersionComparator.isNewer("2026.10.30", "2026.11.01"))
    }
}
