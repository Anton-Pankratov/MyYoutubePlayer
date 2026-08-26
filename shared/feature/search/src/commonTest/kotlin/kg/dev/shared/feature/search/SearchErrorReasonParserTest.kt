package kg.dev.shared.feature.search

import kg.dev.shared.feature.search.data.remote.extractYouTubeErrorReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchErrorReasonParserTest {
    @Test
    fun extractsNormalYouTubeErrorReason() {
        assertEquals(
            "quotaExceeded",
            extractYouTubeErrorReason("""{"error":{"errors":[{"reason":"quotaExceeded"}]}}""")
        )
    }

    @Test
    fun extractsReasonWithWhitespaceAndRepresentativeAndroidRestrictionValue() {
        assertEquals(
            "API_KEY_ANDROID_APP_BLOCKED",
            extractYouTubeErrorReason("""{ "details" : [ { "reason" : "API_KEY_ANDROID_APP_BLOCKED" } ] }""")
        )
    }

    @Test
    fun returnsNullForMissingEmptyOrMalformedReason() {
        assertNull(extractYouTubeErrorReason("""{"error":{}}"""))
        assertNull(extractYouTubeErrorReason("""{"reason":""}"""))
        assertNull(extractYouTubeErrorReason("""{"reason": quotaExceeded}"""))
        assertNull(extractYouTubeErrorReason("not-json"))
    }
}
