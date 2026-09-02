package kg.dev.shared.appshell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.ui.design.AdaptiveLayout
import kg.dev.shared.core.ui.design.AppSurface
import kg.dev.shared.core.ui.design.EmptyState
import kg.dev.shared.core.ui.design.ErrorState
import kg.dev.shared.core.ui.design.MediaSpacing
import kg.dev.shared.core.ui.design.MediaTheme
import kg.dev.shared.core.ui.design.layoutForWidth
import kg.dev.shared.core.ui.navigation.Configuration
import kg.dev.shared.core.ui.navigation.MediaOpenState
import kg.dev.shared.core.ui.navigation.PlayerComponent
import kg.dev.shared.core.ui.navigation.RootComponent
import kg.dev.shared.feature.home.presentation.HomeComponent
import kg.dev.shared.feature.home.presentation.HomeMediaItemUiModel
import kg.dev.shared.feature.home.ui.HomeContent
import kg.dev.shared.feature.search.presentation.SearchComponent
import kg.dev.shared.feature.search.ui.SearchContent
import kg.dev.shared.feature.player.library.LibraryComponent
import kg.dev.shared.feature.player.library.LibraryContent
import kg.dev.shared.feature.player.library.LibraryHubComponent
import kg.dev.shared.feature.player.library.LibraryHubContent
import kg.dev.shared.feature.player.library.SavedMedia

private data class Destination(
    val configuration: Configuration,
    val label: String,
    val icon: ImageVector,
    val navigate: () -> Unit
)

typealias HomeComponentFactory = (ComponentContext, (HomeMediaItemUiModel) -> Unit, (() -> Unit)?) -> HomeComponent
typealias LibraryComponentFactory = (ComponentContext, (MediaCatalogItem) -> Unit) -> LibraryHubComponent

@Composable
fun SharedAppContent(
    rootComponent: RootComponent<SearchComponent>,
    homeComponentFactory: HomeComponentFactory? = null,
    libraryComponentFactory: LibraryComponentFactory? = null,
    onImportLocalMedia: (() -> Unit)? = null,
    playerContent: @Composable (PlayerComponent, Modifier) -> Unit = { player, modifier ->
        EmptyState("Playback unavailable", player.title ?: "This media cannot be played here.", modifier)
    }
) {
    val stack by rootComponent.childStack.subscribeAsState()
    val mediaOpenState by rootComponent.mediaOpenState.subscribeAsState()
    val activeConfiguration = stack.active.configuration
    val destinations = listOf(
        Destination(Configuration.Home, "Home", Icons.Outlined.Home, rootComponent::showHome),
        Destination(Configuration.Search, "Discover", Icons.Outlined.Search, rootComponent::showSearch),
        Destination(Configuration.Profile, "Library", Icons.Outlined.FavoriteBorder, rootComponent::showProfile)
    )

    AppSurface(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val layout = layoutForWidth(maxWidth)
            val isPlayer = activeConfiguration is Configuration.Player
            if (layout == AdaptiveLayout.Compact || isPlayer) {
                CompactShell(!isPlayer, destinations, activeConfiguration) { contentModifier ->
                    ActiveContent(
                        child = stack.active.instance,
                        rootComponent = rootComponent,
                        homeComponentFactory = homeComponentFactory,
                        libraryComponentFactory = libraryComponentFactory,
                        onImportLocalMedia = onImportLocalMedia,
                        playerContent = playerContent,
                        modifier = contentModifier
                    )
                    if (isPlayer) PlayerBackButton(rootComponent::navigateBack)
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    AppNavigationRail(destinations, activeConfiguration)
                    ActiveContent(
                        child = stack.active.instance,
                        rootComponent = rootComponent,
                        homeComponentFactory = homeComponentFactory,
                        libraryComponentFactory = libraryComponentFactory,
                        onImportLocalMedia = onImportLocalMedia,
                        playerContent = playerContent,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            }
            MediaOpenOverlay(mediaOpenState, rootComponent::retryOpenMedia)
        }
    }
}

@Composable
private fun CompactShell(
    showNavigation: Boolean,
    destinations: List<Destination>,
    activeConfiguration: Configuration,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        containerColor = MediaTheme.colors.background,
        bottomBar = {
            if (showNavigation) {
                NavigationBar(containerColor = MediaTheme.colors.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = destination.configuration == activeConfiguration,
                            onClick = destination.navigate,
                            icon = { Icon(destination.icon, destination.label) },
                            label = { Text(destination.label, style = MediaTheme.typography.label) }
                        )
                    }
                }
            }
        }
    ) { padding -> content(Modifier.fillMaxSize().padding(padding)) }
}

