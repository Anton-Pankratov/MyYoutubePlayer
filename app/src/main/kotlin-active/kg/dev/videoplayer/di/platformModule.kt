package kg.dev.videoplayer.di

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.core.network.NetworkSettings
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.player.AndroidVideoPlayerController
import kg.dev.shared.feature.player.VideoPlayerController
import kg.dev.videoplayer.BuildConfig
import android.content.pm.PackageManager
import java.security.MessageDigest
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

fun androidModule() = module {
    single<ApiConfigurationProvider> {
        object : ApiConfigurationProvider {
            override val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
        }
    }
    single {
        NetworkSettings(
            enableLogging = BuildConfig.DEBUG,
            androidPackageName = BuildConfig.APPLICATION_ID,
            androidCertSha1 = signingCertificateSha1()
        )
    }
    single<SqlDriver> {
        AndroidSqliteDriver(PlayerDatabase.Schema, androidContext(), "youtube-player.db")
    }
    single<VideoPlayerController> {
        AndroidVideoPlayerController(androidContext())
    }
}

/** SHA-1 format expected by Google API keys restricted to Android apps. */
private fun signingCertificateSha1(): String? = runCatching {
    val context = GlobalContext.get().get<android.content.Context>()
    val packageManager = context.packageManager
    @Suppress("DEPRECATION")
    val packageInfo = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
    @Suppress("DEPRECATION")
    val signature = packageInfo.signatures?.firstOrNull()?.toByteArray() ?: return null
    MessageDigest.getInstance("SHA-1").digest(signature)
        .joinToString(":") { byte -> "%02X".format(byte) }
}.getOrNull()
