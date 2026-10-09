package com.safestep.mobile.data

import java.time.Instant

data class AttemptError(val stepNumber: Int, val message: String)
data class AttemptSubmission(
    val startedAt: Instant,
    val score: Int,
    val totalSteps: Int,
    val correctSteps: Int,
    val elapsedSeconds: Long,
    val errors: List<AttemptError>,
)

object AttemptScorer {
    fun evaluate(
        simulation: Simulation,
        selectedOptionIds: Map<String, String>,
        startedAt: Instant,
        completedAt: Instant,
    ): AttemptSubmission {
        val correct = simulation.steps.count { selectedOptionIds[it.id] == it.correctOptionId }
        val total = simulation.steps.size
        val errors = simulation.steps.mapIndexedNotNull { index, step ->
            if (selectedOptionIds[step.id] == step.correctOptionId) null
            else AttemptError(index + 1, "Incorrect answer for step ${index + 1}")
        }
        return AttemptSubmission(
            startedAt,
            if (total == 0) 0 else correct * 100 / total,
            total,
            correct,
            java.time.Duration.between(startedAt, completedAt).seconds.coerceAtLeast(0),
            errors,
        )
    }
}
