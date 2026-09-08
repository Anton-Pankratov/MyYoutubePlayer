package kg.dev.shared.core.ui.navigation

import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackQueueState(
    val items: List<MediaCatalogItem> = emptyList(),
    val currentIndex: Int? = null,
    val pendingIndex: Int? = null,
    val generation: Long = 0,
) {
    val isActive get() = items.isNotEmpty()
    val current get() = currentIndex?.let(items::getOrNull)
    val hasPrevious get() = (currentIndex ?: 0) > 0
    val hasNext get() = (currentIndex ?: -1) < items.lastIndex
}

/** Root-lifetime, session-only queue state. Resolution remains owned by [DefaultRootComponent]. */
class PlaybackQueueController(private val request: (index: Int, generation: Long) -> Unit) {
    private val mutableState = MutableStateFlow(PlaybackQueueState())
    val state: StateFlow<PlaybackQueueState> = mutableState.asStateFlow()

    fun start(items: List<MediaCatalogItem>) {
        val generation = mutableState.value.generation + 1
        if (items.isEmpty()) { mutableState.value = PlaybackQueueState(generation = generation); return }
        mutableState.value = PlaybackQueueState(items = items.toList(), pendingIndex = 0, generation = generation)
        request(0, generation)
    }

    fun clear() { mutableState.value = PlaybackQueueState(generation = mutableState.value.generation + 1) }
    fun next() = requestForward((mutableState.value.pendingIndex ?: mutableState.value.currentIndex ?: -1) + 1)
    fun previous() = requestBackward((mutableState.value.pendingIndex ?: mutableState.value.currentIndex ?: 0) - 1)
    fun onCompleted(reference: MediaReference) {
        val current = mutableState.value.current ?: return
        if (current.reference != reference) return
        requestForward((mutableState.value.currentIndex ?: return) + 1, clearAtBoundary = true)
    }
    fun settle(index: Int, generation: Long): Boolean {
        val state = mutableState.value
        if (state.generation != generation || state.items.getOrNull(index) == null) return false
        mutableState.value = state.copy(currentIndex = index, pendingIndex = null)
        return true
    }
    fun unavailable(index: Int, generation: Long, forward: Boolean): Boolean {
        if (mutableState.value.generation != generation) return false
        if (forward) requestForward(index + 1, clearAtBoundary = true)
        else if (index > 0) requestBackward(index - 1)
        else mutableState.value = mutableState.value.copy(pendingIndex = null)
        return true
    }
    private fun requestForward(index: Int, clearAtBoundary: Boolean = false) {
        val state = mutableState.value
        if (index > state.items.lastIndex) { if (clearAtBoundary) clear(); return }
        if (index < 0) return
        mutableState.value = state.copy(pendingIndex = index)
        request(index, state.generation)
    }
    private fun requestBackward(index: Int) {
        val state = mutableState.value
        if (index < 0 || index > state.items.lastIndex) return
        mutableState.value = state.copy(pendingIndex = index)
        request(index, state.generation)
    }
}
