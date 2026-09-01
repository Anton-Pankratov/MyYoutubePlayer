package kg.dev.shared.feature.player.library

import kotlinx.browser.window

class WebLibraryViewPreferencesStorage : LibraryViewPreferencesStorage {
    override fun read(key: String): String? = window.localStorage.getItem(key)
    override fun write(key: String, value: String) { window.localStorage.setItem(key, value) }
}
