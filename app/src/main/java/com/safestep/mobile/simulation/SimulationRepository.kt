package com.safestep.mobile.simulation

import com.safestep.mobile.core.network.AttemptErrorDto
import com.safestep.mobile.core.network.AttemptRequest
import com.safestep.mobile.core.network.GamificationService
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.core.network.SimulationService
import com.safestep.mobile.core.network.SummaryDto
import com.safestep.mobile.core.network.apiCall
import java.time.Duration
import java.time.Instant

/** Score of one attempt, computed on the device from the answers of the player. */
data class AttemptScore(
    val score: Int,
    val totalSteps: Int,
    val correctSteps: Int,
    val elapsedSeconds: Long,
    val wrongSteps: List<Int>,
)

/** What the server granted for an attempt, read back from the player's summary. */
data class RewardOutcome(
    val score: AttemptScore,
    val earnedCoins: Int,
    val earnedXp: Int,
    val multiplier: Double,
    val successful: Boolean,
    val summary: SummaryDto,
    val leveledUp: Boolean,
)

object AttemptScorer {
    fun evaluate(
        simulation: SimulationDto,
        selectedOptionIds: Map<String, String>,
        startedAt: Instant,
        completedAt: Instant,
    ): AttemptScore {
        val wrong = simulation.steps.mapIndexedNotNull { index, step ->
            if (selectedOptionIds[step.id] == step.correctOptionId) null else index + 1
        }
        val total = simulation.steps.size
        val correct = total - wrong.size
        return AttemptScore(
            score = if (total == 0) 0 else correct * 100 / total,
            totalSteps = total,
            correctSteps = correct,
            elapsedSeconds = Duration.between(startedAt, completedAt).seconds.coerceAtLeast(0),
            wrongSteps = wrong,
        )
    }
}

class SimulationRepository(
    private val simulations: SimulationService,
    private val gamification: GamificationService,
) {
    suspend fun list(): Result<List<SimulationDto>> = apiCall { simulations.list() }

    suspend fun detail(id: String): Result<SimulationDto> = apiCall { simulations.detail(id) }

    suspend fun attempts() = apiCall { simulations.myAttempts() }

    /**
     * Sends the attempt and reads back what the server awarded. The reward is never computed on the
     * device: the server applies the pass threshold and the halving rule, so the numbers shown are
     * exactly the ones that were credited.
     */
    suspend fun submit(
        simulation: SimulationDto,
        answers: Map<String, String>,
        startedAt: Instant,
        now: Instant = Instant.now(),
    ): Result<RewardOutcome> {
        val before = apiCall { gamification.summary() }.getOrNull()
        val score = AttemptScorer.evaluate(simulation, answers, startedAt, now)
        val request = AttemptRequest(
            mode = "practice",
            startedAt = startedAt.toString(),
            completedAt = now.toString(),
            score = score.score,
            totalSteps = score.totalSteps,
            correctSteps = score.correctSteps,
            timeElapsed = score.elapsedSeconds,
            errors = score.wrongSteps.map { AttemptErrorDto(it, "Incorrect answer for step $it") },
        )
        val sent = apiCall { simulations.submitAttempt(simulation.id, request) }
        if (sent.isFailure) return Result.failure(sent.exceptionOrNull()!!)
        val after = apiCall { gamification.summary() }.getOrElse { return Result.failure(it) }
        val transaction = apiCall { gamification.coinTransactions() }.getOrNull()
            ?.filter { it.simulationId == simulation.id }
            ?.maxByOrNull { it.createdAt.orEmpty() }
        return Result.success(
            RewardOutcome(
                score = score,
                earnedCoins = (after.safeCoins - (before?.safeCoins ?: after.safeCoins)).coerceAtLeast(0),
                earnedXp = (after.xp - (before?.xp ?: after.xp)).coerceAtLeast(0),
                multiplier = transaction?.multiplier ?: 0.0,
                successful = transaction?.successful ?: false,
                summary = after,
                leveledUp = before != null && after.level > before.level,
            ),
        )
    }
}
