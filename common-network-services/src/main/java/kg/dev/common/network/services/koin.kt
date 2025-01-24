package kg.dev.common.network.services

import org.koin.dsl.module
import retrofit2.Retrofit

val networkServicesModule = module {
    single { get<Retrofit>().create(YoutubeService::class.java) }
}