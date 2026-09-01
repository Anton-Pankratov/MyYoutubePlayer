package kg.dev.shared.feature.player.library

import platform.Foundation.NSUserDefaults

class IosLibraryViewPreferencesStorage : LibraryViewPreferencesStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun read(key: String): String? = defaults.stringForKey(key)

    override fun write(key: String, value: String) {
        defaults.setObject(value, key)
    }
}
