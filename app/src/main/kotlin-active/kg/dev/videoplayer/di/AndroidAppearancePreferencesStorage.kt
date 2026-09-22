package kg.dev.videoplayer.di

import android.content.Context
import kg.dev.shared.core.ui.design.AppearancePreferencesStorage

class AndroidAppearancePreferencesStorage(context: Context) : AppearancePreferencesStorage {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit())
    }

    private companion object { const val FILE_NAME = "app-appearance" }
}
