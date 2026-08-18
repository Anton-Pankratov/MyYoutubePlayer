package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

actual fun createPlatformHttpClient(settings: NetworkSettings): HttpClient =
    createHttpClient(CIO, settings)
