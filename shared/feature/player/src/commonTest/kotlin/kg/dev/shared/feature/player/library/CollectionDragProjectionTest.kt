package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionDragProjectionTest {
    @Test fun firstToLastDerivesMoveToEnd() {
        val items = items("a", "b", "c", "d")
        val projected = projectCollectionDrag(items, items.first().reference, 3)

        assertEquals(listOf("b", "c", "d", "a"), projected.map { it.reference.externalId })
        assertEquals(null, collectionDragBeforeReference(projected, items.first().reference))
    }

    @Test fun lastToFirstAndMiddleMovesDeriveTheFollowingProviderQualifiedReference() {
        val items = items("a", "b", "c", "d")

        val lastToFirst = projectCollectionDrag(items, items.last().reference, 0)
        assertEquals(listOf("d", "a", "b", "c"), lastToFirst.map { it.reference.externalId })
        assertEquals(items.first().reference, collectionDragBeforeReference(lastToFirst, items.last().reference))

        val middleDown = projectCollectionDrag(items, items[1].reference, 2)
        assertEquals(listOf("a", "c", "b", "d"), middleDown.map { it.reference.externalId })
        assertEquals(items[3].reference, collectionDragBeforeReference(middleDown, items[1].reference))

        val middleUp = projectCollectionDrag(items, items[2].reference, 1)
        assertEquals(listOf("a", "c", "b", "d"), middleUp.map { it.reference.externalId })
        assertEquals(items[1].reference, collectionDragBeforeReference(middleUp, items[2].reference))
    }

    @Test fun providerQualifiedIdentityPreventsExternalIdCollisionsAndProjectionPreservesSnapshots() {
        val youtube = media("youtube", "same", "YouTube")
        val direct = media("direct", "same", "Direct")
        val other = media("youtube", "other", "Other")
        val items = listOf(youtube, direct, other)

        val projected = projectCollectionDrag(items, direct.reference, 0)

        assertEquals(listOf(direct.reference, youtube.reference, other.reference), projected.map { it.reference })
        assertEquals(youtube.reference, collectionDragBeforeReference(projected, direct.reference))
        assertEquals(direct, projected.first())
        assertTrue(!sameCollectionItemSequence(items, projected))
    }

    @Test fun sameSlotEmptyAndSingleItemAreNoOp() {
        val items = items("a", "b")
        assertTrue(sameCollectionItemSequence(items, projectCollectionDrag(items, items[1].reference, 1)))
        assertTrue(projectCollectionDrag(emptyList(), MediaReference(MediaProviderId("youtube"), "missing"), 0).isEmpty())
        val single = items("only")
        assertEquals(single, projectCollectionDrag(single, single.first().reference, 0))
    }

    private fun items(vararg ids: String) = ids.map { media("youtube", it, it) }

    private fun media(provider: String, id: String, title: String) = CollectionMedia(
        collectionId = CollectionId("collection"),
        reference = MediaReference(MediaProviderId(provider), id),
        title = title,
        thumbnailUrl = "thumb-$id",
        authorTitle = "author-$id",
        durationMs = 1,
        addedAtEpochMs = 2,
    )
}
