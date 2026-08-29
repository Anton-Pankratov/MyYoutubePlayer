package kg.dev.shared.feature.player.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class SqlDelightSavedMediaRepositoryTest {
    @Test fun repeatedDesiredStateWritesAreIdempotentAndPreserveTimestamps() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        var now = 1L
        val repository = SqlDelightSavedMediaRepository(createPlayerDatabase(driver)) { now++ }
        val item = media("youtube", "idempotent", "Item")
        repository.setFavorite(item, true)
        val favoriteAt = repository.favorites().value.single().favoriteAddedAtEpochMs
        repository.setFavorite(item, true)
        assertEquals(favoriteAt, repository.favorites().value.single().favoriteAddedAtEpochMs)
        repository.setWatchLater(item, true)
        val laterAt = repository.watchLater().value.single().watchLaterAddedAtEpochMs
        repository.setWatchLater(item, true)
        assertEquals(laterAt, repository.watchLater().value.single().watchLaterAddedAtEpochMs)
        assertEquals(1, repository.favorites().value.size); assertEquals(1, repository.watchLater().value.size)
        repository.setFavorite(item, false); repository.setFavorite(item, false)
        assertTrue(repository.watchLater().value.single().isWatchLater)
        repository.setWatchLater(item, false); repository.setWatchLater(item, false)
        assertTrue(repository.favorites().value.isEmpty()); assertTrue(repository.watchLater().value.isEmpty())
        driver.close()
    }

    @Test fun independentFlagsPreserveEachOtherAndDeleteZeroState() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        val repository = SqlDelightSavedMediaRepository(createPlayerDatabase(driver)) { 100 }
        val item = media("youtube", "same", "A")

        repository.setWatchLater(item, true)
        repository.setFavorite(item, true)
        assertTrue(repository.observe(item.reference).value.isFavorite)
        assertTrue(repository.observe(item.reference).value.isWatchLater)
        repository.setFavorite(item, false)
        assertFalse(repository.observe(item.reference).value.isFavorite)
        assertTrue(repository.observe(item.reference).value.isWatchLater)
        repository.setWatchLater(item, false)
        assertTrue(repository.watchLater().value.isEmpty())
        driver.close()
    }

    @Test fun providerQualifiedIdentityAndOrderingAreDurable() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        var now = 1L
        val repository = SqlDelightSavedMediaRepository(createPlayerDatabase(driver)) { now++ }
        repository.setFavorite(media("youtube", "same", "YouTube"), true)
        repository.setFavorite(media("direct", "same", "Direct"), true)
        assertEquals(listOf("direct", "youtube"), repository.favorites().value.map { it.reference.provider.value })
        assertEquals(2, repository.favorites().value.size)
        driver.close()
    }

    @Test fun fileBackedRestartPreservesFlagsTimestampsMetadataAndProviderCollisions() = runTest {
        val file = File.createTempFile("saved-media-restart", ".db").also { it.delete() }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driverA ->
            PlayerDatabase.Schema.create(driverA)
            var now = 10L
            val repository = SqlDelightSavedMediaRepository(createPlayerDatabase(driverA)) { now++ }
            repository.setFavorite(media("youtube", "favorite", "Favorite", "thumb", "Author", 11), true)
            repository.setWatchLater(media("direct", "later", "Later", "thumb2", "Author2", 22), true)
            repository.setFavorite(media("youtube", "both", "Both", "thumb3", "Author3", 33), true)
            repository.setWatchLater(media("youtube", "both", "Both updated", "thumb4", "Author4", 44), true)
            repository.setFavorite(media("youtube", "same", "Youtube same"), true)
            repository.setFavorite(media("direct", "same", "Direct same"), true)
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driverB ->
            val repository = SqlDelightSavedMediaRepository(createPlayerDatabase(driverB)) { 99 }
            assertEquals(listOf("same", "same", "both", "favorite"), repository.favorites().value.map { it.reference.externalId })
            assertEquals(listOf("both", "later"), repository.watchLater().value.map { it.reference.externalId })
            val both = repository.observe(MediaReference(MediaProviderId("youtube"), "both")).value
            assertTrue(both.isFavorite); assertTrue(both.isWatchLater)
            val bothMetadata = repository.favorites().value.first { it.reference.externalId == "both" }
            assertEquals("Both updated", bothMetadata.title); assertEquals("thumb4", bothMetadata.thumbnailUrl)
            assertEquals(12L, bothMetadata.favoriteAddedAtEpochMs); assertEquals(13L, bothMetadata.watchLaterAddedAtEpochMs)
            assertEquals(2, repository.favorites().value.count { it.reference.externalId == "same" })
        }
        file.delete()
    }

    @Test fun migration3PreservesHistoryAndDirectSourceAndCreatesUsableSavedMedia() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, """CREATE TABLE playbackHistory (providerId TEXT NOT NULL, externalId TEXT NOT NULL, title TEXT NOT NULL, thumbnailUrl TEXT, positionMs INTEGER NOT NULL DEFAULT 0, durationMs INTEGER, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(providerId, externalId));""", 0)
        driver.execute(null, """CREATE TABLE directMediaSource (externalId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, uri TEXT NOT NULL, mimeType TEXT, thumbnailUrl TEXT, authorTitle TEXT, durationMs INTEGER);""", 0)
        driver.execute(null, "INSERT INTO playbackHistory VALUES ('youtube','history','History','thumb',42,180,1000)", 0)
        driver.execute(null, "INSERT INTO directMediaSource VALUES ('direct','Direct','file:///media.mp4','video/mp4','thumb','Author',180)", 0)
        PlayerDatabase.Schema.migrate(driver, 3, 4)
        val database = createPlayerDatabase(driver)
        val history = database.playerDatabaseQueries.selectRecent(10).executeAsOne()
        assertEquals("history", history.externalId); assertEquals(42, history.positionMs); assertEquals(180, history.durationMs)
        val direct = database.playerDatabaseQueries.findDirectMedia("direct").executeAsOne()
        assertEquals("file:///media.mp4", direct.uri); assertEquals("video/mp4", direct.mimeType)
        val repository = SqlDelightSavedMediaRepository(database) { 1 }
        repository.setFavorite(media("youtube", "saved", "Saved"), true)
        assertTrue(repository.favorites().value.single().isFavorite)
        driver.close()
    }

    private fun media(provider: String, id: String, title: String, thumbnail: String? = null, author: String? = null, duration: Long? = 42_000) = MediaCatalogItem(
        MediaReference(MediaProviderId(provider), id), title, thumbnail, author, duration
    )
}
