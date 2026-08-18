package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createPlatformHttpClient(settings: NetworkSettings): HttpClient =
    createHttpClient(Darwin, settings)
