package kg.dev.shared.feature.search

import kg.dev.shared.feature.search.data.remote.KtorSearchRemoteDataSource
import kg.dev.shared.feature.search.data.remote.SearchRemoteDataSource
import kg.dev.shared.feature.search.data.repository.DefaultSearchRepository
import kg.dev.shared.feature.search.domain.repository.SearchRepository
import kg.dev.shared.feature.search.domain.usecase.SearchChannelsUseCase
import kg.dev.shared.feature.search.presentation.DefaultSearchComponent
import kg.dev.shared.feature.search.presentation.SearchComponent
import com.arkivanov.decompose.ComponentContext
import org.koin.dsl.module
import kg.dev.shared.feature.search.data.provider.youtube.YouTubePlaybackResolver
import kg.dev.shared.feature.player.PlaybackSourceResolver

val searchFeatureModule = module {
    single<SearchRemoteDataSource> { KtorSearchRemoteDataSource(get(), get()) }
    single<SearchRepository> { DefaultSearchRepository(get()) }
    factory<PlaybackSourceResolver> { YouTubePlaybackResolver() }
    factory { SearchChannelsUseCase(get()) }
    factory<SearchComponent> { (componentContext: ComponentContext) ->
        DefaultSearchComponent(componentContext, get())
    }
}
