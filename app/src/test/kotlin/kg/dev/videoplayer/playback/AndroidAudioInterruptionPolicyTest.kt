package kg.dev.videoplayer.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAudioInterruptionPolicyTest {
    @Test
    fun taskRemovalKeepsOnlyOngoingPlaybackAlive() {
        val policy = AndroidAudioInterruptionPolicy()

        assertFalse(policy.shouldStopServiceAfterTaskRemoval(playbackOngoing = true))
        assertTrue(policy.shouldStopServiceAfterTaskRemoval(playbackOngoing = false))
    }
}
