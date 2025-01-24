package kg.dev.common.network.services

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeService {

    @GET("${BuildConfig.YOUTUBE_API_URL}search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("type") type: String,
        @Query("q") query: String,
        @Query("order") order: String = "relevance",
        @Query("maxResults") maxResults: Int = 10,
        @Query("key") key: String = BuildConfig.YOUTUBE_API_KEY
    ): Response<ResponseBody>
}