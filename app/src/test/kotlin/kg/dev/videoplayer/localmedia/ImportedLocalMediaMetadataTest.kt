package kg.dev.videoplayer.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedLocalMediaMetadataTest {
    @Test
    fun mapsPickerMetadataToDurableDirectDescriptor() {
        val descriptor = ImportedLocalMediaMetadata(
            title = "Lesson.mp4",
            uri = "content://documents/video/42",
            mimeType = "video/mp4",
            durationMs = 90_000
        ).toDirectDescriptor("stable-id")

        assertEquals("stable-id", descriptor.id)
        assertEquals("Lesson.mp4", descriptor.title)
        assertEquals("content://documents/video/42", descriptor.uri)
        assertEquals("video/mp4", descriptor.mimeType)
        assertEquals(90_000L, descriptor.durationMs)
    }

    @Test
    fun preservesMissingOptionalPickerMetadata() {
        val descriptor = ImportedLocalMediaMetadata("Imported video", "content://documents/video/43", null, null)
            .toDirectDescriptor("stable-id")

        assertNull(descriptor.mimeType)
        assertNull(descriptor.durationMs)
    }
}
