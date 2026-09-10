package kg.dev.videoplayer

import android.app.Application
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.feature.player.playerFeatureModule
import kg.dev.videoplayer.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import kg.dev.shared.feature.player.DirectAudioSessionCoordinator
import kg.dev.shared.feature.player.DirectPlaybackCommandCallbacks
import kg.dev.videoplayer.playback.AndroidDirectAudioCommandRegistry
import kg.dev.videoplayer.playback.AndroidServiceDirectPlaybackHost

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@App)
            modules(commonModules() + playerFeatureModule + androidModule())
        }.koin
        val host = koin.get<AndroidServiceDirectPlaybackHost>()
        val coordinator = koin.get<DirectAudioSessionCoordinator>()
        AndroidDirectAudioCommandRegistry.callbacks = object : DirectPlaybackCommandCallbacks {
            override fun play() = host.play()
            override fun pause() = host.pause()
            override fun seekTo(positionMs: Long) = host.seekTo(positionMs)
            override fun next() = coordinator.next()
            override fun previous() = coordinator.previous()
            override fun stop() = coordinator.requestStop()
        }
    }
}
