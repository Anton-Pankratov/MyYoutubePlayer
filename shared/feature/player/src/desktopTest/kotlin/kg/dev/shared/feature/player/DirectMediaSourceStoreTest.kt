package kg.dev.shared.feature.player

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.io.File
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference

class DirectMediaSourceStoreTest {
    @Test fun insertFindAndUpsertPreserveDirectMetadata() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        val store = SqlDelightDirectMediaSourceStore(createPlayerDatabase(driver))
        store.upsert(source("A", "file:///a.mp4", 100))
        assertEquals(source("A", "file:///a.mp4", 100), store.find("direct-1"))
        store.upsert(source("B", "file:///b.mp4", 200))
        assertEquals(source("B", "file:///b.mp4", 200), store.find("direct-1"))
        assertNull(store.find("missing"))
        driver.close()
    }
    @Test fun resolverAndHistorySurviveFileBackedRestart() = runTest {
        val file = File.createTempFile("direct-media", ".db").also { it.delete() }
        val reference = MediaReference(MediaProviders.Direct, "restart-media")
        val descriptor = DirectMediaDescriptor("restart-media", "Restart media", "file:///durable/restart.mp4", "video/mp4", "thumb", durationMs = 180000)
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").let { driver ->
            PlayerDatabase.Schema.create(driver)
            val database = createPlayerDatabase(driver)
            val provider = DirectMediaProvider(SqlDelightDirectMediaSourceStore(database))
            provider.register(descriptor)
            assertIs<PlaybackResolution.Resolved>(provider.resolve(descriptor.toCatalog())).also { assertTrue(it.media.source is PlaybackSource.Direct) }
            driver.close()
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").let { driver ->
            val database = createPlayerDatabase(driver)
            val provider = DirectMediaProvider(SqlDelightDirectMediaSourceStore(database))
            val resolved = assertIs<PlaybackResolution.Resolved>(provider.resolve(descriptor.toCatalog()))
            val direct = assertIs<PlaybackSource.Direct>(resolved.media.source)
            assertEquals("file:///durable/restart.mp4", direct.uri); assertEquals("video/mp4", direct.mimeType)
            assertEquals(180000, resolved.media.catalogItem.durationMs)
            driver.close()
        }
        file.delete()
    }
    @Test fun missingDirectSourceIsUnavailable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); PlayerDatabase.Schema.create(driver)
        val result = DirectMediaProvider(SqlDelightDirectMediaSourceStore(createPlayerDatabase(driver))).resolve(
            kg.dev.shared.core.common.media.MediaCatalogItem(MediaReference(MediaProviders.Direct, "missing"), "Missing")
        )
        assertEquals(PlaybackResolutionError.MediaUnavailable, assertIs<PlaybackResolution.Failed>(result).error); driver.close()
    }
    private fun source(title: String, uri: String, duration: Long?) = StoredDirectMedia("direct-1", title, uri, "video/mp4", "thumb", "author", duration)
    private fun DirectMediaDescriptor.toCatalog() = kg.dev.shared.core.common.media.MediaCatalogItem(MediaReference(MediaProviders.Direct, id), title, thumbnailUrl, authorTitle, durationMs)
}
