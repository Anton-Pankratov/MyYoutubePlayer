package kg.dev.videoplayer.presentation.tabs.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kg.dev.core.repositories.youtube.ChannelsRepository
import kg.dev.videoplayer.data.Channel
import kg.dev.videoplayer.data.ChannelsMapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class ChannelsViewModel(private val repository: ChannelsRepository) : ViewModel() {

    private val _foundChannels = MutableSharedFlow<List<Channel>?>()
    val foundChannels: SharedFlow<List<Channel>?> = _foundChannels

    init {
        viewModelScope.launch {
            val response = repository.findChannels("Education")
            response?.let {
                _foundChannels.tryEmit(ChannelsMapper().convert(it))
            }
        }
    }
}