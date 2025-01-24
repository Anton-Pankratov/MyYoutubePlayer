package kg.dev.videoplayer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kg.dev.videoplayer.presentation.main.MainScreen
import kg.dev.videoplayer.ui.theme.MyYoutubePlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyYoutubePlayerTheme {
                MainScreen()
            }
        }
    }
}