package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class NetworkSettings(
    val requestTimeoutMillis: Long = 15_000,
    val connectTimeoutMillis: Long = 10_000,
    val socketTimeoutMillis: Long = 15_000,
    val enableLogging: Boolean = false
)

fun <T : HttpClientEngineConfig> createHttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    settings: NetworkSettings = NetworkSettings()
): HttpClient = HttpClient(engineFactory) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = false
            }
        )
    }
    install(HttpTimeout) {
        requestTimeoutMillis = settings.requestTimeoutMillis
        connectTimeoutMillis = settings.connectTimeoutMillis
        socketTimeoutMillis = settings.socketTimeoutMillis
    }
    if (settings.enableLogging) {
        install(Logging) { level = LogLevel.INFO }
    }
}

expect fun createPlatformHttpClient(settings: NetworkSettings = NetworkSettings()): HttpClient
