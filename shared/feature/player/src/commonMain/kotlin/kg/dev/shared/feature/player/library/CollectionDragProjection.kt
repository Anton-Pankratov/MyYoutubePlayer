package kg.dev.shared.feature.player.library

import kg.dev.shared.core.common.media.MediaReference

/**
 * Pure, transient ordering used while a collection row is being dragged. The repository remains
 * authoritative; this projection is only converted to one [MediaCollectionRepository.moveMedia]
 * command when the gesture ends.
 */
internal fun projectCollectionDrag(
    items: List<CollectionMedia>,
    draggedReference: MediaReference,
    destinationIndex: Int,
): List<CollectionMedia> {
    val sourceIndex = items.indexOfFirst { it.reference == draggedReference }
    if (sourceIndex < 0 || items.size < 2) return items

    val projected = items.toMutableList()
    val dragged = projected.removeAt(sourceIndex)
    projected.add(destinationIndex.coerceIn(0, projected.size), dragged)
    return projected
}

internal fun collectionDragBeforeReference(
    projectedItems: List<CollectionMedia>,
    draggedReference: MediaReference,
): MediaReference? {
    val draggedIndex = projectedItems.indexOfFirst { it.reference == draggedReference }
    return projectedItems.getOrNull(draggedIndex + 1)?.reference
}

internal fun sameCollectionItemSequence(
    first: List<CollectionMedia>,
    second: List<CollectionMedia>,
): Boolean = first.map { it.reference } == second.map { it.reference }
