package kg.dev.shared.core.di

import io.ktor.client.HttpClient
import kg.dev.shared.core.network.NetworkSettings
import kg.dev.shared.core.network.createPlatformHttpClient
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.search.searchFeatureModule
import kg.dev.shared.feature.history.historyFeatureModule
import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule = module {
    single<HttpClient> {
        createPlatformHttpClient(getOrNull<NetworkSettings>() ?: NetworkSettings())
    }
}

val storageModule = module {
    single<PlayerDatabase> { createPlayerDatabase(get()) }
}

fun commonModules(): List<Module> = listOf(
    networkModule,
    storageModule,
    searchFeatureModule,
    historyFeatureModule
)
