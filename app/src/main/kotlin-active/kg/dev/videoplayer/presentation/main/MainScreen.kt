package kg.dev.videoplayer.presentation.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kg.dev.shared.appshell.SharedAppContent
import kg.dev.shared.core.common.media.MediaProviders
import kg.dev.shared.core.ui.navigation.RootComponent
import kg.dev.shared.feature.home.presentation.DefaultHomeComponent
import kg.dev.shared.feature.home.presentation.HomeMediaAvailability
import kg.dev.shared.feature.history.domain.HistoryRepository
import kg.dev.shared.feature.player.presentation.PlayerComponent
import kg.dev.shared.feature.player.ui.AndroidPlayerContent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.videoplayer.localmedia.AndroidLocalMediaImporter
import kg.dev.videoplayer.localmedia.LocalMediaImportResult
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun MainScreen(rootComponent: RootComponent<SearchComponent>) {
    val historyRepository = koinInject<HistoryRepository>()
    val localMediaImporter = koinInject<AndroidLocalMediaImporter>()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = localMediaImporter.import(uri)) {
                is LocalMediaImportResult.Success -> rootComponent.openMedia(result.media)
                is LocalMediaImportResult.Failure -> Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    SharedAppContent(
        rootComponent = rootComponent,
        homeComponentFactory = { componentContext, selected, onImportRequested ->
            DefaultHomeComponent(
                componentContext = componentContext,
                historyRepository = historyRepository,
                mediaAvailability = HomeMediaAvailability { it.provider == MediaProviders.Direct },
                onItemSelected = selected,
                onLocalMediaImportRequested = onImportRequested
            )
        },
        onImportLocalMedia = { localVideoPicker.launch(arrayOf("video/*")) }
    ) { navigationComponent, modifier ->
        val playerComponent = navigationComponent as? PlayerComponent
        if (playerComponent is kg.dev.shared.feature.player.presentation.DefaultPlayerComponent) {
            AndroidPlayerContent(playerComponent, modifier)
        }
    }
}
