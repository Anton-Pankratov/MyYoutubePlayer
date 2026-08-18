package kg.dev.shared.core.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Configuration {
    @Serializable
    data object Home : Configuration

    @Serializable
    data object Search : Configuration

    @Serializable
    data class Player(
        val providerId: String,
        val externalId: String,
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val playbackKind: String,
        val directUri: String? = null,
        val mimeType: String? = null,
        val startPositionMs: Long = 0
    ) : Configuration

    @Serializable
    data object Profile : Configuration
}
