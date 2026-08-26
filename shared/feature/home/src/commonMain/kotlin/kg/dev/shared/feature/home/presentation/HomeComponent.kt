package kg.dev.shared.feature.home.presentation

import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.feature.history.domain.ResumeDecision
import kotlinx.coroutines.flow.StateFlow

data class HomeMediaItemUiModel(
    val reference: MediaReference,
    val title: String,
    val thumbnailUrl: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val watchedAtEpochMs: Long,
    val resumeDecision: ResumeDecision,
    val isAvailable: Boolean
) {
    val startPositionMs: Long get() = resumeDecision.startPositionMs
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val continueWatching: List<HomeMediaItemUiModel> = emptyList(),
    val recentlyWatched: List<HomeMediaItemUiModel> = emptyList(),
    val error: Boolean = false
)

fun interface HomeMediaAvailability {
    fun isAvailable(reference: MediaReference): Boolean
}

interface HomeComponent {
    val state: StateFlow<HomeUiState>
    val isLocalMediaImportAvailable: Boolean
    fun refresh()
    fun select(item: HomeMediaItemUiModel)
    fun requestLocalMediaImport()
}
