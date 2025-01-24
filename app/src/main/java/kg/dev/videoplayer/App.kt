package kg.dev.videoplayer

import android.app.Application
import kg.dev.common.network.services.networkServicesModule
import kg.dev.core.repositories.repositoriesModule
import kg.dev.service.network.networkClientModule
import kg.dev.videoplayer.di.appModule
import org.koin.core.context.startKoin

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            modules(
                appModule,
                networkClientModule,
                networkServicesModule,
                repositoriesModule
            )
        }
    }
}