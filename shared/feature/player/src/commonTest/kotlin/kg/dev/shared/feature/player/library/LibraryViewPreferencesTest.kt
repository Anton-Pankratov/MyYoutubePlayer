package kg.dev.shared.feature.player.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LibraryViewPreferencesTest {
    @Test fun defaultsUnknownValuesAndDesiredStateWritesAreSafe() {
        val storage = MutableStorage()
        val repository = PersistentLibraryViewPreferencesRepository(storage)
        assertEquals(LibraryViewPreferences(), repository.preferences.value)

        storage.values["library.view.filter"] = "future_filter"
        storage.values["library.view.sort"] = "title_asc"
        assertEquals(
            LibraryViewPreferences(SavedMediaFilter.All, SavedMediaSort.TitleAscending),
            PersistentLibraryViewPreferencesRepository(storage).preferences.value
        )

        storage.values["library.view.filter"] = "both"
        storage.values["library.view.sort"] = "future_sort"
        assertEquals(
            LibraryViewPreferences(SavedMediaFilter.Both, SavedMediaSort.RecentlySaved),
            PersistentLibraryViewPreferencesRepository(storage).preferences.value
        )

    }

    @Test fun everyFilterAndSortUsesAStableStorageValue() = kotlinx.coroutines.test.runTest {
        val storage = MutableStorage()
        val repository = PersistentLibraryViewPreferencesRepository(storage)

        val filters = mapOf(
            SavedMediaFilter.All to "all",
            SavedMediaFilter.Favorites to "favorites",
            SavedMediaFilter.WatchLater to "watch_later",
            SavedMediaFilter.Both to "both"
        )
        filters.forEach { (filter, storedValue) ->
            repository.setFilter(filter)
            assertEquals(storedValue, storage.values["library.view.filter"])
            assertEquals(filter, PersistentLibraryViewPreferencesRepository(storage).preferences.value.filter)
        }
        repository.setFilter(SavedMediaFilter.Favorites)
        repository.setFilter(SavedMediaFilter.Favorites)
        assertEquals("favorites", storage.values["library.view.filter"])

        val sorts = mapOf(
            SavedMediaSort.RecentlySaved to "recently_saved",
            SavedMediaSort.TitleAscending to "title_asc",
            SavedMediaSort.TitleDescending to "title_desc"
        )
        sorts.forEach { (sort, storedValue) ->
            repository.setSort(sort)
            assertEquals(storedValue, storage.values["library.view.sort"])
            assertEquals(sort, PersistentLibraryViewPreferencesRepository(storage).preferences.value.sort)
        }
        repository.setSort(SavedMediaSort.TitleDescending)
        repository.setSort(SavedMediaSort.TitleDescending)
        assertEquals("title_desc", storage.values["library.view.sort"])
    }

    @Test fun failedWriteDoesNotPublishAnUnpersistedPreference() = kotlinx.coroutines.test.runTest {
        val repository = PersistentLibraryViewPreferencesRepository(MutableStorage(failWrites = true))
        assertFailsWith<IllegalStateException> { repository.setFilter(SavedMediaFilter.Both) }
        assertEquals(LibraryViewPreferences(), repository.preferences.value)
    }

    private class MutableStorage(private val failWrites: Boolean = false) : LibraryViewPreferencesStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            check(!failWrites)
            values[key] = value
        }
    }
}
