package kg.dev.shared.feature.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.feature.history.data.DefaultHistoryRepository
import kg.dev.shared.feature.history.data.SqlDelightHistoryDataSource
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.core.common.media.MediaProviderId
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference

class SqlDelightHistoryRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var repository: DefaultHistoryRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        repository = DefaultHistoryRepository(
            SqlDelightHistoryDataSource(createPlayerDatabase(driver))
        )
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun insertAndReadHistory() = runTest {
        repository.save(video("first", 100))
        assertEquals("first", repository.recent().single().reference.externalId)
    }

    @Test
    fun historyIsOrderedByNewestTimestamp() = runTest {
        repository.save(video("older", 100))
        repository.save(video("newer", 200))
        assertEquals(listOf("newer", "older"), repository.recent().map { it.reference.externalId })
    }

    @Test
    fun deleteHistory() = runTest {
        repository.save(video("delete-me", 100))
        repository.delete(MediaReference(MediaProviderId("test"), "delete-me"))
        assertTrue(repository.recent().isEmpty())
    }

    @Test fun historySurvivesFileBackedRestart() = runTest {
        val file = File.createTempFile("history-restart", ".db").also { it.delete() }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").let { runtimeA ->
            PlayerDatabase.Schema.create(runtimeA)
            DefaultHistoryRepository(SqlDelightHistoryDataSource(createPlayerDatabase(runtimeA))).save(
                WatchedVideo(MediaReference(MediaProviders.Direct, "restart-media"), "Restart media", "restart-thumbnail", 42000, 180000, 1234)
            )
            runtimeA.close()
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").let { runtimeB ->
            val video = DefaultHistoryRepository(SqlDelightHistoryDataSource(createPlayerDatabase(runtimeB))).recent().single()
            assertEquals(MediaProviders.Direct, video.reference.provider)
            assertEquals("restart-media", video.reference.externalId)
            assertEquals("Restart media", video.title)
            assertEquals("restart-thumbnail", video.thumbnailUrl)
            assertEquals(42000, video.positionMs); assertEquals(180000, video.durationMs); assertEquals(1234, video.watchedAtEpochMs)
            runtimeB.close()
        }
        file.delete()
    }

    private fun video(id: String, timestamp: Long) = WatchedVideo(
        MediaReference(MediaProviderId("test"), id), id, positionMs = 42, watchedAtEpochMs = timestamp
    )
}
