package kg.dev.shared.feature.player.library

import platform.posix.time
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
internal actual fun savedMediaCurrentEpochMillis(): Long = time(null).toLong() * 1_000L
