package kg.dev.shared.feature.home.presentation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.ResumeDecision
import kg.dev.shared.feature.history.domain.ResumePolicy
import kg.dev.shared.feature.history.domain.WatchedVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val historyRepository: HistoryRepository,
    private val mediaAvailability: HomeMediaAvailability = HomeMediaAvailability { true },
    private val onItemSelected: (HomeMediaItemUiModel) -> Unit = {},
    coroutineContext: CoroutineContext = Dispatchers.Default
) : HomeComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val mutableState = MutableStateFlow(HomeUiState())
    override val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

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
                val items = historyRepository.recent(HOME_HISTORY_FETCH_LIMIT).map(::toHomeItem)
                HomeUiState(
                    continueWatching = items
                        .asSequence()
                        .filter { it.isAvailable && it.resumeDecision is ResumeDecision.ResumeFrom }
                        .take(HOME_SECTION_LIMIT)
                        .toList(),
                    recentlyWatched = items.take(HOME_SECTION_LIMIT)
                )
            } catch (_: Throwable) {
                HomeUiState(error = true)
            }
        }
    }

    override fun select(item: HomeMediaItemUiModel) {
        if (item.isAvailable) onItemSelected(item)
    }

    private fun toHomeItem(video: WatchedVideo): HomeMediaItemUiModel = HomeMediaItemUiModel(
        reference = video.reference,
        title = video.title,
        thumbnailUrl = video.thumbnailUrl,
        positionMs = video.positionMs,
        durationMs = video.durationMs,
        watchedAtEpochMs = video.watchedAtEpochMs,
        resumeDecision = ResumePolicy.evaluate(video.positionMs, video.durationMs),
        isAvailable = mediaAvailability.isAvailable(video.reference)
    )

    private companion object {
        const val HOME_HISTORY_FETCH_LIMIT = 50L
        const val HOME_SECTION_LIMIT = 10
    }
}
