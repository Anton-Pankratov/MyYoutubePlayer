package kg.dev.shared.feature.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kg.dev.shared.feature.player.PlayerError
import kg.dev.shared.feature.player.presentation.PlayerComponent

@Composable
fun PlayerContent(
    component: PlayerComponent,
    modifier: Modifier = Modifier,
    mediaSurface: @Composable ((Modifier) -> Unit)? = null
) {
    val state by component.state.collectAsState()
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        mediaSurface?.invoke(Modifier.fillMaxWidth().weight(1f, fill = false))
        Text(state.media?.catalogItem?.title ?: "Player", style = MaterialTheme.typography.titleLarge)
        state.media?.catalogItem?.authorTitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        state.error?.let { error ->
            Text(errorMessage(error), color = MaterialTheme.colorScheme.error)
            Button(onClick = component::retry) { Text("Retry") }
        }
        val duration = state.durationMs?.takeIf { it > 0 }
        if (duration != null) {
            Slider(
                value = state.positionMs.coerceIn(0, duration).toFloat(),
                onValueChange = { component.seekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("${state.positionMs / 1_000}s${duration?.let { " / ${it / 1_000}s" }.orEmpty()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = if (state.isPlaying) component::pause else component::play) {
                Text(if (state.isPlaying) "Pause" else "Play")
            }
        }
    }
}

private fun errorMessage(error: PlayerError): String = when (error) {
    PlayerError.UnsupportedMedia -> "This source has no direct media URL supported by this player."
    PlayerError.NetworkFailure -> "The media network request failed."
    PlayerError.SourceUnavailable -> "The media source is unavailable."
    PlayerError.InitializationFailed -> "The player could not be initialized."
    PlayerError.PlaybackFailed, PlayerError.Unknown -> "Playback failed."
}
