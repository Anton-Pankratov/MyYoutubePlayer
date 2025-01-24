package kg.dev.common.network.api.request

import kg.dev.common.network.api.BuildConfig

sealed class ApiRequest(open val service: String) {

    data class Youtube(
        val type: String,
        val query: String,
        val maxResults: Int = 10,
        val apiKey: String = BuildConfig.YOUTUBE_API_KEY,
        override val service: String = BuildConfig.YOUTUBE_API_URL
    ) : ApiRequest(service)
}