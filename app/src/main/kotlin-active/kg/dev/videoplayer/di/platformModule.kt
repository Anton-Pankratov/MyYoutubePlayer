package kg.dev.videoplayer.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.network.NetworkSettings
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.VideoPlayerController
import kg.dev.videoplayer.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

fun androidModule() = module {
    single<ApiConfigurationProvider> {
        object : ApiConfigurationProvider {
            override val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
        }
    }
    single { NetworkSettings(enableLogging = BuildConfig.DEBUG) }
    single<SqlDriver> {
        AndroidSqliteDriver(PlayerDatabase.Schema, androidContext(), "youtube-player.db")
    }
    single<VideoPlayerController> {
        AndroidVideoPlayerController(androidContext())
    }
}
