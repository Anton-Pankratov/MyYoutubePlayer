package kg.dev.shared.feature.player

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosAudioSessionPolicyTest {
    @Test
    fun interruptionResumesOnlyTheCurrentWantedSession() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)

        assertTrue(policy.shouldResume(1, systemAllowsResume = true))
        assertFalse(policy.shouldResume(2, systemAllowsResume = true))
    }

    @Test
    fun userPauseStopAndRouteLossCancelInterruptionResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onPauseOrStop()
        assertFalse(policy.shouldResume(1, systemAllowsResume = true))

        policy.onSessionStarted(2)
        policy.onInterruptionBegan(2, wasPlaying = true)
        policy.onRouteLost()
        assertFalse(policy.shouldResume(2, systemAllowsResume = true))
    }

    @Test
    fun sessionReplacementInvalidatesOldInterruptionResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onSessionStarted(2)

        assertFalse(policy.shouldResume(1, systemAllowsResume = true))
        assertFalse(policy.shouldResume(2, systemAllowsResume = true))
    }

    @Test
    fun nowPlayingProjectionOmitsInvalidDurationAndUsesPausedRate() {
        val media = PlayableMedia(
            MediaCatalogItem(MediaReference(MediaProviderId("direct"), "a"), "Title", authorTitle = "Author"),
            PlaybackSource.Direct("https://example.test/a.mp3", "audio/mpeg"),
        )
        val projection = nowPlayingProjection(media, PlayerState(media, PlaybackState.Paused, 4_200, null, 1))

        assertEquals("Title", projection.title)
        assertEquals("Author", projection.author)
        assertNull(projection.durationSeconds)
        assertEquals(4.2, projection.elapsedSeconds)
        assertEquals(0.0, projection.playbackRate)
    }

    @Test
    fun remoteRouterRoutesCommandsAndRejectsInvalidSeek() {
        val calls = mutableListOf<String>()
        val router = IosRemoteCommandRouter(object : DirectPlaybackCommandCallbacks {
            override fun play() { calls += "play" }
            override fun pause() { calls += "pause" }
            override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
            override fun next() { calls += "next" }
            override fun previous() { calls += "previous" }
            override fun stop() { calls += "stop" }
        })

        assertTrue(router.play(true))
        assertTrue(router.pause(true))
        assertTrue(router.seek(42.0, 40_000))
        assertFalse(router.seek(Double.NaN, null))
        router.next(); router.previous(); router.stop()
        assertEquals(listOf("play", "pause", "seek:40000", "next", "previous", "stop"), calls)
    }
}
