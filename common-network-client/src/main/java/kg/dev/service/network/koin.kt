package kg.dev.service.network

import org.koin.dsl.module
import retrofit2.Retrofit

val networkClientModule = module {
    single<Retrofit> { RetrofitNetworkClientImpl().buildClient() }
}