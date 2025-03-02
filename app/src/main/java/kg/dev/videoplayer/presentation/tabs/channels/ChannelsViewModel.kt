package kg.dev.videoplayer.presentation.tabs.channels

import androidx.lifecycle.viewModelScope
import kg.dev.common.viewmodel.CommonViewModel
import kg.dev.videoplayer.data.events.AppEvents.Channel.CallNextPage
import kg.dev.videoplayer.domain.channel.SearchChannelsUseCase
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

class ChannelsViewModel: CommonViewModel() {

    private val useCase: SearchChannelsUseCase by inject { parametersOf(viewModelScope) }

    val pageLoad: StateFlow<CallNextPage> = useCase.pageLoad
    val channels = useCase.channels

    fun findChannels(query: String?) = useCase.findChannels(query)
}