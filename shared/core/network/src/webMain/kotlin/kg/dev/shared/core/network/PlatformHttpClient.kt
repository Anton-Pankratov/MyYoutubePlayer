package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun createPlatformHttpClient(settings: NetworkSettings): HttpClient =
    createHttpClient(Js, settings)
