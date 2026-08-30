package kg.dev.videoplayer.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.network.NetworkSettings
import kg.dev.shared.core.network.createPlatformHttpClient
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.VideoPlayerController
import kg.dev.videoplayer.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    single<ApiConfigurationProvider> {
        object : ApiConfigurationProvider {
            override val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
        }
    }
    single<HttpClient> {
        createPlatformHttpClient(
            NetworkSettings(
                enableLogging = BuildConfig.DEBUG,
                androidPackageName = BuildConfig.APPLICATION_ID
            )
        )
    }
    single<PlayerDatabase> {
        createPlayerDatabase(
            AndroidSqliteDriver(PlayerDatabase.Schema, androidContext(), "youtube-player.db")
        )
    }
    single<VideoPlayerController> {
        AndroidVideoPlayerController(androidContext())
    }
}
