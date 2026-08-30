package kg.dev.videoplayer

import android.app.Application
import kg.dev.videoplayer.di.androidPlatformModule
import kg.dev.shared.core.di.commonModules
import org.koin.core.context.startKoin

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin { modules(commonModules() + androidPlatformModule) }
    }
}
