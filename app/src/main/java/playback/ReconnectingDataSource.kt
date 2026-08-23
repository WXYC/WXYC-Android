package playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A [DataSource] that hides live-stream connection drops from ExoPlayer.
 *
 * ExoPlayer treats the WXYC mount as a progressive source, so a server-side
 * disconnect arrives as a clean end of input and settles the player in
 * `STATE_ENDED` — silently, with no error to react to. Recovering at the player
 * level means the audio has already run out by the time anything notices.
 *
 * This recovers a layer lower instead. [SeamlessStreamReader] reopens the
 * connection and trims the history the server replays, so the bytes ExoPlayer
 * reads stay continuous and it never learns the stream ended. Playback does not
 * leave `STATE_READY`, which also keeps the media notification and the
 * foreground service in place.
 *
 * ICY metadata is deliberately not requested. ExoPlayer asks for it by default,
 * and the server then interleaves metadata blocks at fixed byte intervals — an
 * offset that splicing would desynchronise, turning the metadata into noise in
 * the audio. Nothing in the app reads it: now-playing information comes from the
 * playlist API, not the stream.
 */
@OptIn(UnstableApi::class)
class ReconnectingDataSource(
    private val upstreamFactory: DataSource.Factory
) : DataSource {

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = ReconnectingDataSource(upstreamFactory)
    }

    private val transferListeners = mutableListOf<TransferListener>()
    private var upstream: DataSource? = null
    private var reader: SeamlessStreamReader? = null
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val spec = dataSpec.withoutIcyMetadata()
        uri = spec.uri

        val stream = object : LiveByteStream {
            override fun open() {
                val source = upstreamFactory.createDataSource()
                transferListeners.forEach(source::addTransferListener)
                source.open(spec)
                upstream = source
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val source = upstream ?: return C.RESULT_END_OF_INPUT
                return source.read(buffer, offset, length)
            }

            override fun close() {
                try {
                    upstream?.close()
                } catch (e: IOException) {
                    // Closing a already-broken connection is not interesting.
                } finally {
                    upstream = null
                }
            }
        }

        reader = SeamlessStreamReader(stream).apply { open() }
        // A live stream has no length, and never reports one.
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val n = reader?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        return if (n < 0) C.RESULT_END_OF_INPUT else n
    }

    override fun getUri(): Uri? = upstream?.uri ?: uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        upstream?.responseHeaders ?: emptyMap()

    override fun close() {
        reader?.close()
        reader = null
        uri = null
    }

    /** Reconnects performed on this connection, for diagnostics. */
    val reconnectCount: Int get() = reader?.reconnectCount ?: 0

    /** False when the last reconnect could not be spliced without a hole. */
    val lastSpliceWasSeamless: Boolean get() = reader?.lastSpliceWasSeamless ?: true

    private companion object {
        const val ICY_METADATA_HEADER = "Icy-MetaData"

        fun DataSpec.withoutIcyMetadata(): DataSpec {
            if (httpRequestHeaders.keys.none { it.equals(ICY_METADATA_HEADER, ignoreCase = true) }) {
                return this
            }
            val headers = httpRequestHeaders.filterKeys {
                !it.equals(ICY_METADATA_HEADER, ignoreCase = true)
            }
            return buildUpon().setHttpRequestHeaders(headers).build()
        }
    }
}
