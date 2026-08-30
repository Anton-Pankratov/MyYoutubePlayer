package kg.dev.videoplayer.presentation.tabs.channels

import kg.dev.common.viewmodel.CommonViewModel
import androidx.lifecycle.viewModelScope
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent

class ChannelsViewModel(useCase: SearchChannelsUseCase) : CommonViewModel(), SearchComponent {
    private val component = DefaultSearchComponent(useCase, viewModelScope)
    override val state = component.state

    override fun onQueryChanged(query: String) = component.onQueryChanged(query)
    override fun loadNextPage() = component.loadNextPage()
    override fun retry() = component.retry()
}
