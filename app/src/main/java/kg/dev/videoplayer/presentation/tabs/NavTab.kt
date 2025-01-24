package kg.dev.videoplayer.presentation.tabs

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import kg.dev.videoplayer.R

enum class NavTab(val dest: String, @DrawableRes val iconRes: Int, @StringRes val textRes: Int) {
    HOME(dest = "home", iconRes = R.drawable.ic_home, textRes = R.string.home),
    CHANNELS(dest = "channels", iconRes = R.drawable.ic_channels, textRes = R.string.search),
    PROFILE(dest = "profile", iconRes = R.drawable.ic_person, textRes = R.string.profile)
}