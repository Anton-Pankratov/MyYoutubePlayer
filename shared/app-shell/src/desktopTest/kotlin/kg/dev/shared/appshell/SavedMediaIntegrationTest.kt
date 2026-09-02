package kg.dev.shared.appshell

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.common.media.MediaReference
import kg.dev.shared.core.storage.createPlayerDatabase
import kg.dev.shared.core.storage.db.PlayerDatabase
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.DefaultRootComponent
import kg.dev.shared.core.ui.navigation.MediaOpenCoordinator
import kg.dev.shared.feature.history.historyFeatureModule
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.history.domain.WatchedVideo
import kg.dev.shared.feature.player.DirectMediaDescriptor
import kg.dev.shared.feature.player.DirectMediaProvider
import kg.dev.shared.feature.player.playerFeatureModule
import kg.dev.shared.feature.player.library.DefaultLibraryComponent
import kg.dev.shared.feature.player.library.DefaultCollectionDetailComponent
import kg.dev.shared.feature.player.library.CollectionId
import kg.dev.shared.feature.player.library.CollectionIdGenerator
import kg.dev.shared.feature.player.library.LibraryViewPreferences
import kg.dev.shared.feature.player.library.LibraryViewPreferencesRepository
import kg.dev.shared.feature.player.library.LibraryViewPreferencesStorage
import kg.dev.shared.feature.player.library.SavedMediaRepository
import kg.dev.shared.feature.player.library.SavedMediaFilter
import kg.dev.shared.feature.player.library.SavedMediaSort
import kg.dev.shared.feature.player.library.SqlDelightMediaCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavedMediaIntegrationTest {
    @Test fun savedMediaAndHistoryDeletesRemainIndependent() = runTest {
        withGraph { saved, history, _, _, _ ->
            val item = item("independence")
            val watched = WatchedVideo(item.reference, item.title, positionMs = 42_000, durationMs = 180_000, watchedAtEpochMs = 7)
            history.save(watched); saved.setFavorite(item, true); saved.setFavorite(item, false)
            assertTrue(saved.favorites().value.isEmpty())
            assertEquals(watched, history.recent().single())
            saved.setWatchLater(item, true); history.delete(item.reference)
            assertTrue(history.recent().isEmpty())
            assertTrue(saved.observe(item.reference).value.isWatchLater)
        }
    }

    @Test fun unavailableOpenRetainsSavedMediaAndLibrarySelectionCallsRoot() = runTest {
        withGraph { saved, _, direct, coordinator, database ->
            val lifecycle = LifecycleRegistry().also { it.onCreate() }
            val root = DefaultRootComponent(DefaultComponentContext(lifecycle), Configuration.Home, { Any() }, coordinator, StandardTestDispatcher(testScheduler))
            val missing = item("missing-direct-source")
            saved.setFavorite(missing, true)
            assertIs<kg.dev.shared.core.ui.navigation.MediaOpenResult.Failure>(coordinator.open(missing))
            assertTrue(saved.observe(missing.reference).value.isFavorite)
            val present = item("library-root-direct")
            direct.register(DirectMediaDescriptor("library-root-direct", "Stored", "file:///stored.mp4", "video/mp4"))
            val library = DefaultLibraryComponent(DefaultComponentContext(lifecycle), saved, InMemoryLibraryViewPreferencesRepository(), { root.openMedia(it.toCatalogItem()) }, StandardTestDispatcher(testScheduler))
            saved.setFavorite(present, true); advanceUntilIdle(); library.open(saved.favorites().value.first { it.reference == present.reference }); advanceUntilIdle()
            val player = assertIs<Configuration.Player>(root.childStack.value.active.configuration)
            assertEquals("direct", player.providerId); assertEquals("library-root-direct", player.externalId); assertEquals("direct", player.playbackKind)
            lifecycle.onDestroy()
        }
    }

    @Test fun collectionSelectionUsesRootForDirectYoutubeAndUnavailableMediaWithoutDeletingMembership() = runTest {
        withGraph { _, _, direct, coordinator, database ->
            val lifecycle = LifecycleRegistry().also { it.onCreate() }
            val root = DefaultRootComponent(DefaultComponentContext(lifecycle), Configuration.Home, { Any() }, coordinator, StandardTestDispatcher(testScheduler))
            val collections = SqlDelightMediaCollectionRepository(
                database,
                CollectionIdGenerator { CollectionId("collection") },
            ) { 1L }
            val id = collections.create("Collection")
            val directItem = item("collection-direct")
            direct.register(DirectMediaDescriptor("collection-direct", "Stored", "file:///collection.mp4", "video/mp4"))
            collections.addMedia(id, directItem)
            val detail = DefaultCollectionDetailComponent(
                DefaultComponentContext(lifecycle), id, collections, root::openMedia, {}, StandardTestDispatcher(testScheduler)
            )
            advanceUntilIdle()
            detail.open(collections.observeCollection(id).value!!.items.single())
            advanceUntilIdle()
            assertEquals("direct", assertIs<Configuration.Player>(root.childStack.value.active.configuration).playbackKind)

            val youtube = MediaCatalogItem(MediaReference(MediaProviders.YouTube, "youtube-collection"), "YouTube")
            collections.addMedia(id, youtube)
            detail.open(collections.observeCollection(id).value!!.items.first { it.reference == youtube.reference })
            advanceUntilIdle()
            assertEquals("provider-controlled", assertIs<Configuration.Player>(root.childStack.value.active.configuration).playbackKind)

            val unavailable = item("unavailable-collection")
            collections.addMedia(id, unavailable)
            val before = collections.observeCollection(id).value!!
            detail.open(before.items.first { it.reference == unavailable.reference })
            advanceUntilIdle()
            assertIs<kg.dev.shared.core.ui.navigation.MediaOpenState.Failed>(root.mediaOpenState.value)
            assertEquals(before, collections.observeCollection(id).value!!)
            lifecycle.onDestroy()
        }
    }

    private suspend fun withGraph(block: suspend (SavedMediaRepository, HistoryRepository, DirectMediaProvider, MediaOpenCoordinator, PlayerDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); PlayerDatabase.Schema.create(driver); val database = createPlayerDatabase(driver)
        startKoin { modules(module {
            single<PlayerDatabase> { database }
            single<LibraryViewPreferencesStorage> { InMemoryLibraryViewPreferencesStorage() }
        }, historyFeatureModule, playerFeatureModule) }
        try { val koin = org.koin.java.KoinJavaComponent.getKoin(); block(koin.get(), koin.get(), koin.get(), koin.get(), database) } finally { stopKoin(); driver.close() }
    }
    private fun item(id: String) = MediaCatalogItem(MediaReference(MediaProviders.Direct, id), id)
}

private class InMemoryLibraryViewPreferencesRepository : LibraryViewPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(LibraryViewPreferences())
    override val preferences = mutablePreferences
    override suspend fun setFilter(filter: SavedMediaFilter) { mutablePreferences.value = mutablePreferences.value.copy(filter = filter) }
    override suspend fun setSort(sort: SavedMediaSort) { mutablePreferences.value = mutablePreferences.value.copy(sort = sort) }
}

private class InMemoryLibraryViewPreferencesStorage : LibraryViewPreferencesStorage {
    private val values = mutableMapOf<String, String>()
    override fun read(key: String): String? = values[key]
    override fun write(key: String, value: String) { values[key] = value }
}
