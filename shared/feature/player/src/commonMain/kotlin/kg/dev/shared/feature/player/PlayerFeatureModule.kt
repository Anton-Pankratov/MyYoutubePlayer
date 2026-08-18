package kg.dev.shared.feature.player

import org.koin.dsl.module
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.core.storage.db.PlayerDatabase

/** Only application-owned, explicitly supplied direct media is registered by default. */
val playerFeatureModule = module {
    single<DirectMediaSourceStore> { SqlDelightDirectMediaSourceStore(get<PlayerDatabase>()) }
    single { DirectMediaProvider(get()) }
    factory<PlaybackSourceResolver> { get<DirectMediaProvider>() }
    single { PlaybackSourceResolverRegistry(getAll<PlaybackSourceResolver>().toSet()) }
    single<MediaOpenCoordinator> { DefaultMediaOpenCoordinator(get()) }
}
