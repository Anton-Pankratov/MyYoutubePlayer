package kg.dev.shared.feature.player.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.MediaSpacing

@Composable
fun LibraryHubContent(component: LibraryHubComponent, modifier: Modifier = Modifier) {
    val destination by component.destination.collectAsState()
    var picker by remember { mutableStateOf<CollectionPickerComponent?>(null) }
    when (val current = destination) {
        LibraryHubDestination.Saved -> Column(modifier.fillMaxSize()) {
            OutlinedButton(onClick = component::showCollections, modifier = Modifier.padding(MediaSpacing.lg)) { Text("Collections") }
            LibraryContent(component.savedLibrary, Modifier.weight(1f)) { media -> picker = component.picker(media) }
        }
        LibraryHubDestination.Collections -> CollectionListContent(
            component = component.collections,
            back = component::showSaved,
            onRenameRequest = component.collections::requestRename,
            onDeleteRequest = component.collections::requestDelete,
            modifier = modifier,
        )
        is LibraryHubDestination.Detail -> CollectionDetailContent(component.detail(current.id), component::showCollections, modifier)
    }
    picker?.let { PickerContent(it, onConfirm = { picker = null }, onCancel = { it.cancel(); picker = null }) }
    when (val dialog = component.collections.dialog.collectAsState().value) {
        is CollectionDialogState.Rename -> {
        CollectionNameDialog(
            title = "Rename collection",
            name = dialog.draft,
            confirmLabel = "Rename",
            onNameChanged = component.collections::updateRenameDraft,
            onConfirm = component.collections::confirmRename,
            onCancel = component.collections::cancelDialog,
        )
        }
        is CollectionDialogState.Delete -> {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = component.collections::cancelDialog,
            title = { Text("Delete collection?") },
            text = { Text("This deletes \"${dialog.collection.name}\" and its collection entries. Saved media and playback history are unchanged.") },
            confirmButton = {
                OutlinedButton(onClick = component.collections::confirmDelete) { Text("Delete") }
            },
            dismissButton = { OutlinedButton(onClick = component.collections::cancelDialog) { Text("Cancel") } },
        )
        }
        CollectionDialogState.None -> Unit
    }
}

