package kg.dev.shared.core.ui.navigation

import kg.dev.shared.core.common.media.MediaCatalogItem

/** Shared navigation boundary; implementations resolve providers outside UI/platform entry points. */
interface MediaOpenCoordinator {
    suspend fun open(item: MediaCatalogItem): MediaOpenResult
}

sealed interface MediaOpenResult {
    data class Player(val configuration: Configuration.Player) : MediaOpenResult
    data class Failure(val message: String, val retryable: Boolean = true) : MediaOpenResult
}

sealed interface MediaOpenState {
    data object Idle : MediaOpenState
    data class Resolving(val item: MediaCatalogItem) : MediaOpenState
    data class Failed(val item: MediaCatalogItem, val message: String, val retryable: Boolean) : MediaOpenState
}
