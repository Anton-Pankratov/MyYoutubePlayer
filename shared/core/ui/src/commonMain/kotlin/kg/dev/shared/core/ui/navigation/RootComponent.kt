package kg.dev.shared.core.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.bringToFront
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushNew
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.MutableValue
import kg.dev.shared.core.common.media.MediaCatalogItem
import kg.dev.shared.core.common.media.DirectBackgroundEligibility
import kg.dev.shared.core.common.media.directMimeBackgroundEligibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

interface RootComponent<SearchComponent : Any> {
    val childStack: Value<ChildStack<Configuration, Child<SearchComponent>>>
    val navigationState: Value<NavigationState>
    val mediaOpenState: Value<MediaOpenState>
    val foregroundPlaybackState: Value<ForegroundPlaybackState>
    val playbackQueue: StateFlow<PlaybackQueueState>

    fun showHome()
    fun showSearch()
    fun openMedia(media: MediaCatalogItem, startPositionMs: Long = 0)
    fun playAll(items: List<MediaCatalogItem>)
    fun queueNext()
    fun queuePrevious()
    fun selectQueueItem(index: Int)
    fun onQueueItemCompleted(reference: kg.dev.shared.core.common.media.MediaReference)
    fun retryOpenMedia()
    fun openPendingForegroundPlayback()
    fun stopPlayback()
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
interface ProfileComponent : ComponentContext

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
        { context, configuration -> DefaultPlayerComponent(context, configuration) },
    private val canRetainEligibleDirectSession: (Configuration.Player) -> Boolean = { false },
    private val onEligiblePlayerUiDetached: (Configuration.Player) -> Unit = {},
    private val onForegroundPlaybackRequired: suspend () -> Unit = {},
    private val onBeforeQueueSelection: suspend () -> Unit = {},
    private val onStopPlayback: () -> Unit = {},
) : RootComponent<SearchComponent>, ComponentContext by componentContext {
    private data class PendingForegroundPlayback(
        val item: MediaCatalogItem,
        val configuration: Configuration.Player,
        val queueIndex: Int,
        val queueGeneration: Long,
    )

    // Decompose navigation creates lifecycle-bound children and must run on the UI thread.
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var openJob: Job? = null
    private var openGeneration = 0L
    private var queueTransition = false
    private var foregroundPlayerAvailable = true
    private var pendingForegroundPlayback: PendingForegroundPlayback? = null
    private lateinit var queueController: PlaybackQueueController
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
    private val mutableForegroundPlaybackState = MutableValue<ForegroundPlaybackState>(ForegroundPlaybackState.Idle)
    override val foregroundPlaybackState: Value<ForegroundPlaybackState> = mutableForegroundPlaybackState
    override val playbackQueue: StateFlow<PlaybackQueueState>

    init {
        queueController = PlaybackQueueController(::openQueueCandidate)
        playbackQueue = queueController.state
        lifecycle.subscribe(object : com.arkivanov.essenty.lifecycle.Lifecycle.Callbacks {
            override fun onStart() {
                foregroundPlayerAvailable = true
                openPendingForegroundPlayback()
            }
            override fun onStop() { foregroundPlayerAvailable = false }
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
        invalidatePendingForegroundPlayback()
        foregroundPlayerAvailable = true
        queueController.clear()
        openStandaloneMedia(media, startPositionMs)
    }
    private fun openStandaloneMedia(media: MediaCatalogItem, startPositionMs: Long) {
        queueTransition = false
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

    override fun playAll(items: List<MediaCatalogItem>) {
        invalidatePendingForegroundPlayback()
        foregroundPlayerAvailable = true
        queueController.start(items)
    }
    override fun queueNext() = queueController.next()
    override fun queuePrevious() = queueController.previous()
    override fun onQueueItemCompleted(reference: kg.dev.shared.core.common.media.MediaReference) = queueController.onCompleted(reference)

    override fun selectQueueItem(index: Int) {
        val beforeSelection = queueController.state.value
        val item = beforeSelection.items.getOrNull(index) ?: return
        val queueGeneration = beforeSelection.generation
        val unavailableForward = index > (beforeSelection.currentIndex ?: index)
        if (!queueController.select(index)) return

        invalidatePendingForegroundPlayback()
        val generation = ++openGeneration
        openJob?.cancel()
        if (beforeSelection.currentIndex == index) {
            mutableMediaOpenState.value = MediaOpenState.Idle
            return
        }
        openJob = scope.launch {
            onBeforeQueueSelection()
            val queue = queueController.state.value
            if (
                generation != openGeneration ||
                queue.generation != queueGeneration ||
                queue.currentIndex != index ||
                queue.current?.reference != item.reference
            ) return@launch
            resolveQueueCandidate(item, index, queueGeneration, unavailableForward, generation)
        }
    }

    private fun openQueueCandidate(index: Int, queueGeneration: Long) {
        val media = queueController.state.value.items.getOrNull(index) ?: return
        invalidatePendingForegroundPlayback()
        val generation = ++openGeneration
        openJob?.cancel()
        openJob = scope.launch {
            resolveQueueCandidate(
                media = media,
                index = index,
                queueGeneration = queueGeneration,
                unavailableForward = index > (queueController.state.value.currentIndex ?: -1),
                generation = generation,
            )
        }
    }

    private suspend fun resolveQueueCandidate(
        media: MediaCatalogItem,
        index: Int,
        queueGeneration: Long,
        unavailableForward: Boolean,
        generation: Long,
    ) {
        mutableMediaOpenState.value = MediaOpenState.Resolving(media)
        when (val result = mediaOpenCoordinator.open(media)) {
            is MediaOpenResult.Player -> {
                if (generation != openGeneration) return
                val configuration = result.configuration.copy(startPositionMs = 0)
                if (!configuration.isDirectBackgroundEligible()) {
                    if (!queueController.settle(index, queueGeneration)) return
                    onForegroundPlaybackRequired()
                    val queue = queueController.state.value
                    if (
                        generation != openGeneration ||
                        queue.generation != queueGeneration ||
                        queue.currentIndex != index ||
                        queue.current?.reference != media.reference
                    ) return
                    mutableMediaOpenState.value = MediaOpenState.Idle
                    if (foregroundPlayerAvailable) {
                        openQueuePlayer(configuration)
                    } else {
                        pendingForegroundPlayback = PendingForegroundPlayback(
                            item = media,
                            configuration = configuration,
                            queueIndex = index,
                            queueGeneration = queueGeneration,
                        )
                        mutableForegroundPlaybackState.value = ForegroundPlaybackState.Required(media)
                    }
                    return
                }
                if (!queueController.settle(index, queueGeneration)) return
                mutableMediaOpenState.value = MediaOpenState.Idle
                openQueuePlayer(configuration)
            }
            is MediaOpenResult.Failure -> if (generation == openGeneration) {
                if (result.retryable) mutableMediaOpenState.value = MediaOpenState.Failed(media, result.message, true)
                else queueController.unavailable(index, queueGeneration, forward = unavailableForward)
            }
        }
    }

    override fun retryOpenMedia() {
        (mutableMediaOpenState.value as? MediaOpenState.Failed)?.let { openMedia(it.item) }
    }

    override fun openPendingForegroundPlayback() {
        val pending = pendingForegroundPlayback ?: return
        val queue = queueController.state.value
        if (
            queue.generation != pending.queueGeneration ||
            queue.currentIndex != pending.queueIndex ||
            queue.current?.reference != pending.item.reference
        ) {
            invalidatePendingForegroundPlayback()
            return
        }
        pendingForegroundPlayback = null
        mutableForegroundPlaybackState.value = ForegroundPlaybackState.Idle
        foregroundPlayerAvailable = true
        openQueuePlayer(pending.configuration)
    }

    override fun stopPlayback() {
        ++openGeneration
        openJob?.cancel()
        invalidatePendingForegroundPlayback()
        foregroundPlayerAvailable = true
        onStopPlayback()
        queueController.clear()
        if (childStack.value.active.configuration is Configuration.Player) navigation.pop()
    }

    override fun navigateBack() {
        val player = childStack.value.active.configuration as? Configuration.Player
        if (player != null) {
            if (player.isDirectBackgroundEligible() && canRetainEligibleDirectSession(player)) {
                foregroundPlayerAvailable = false
                onEligiblePlayerUiDetached(player)
            }
            else queueController.clear()
        }
        navigation.pop()
    }

    private fun openQueuePlayer(configuration: Configuration.Player) {
        if (queueTransition && childStack.value.active.configuration is Configuration.Player) {
            navigation.replaceCurrent(configuration)
        } else {
            queueTransition = true
            navigation.pushNew(configuration)
        }
    }

    private fun invalidatePendingForegroundPlayback() {
        pendingForegroundPlayback = null
        mutableForegroundPlaybackState.value = ForegroundPlaybackState.Idle
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

private fun Configuration.Player.isDirectBackgroundEligible(): Boolean =
    playbackKind == "direct" &&
        directMimeBackgroundEligibility(mimeType) == DirectBackgroundEligibility.Eligible

private class DefaultHomeComponent(componentContext: ComponentContext) :
    HomeComponent, ComponentContext by componentContext

private class DefaultProfileComponent(componentContext: ComponentContext) : ProfileComponent, ComponentContext by componentContext

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