@Composable
private fun PickerContent(picker: CollectionPickerComponent, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val state by picker.state.collectAsState()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Add to collections") },
        text = {
            Column {
                state.collections.forEach { collection ->
                    Row(Modifier.fillMaxWidth().clickable { picker.toggle(collection.id) }) {
                        Checkbox(collection.id in state.selected, { picker.toggle(collection.id) })
                        Text(collection.name)
                    }
                }
            }
        },
        confirmButton = { OutlinedButton(onClick = { picker.confirm(onConfirm) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun CollectionListContent(
    component: CollectionListComponent,
    back: () -> Unit,
    onRenameRequest: (MediaCollection) -> Unit,
    onDeleteRequest: (MediaCollection) -> Unit,
    modifier: Modifier,
) {
    val state by component.state.collectAsState()
    var name by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(MediaSpacing.lg)) {
        Row { OutlinedButton(onClick = back) { Text("Saved") }; OutlinedButton(onClick = { component.create(name); name = "" }) { Text("New Collection") } }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Collection name") })
        when (val value = state) {
            CollectionListUiState.Loading -> Text("Loading collections")
            CollectionListUiState.Empty -> EmptyState("Collections", "No collections yet")
            CollectionListUiState.Error -> EmptyState("Collections unavailable", "Try again")
            is CollectionListUiState.Content -> value.collections.forEach { collection ->
                Row(Modifier.fillMaxWidth().clickable { component.open(collection.id) }.padding(MediaSpacing.sm)) {
                    Text(collection.name, Modifier.weight(1f)); Text(collection.itemCount.toString())
                    OutlinedButton(onClick = { onRenameRequest(collection) }) { Text("Rename") }
                    OutlinedButton(onClick = { onDeleteRequest(collection) }) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun CollectionNameDialog(
    title: String,
    name: String,
    confirmLabel: String,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Collection name") },
            )
        },
        confirmButton = { OutlinedButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { OutlinedButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun CollectionDetailContent(component: CollectionDetailComponent, back: () -> Unit, modifier: Modifier) {
    val state by component.state.collectAsState()
    var draggedReference by remember { mutableStateOf<kg.dev.shared.core.common.media.MediaReference?>(null) }
    var projectedItems by remember { mutableStateOf<List<CollectionMedia>>(emptyList()) }
    var dragStartSequence by remember { mutableStateOf<List<kg.dev.shared.core.common.media.MediaReference>>(emptyList()) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    Column(modifier.fillMaxSize().padding(MediaSpacing.lg)) {
        OutlinedButton(onClick = back) { Text("Collections") }
        when (val value = state) {
            CollectionDetailUiState.Loading -> Text("Loading collection")
            CollectionDetailUiState.NotFound -> EmptyState("Collection unavailable", "It may have been deleted")
            CollectionDetailUiState.Error -> EmptyState("Collection unavailable", "Try again")
            is CollectionDetailUiState.Content -> {
                Text(value.detail.collection.name)
                if (value.detail.items.isEmpty()) EmptyState("Collection", "No items yet")
                val authoritativeItems = value.detail.items
                val authoritativeSequence = authoritativeItems.map { it.reference }
                LaunchedEffect(authoritativeSequence) {
                    if (draggedReference != null && dragStartSequence != authoritativeSequence) {
                        draggedReference = null
                        projectedItems = emptyList()
                        dragStartSequence = emptyList()
                        dragOffsetY = 0f
                    }
                }
                val visibleItems = if (draggedReference == null) authoritativeItems else projectedItems
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(Modifier.weight(1f), state = listState) {
                    itemsIndexed(
                        items = visibleItems,
                        key = { _, media -> "${media.reference.provider.value}\u0000${media.reference.externalId}" },
                    ) { index, media ->
                        val isDragged = draggedReference == media.reference
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .graphicsLayer { if (isDragged) translationY = dragOffsetY }
                                .clickable { if (draggedReference == null) component.open(media) }
                                .padding(MediaSpacing.sm),
                        ) {
                            Text(media.title, Modifier.weight(1f))
                            Text(
                                "Reorder",
                                Modifier
                                    .semantics { contentDescription = "Reorder" }
                                    .pointerInput(media.reference, authoritativeSequence, draggedReference) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                if (draggedReference != null) return@detectDragGesturesAfterLongPress
                                                draggedReference = media.reference
                                                projectedItems = authoritativeItems
                                                dragStartSequence = authoritativeSequence
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedReference = null
                                                projectedItems = emptyList()
                                                dragStartSequence = emptyList()
                                                dragOffsetY = 0f
                                            },
                                            onDragEnd = {
                                                val dragged = draggedReference
                                                val projected = projectedItems
                                                if (dragged != null &&
                                                    dragStartSequence == authoritativeSequence &&
                                                    !sameCollectionItemSequence(authoritativeItems, projected)
                                                ) {
                                                    component.moveBefore(dragged, collectionDragBeforeReference(projected, dragged))
                                                }
                                                draggedReference = null
                                                projectedItems = emptyList()
                                                dragStartSequence = emptyList()
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, amount ->
                                                if (draggedReference != media.reference) return@detectDragGesturesAfterLongPress
                                                change.consume()
                                                dragOffsetY += amount.y
                                                val currentIndex = projectedItems.indexOfFirst { it.reference == media.reference }
                                                val visible = listState.layoutInfo.visibleItemsInfo
                                                val current = visible.firstOrNull { it.index == currentIndex }
                                                    ?: return@detectDragGesturesAfterLongPress
                                                val center = current.offset + current.size / 2 + dragOffsetY
                                                val destination = when {
                                                    amount.y > 0f -> visible.firstOrNull { it.index == currentIndex + 1 }
                                                        ?.takeIf { center > it.offset + it.size / 2 }
                                                        ?.index
                                                    amount.y < 0f -> visible.firstOrNull { it.index == currentIndex - 1 }
                                                        ?.takeIf { center < it.offset + it.size / 2 }
                                                        ?.index
                                                    else -> null
                                                }
                                                if (destination != null) {
                                                    projectedItems = projectCollectionDrag(projectedItems, media.reference, destination)
                                                    dragOffsetY = 0f
                                                }
                                            },
                                        )
                                    },
                            )
                            if (index > 0) OutlinedButton(
                                onClick = { component.moveUp(media.reference) },
                                enabled = draggedReference == null,
                            ) { Text("Move up") }
                            if (index < visibleItems.lastIndex) OutlinedButton(
                                onClick = { component.moveDown(media.reference) },
                                enabled = draggedReference == null,
                            ) { Text("Move down") }
                            OutlinedButton(
                                onClick = { component.remove(media) },
                                enabled = draggedReference == null,
                            ) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
