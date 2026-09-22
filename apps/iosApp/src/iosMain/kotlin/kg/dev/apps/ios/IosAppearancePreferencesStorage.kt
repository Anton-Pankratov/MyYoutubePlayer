package kg.dev.apps.ios

import kg.dev.shared.core.ui.design.AppearancePreferencesStorage
import platform.Foundation.NSUserDefaults

class IosAppearancePreferencesStorage : AppearancePreferencesStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun read(key: String): String? = defaults.stringForKey(key)
    override fun write(key: String, value: String) { defaults.setObject(value, key) }
}
