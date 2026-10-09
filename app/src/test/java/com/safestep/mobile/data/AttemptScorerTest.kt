package com.safestep.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AttemptScorerTest {
    private val started = Instant.parse("2026-09-16T10:00:00Z")
    private val completed = Instant.parse("2026-09-16T10:01:00Z")

    @Test
    fun `scores correct and incorrect answers`() {
        val simulation = Simulation(
            "cpr", "CPR", "", "BASIC",
            listOf(
                SimulationStep("s1", "First?", "a", emptyList()),
                SimulationStep("s2", "Second?", "b", emptyList()),
            ),
        )
        val result = AttemptScorer.evaluate(simulation, mapOf("s1" to "a", "s2" to "wrong"), started, completed)
        assertEquals(50, result.score)
        assertEquals(1, result.correctSteps)
        assertEquals(2, result.totalSteps)
        assertEquals(60, result.elapsedSeconds)
        assertEquals(2, result.errors.single().stepNumber)
    }

    @Test
    fun `empty simulation does not divide by zero`() {
        val result = AttemptScorer.evaluate(
            Simulation("empty", "Empty", "", "BASIC", emptyList()),
            emptyMap(), started, completed,
        )
        assertEquals(0, result.score)
        assertEquals(0, result.totalSteps)
    }
}
