package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

data class NetworkSettings(
    val requestTimeoutMillis: Long = 15_000,
    val connectTimeoutMillis: Long = 10_000,
    val socketTimeoutMillis: Long = 15_000,
    val enableLogging: Boolean = false,
    /** Optional identity headers required by Google keys restricted to Android apps. */
    val androidPackageName: String? = null,
    val androidCertSha1: String? = null
)

fun <T : HttpClientEngineConfig> createHttpClient(
    engineFactory: HttpClientEngineFactory<T>,
    settings: NetworkSettings = NetworkSettings()
): HttpClient = HttpClient(engineFactory) {
    expectSuccess = false
    defaultRequest {
        settings.androidPackageName?.takeIf { it.isNotBlank() }?.let { packageName: String ->
            header("X-Android-Package", packageName)
        }
        settings.androidCertSha1?.takeIf { it.isNotBlank() }?.let { certSha1: String ->
            header("X-Android-Cert", certSha1)
        }
    }
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
        // Keep the full request/response exchange in debug builds so transport
        // failures (DNS, TLS, redirects, and status codes) are visible in
        // Logcat instead of being reduced to a generic Network error.
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    // API keys must never end up in Logcat or an attached log.
                    println(message.replace(API_KEY_PATTERN, "$1<redacted>"))
                }
            }
        }
    }
}

private val API_KEY_PATTERN = Regex("([?&]key=)[^&\\s]*")

expect fun createPlatformHttpClient(settings: NetworkSettings = NetworkSettings()): HttpClient
