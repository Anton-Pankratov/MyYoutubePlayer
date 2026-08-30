package kg.dev.shared.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kg.dev.shared.core.common.media.MediaProviderId
import kotlinx.coroutines.flow.StateFlow

/** Truthful controls and telemetry exposed by an approved embedded provider player. */
data class ProviderPlaybackCapabilities(
    val canPlayPause: Boolean,
    val canSeek: Boolean,
    val reportsPosition: Boolean,
    val reportsDuration: Boolean,
    val reportsBufferedPosition: Boolean = false
)

/**
 * Provider-owned playback backend for one Player destination.
 *
 * The session deliberately uses the same normalized [PlayerState] as native playback so the
 * product layer never needs to understand provider SDK or WebView state values.
 */
interface ProviderPlaybackSession {
    val state: StateFlow<PlayerState>
    val capabilities: ProviderPlaybackCapabilities

    /** Prepares a visible first frame without starting playback. */
    suspend fun preload(media: PlayableMedia)
    suspend fun load(media: PlayableMedia)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun retry()
    fun release()
}

typealias ProviderMediaSurface = @Composable (
    session: ProviderPlaybackSession?,
    media: PlayableMedia,
    modifier: Modifier
) -> Unit

typealias RenderOnlyProviderMediaSurface = @Composable (
    reference: kg.dev.shared.core.common.media.MediaReference,
    startPositionMs: Long,
    modifier: Modifier
) -> Unit

/** Platform-owned approved in-app provider adapter. */
interface ProviderPlaybackAdapter {
    val providerId: MediaProviderId

    /** Returns null only when this platform can render, but cannot truthfully control, the embed. */
    fun createSession(media: PlayableMedia): ProviderPlaybackSession?

    @Composable
    fun Surface(
        session: ProviderPlaybackSession?,
        media: PlayableMedia,
        startPositionMs: Long,
        modifier: Modifier
    )
}

/**
 * Transitional adapter for platforms that can embed a provider but cannot yet obtain official
 * lifecycle callbacks. It intentionally exposes no session, controls, or fabricated telemetry.
 */
class RenderOnlyProviderPlaybackAdapter(
    override val providerId: MediaProviderId,
    private val surface: RenderOnlyProviderMediaSurface
) : ProviderPlaybackAdapter {
    override fun createSession(media: PlayableMedia): ProviderPlaybackSession? = null

    @Composable
    override fun Surface(
        session: ProviderPlaybackSession?,
        media: PlayableMedia,
        startPositionMs: Long,
        modifier: Modifier
    ) {
        surface(media.catalogItem.reference, startPositionMs, modifier)
    }
}

class ProviderPlaybackAdapterRegistry(adapters: List<ProviderPlaybackAdapter>) {
    private val byProvider = adapters.associateBy(ProviderPlaybackAdapter::providerId)

    init {
        require(byProvider.size == adapters.size) { "Duplicate provider playback adapter registration" }
    }

    operator fun get(providerId: MediaProviderId): ProviderPlaybackAdapter? = byProvider[providerId]

    companion object {
        val Empty = ProviderPlaybackAdapterRegistry(emptyList())
    }
}
