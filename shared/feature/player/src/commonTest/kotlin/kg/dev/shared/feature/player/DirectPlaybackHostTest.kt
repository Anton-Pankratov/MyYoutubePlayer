package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.DirectBackgroundEligibility
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectPlaybackHostTest {
    @Test fun directBackgroundEligibilityUsesOnlyCaseInsensitiveAudioMimeType() {
        assertEquals(DirectBackgroundEligibility.Eligible, media("audio/mpeg").directBackgroundEligibility())
        assertEquals(DirectBackgroundEligibility.Eligible, media("audio/mp4").directBackgroundEligibility())
        assertEquals(DirectBackgroundEligibility.Eligible, media(" AUDIO/MPEG ").directBackgroundEligibility())
        listOf("video/mp4", "application/octet-stream", null, "", "   ", "audio").forEach {
            assertEquals(DirectBackgroundEligibility.ForegroundOnly, media(it).directBackgroundEligibility())
        }
    }

    @Test fun providerControlledMediaIsAlwaysForegroundOnly() {
        val media = PlayableMedia(MediaCatalogItem(MediaReference(MediaProviders.YouTube, "id"), "YouTube"), PlaybackSource.ProviderControlled(MediaReference(MediaProviders.YouTube, "id")))
        assertEquals(DirectBackgroundEligibility.ForegroundOnly, media.directBackgroundEligibility())
    }

    @Test fun attachAndDetachKeepTheSameLogicalSessionWithoutStoppingIt() {
        val activeMedia = media("audio/mpeg")
        val host = FakeHost(PlayerState(media = activeMedia, playbackState = PlaybackState.Playing, positionMs = 42_000))
        host.attachUi(); host.detachUi(); host.attachUi()
        assertTrue(host.isUiAttached.value)
        assertEquals(activeMedia, host.state.value.media)
        assertEquals(42_000, host.state.value.positionMs)
        assertEquals(0, host.stopCount)
    }

    @Test fun commandRouterDelegatesTransportToHostAndQueueCommandsOutward() {
        val host = FakeHost()
        val callbacks = FakeCallbacks()
        val router = DirectPlaybackCommandRouter(host, callbacks)
        router.play(); router.pause(); router.seekTo(12_345); router.next(); router.previous(); router.stop()
        assertEquals(1, host.playCount)
        assertEquals(1, host.pauseCount)
        assertEquals(listOf(12_345L), host.seekPositions)
        assertEquals(1, callbacks.nextCount)
        assertEquals(1, callbacks.previousCount)
        assertEquals(1, callbacks.stopCount)
        assertEquals(0, host.stopCount)
    }

    private fun media(mimeType: String?) = PlayableMedia(
        MediaCatalogItem(MediaReference(MediaProviders.Direct, "id"), "Direct"),
        PlaybackSource.Direct("https://example.test/file", mimeType)
    )

    private class FakeHost(initial: PlayerState = PlayerState()) : DirectPlaybackHost {
        override val state = MutableStateFlow(initial)
        override val isUiAttached = MutableStateFlow(false)
        override val capabilities = DirectPlaybackHostCapabilities()
        var playCount = 0
        var pauseCount = 0
        var stopCount = 0
        val seekPositions = mutableListOf<Long>()
        override fun attachUi() { isUiAttached.value = true }
        override fun detachUi() { isUiAttached.value = false }
        override suspend fun play(media: PlayableMedia) { state.value = state.value.copy(media = media) }
        override fun play() { playCount++ }
        override fun pause() { pauseCount++ }
        override fun seekTo(positionMs: Long) { seekPositions += positionMs }
        override fun stop() { stopCount++; state.value = PlayerState() }
    }

    private class FakeCallbacks : DirectPlaybackCommandCallbacks {
        var nextCount = 0
        var previousCount = 0
        var stopCount = 0
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun next() { nextCount++ }
        override fun previous() { previousCount++ }
        override fun stop() { stopCount++ }
    }
}
