package kg.dev.shared.core.common

interface ApiConfigurationProvider {
    val youtubeApiKey: String
}

const val YOUTUBE_API_BASE_URL: String = "https://www.googleapis.com/youtube/v3"
