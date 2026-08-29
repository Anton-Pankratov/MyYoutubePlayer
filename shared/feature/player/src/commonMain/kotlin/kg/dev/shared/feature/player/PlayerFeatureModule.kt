package kg.dev.shared.feature.player

import org.koin.dsl.module
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.player.library.SavedMediaRepository
import kg.dev.shared.feature.player.library.SqlDelightSavedMediaRepository

/** Only application-owned, explicitly supplied direct media is registered by default. */
val playerFeatureModule = module {
    single<SavedMediaRepository> { SqlDelightSavedMediaRepository(get<PlayerDatabase>()) }
    single<DirectMediaSourceStore> { SqlDelightDirectMediaSourceStore(get<PlayerDatabase>()) }
    single { DirectMediaProvider(get()) }
    factory<PlaybackSourceResolver> { get<DirectMediaProvider>() }
    single { PlaybackSourceResolverRegistry(getAll<PlaybackSourceResolver>().toSet()) }
    single<MediaOpenCoordinator> { DefaultMediaOpenCoordinator(get()) }
}
