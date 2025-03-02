package kg.dev.videoplayer

import android.app.Application
import kg.dev.common.events.eventsModule
import kg.dev.common.loggerModule
import kg.dev.common.network.services.networkServicesModule
import kg.dev.core.repositories.repositoriesModule
import kg.dev.service.network.networkClientModule
import kg.dev.videoplayer.di.appModule
import kg.dev.videoplayer.presentation.view.viewsModule
import org.koin.core.context.startKoin

class App : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            modules(
                appModule,
                viewsModule,
                eventsModule,
                loggerModule,
                networkClientModule,
                networkServicesModule,
                repositoriesModule
            )
        }
    }
}