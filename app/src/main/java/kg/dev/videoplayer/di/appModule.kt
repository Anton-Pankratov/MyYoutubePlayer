package kg.dev.videoplayer.di

import kg.dev.videoplayer.data.channel.ChannelsMapper
import kg.dev.videoplayer.domain.channel.SearchChannelsUseCase
import kg.dev.videoplayer.presentation.main.MainViewModel
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsViewModel
import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    factory { ChannelsMapper() }
    single { (coroutineScope: CoroutineScope) -> SearchChannelsUseCase(get(), get(), coroutineScope) }

    viewModel { MainViewModel() }
    viewModel { ChannelsViewModel() }
}