package kg.dev.shared.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kg.dev.shared.core.ui.design.MediaShapes
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.navigation.PlaybackQueueState

internal data class ActiveQueueRow(
    val index: Int,
    val isCurrent: Boolean,
)

internal fun PlaybackQueueState.activeQueueRows(): List<ActiveQueueRow> =
    items.indices.map { index -> ActiveQueueRow(index = index, isCurrent = index == logicalCurrentIndex) }

/** Shared, presentation-only view of Root's immutable active queue snapshot. */
@Composable
internal fun ActiveQueuePanel(
    queue: PlaybackQueueState,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentIndex = queue.logicalCurrentIndex
    val rows = queue.activeQueueRows()
    LaunchedEffect(queue.generation, currentIndex) {
        currentIndex?.let { listState.scrollToItem(it) }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Active queue", style = MediaTheme.typography.sectionTitle) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 440.dp)
                    .semantics { contentDescription = "Active queue" },
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.index },
                ) { _, row ->
                    val index = row.index
                    val item = queue.items[index]
                    val isCurrent = row.isCurrent
                    val rowDescription = buildString {
                        append("${index + 1}. ${item.title}")
                        if (isCurrent) append(", current queue item")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) MediaTheme.colors.surfaceSelected else MediaTheme.colors.surface,
                                MediaShapes.medium,
                            )
                            .semantics {
                                contentDescription = rowDescription
                                selected = isCurrent
                            }
                            .clickable(enabled = !isCurrent) { onSelect(index) }
                            .padding(MediaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm),
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MediaTheme.typography.label,
                            color = MediaTheme.colors.textTertiary,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xxs)) {
                            Text(
                                text = item.title,
                                style = if (isCurrent) MediaTheme.typography.cardTitle else MediaTheme.typography.body,
                                color = MediaTheme.colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.authorTitle?.takeIf(String::isNotBlank)?.let { author ->
                                Text(
                                    text = author,
                                    style = MediaTheme.typography.metadata,
                                    color = MediaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (isCurrent) {
                            Text(
                                text = "Current",
                                style = MediaTheme.typography.label,
                                color = MediaTheme.colors.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Done") }
        },
    )
}
