package kg.dev.shared.feature.player.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviderId
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.core.ui.navigation.MediaOpenResult
import kg.dev.shared.feature.player.DefaultMediaOpenCoordinator
import kg.dev.shared.feature.player.DirectMediaDescriptor
import kg.dev.shared.feature.player.DirectMediaProvider
import kg.dev.shared.feature.player.PlaybackSource
import kg.dev.shared.feature.player.PlaybackSourceResolverRegistry
import kg.dev.shared.feature.player.SqlDelightDirectMediaSourceStore
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqlDelightMediaCollectionRepositoryTest {
    @Test fun createMembershipOrderingIndependenceAndNoOps() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        var now = 10L
        var id = 0
        val repository = SqlDelightMediaCollectionRepository(createPlayerDatabase(driver), CollectionIdGenerator { CollectionId("id-${++id}") }) { now++ }
        assertFailsWith<IllegalArgumentException> { repository.create(" ") }
        assertFailsWith<IllegalArgumentException> { repository.create("x".repeat(101)) }
        val a = repository.create("  A  ")
        val b = repository.create("a")
        assertEquals(listOf("a", "A"), repository.collections().value.map { it.name })
        val youtube = media("youtube", "same-id", "YouTube")
        val direct = media("direct", "same-id", "Direct")
        repository.addMedia(a, youtube); repository.addMedia(a, direct); repository.addMedia(b, direct)
        val addedAt = repository.observeCollection(a).value!!.items.first { it.reference == youtube.reference }.addedAtEpochMs
        val updatedAt = repository.observeCollection(a).value!!.collection.updatedAtEpochMs
        repository.addMedia(a, youtube)
        assertEquals(addedAt, repository.observeCollection(a).value!!.items.first { it.reference == youtube.reference }.addedAtEpochMs)
        assertEquals(updatedAt, repository.observeCollection(a).value!!.collection.updatedAtEpochMs)
        assertEquals(2, repository.observeCollection(a).value!!.items.size)
        repository.removeMedia(a, youtube.reference)
        repository.removeMedia(a, youtube.reference)
        assertEquals(listOf(direct.reference), repository.observeCollection(a).value!!.items.map { it.reference })
        assertEquals(listOf(direct.reference), repository.observeCollection(b).value!!.items.map { it.reference })
        repository.delete(a)
        assertTrue(repository.observeCollection(a).value == null)
        assertEquals(listOf(direct.reference), repository.observeCollection(b).value!!.items.map { it.reference })
        driver.close()
    }

    @Test fun migration4PreservesExistingDataAndCreatesUsableCollections() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "CREATE TABLE playbackHistory (providerId TEXT NOT NULL, externalId TEXT NOT NULL, title TEXT NOT NULL, thumbnailUrl TEXT, positionMs INTEGER NOT NULL DEFAULT 0, durationMs INTEGER, updatedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(providerId, externalId));", 0)
        driver.execute(null, "CREATE TABLE directMediaSource (externalId TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, uri TEXT NOT NULL, mimeType TEXT, thumbnailUrl TEXT, authorTitle TEXT, durationMs INTEGER);", 0)
        driver.execute(null, "CREATE TABLE savedMedia (providerId TEXT NOT NULL, externalId TEXT NOT NULL, title TEXT NOT NULL, thumbnailUrl TEXT, authorTitle TEXT, durationMs INTEGER, isFavorite INTEGER NOT NULL DEFAULT 0, isWatchLater INTEGER NOT NULL DEFAULT 0, favoriteAddedAtEpochMs INTEGER, watchLaterAddedAtEpochMs INTEGER, PRIMARY KEY(providerId, externalId));", 0)
        driver.execute(null, "INSERT INTO playbackHistory VALUES ('youtube','history','History',NULL,1,NULL,2)", 0)
        driver.execute(null, "INSERT INTO directMediaSource VALUES ('direct','Direct','file:///direct.mp4','video/mp4',NULL,NULL,NULL)", 0)
        driver.execute(null, "INSERT INTO savedMedia VALUES ('youtube','saved','Saved',NULL,NULL,NULL,1,0,3,NULL)", 0)
        PlayerDatabase.Schema.migrate(driver, 4, 5)
        driver.execute(null, "INSERT INTO mediaCollection VALUES ('legacy','Legacy',4,4)", 0)
        driver.execute(null, "INSERT INTO collectionMedia(collectionId,providerId,externalId,title,thumbnailUrl,authorTitle,durationMs,addedAtEpochMs) VALUES ('legacy','youtube','b','B',NULL,NULL,NULL,100)", 0)
        driver.execute(null, "INSERT INTO collectionMedia(collectionId,providerId,externalId,title,thumbnailUrl,authorTitle,durationMs,addedAtEpochMs) VALUES ('legacy','direct','z','Z',NULL,NULL,NULL,100)", 0)
        driver.execute(null, "INSERT INTO collectionMedia(collectionId,providerId,externalId,title,thumbnailUrl,authorTitle,durationMs,addedAtEpochMs) VALUES ('legacy','youtube','a','A',NULL,NULL,NULL,100)", 0)
        driver.execute(null, "INSERT INTO collectionMedia(collectionId,providerId,externalId,title,thumbnailUrl,authorTitle,durationMs,addedAtEpochMs) VALUES ('legacy','youtube','new','New',NULL,NULL,NULL,200)", 0)
        PlayerDatabase.Schema.migrate(driver, 5, 6)
        val database = createPlayerDatabase(driver)
        assertEquals("history", database.playerDatabaseQueries.selectRecent(1).executeAsOne().externalId)
        assertEquals("file:///direct.mp4", database.playerDatabaseQueries.findDirectMedia("direct").executeAsOne().uri)
        assertEquals("saved", database.playerDatabaseQueries.selectFavorites().executeAsOne().externalId)
        assertEquals(listOf("new", "z", "a", "b"), database.playerDatabaseQueries.selectCollectionItems("legacy").executeAsList().map { it.externalId })
        assertEquals(listOf(0L, 1L, 2L, 3L), database.playerDatabaseQueries.selectCollectionItems("legacy").executeAsList().map { it.manualPosition })
        val repository = SqlDelightMediaCollectionRepository(database, CollectionIdGenerator { CollectionId("migrated") }) { 4 }
        assertEquals(1, repository.collections().value.size)
        val collection = repository.create("Migrated")
        repository.addMedia(collection, media("youtube", "item", "Item"))
        assertEquals(1, repository.observeCollection(collection).value!!.items.size)
        driver.close()
    }

    @Test fun fileBackedRestartRetainsIndependentCollectionSnapshots() = runTest {
        val file = File.createTempFile("collection-restart", ".db").also { it.delete() }
        val direct = media("direct", "same", "Direct", "thumb", "Author", 100)
        val youtube = media("youtube", "same", "Youtube", "thumb2", "Author2", 200)
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            PlayerDatabase.Schema.create(driver)
            var count = 0
            val repository = SqlDelightMediaCollectionRepository(createPlayerDatabase(driver), CollectionIdGenerator { CollectionId("id-${++count}") }) { count.toLong() }
            val a = repository.create("A"); val b = repository.create("B")
            repository.addMedia(a, direct); repository.addMedia(a, youtube); repository.addMedia(b, direct)
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            val repository = SqlDelightMediaCollectionRepository(createPlayerDatabase(driver)) { 99 }
            assertEquals(2, repository.collections().value.size)
            assertEquals(2, repository.observeCollection(CollectionId("id-1")).value!!.items.size)
            assertEquals(setOf(CollectionId("id-1"), CollectionId("id-2")), repository.collectionIdsContaining(direct.reference).value)
            assertEquals(2, repository.observeCollection(CollectionId("id-1")).value!!.items.map { it.reference.provider }.toSet().size)
        }
        file.delete()
    }

    @Test fun collectionMembershipSurvivesSavedMediaZeroStateDeletion() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        val database = createPlayerDatabase(driver)
        val media = media("direct", "collection-only", "Collection only")
        val saved = SqlDelightSavedMediaRepository(database) { 10 }
        val collections = SqlDelightMediaCollectionRepository(database, CollectionIdGenerator { CollectionId("collection") }) { 20 }
        saved.setFavorite(media, true); saved.setWatchLater(media, true)
        val id = collections.create("Independent")
        collections.addMedia(id, media)
        saved.setFavorite(media, false); saved.setWatchLater(media, false)
        assertTrue(saved.favorites().value.isEmpty())
        assertTrue(saved.watchLater().value.isEmpty())
        val item = collections.observeCollection(id).value!!.items.single()
        assertEquals(media.reference, item.reference)
        assertEquals("Collection only", item.title)
        driver.close()
    }

    @Test fun collectionOnlyDirectMediaRestartsAndResolvesThroughTheProductionCoordinator() = runTest {
        val file = File.createTempFile("collection-direct", ".db").also { it.delete() }
        val descriptor = DirectMediaDescriptor(
            id = "collection-only-direct",
            title = "Collection direct",
            uri = "file:///durable/collection.mp4",
            mimeType = "video/mp4",
            thumbnailUrl = "thumb",
            authorTitle = "Camera",
            durationMs = 42_000,
        )
        val catalog = descriptor.toCatalogItem()
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            PlayerDatabase.Schema.create(driver)
            val database = createPlayerDatabase(driver)
            val sourceStore = SqlDelightDirectMediaSourceStore(database)
            DirectMediaProvider(sourceStore).register(descriptor)
            val saved = SqlDelightSavedMediaRepository(database) { 1L }
            val collections = SqlDelightMediaCollectionRepository(database, CollectionIdGenerator { CollectionId("direct-collection") }) { 2L }
            saved.setFavorite(catalog, true)
            saved.setWatchLater(catalog, true)
            val id = collections.create("Direct")
            collections.addMedia(id, catalog)
            saved.setFavorite(catalog, false)
            saved.setWatchLater(catalog, false)
            assertTrue(saved.favorites().value.isEmpty())
            assertTrue(saved.watchLater().value.isEmpty())
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            val database = createPlayerDatabase(driver)
            val runtimeBStore = SqlDelightDirectMediaSourceStore(database)
            val runtimeBCollections = SqlDelightMediaCollectionRepository(database) { 3L }
            val item = runtimeBCollections.observeCollection(CollectionId("direct-collection")).value!!.items.single().toCatalogItem()
            val registry = PlaybackSourceResolverRegistry(setOf(DirectMediaProvider(runtimeBStore)))
            val resolved = assertIs<kg.dev.shared.feature.player.PlaybackResolution.Resolved>(registry.resolve(item))
            val source = assertIs<PlaybackSource.Direct>(resolved.media.source)
            assertEquals(descriptor.uri, source.uri)
            assertEquals(descriptor.mimeType, source.mimeType)
            val player = assertIs<MediaOpenResult.Player>(DefaultMediaOpenCoordinator(registry).open(item)).configuration
            assertEquals("direct", player.playbackKind)
            assertEquals(descriptor.uri, player.directUri)
            assertEquals(descriptor.mimeType, player.mimeType)
            assertEquals(catalog.reference, item.reference)
            assertEquals(1, runtimeBCollections.observeCollection(CollectionId("direct-collection")).value!!.items.size)
        }
        file.delete()
    }

    @Test fun unavailableCollectionMediaRemainsUntouchedAfterCoordinatorOpenFailure() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        PlayerDatabase.Schema.create(driver)
        val database = createPlayerDatabase(driver)
        val collections = SqlDelightMediaCollectionRepository(database, CollectionIdGenerator { CollectionId("unavailable") }) { 10L }
        val id = collections.create("Unavailable")
        collections.addMedia(id, media("direct", "missing-source", "Retained", "thumb", "author", 99L))
        val before = collections.observeCollection(id).value!!
        val item = before.items.single().toCatalogItem()
        val coordinator = DefaultMediaOpenCoordinator(
            PlaybackSourceResolverRegistry(setOf(DirectMediaProvider(SqlDelightDirectMediaSourceStore(database))))
        )
        assertIs<MediaOpenResult.Failure>(coordinator.open(item))
        val after = collections.observeCollection(id).value!!
        assertEquals(before, after)
        assertEquals(1, after.collection.itemCount)
        assertEquals(item, after.items.single().toCatalogItem())
        driver.close()
    }

    @Test fun denseMovesAppendCompactionAndProviderIdentityArePersistent() = runTest {
        val file = File.createTempFile("collection-order", ".db").also { it.delete() }
        val a = media("youtube", "a", "A")
        val b = media("direct", "same", "B")
        val c = media("youtube", "same", "C")
        val d = media("youtube", "d", "D")
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            PlayerDatabase.Schema.create(driver)
            var now = 0L
            val repo = SqlDelightMediaCollectionRepository(createPlayerDatabase(driver), CollectionIdGenerator { CollectionId("ordered") }) { ++now }
            val id = repo.create("Ordered")
            listOf(a, b, c, d).forEach { repo.addMedia(id, it) }
            assertEquals(listOf("a", "same", "same", "d"), repo.observeCollection(id).value!!.items.map { it.reference.externalId })
            val added = repo.observeCollection(id).value!!.items.associate { it.reference to it.addedAtEpochMs }
            val unchangedAt = repo.observeCollection(id).value!!.collection.updatedAtEpochMs
            repo.moveMedia(id, c.reference, b.reference)
            assertEquals(listOf(a.reference, c.reference, b.reference, d.reference), repo.observeCollection(id).value!!.items.map { it.reference })
            repo.moveMedia(id, b.reference, null)
            assertEquals(listOf(a.reference, c.reference, d.reference, b.reference), repo.observeCollection(id).value!!.items.map { it.reference })
            assertTrue(repo.observeCollection(id).value!!.collection.updatedAtEpochMs > unchangedAt)
            val noOpAt = repo.observeCollection(id).value!!.collection.updatedAtEpochMs
            repo.moveMedia(id, b.reference, null)
            assertEquals(noOpAt, repo.observeCollection(id).value!!.collection.updatedAtEpochMs)
            repo.removeMedia(id, a.reference)
            assertEquals(listOf(c.reference, d.reference, b.reference), repo.observeCollection(id).value!!.items.map { it.reference })
            repo.addMedia(id, a)
            assertEquals(listOf(c.reference, d.reference, b.reference, a.reference), repo.observeCollection(id).value!!.items.map { it.reference })
            assertEquals(added[c.reference], repo.observeCollection(id).value!!.items.first { it.reference == c.reference }.addedAtEpochMs)
        }
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            val repo = SqlDelightMediaCollectionRepository(createPlayerDatabase(driver)) { 99 }
            assertEquals(listOf("same", "d", "same", "a"), repo.observeCollection(CollectionId("ordered")).value!!.items.map { it.reference.externalId })
        }
        file.delete()
    }

    private fun media(provider: String, id: String, title: String, thumbnail: String? = null, author: String? = null, duration: Long? = null) =
        MediaCatalogItem(MediaReference(MediaProviderId(provider), id), title, thumbnail, author, duration)

    private fun DirectMediaDescriptor.toCatalogItem() =
        MediaCatalogItem(MediaReference(MediaProviderId("direct"), id), title, thumbnailUrl, authorTitle, durationMs)
}
