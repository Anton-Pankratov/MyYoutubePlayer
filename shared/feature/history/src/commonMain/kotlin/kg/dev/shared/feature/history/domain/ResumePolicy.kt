package kg.dev.shared.feature.history.domain

sealed interface ResumeDecision {
    val startPositionMs: Long

    data object Unstarted : ResumeDecision {
        override val startPositionMs: Long = 0
    }

    data class ResumeFrom(override val startPositionMs: Long) : ResumeDecision

    data object Completed : ResumeDecision {
        override val startPositionMs: Long = 0
    }
}

/** The single product policy for translating persisted progress into a playback start position. */
object ResumePolicy {
    fun evaluate(savedPositionMs: Long, durationMs: Long?): ResumeDecision {
        if (savedPositionMs <= 0) return ResumeDecision.Unstarted
        if (durationMs == null || durationMs <= 0) return ResumeDecision.ResumeFrom(savedPositionMs)
        if (savedPositionMs > durationMs) return ResumeDecision.Completed

        // 95% is 19/20. This form computes ceil(duration * 0.95) without overflow.
        val completionThresholdMs = durationMs - durationMs / 20
        return if (savedPositionMs >= completionThresholdMs) {
            ResumeDecision.Completed
        } else {
            ResumeDecision.ResumeFrom(savedPositionMs)
        }
    }

    fun resolveStartPosition(savedPositionMs: Long, durationMs: Long?): Long =
        evaluate(savedPositionMs, durationMs).startPositionMs
}
