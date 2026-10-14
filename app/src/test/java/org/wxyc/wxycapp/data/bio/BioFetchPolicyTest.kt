package org.wxyc.wxycapp.data.bio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.wxyc.wxycapp.data.Playcut
import org.wxyc.wxycapp.data.PlaycutMetadata

class BioFetchPolicyTest {

    private fun playcut(bio: String? = "Formed in London.", artist: String? = "Stereolab", status: String? = null) =
        Playcut(
            id = 1, entryType = "track", hour = null, playOrder = 1,
            artistName = artist, metadata = PlaycutMetadata(artistBio = bio), metadataStatus = status,
        )

    @Test
    fun `fetches only for rows that show a bio, and skips final rows only when switched off`() {
        val cases = listOf(
            Triple(playcut(), true, true),
            Triple(playcut(bio = null), true, false),
            Triple(playcut(artist = null), true, false),
            Triple(playcut(status = "enriched_match"), true, true),
            Triple(playcut(status = "enriched_match"), false, false),
            Triple(playcut(status = "enriched_no_match"), false, false),
            Triple(playcut(status = "failed_no_retry"), false, false),
            Triple(playcut(status = "pending"), false, true),
            Triple(playcut(status = "enriching"), false, true),
            Triple(playcut(status = null), false, true),
        )
        for ((row, fetchForFinal, expected) in cases) {
            assertEquals("${row.metadataStatus} / $fetchForFinal", expected, BioFetchPolicy.shouldFetch(row, fetchForFinal))
        }
    }

    @Test
    fun `holds off a playcut for a while after a miss`() {
        var now = 1_000L
        val misses = RecentMisses(ttlSeconds = 300, clock = { now })

        assertFalse(misses.isRecent(7))
        misses.record(7)
        assertTrue(misses.isRecent(7))
        assertFalse("other playcuts are unaffected", misses.isRecent(8))

        now += 299
        assertTrue(misses.isRecent(7))
        now += 1
        assertFalse(misses.isRecent(7))
    }
}
