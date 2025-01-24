package kg.dev.videoplayer.di

import kg.dev.videoplayer.presentation.main.MainViewModel
import kg.dev.videoplayer.presentation.tabs.channels.ChannelsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    viewModel { MainViewModel() }
    viewModel { ChannelsViewModel(get()) }
}