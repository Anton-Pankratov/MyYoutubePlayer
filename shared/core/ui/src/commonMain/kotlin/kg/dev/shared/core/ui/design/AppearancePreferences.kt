package kg.dev.shared.core.ui.design

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small provider-neutral preference boundary shared by every Compose host. */
interface AppearancePreferencesStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

data class AppAppearance(
    val themeMode: AppThemeMode = AppThemeMode.System,
    val palette: AppColorPalette = AppColorPalette.Default,
)

class AppearancePreferences(
    private val storage: AppearancePreferencesStorage,
) {
    private val mutableAppearance = MutableStateFlow(
        AppAppearance(
            themeMode = storage.read(THEME_MODE_KEY).toThemeMode(),
            palette = storage.read(PALETTE_KEY).toPalette(),
        )
    )
    val appearance: StateFlow<AppAppearance> = mutableAppearance.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) = update(mutableAppearance.value.copy(themeMode = mode))

    fun setPalette(palette: AppColorPalette) = update(mutableAppearance.value.copy(palette = palette))

    private fun update(next: AppAppearance) {
        storage.write(THEME_MODE_KEY, next.themeMode.name)
        storage.write(PALETTE_KEY, next.palette.name)
        mutableAppearance.value = next
    }

    private fun String?.toThemeMode(): AppThemeMode = AppThemeMode.values().firstOrNull { it.name == this } ?: AppThemeMode.System
    private fun String?.toPalette(): AppColorPalette = AppColorPalette.values().firstOrNull { it.name == this } ?: AppColorPalette.Default

    private companion object {
        const val THEME_MODE_KEY = "app.appearance.theme_mode"
        const val PALETTE_KEY = "app.appearance.palette"
    }
}
