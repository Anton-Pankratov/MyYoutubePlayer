package kg.dev.shared.feature.player.library

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
internal actual fun collectionCurrentEpochMillis(): Long = time(null).toLong() * 1_000L
