package com.apkhub.app.domain

import com.apkhub.app.domain.logic.ChecksumExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecksumExtractorTest {

    private val sampleHash = "a3f5c8e91b2d4f6a8c0e2a4b6d8f0a2c4e6f8a0b2c4d6e8f0a1b2c3d4e5f6a7b"

    @Test
    fun extractsHashFromStandardSha256SumsList() {
        val text = """
            1111111111111111111111111111111111111111111111111111111111111111  other.apk
            $sampleHash  app-arm64-v8a.apk
        """.trimIndent()

        assertEquals(sampleHash, ChecksumExtractor.extractExpectedSha256(text, "app-arm64-v8a.apk"))
        assertNull(ChecksumExtractor.extractExpectedSha256(text, "missing.apk"))
    }

    @Test
    fun extractsHashFromMultilineMarkdownReleaseNotes() {
        val notes = """
            ### Checksums
            - `app-universal.apk`
              SHA-256: `${sampleHash.uppercase()}`
        """.trimIndent()

        assertEquals(sampleHash, ChecksumExtractor.extractExpectedSha256(notes, "app-universal.apk"))
    }

    @Test
    fun identifiesSiblingChecksumAssets() {
        assertTrue(ChecksumExtractor.isChecksumAssetFor("app.apk.sha256", "app.apk"))
        assertTrue(ChecksumExtractor.isChecksumAssetFor("SHA256SUMS.txt", "app.apk"))
        assertFalse(ChecksumExtractor.isChecksumAssetFor("source.zip", "app.apk"))
    }
}
