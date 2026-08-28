package kg.dev.shared.feature.player

import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IosVideoPlayerControllerTest {
    @Test
    fun mapsNativeLifecycleIntoCommonPlaybackState() {
        assertEquals(PlaybackState.Loading, iosDirectPlaybackState(0, AVPlayerTimeControlStatusPaused, false))
        assertEquals(PlaybackState.Ready, iosDirectPlaybackState(AVPlayerItemStatusReadyToPlay, AVPlayerTimeControlStatusPaused, false))
        assertEquals(PlaybackState.Playing, iosDirectPlaybackState(AVPlayerItemStatusReadyToPlay, AVPlayerTimeControlStatusPlaying, false))
        assertEquals(PlaybackState.Paused, iosDirectPlaybackState(AVPlayerItemStatusReadyToPlay, AVPlayerTimeControlStatusPaused, true))
        assertEquals(PlaybackState.Buffering, iosDirectPlaybackState(AVPlayerItemStatusReadyToPlay, AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate, true))
        assertIs<PlaybackState.Error>(iosDirectPlaybackState(AVPlayerItemStatusFailed, AVPlayerTimeControlStatusPaused, false))
    }

    @Test
    fun convertsMillisecondsWithoutChangingUnits() {
        assertEquals(0.0, millisecondsToSeconds(0))
        assertEquals(42.0, millisecondsToSeconds(42_000))
        assertEquals(42.5, millisecondsToSeconds(42_500))
        assertEquals(0.0, millisecondsToSeconds(-1))
    }

    @Test
    fun acceptsOnlyIosCompatibleDirectUris() {
        assertEquals(true, isIosDirectUriSupported("https://example.test/video.mp4"))
        assertEquals(true, isIosDirectUriSupported("http://example.test/video.mp4"))
        assertEquals(true, isIosDirectUriSupported("file:///private/video.mp4"))
        assertEquals(false, isIosDirectUriSupported("content://media/external/video/1"))
        assertEquals(false, isIosDirectUriSupported("not-a-url"))
    }
}