@Composable
private fun AppNavigationRail(destinations: List<Destination>, activeConfiguration: Configuration) {
    NavigationRail(containerColor = MediaTheme.colors.surface) {
        Surface(
            color = MediaTheme.colors.primary,
            contentColor = MediaTheme.colors.onPrimary,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) { Text("L", style = MediaTheme.typography.sectionTitle) }
        }
        Spacer(Modifier.height(MediaSpacing.xl))
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column {
                destinations.forEach { destination ->
                    NavigationRailItem(
                        selected = destination.configuration == activeConfiguration,
                        onClick = destination.navigate,
                        icon = { Icon(destination.icon, destination.label) },
                        label = { Text(destination.label, style = MediaTheme.typography.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveContent(
    child: RootComponent.Child<SearchComponent>,
    rootComponent: RootComponent<SearchComponent>,
    homeComponentFactory: HomeComponentFactory?,
    libraryComponentFactory: LibraryComponentFactory?,
    onImportLocalMedia: (() -> Unit)?,
    playerContent: @Composable (PlayerComponent, Modifier) -> Unit,
    modifier: Modifier
) {
    when (child) {
        is RootComponent.Child.Home -> {
            val home = homeComponentFactory?.let { factory ->
                remember(child.component) {
                    factory(child.component, { item -> rootComponent.openHomeItem(item) }, onImportLocalMedia)
                }
            }
            if (home == null) {
                EmptyState("Home is not available", "Playback history storage is not available on this platform yet.", modifier)
            } else {
                HomeContent(home, modifier)
            }
        }
        is RootComponent.Child.Search -> SearchContent(child.component, modifier)
        is RootComponent.Child.Player -> playerContent(child.component, modifier)
        is RootComponent.Child.Profile -> {
            val library = libraryComponentFactory?.let { factory ->
                remember(child.component) { factory(child.component as ComponentContext, rootComponent::openMedia) }
            }
            if (library == null) EmptyState("Library is not available", "Saved media storage is not available on this platform yet.", modifier)
            else LibraryHubContent(library, modifier)
        }
    }
}

internal fun <SearchComponent : Any> RootComponent<SearchComponent>.openHomeItem(item: HomeMediaItemUiModel) {
    openMedia(
        MediaCatalogItem(
            reference = item.reference,
            title = item.title,
            thumbnailUrl = item.thumbnailUrl,
            durationMs = item.durationMs
        ),
        item.startPositionMs
    )
}

@Composable
private fun PlayerBackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(MediaSpacing.md)
            .background(MediaTheme.colors.overlay, androidx.compose.foundation.shape.CircleShape)
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = MediaTheme.colors.playerControls)
    }
}

@Composable
private fun BoxScope.MediaOpenOverlay(state: MediaOpenState, onRetry: () -> Unit) {
    when (state) {
        MediaOpenState.Idle -> Unit
        is MediaOpenState.Resolving -> Box(
            Modifier.fillMaxSize().background(MediaTheme.colors.overlay),
            contentAlignment = Alignment.Center
        ) { androidx.compose.material3.CircularProgressIndicator(color = MediaTheme.colors.primary) }
        is MediaOpenState.Failed -> Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(MediaSpacing.xl),
            contentAlignment = Alignment.BottomCenter
        ) {
            ErrorState(
                title = "Playback isn’t available",
                message = "This provider cannot open the selected media right now.",
                onRetry = if (state.retryable) onRetry else null,
                modifier = Modifier.widthIn(max = 520.dp)
                    .background(MediaTheme.colors.surface, kg.dev.shared.core.ui.design.MediaShapes.large)
            )
        }
    }
}
