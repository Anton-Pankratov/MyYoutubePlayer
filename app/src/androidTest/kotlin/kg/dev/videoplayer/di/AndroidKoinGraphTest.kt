package kg.dev.videoplayer.di

import androidx.test.ext.junit.runners.AndroidJUnit4
import kg.dev.shared.feature.player.library.LibraryViewPreferencesRepository
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class AndroidKoinGraphTest {
    @Test
    fun libraryViewPreferencesRepositoryResolvesFromApplicationGraph() {
        assertNotNull(GlobalContext.get().get<LibraryViewPreferencesRepository>())
    }
}
