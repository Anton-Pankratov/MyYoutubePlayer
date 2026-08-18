package kg.dev.shared.feature.history.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DefaultHistoryComponent(
    componentContext: ComponentContext,
    private val repository: HistoryRepository,
    private val onItemSelected: (HistoryItemUiModel) -> Unit = {},
    coroutineContext: CoroutineContext = Dispatchers.Default
) : HistoryComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val mutableState = MutableStateFlow(HistoryUiState())
    override val state: StateFlow<HistoryUiState> = mutableState.asStateFlow()

    init {
        lifecycle.subscribe(object : Lifecycle.Callbacks {
            override fun onDestroy() = scope.cancel()
        })
        refresh()
    }

    override fun refresh() {
        scope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = false)
            mutableState.value = try {
                HistoryUiState(items = repository.recent().map { video ->
                    HistoryItemUiModel(video.reference, video.title, video.thumbnailUrl, video.positionMs, video.durationMs, video.watchedAtEpochMs)
                })
            } catch (_: Throwable) {
                HistoryUiState(error = true)
            }
        }
    }

    override fun delete(reference: MediaReference) {
        scope.launch {
            repository.delete(reference)
            refresh()
        }
    }

    override fun select(item: HistoryItemUiModel) = onItemSelected(item)
}
