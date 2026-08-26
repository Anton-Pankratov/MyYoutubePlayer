package kg.dev.shared.feature.history.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResumePolicyTest {
    @Test
    fun nonPositiveSavedPositionStartsFromBeginning() {
        assertEquals(0, ResumePolicy.resolveStartPosition(0, 100_000))
        assertEquals(0, ResumePolicy.resolveStartPosition(-1, 100_000))
        assertIs<ResumeDecision.Unstarted>(ResumePolicy.evaluate(Long.MIN_VALUE, 100_000))
    }

    @Test
    fun knownDurationResumesStrictlyBelowNinetyFivePercent() {
        assertEquals(20_000, ResumePolicy.resolveStartPosition(20_000, 100_000))
        assertEquals(94_999, ResumePolicy.resolveStartPosition(94_999, 100_000))
    }

    @Test
    fun knownDurationRestartsAtOrBeyondNinetyFivePercent() {
        assertEquals(0, ResumePolicy.resolveStartPosition(95_000, 100_000))
        assertEquals(0, ResumePolicy.resolveStartPosition(99_000, 100_000))
        assertEquals(0, ResumePolicy.resolveStartPosition(100_000, 100_000))
        assertEquals(0, ResumePolicy.resolveStartPosition(100_001, 100_000))
        assertIs<ResumeDecision.Completed>(ResumePolicy.evaluate(95_000, 100_000))
    }

    @Test
    fun unusableDurationPreservesPositiveSavedPosition() {
        assertEquals(42_000, ResumePolicy.resolveStartPosition(42_000, null))
        assertEquals(42_000, ResumePolicy.resolveStartPosition(42_000, 0))
        assertEquals(42_000, ResumePolicy.resolveStartPosition(42_000, -1))
    }

    @Test
    fun longBoundariesAreEvaluatedWithoutOverflow() {
        val duration = Long.MAX_VALUE
        val threshold = duration - duration / 20

        assertEquals(threshold - 1, ResumePolicy.resolveStartPosition(threshold - 1, duration))
        assertEquals(0, ResumePolicy.resolveStartPosition(threshold, duration))
        assertEquals(0, ResumePolicy.resolveStartPosition(Long.MAX_VALUE, duration))
    }
}
