package kg.dev.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createPlatformHttpClient(settings: NetworkSettings): HttpClient =
    createHttpClient(OkHttp, settings)
