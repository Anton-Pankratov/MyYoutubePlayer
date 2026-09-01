package kg.dev.shared.feature.player.library

import java.io.File
import java.util.Properties

class DesktopLibraryViewPreferencesStorage(
    private val file: File = File(File(System.getProperty("user.home"), ".my-youtube-player"), "library-view-preferences.properties")
) : LibraryViewPreferencesStorage {
    override fun read(key: String): String? = load().getProperty(key)

    override fun write(key: String, value: String) {
        val properties = load()
        properties.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.outputStream().use { properties.store(it, null) }
    }

    private fun load() = Properties().also { properties ->
        if (file.exists()) file.inputStream().use(properties::load)
    }
}
