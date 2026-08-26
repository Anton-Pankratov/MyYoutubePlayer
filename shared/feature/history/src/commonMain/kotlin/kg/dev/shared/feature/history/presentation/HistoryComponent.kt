package kg.dev.shared.feature.history.presentation

import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.history.domain.ResumeDecision
import kotlinx.coroutines.flow.StateFlow

data class HistoryItemUiModel(
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val watchedAtEpochMs: Long,
    val resumeDecision: ResumeDecision
) {
    val startPositionMs: Long get() = resumeDecision.startPositionMs
}

data class HistoryUiState(
    val isLoading: Boolean = false,
    val items: List<HistoryItemUiModel> = emptyList(),
    val error: Boolean = false
)

interface HistoryComponent {
    val state: StateFlow<HistoryUiState>
    fun refresh()
    fun delete(reference: MediaReference)
    fun select(item: HistoryItemUiModel)
}
