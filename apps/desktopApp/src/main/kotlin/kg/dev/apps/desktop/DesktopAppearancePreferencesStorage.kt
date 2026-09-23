package kg.dev.apps.desktop

import java.io.File
import java.util.Properties
import kg.dev.shared.core.ui.design.AppearancePreferencesStorage

class DesktopAppearancePreferencesStorage(
    private val file: File = File(File(System.getProperty("user.home"), ".my-youtube-player"), "appearance.properties"),
) : AppearancePreferencesStorage {
    override fun read(key: String): String? = load().getProperty(key)

    override fun write(key: String, value: String) {
        load().also {
            it.setProperty(key, value)
            file.parentFile?.mkdirs()
            file.outputStream().use { output -> it.store(output, null) }
        }
    }

    private fun load() = Properties().also { if (file.exists()) file.inputStream().use(it::load) }
}
