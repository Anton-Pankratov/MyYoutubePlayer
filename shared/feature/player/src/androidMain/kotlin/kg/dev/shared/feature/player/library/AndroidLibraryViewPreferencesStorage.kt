package kg.dev.shared.feature.player.library

import android.content.Context

class AndroidLibraryViewPreferencesStorage(context: Context) : LibraryViewPreferencesStorage {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit())
    }

    private companion object { const val FILE_NAME = "library-view-preferences" }
}
