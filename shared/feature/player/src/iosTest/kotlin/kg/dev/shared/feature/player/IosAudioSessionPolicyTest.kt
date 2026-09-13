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
        assertTrue(policy.onInterruptionBegan(1, wasPlaying = true))

        assertTrue(policy.claimResume(1, systemAllowsResume = true))
        assertFalse(policy.claimResume(2, systemAllowsResume = true))
    }

    @Test
    fun userPauseStopAndRouteLossCancelInterruptionResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onPause(1)
        assertFalse(policy.claimResume(1, systemAllowsResume = true))

        policy.onSessionStarted(2)
        policy.onInterruptionBegan(2, wasPlaying = true)
        policy.onRouteLost(2)
        assertFalse(policy.claimResume(2, systemAllowsResume = true))

        policy.onSessionStarted(3)
        policy.onInterruptionBegan(3, wasPlaying = true)
        policy.onStop(3)
        assertFalse(policy.claimResume(3, systemAllowsResume = true))
    }

    @Test
    fun sessionReplacementInvalidatesOldInterruptionResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onSessionStarted(2)

        assertFalse(policy.claimResume(1, systemAllowsResume = true))
        assertFalse(policy.claimResume(2, systemAllowsResume = true))
    }

    @Test
    fun duplicateInterruptionBeginIsIdempotent() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(7)

        assertTrue(policy.onInterruptionBegan(7, wasPlaying = true))
        assertFalse(policy.onInterruptionBegan(7, wasPlaying = true))
        assertTrue(policy.claimResume(7, systemAllowsResume = true))
    }

    @Test
    fun interruptionEndWithoutBeginNeverResumes() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(7)

        assertFalse(policy.claimResume(7, systemAllowsResume = true))
    }

    @Test
    fun duplicateInterruptionEndConsumesResumeOnlyOnce() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(7)
        policy.onInterruptionBegan(7, wasPlaying = true)

        assertTrue(policy.claimResume(7, systemAllowsResume = true))
        assertFalse(policy.claimResume(7, systemAllowsResume = true))
    }

    @Test
    fun routeLossDuringInterruptionPreventsResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(7)
        policy.onInterruptionBegan(7, wasPlaying = true)
        assertTrue(policy.onRouteLost(7))

        assertFalse(policy.claimResume(7, systemAllowsResume = true))
    }

    @Test
    fun replacementInvalidatesAlreadyEligibleResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onSessionStarted(2)

        assertFalse(policy.claimResume(1, systemAllowsResume = true))
    }

    @Test
    fun stopInvalidatesAlreadyEligibleResume() {
        val policy = IosAudioSessionPolicy()
        policy.onSessionStarted(1)
        policy.onInterruptionBegan(1, wasPlaying = true)
        policy.onStop(1)

        assertFalse(policy.claimResume(1, systemAllowsResume = true))
    }

    @Test
    fun newDeviceAvailableDoesNotResumeRouteLossPause() {
        assertEquals(IosRouteChangeAction.Ignore, iosRouteChangeAction(isOldDeviceUnavailable = false))
        assertEquals(IosRouteChangeAction.PauseAndCancelResume, iosRouteChangeAction(isOldDeviceUnavailable = true))
    }

    @Test
    fun staleProgressCallbackCannotMutateCurrentSession() {
        val gate = IosHostSessionGeneration()
        val first = gate.replaceOrInvalidate()
        val second = gate.replaceOrInvalidate()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun staleCompletionCannotCompleteReplacementSession() {
        val gate = IosHostSessionGeneration()
        val first = gate.replaceOrInvalidate()
        gate.replaceOrInvalidate()

        assertFalse(gate.claimCompletion(first))
    }

    @Test
    fun duplicateCompletionIsEmittedAtMostOncePerGeneration() {
        val gate = IosHostSessionGeneration()
        val generation = gate.replaceOrInvalidate()

        assertTrue(gate.claimCompletion(generation))
        assertFalse(gate.claimCompletion(generation))
    }

    @Test
    fun completionAfterStopIsIgnored() {
        val gate = IosHostSessionGeneration()
        val generation = gate.replaceOrInvalidate()
        gate.replaceOrInvalidate()

        assertFalse(gate.claimCompletion(generation))
    }

    @Test
    fun currentItemFailureProducesErrorWithoutCompletion() {
        assertEquals(PlaybackState.Error(PlayerError.SourceUnavailable), iosCurrentItemFailureState())
        assertFalse(iosCurrentItemFailureState() == PlaybackState.Completed)
    }

    @Test
    fun staleFailureCannotFailReplacementSession() {
        val gate = IosHostSessionGeneration()
        val first = gate.replaceOrInvalidate()
        gate.replaceOrInvalidate()

        assertFalse(gate.isCurrent(first))
    }

    @Test
    fun nowPlayingProjectionOmitsInvalidDurationAndUsesPausedRate() {
        val media = testMedia()
        val projection = nowPlayingProjection(media, PlayerState(media, PlaybackState.Paused, 4_200, null, 1))

        assertEquals("Title", projection.title)
        assertEquals("Author", projection.author)
        assertNull(projection.durationSeconds)
        assertEquals(4.2, projection.elapsedSeconds)
        assertEquals(0.0, projection.playbackRate)
        assertEquals(1.0, nowPlayingProjection(media, PlayerState(media, PlaybackState.Playing, 4_200, 60_000, 1)).playbackRate)
        assertEquals(0.0, nowPlayingProjection(media, PlayerState(media, PlaybackState.Error(PlayerError.SourceUnavailable), 4_200, 60_000, 1)).playbackRate)
    }

    @Test
    fun remoteRouterRoutesCommandsAndRejectsInvalidSeek() {
        val calls = mutableListOf<String>()
        val router = IosRemoteCommandRouter(callbacks(calls))

        assertTrue(router.play(true))
        assertTrue(router.pause(true))
        assertTrue(router.seek(42.0, 40_000))
        assertFalse(router.seek(Double.NaN, null))
        assertTrue(router.next(true))
        assertTrue(router.previous(true))
        assertTrue(router.stop(true))
        assertFalse(router.play(false))
        assertFalse(router.pause(false))
        assertFalse(router.next(false))
        assertFalse(router.previous(false))
        assertTrue(router.stop(false))
        assertEquals(listOf("play", "pause", "seek:40000", "next", "previous", "stop"), calls)
    }

    private fun testMedia() = PlayableMedia(
        MediaCatalogItem(MediaReference(MediaProviderId("direct"), "a"), "Title", authorTitle = "Author"),
        PlaybackSource.Direct("https://example.test/a.mp3", "audio/mpeg"),
    )

    private fun callbacks(calls: MutableList<String>) = object : DirectPlaybackCommandCallbacks {
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun seekTo(positionMs: Long) { calls += "seek:$positionMs" }
        override fun next() { calls += "next" }
        override fun previous() { calls += "previous" }
        override fun stop() { calls += "stop" }
    }
}
