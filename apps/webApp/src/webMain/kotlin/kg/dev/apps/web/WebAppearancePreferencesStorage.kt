package kg.dev.apps.web

import kg.dev.shared.core.ui.design.AppearancePreferencesStorage
import kotlinx.browser.window

class WebAppearancePreferencesStorage : AppearancePreferencesStorage {
    override fun read(key: String): String? = window.localStorage.getItem(key)
    override fun write(key: String, value: String) { window.localStorage.setItem(key, value) }
}
