package kg.dev.shared.feature.player.library

import kotlin.js.Date

internal actual fun collectionCurrentEpochMillis(): Long = Date.now().toLong()
