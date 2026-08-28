package kg.dev.shared.feature.player.ui

import kg.dev.shared.feature.player.PlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class IosYouTubePlaybackAdapterTest {
    @Test
    fun mapsOfficialYouTubeStatesToCommonPlaybackState() {
        assertEquals(PlaybackState.Loading, iosYouTubePlaybackState("-1"))
        assertEquals(PlaybackState.Completed, iosYouTubePlaybackState("0"))
        assertEquals(PlaybackState.Playing, iosYouTubePlaybackState("1"))
        assertEquals(PlaybackState.Paused, iosYouTubePlaybackState("2"))
        assertEquals(PlaybackState.Buffering, iosYouTubePlaybackState("3"))
        assertEquals(PlaybackState.Ready, iosYouTubePlaybackState("5"))
    }

    @Test
    fun parsesBridgeTelemetryAndConvertsSecondsToMilliseconds() {
        val event = parseIosYouTubeBridgeMessage("state|1|42.5|180")

        assertIs<IosYouTubeBridgeEvent.State>(event)
        assertEquals("1", event.code)
        assertEquals(42_500L, secondsToMilliseconds(event.positionSeconds))
        assertEquals(180_000L, secondsToMilliseconds(event.durationSeconds))
    }

    @Test
    fun rejectsMalformedOrInvalidTelemetry() {
        assertNull(parseIosYouTubeBridgeMessage("state|1|not-a-number|180"))
        assertNull(parseIosYouTubeBridgeMessage("progress|0|-1|180"))
        assertNull(parseIosYouTubeBridgeMessage("unknown|payload"))
        assertEquals(0L, secondsToMilliseconds(Double.NaN))
        assertEquals(0L, secondsToMilliseconds(-1.0))
    }
}
