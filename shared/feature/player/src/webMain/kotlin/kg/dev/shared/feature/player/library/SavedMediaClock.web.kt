package kg.dev.shared.feature.player.library

internal actual fun savedMediaCurrentEpochMillis(): Long = js("Date.now()") as Long
