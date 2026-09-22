package kg.dev.shared.core.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals

class AppearancePreferencesTest {
    @Test
    fun persistedThemeAndPaletteRestoreAndResolveIndependently() {
        val storage = MutableStorage()
        val preferences = AppearancePreferences(storage)

        preferences.setThemeMode(AppThemeMode.Dark)
        preferences.setPalette(AppColorPalette.Ocean)

        val restored = AppearancePreferences(storage)
        assertEquals(AppThemeMode.Dark, restored.appearance.value.themeMode)
        assertEquals(AppColorPalette.Ocean, restored.appearance.value.palette)
        assertEquals(true, resolvedDarkTheme(AppThemeMode.System, true))
        assertEquals(false, resolvedDarkTheme(AppThemeMode.Light, true))
        assertEquals(true, resolvedDarkTheme(AppThemeMode.Dark, false))
    }

    private class MutableStorage : AppearancePreferencesStorage {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) { values[key] = value }
    }
}
