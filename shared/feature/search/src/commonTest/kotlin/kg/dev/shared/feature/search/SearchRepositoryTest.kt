package kg.dev.shared.feature.search

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kg.dev.shared.core.common.ApiConfigurationProvider
import kg.dev.shared.feature.search.data.remote.KtorSearchRemoteDataSource
import kg.dev.shared.feature.search.data.repository.DefaultSearchRepository
import kg.dev.shared.feature.search.domain.model.Channel
import kg.dev.shared.feature.search.domain.model.SearchError
import kg.dev.shared.feature.search.domain.model.SearchResult
import kg.dev.shared.feature.search.domain.repository.SearchRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.io.IOException

class SearchRepositoryTest {
    @Test
    fun successfulSearchMapsDomainModelAndToken() = runRepositoryTest(
        json = """{
            "nextPageToken":"opaque-token",
            "items":[{
                "id":{"channelId":"channel-1"},
                "snippet":{
                    "title":"Kotlin",
                    "description":"Multiplatform",
                    "thumbnails":{"medium":{"url":"https://example.test/image.jpg"}}
                }
            }]
        }"""
    ) { result, requestedUrl ->
        val success = assertIs<SearchResult.Success>(result)
        assertEquals("opaque-token", success.page.nextPageToken)
        assertEquals(
            Channel("channel-1", "Kotlin", "Multiplatform", "https://example.test/image.jpg"),
            success.page.items.single()
        )
        assertTrue("pageToken=previous-token" in requestedUrl)
    }

    @Test
    fun emptyResponseProducesEmptyDomainPage() = runRepositoryTest(
        json = """{"items":[]}"""
    ) { result, _ ->
        assertTrue(assertIs<SearchResult.Success>(result).page.items.isEmpty())
    }

    @Test
    fun malformedResponseIsNormalized() = runRepositoryTest(
        json = "not-json"
    ) { result, _ ->
        assertEquals(SearchError.InvalidResponse, assertIs<SearchResult.Failure>(result).error)
    }

    @Test
    fun quota403IsNormalizedWithoutLeakingTransportException() {
        val client = testClient(MockEngine { respondError(HttpStatusCode.Forbidden) })
        val repository: SearchRepository = DefaultSearchRepository(KtorSearchRemoteDataSource(client, TestConfiguration))
        kotlinx.coroutines.test.runTest {
            val failure = assertIs<SearchResult.Failure>(repository.searchChannels("Kotlin"))
            assertEquals(SearchError.QuotaExceeded, failure.error)
        }
        client.close()
    }

    @Test
    fun server500IsNormalizedAsNetworkError() {
        val client = testClient(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        val repository: SearchRepository = DefaultSearchRepository(KtorSearchRemoteDataSource(client, TestConfiguration))
        kotlinx.coroutines.test.runTest {
            val failure = assertIs<SearchResult.Failure>(repository.searchChannels("Kotlin"))
            assertEquals(SearchError.Network, failure.error)
        }
        client.close()
    }

    @Test
    fun transportTimeoutIsNormalizedAsNetworkError() {
        val client = testClient(MockEngine { throw IOException("simulated timeout") })
        val repository: SearchRepository = DefaultSearchRepository(KtorSearchRemoteDataSource(client, TestConfiguration))
        kotlinx.coroutines.test.runTest {
            val failure = assertIs<SearchResult.Failure>(repository.searchChannels("Kotlin"))
            assertEquals(SearchError.Network, failure.error)
        }
        client.close()
    }

    private fun runRepositoryTest(
        json: String,
        assertions: (SearchResult, String) -> Unit
    ) {
        var requestedUrl = ""
        val client = testClient(MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val repository: SearchRepository = DefaultSearchRepository(KtorSearchRemoteDataSource(client, TestConfiguration))
        kotlinx.coroutines.test.runTest {
            assertions(repository.searchChannels("Kotlin", "previous-token"), requestedUrl)
        }
        client.close()
    }

    private fun testClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json() }
    }

    private object TestConfiguration : ApiConfigurationProvider {
        override val youtubeApiKey = "test-key"
    }
}
