package playback

/**
 * Works out where a freshly-opened connection stops repeating audio we have
 * already handed downstream.
 *
 * Icecast bursts recent history to every new client (measured at ~3.3s on the
 * WXYC mount), so a reconnecting listener does not resume at the live edge — it
 * resumes behind it, re-sending audio the previous connection already delivered.
 * Playing that burst verbatim would repeat those seconds. Discarding it wholesale
 * would instead leave a hole. Either way the listener hears the seam.
 *
 * The burst is also what makes a seamless reconnect *possible*: it covers the
 * same span the player still has buffered, so if the duplicated prefix is
 * trimmed off, the new connection dovetails exactly onto the old one.
 *
 * Matching is on raw bytes rather than decoded audio, which is sound here because
 * both connections carry the identical encoder output for the same broadcast.
 */
internal object StreamSplicer {

    /** Returned when the new stream shares nothing with what we already sent. */
    const val NO_OVERLAP = -1

    /**
     * How many trailing delivered bytes to match on. At 128 kbps this is a
     * quarter-second of audio — long enough to be unique in practice, short
     * enough that the scan stays cheap.
     */
    const val ANCHOR_BYTES = 4_096

    /**
     * Index into [probe] at which audio we have not delivered yet begins.
     *
     * Returns [NO_OVERLAP] when the anchor cannot be found, which means the
     * outage outlasted the server's burst and there is a genuine hole in the
     * content. Callers must not discard anything in that case — the missing
     * audio is gone, and dropping more only widens the hole.
     *
     * @param tail the most recently delivered bytes; only the last
     *   [ANCHOR_BYTES] are used.
     * @param probe buffer holding the start of the reopened stream.
     * @param probeLength how much of [probe] has actually been read.
     */
    fun findResumePoint(tail: ByteArray, probe: ByteArray, probeLength: Int): Int {
        val anchorSize = minOf(ANCHOR_BYTES, tail.size)
        if (anchorSize == 0 || probeLength < anchorSize) return NO_OVERLAP

        val anchorStart = tail.size - anchorSize
        val firstByte = tail[anchorStart]

        // Scan backwards: with a burst the anchor sits near the end of the probe,
        // and a repeated pattern (silence, a loop) can match more than once. The
        // latest match is the one that does not replay audio.
        var i = probeLength - anchorSize
        while (i >= 0) {
            if (probe[i] == firstByte && matchesAt(tail, anchorStart, anchorSize, probe, i)) {
                return i + anchorSize
            }
            i--
        }
        return NO_OVERLAP
    }

    private fun matchesAt(
        tail: ByteArray,
        anchorStart: Int,
        anchorSize: Int,
        probe: ByteArray,
        offset: Int
    ): Boolean {
        var j = 1
        while (j < anchorSize) {
            if (tail[anchorStart + j] != probe[offset + j]) return false
            j++
        }
        return true
    }
}
