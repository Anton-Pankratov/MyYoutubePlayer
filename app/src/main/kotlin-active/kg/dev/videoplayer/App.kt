package kg.dev.videoplayer

import android.app.Application
import kg.dev.shared.core.di.commonModules
import kg.dev.shared.feature.player.playerFeatureModule
import kg.dev.videoplayer.di.androidModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(commonModules() + playerFeatureModule + androidModule())
        }
    }
}
