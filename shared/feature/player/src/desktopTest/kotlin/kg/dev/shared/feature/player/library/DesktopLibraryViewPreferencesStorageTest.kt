package kg.dev.shared.feature.player.library

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLibraryViewPreferencesStorageTest {
    @Test fun preferencesSurviveRepositoryAndStorageRecreation() = kotlinx.coroutines.test.runTest {
        val file = kotlin.io.path.createTempDirectory("library-preferences").toFile().resolve("preferences.properties")
        try {
            val runtimeA = PersistentLibraryViewPreferencesRepository(DesktopLibraryViewPreferencesStorage(file))
            runtimeA.setFilter(SavedMediaFilter.WatchLater)
            runtimeA.setSort(SavedMediaSort.TitleAscending)

            val runtimeB = PersistentLibraryViewPreferencesRepository(DesktopLibraryViewPreferencesStorage(file))
            assertEquals(LibraryViewPreferences(SavedMediaFilter.WatchLater, SavedMediaSort.TitleAscending), runtimeB.preferences.value)
        } finally {
            file.parentFile?.deleteRecursively()
        }
    }
}
