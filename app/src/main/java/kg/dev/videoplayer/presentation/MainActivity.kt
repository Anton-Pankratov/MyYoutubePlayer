package kg.dev.videoplayer.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kg.dev.common.logger.Logger
import kg.dev.videoplayer.presentation.main.MainScreen
import kg.dev.videoplayer.ui.theme.MyYoutubePlayerTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainActivity : ComponentActivity(), KoinComponent {

    private val logger: Logger by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.init()

        enableEdgeToEdge()

        setContent {
            MyYoutubePlayerTheme {
                MainScreen()
            }
        }
    }
}