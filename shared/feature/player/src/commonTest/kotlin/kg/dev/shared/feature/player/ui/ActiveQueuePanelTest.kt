package kg.dev.shared.feature.player.ui

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.ui.navigation.PlaybackQueueState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActiveQueuePanelTest {
    @Test
    fun rowsPreserveQueueSnapshotOrderAndCurrentMarker() {
        val rows = PlaybackQueueState(
            items = listOf(item("first"), item("second"), item("third")),
            currentIndex = 1,
        ).activeQueueRows()

        assertEquals(listOf(0, 1, 2), rows.map(ActiveQueueRow::index))
        assertEquals(listOf(false, true, false), rows.map(ActiveQueueRow::isCurrent))
    }

    @Test
    fun emptyQueueHasNoPresentationRows() {
        assertTrue(PlaybackQueueState().activeQueueRows().isEmpty())
    }
}

private fun item(id: String) = MediaCatalogItem(
    reference = MediaReference(MediaProviders.Direct, id),
    title = id,
)
