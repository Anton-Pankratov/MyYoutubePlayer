package kg.dev.service.network

import kg.dev.common.network.client.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitNetworkClientImpl : RetrofitNetworkClient {

    override fun buildClient(): Retrofit {
        val okHttp3Client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor())
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.GOOGLE_APIS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(okHttp3Client)
            .build()
    }
}
