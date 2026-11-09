package com.safestep.mobile.simulation

import com.safestep.mobile.core.network.OptionDto
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.core.network.StepDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AttemptScorerTest {
    private val started = Instant.parse("2026-09-16T10:00:00Z")
    private val completed = Instant.parse("2026-09-16T10:01:00Z")

    private fun simulation(vararg correct: String) = SimulationDto(
        id = "cpr",
        title = "CPR",
        steps = correct.mapIndexed { index, answer ->
            StepDto("s${index + 1}", "Step ${index + 1}?", answer, listOf(OptionDto("a", "A"), OptionDto("b", "B")))
        },
    )

    @Test
    fun `scores correct and incorrect answers`() {
        val result = AttemptScorer.evaluate(simulation("a", "b"), mapOf("s1" to "a", "s2" to "wrong"), started, completed)
        assertEquals(50, result.score)
        assertEquals(1, result.correctSteps)
        assertEquals(2, result.totalSteps)
        assertEquals(60, result.elapsedSeconds)
        assertEquals(listOf(2), result.wrongSteps)
    }

    @Test
    fun `an unanswered step counts as wrong`() {
        val result = AttemptScorer.evaluate(simulation("a", "b", "a", "b"), mapOf("s1" to "a", "s2" to "b", "s3" to "a"), started, completed)
        assertEquals(75, result.score)
        assertEquals(listOf(4), result.wrongSteps)
    }

    @Test
    fun `empty simulation does not divide by zero`() {
        val result = AttemptScorer.evaluate(simulation(), emptyMap(), started, completed)
        assertEquals(0, result.score)
        assertEquals(0, result.totalSteps)
        assertTrue(result.wrongSteps.isEmpty())
    }

    @Test
    fun `a completion time before the start never gives a negative duration`() {
        val result = AttemptScorer.evaluate(simulation("a"), mapOf("s1" to "a"), completed, started)
        assertEquals(0, result.elapsedSeconds)
    }
}
