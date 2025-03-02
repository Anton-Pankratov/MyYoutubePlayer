package kg.dev.core.repositories

import com.google.gson.Gson
import kg.dev.core.repositories.youtube.channel.ChannelsRepository
import kg.dev.core.repositories.youtube.channel.ChannelsRepositoryImpl
import org.koin.dsl.module

val repositoriesModule = module {

    single { Gson() }
    single<ChannelsRepository> { ChannelsRepositoryImpl(get()) }
}