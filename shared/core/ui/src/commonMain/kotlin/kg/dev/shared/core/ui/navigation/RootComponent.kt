package kg.dev.shared.core.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.MutableValue
import kg.dev.shared.core.common.media.MediaCatalogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

interface RootComponent<SearchComponent : Any> {
    val childStack: Value<ChildStack<Configuration, Child<SearchComponent>>>
    val navigationState: Value<NavigationState>
    val mediaOpenState: Value<MediaOpenState>

    fun showHome()
    fun showSearch()
    fun openMedia(media: MediaCatalogItem, startPositionMs: Long = 0)
    fun retryOpenMedia()
    fun showProfile()
    fun navigateBack()

    sealed interface Child<out SearchComponent : Any> {
        data class Home(val component: HomeComponent) : Child<Nothing>
        data class Search<SearchComponent : Any>(val component: SearchComponent) : Child<SearchComponent>
        data class Player(val component: PlayerComponent) : Child<Nothing>
        data class Profile(val component: ProfileComponent) : Child<Nothing>
    }
}

interface HomeComponent : ComponentContext
interface ProfileComponent

interface PlayerComponent {
    val mediaId: String
    val providerId: String
    val title: String?
    val thumbnailUrl: String?
    val authorTitle: String?
    val catalogDurationMs: Long?
    val playbackKind: String
    val directUri: String?
    val mimeType: String?
    val startPositionMs: Long
}

class DefaultRootComponent<SearchComponent : Any>(
    componentContext: ComponentContext,
    initialConfiguration: Configuration = Configuration.Home,
    private val searchComponentFactory: (ComponentContext) -> SearchComponent,
    private val mediaOpenCoordinator: MediaOpenCoordinator = object : MediaOpenCoordinator {
        override suspend fun open(item: MediaCatalogItem) = MediaOpenResult.Failure("Playback is not configured.", false)
    },
    coroutineContext: CoroutineContext = Dispatchers.Main.immediate,
    private val playerComponentFactory: (ComponentContext, Configuration.Player) -> PlayerComponent =
        { context, configuration -> DefaultPlayerComponent(context, configuration) }
) : RootComponent<SearchComponent>, ComponentContext by componentContext {
    // Decompose navigation creates lifecycle-bound children and must run on the UI thread.
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var openJob: Job? = null
    private var openGeneration = 0L
    private val navigation = StackNavigation<Configuration>()

    override val childStack: Value<ChildStack<Configuration, RootComponent.Child<SearchComponent>>> =
        childStack(
            source = navigation,
            serializer = Configuration.serializer(),
            initialConfiguration = initialConfiguration,
            handleBackButton = true,
            childFactory = ::createChild
        )
    private val mutableNavigationState = MutableValue(
        NavigationState(initialConfiguration, canNavigateBack = false)
    )
    override val navigationState: Value<NavigationState> = mutableNavigationState
    private val mutableMediaOpenState = MutableValue<MediaOpenState>(MediaOpenState.Idle)
    override val mediaOpenState: Value<MediaOpenState> = mutableMediaOpenState

    init {
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onDestroy() { openJob?.cancel(); scope.cancel() }
        })
        childStack.subscribe { stack ->
            mutableNavigationState.value = NavigationState(
                active = stack.active.configuration,
                canNavigateBack = stack.backStack.isNotEmpty()
            )
        }
    }

    override fun showHome() = navigation.bringToFront(Configuration.Home)
    override fun showSearch() = navigation.bringToFront(Configuration.Search)
    override fun showProfile() = navigation.bringToFront(Configuration.Profile)

    override fun openMedia(media: MediaCatalogItem, startPositionMs: Long) {
        val generation = ++openGeneration
        openJob?.cancel()
        openJob = scope.launch {
            mutableMediaOpenState.value = MediaOpenState.Resolving(media)
            when (val result = mediaOpenCoordinator.open(media)) {
                is MediaOpenResult.Player -> {
                    if (generation != openGeneration) return@launch
                    mutableMediaOpenState.value = MediaOpenState.Idle
                    navigation.pushNew(result.configuration.copy(startPositionMs = startPositionMs))
                }
                is MediaOpenResult.Failure -> if (generation == openGeneration) {
                    mutableMediaOpenState.value = MediaOpenState.Failed(media, result.message, result.retryable)
                }
            }
        }
    }

    override fun retryOpenMedia() {
        (mutableMediaOpenState.value as? MediaOpenState.Failed)?.let { openMedia(it.item) }
    }

    override fun navigateBack() {
        navigation.pop()
    }

    private fun createChild(
        configuration: Configuration,
        childContext: ComponentContext
    ): RootComponent.Child<SearchComponent> = when (configuration) {
        Configuration.Home -> RootComponent.Child.Home(DefaultHomeComponent(childContext))
        Configuration.Search -> RootComponent.Child.Search(searchComponentFactory(childContext))
        is Configuration.Player -> RootComponent.Child.Player(
            playerComponentFactory(childContext, configuration)
        )
        Configuration.Profile -> RootComponent.Child.Profile(DefaultProfileComponent(childContext))
    }
}

private class DefaultHomeComponent(componentContext: ComponentContext) :
    HomeComponent, ComponentContext by componentContext

private class DefaultProfileComponent(componentContext: ComponentContext) :
    ProfileComponent, ComponentContext by componentContext

private class DefaultPlayerComponent(
    componentContext: ComponentContext,
    configuration: Configuration.Player
) : PlayerComponent, ComponentContext by componentContext {
    override val mediaId = configuration.externalId
    override val providerId = configuration.providerId
    override val title = configuration.title
    override val thumbnailUrl = configuration.thumbnailUrl
    override val authorTitle = configuration.authorTitle
    override val catalogDurationMs = configuration.catalogDurationMs
    override val playbackKind = configuration.playbackKind
    override val directUri = configuration.directUri
    override val mimeType = configuration.mimeType
    override val startPositionMs = configuration.startPositionMs
}
