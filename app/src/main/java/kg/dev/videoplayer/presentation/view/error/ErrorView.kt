package kg.dev.videoplayer.presentation.view.error

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kg.dev.videoplayer.R

@Composable
fun ErrorScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_error_something_wrong),
            contentDescription = "error_something_wrong",
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .align(Alignment.Center)
        )
    }
}