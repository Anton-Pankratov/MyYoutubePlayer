package kg.dev.shared.feature.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeEmbedTest {
    @Test
    fun buildsOfficialEmbedUrlWithStartPosition() {
        assertEquals(
            "https://www.youtube.com/embed/M7lc1UVf-VE?playsinline=1&rel=0&start=12",
            youtubeEmbedUrl("M7lc1UVf-VE", 12_999)
        )
    }

    @Test
    fun rejectsUnsafeVideoIds() {
        assertNull(youtubeEmbedUrl("</iframe>"))
        assertNull(youtubePlayerHtml("</script>"))
    }

    @Test
    fun buildsIFrameApiHostWithValidatedMediaAndStart() {
        val html = youtubePlayerHtml("M7lc1UVf-VE", 12_999)!!
        assertTrue("videoId: 'M7lc1UVf-VE'" in html)
        assertTrue("start: 12" in html)
        assertTrue("enablejsapi: 1" in html)
        assertTrue("origin: 'https://kg.dev.videoplayer'" in html)
        assertTrue("AndroidPlayerBridge.onReady()" in html)
        assertTrue("AndroidPlayerBridge.onStateChanged" in html)
        assertTrue("AndroidPlayerBridge.onError" in html)
        assertTrue("AndroidPlayerBridge.onProgress" in html)
    }
}
