package kg.dev.service.network

import retrofit2.Retrofit

interface RetrofitNetworkClient {

    fun buildClient(): Retrofit
}