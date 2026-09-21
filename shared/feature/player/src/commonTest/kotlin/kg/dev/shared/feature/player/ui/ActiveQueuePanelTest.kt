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

    @Test
    fun currentMarkerFollowsPendingLogicalTargetAndKeepsDuplicateReferencesDistinct() {
        val duplicate = MediaReference(MediaProviders.Direct, "duplicate")
        val rows = PlaybackQueueState(
            items = listOf(
                MediaCatalogItem(duplicate, "first duplicate"),
                MediaCatalogItem(duplicate, "second duplicate"),
                item("third"),
            ),
            currentIndex = 0,
            pendingIndex = 1,
        ).activeQueueRows()

        assertEquals(listOf(0, 1, 2), rows.map(ActiveQueueRow::index))
        assertEquals(listOf(false, true, false), rows.map(ActiveQueueRow::isCurrent))
    }

    @Test
    fun currentMarkerMovesWhenLogicalQueueTargetChanges() {
        val items = listOf(item("first"), item("second"), item("third"))

        val firstRows = PlaybackQueueState(items = items, currentIndex = 0).activeQueueRows()
        val nextRows = PlaybackQueueState(items = items, currentIndex = 0, pendingIndex = 1).activeQueueRows()

        assertEquals(listOf(true, false, false), firstRows.map(ActiveQueueRow::isCurrent))
        assertEquals(listOf(false, true, false), nextRows.map(ActiveQueueRow::isCurrent))
    }
}

private fun item(id: String) = MediaCatalogItem(
    reference = MediaReference(MediaProviders.Direct, id),
    title = id,
)
