package kg.dev.videoplayer.presentation.main

import androidx.lifecycle.ViewModel
import kg.dev.videoplayer.presentation.tabs.NavTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel : ViewModel() {

    private val _selectedTabState = MutableStateFlow(NavTab.HOME.name)
    val selectedTabState: StateFlow<String> get() = _selectedTabState

    fun updateSelectedTabState(tab: String) {
        _selectedTabState.value = tab
    }
}