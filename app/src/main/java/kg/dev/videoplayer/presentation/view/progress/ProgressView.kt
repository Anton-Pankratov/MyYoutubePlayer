package kg.dev.videoplayer.presentation.view.progress

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kg.dev.videoplayer.ui.theme.Rose80

sealed class Progress {

    @Composable
    abstract fun View()

    class Small : Progress() {

        @Composable
        override fun View() = ProgressView(26)
    }

    class Large : Progress() {

        @Composable
        override fun View() = ProgressView(50)
    }
}

@Composable
fun ProgressView(size: Int) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(size.dp)
                .align(Alignment.Center),
            color = Rose80
        )
    }
}