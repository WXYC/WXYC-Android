package org.wxyc.wxycapp.data.bio

import org.wxyc.wxycapp.data.Playcut

/** When a detail sheet asks the server to help render a playcut's artist bio. */
object BioFetchPolicy {

    /**
     * Whether rows whose enrichment is final also fetch the server's bio. iOS skips the
     * proxy for those rows, so it rarely gets server tokens for them; Android fetches,
     * because otherwise most bios could not use them. Set to false to match iOS. Pending
     * Jake's decision (WXYC-Android#79).
     */
    const val FETCH_FOR_FINAL_METADATA = true

    /** Statuses after which Backend-Service will not enrich a row further; iOS `MetadataStatus.isTerminal`. */
    private val FINAL_STATUSES = setOf("enriched_match", "enriched_no_match", "failed_no_retry")

    /**
     * True when [playcut] shows a bio and has an artist to look up, unless its metadata is
     * final and [fetchForFinalMetadata] is off.
     */
    fun shouldFetch(playcut: Playcut, fetchForFinalMetadata: Boolean = FETCH_FOR_FINAL_METADATA): Boolean {
        if (playcut.artistName == null || playcut.metadata?.artistBio == null) return false
        return fetchForFinalMetadata || playcut.metadataStatus !in FINAL_STATUSES
    }
}

/**
 * Playcut ids whose bio lookup recently came back empty or failed, so reopening the same
 * sheet does not repeat the lookup until [ttlSeconds] pass.
 *
 * @param clock the current time in epoch seconds.
 */
class RecentMisses(
    private val ttlSeconds: Long,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val missedAt = mutableMapOf<Int, Long>()

    /** True when [playcutId] missed less than [ttlSeconds] ago. */
    @Synchronized
    fun isRecent(playcutId: Int): Boolean = missedAt[playcutId]?.let { clock() - it < ttlSeconds } ?: false

    /** Records a miss for [playcutId] now. */
    @Synchronized
    fun record(playcutId: Int) {
        missedAt[playcutId] = clock()
    }
}
