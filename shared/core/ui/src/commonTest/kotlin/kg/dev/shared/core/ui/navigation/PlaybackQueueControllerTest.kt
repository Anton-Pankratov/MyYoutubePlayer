package kg.dev.shared.core.ui.navigation

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackQueueControllerTest {
    @Test fun startNextPreviousAndCompletionUseOneSnapshot() {
        val requests = mutableListOf<Pair<Int, Long>>()
        val queue = PlaybackQueueController { index, generation -> requests += index to generation }
        queue.start(items("a", "b", "c"))
        val generation = queue.state.value.generation
        assertEquals(0 to generation, requests.single())
        assertTrue(queue.settle(0, generation))
        queue.next(); assertEquals(1, requests.last().first)
        assertTrue(queue.settle(1, generation))
        queue.previous(); assertEquals(0, requests.last().first)
        assertTrue(queue.settle(0, generation))
        queue.onCompleted(MediaReference(MediaProviderId("youtube"), "a"))
        assertEquals(1, requests.last().first)
    }

    @Test fun staleCompletionAndFinalCompletionDoNotAdvanceTwice() {
        val requests = mutableListOf<Pair<Int, Long>>()
        val queue = PlaybackQueueController { index, generation -> requests += index to generation }
        queue.start(items("a")); val generation = queue.state.value.generation
        queue.settle(0, generation)
        queue.onCompleted(MediaReference(MediaProviderId("youtube"), "other"))
        assertEquals(1, requests.size)
        queue.onCompleted(MediaReference(MediaProviderId("youtube"), "a"))
        assertFalse(queue.state.value.isActive)
        queue.onCompleted(MediaReference(MediaProviderId("youtube"), "a"))
        assertEquals(1, requests.size)
    }

    @Test fun previousSkipsUnavailableItemsBackwardAndKeepsCurrentWhenNoneRemain() {
        val requests = mutableListOf<Pair<Int, Long>>()
        val queue = PlaybackQueueController { index, generation -> requests += index to generation }
        queue.start(items("a", "b", "c", "d")); val generation = queue.state.value.generation
        queue.settle(3, generation)
        queue.previous(); queue.unavailable(2, generation, forward = false)
        queue.unavailable(1, generation, forward = false)
        assertEquals(0, requests.last().first)
        queue.unavailable(0, generation, forward = false)
        assertEquals(3, queue.state.value.currentIndex)
        assertEquals(null, queue.state.value.pendingIndex)
    }

    private fun items(vararg ids: String) = ids.map { id ->
        MediaCatalogItem(MediaReference(MediaProviderId("youtube"), id), id)
    }
}
