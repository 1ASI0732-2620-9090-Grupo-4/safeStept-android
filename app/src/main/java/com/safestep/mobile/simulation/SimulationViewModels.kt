package com.safestep.mobile.simulation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.ui.Load
import com.safestep.mobile.ui.toLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class SimulationFilters(val query: String = "", val emergencyType: String? = null, val difficulty: String? = null)

data class SimulationListState(val load: Load<List<SimulationDto>> = Load.Loading, val filters: SimulationFilters = SimulationFilters()) {
    val visible: List<SimulationDto>
        get() = (load as? Load.Ready)?.data.orEmpty().filter { simulation ->
            (filters.emergencyType == null || simulation.emergencyType == filters.emergencyType) &&
                (filters.difficulty == null || simulation.difficulty == filters.difficulty) &&
                (filters.query.isBlank() || simulation.title.contains(filters.query.trim(), ignoreCase = true))
        }

    val emergencyTypes: List<String>
        get() = (load as? Load.Ready)?.data.orEmpty().map { it.emergencyType }.distinct().sorted()

    val difficulties: List<String>
        get() = (load as? Load.Ready)?.data.orEmpty().map { it.difficulty }.distinct()
}

class SimulationListViewModel(private val repository: SimulationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(SimulationListState())
    val state: StateFlow<SimulationListState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(load = Load.Loading) }
        viewModelScope.launch { repository.list().let { result -> mutable.update { it.copy(load = result.toLoad()) } } }
    }

    fun search(query: String) = mutable.update { it.copy(filters = it.filters.copy(query = query)) }

    fun filterByType(type: String?) = mutable.update { it.copy(filters = it.filters.copy(emergencyType = type)) }

    fun filterByDifficulty(difficulty: String?) = mutable.update { it.copy(filters = it.filters.copy(difficulty = difficulty)) }
}

/** Where the player is inside one simulation. */
sealed interface Stage {
    data object Loading : Stage
    data class Failed(val message: String) : Stage
    data class Intro(val simulation: SimulationDto) : Stage
    data class Playing(
        val simulation: SimulationDto,
        val stepIndex: Int,
        val answers: Map<String, String>,
    ) : Stage {
        val step get() = simulation.steps[stepIndex]
        val answered get() = answers.containsKey(step.id)
        val isLast get() = stepIndex == simulation.steps.lastIndex
    }

    data class Submitting(val simulation: SimulationDto) : Stage
    data class Finished(val simulation: SimulationDto, val outcome: RewardOutcome) : Stage
}

class SimulationFlowViewModel(
    private val simulationId: String,
    private val repository: SimulationRepository,
    private val clock: () -> Instant = { Instant.now() },
) : ViewModel() {
    private val mutable = MutableStateFlow<Stage>(Stage.Loading)
    val stage: StateFlow<Stage> = mutable.asStateFlow()

    /** Message of a failed submission; the answers are kept so the player can try again. */
    private val mutableSubmitError = MutableStateFlow<String?>(null)
    val submitError: StateFlow<String?> = mutableSubmitError.asStateFlow()

    private var startedAt: Instant = clock()

    init {
        load()
    }

    fun load() {
        mutable.value = Stage.Loading
        viewModelScope.launch {
            repository.detail(simulationId).fold(
                onSuccess = { mutable.value = Stage.Intro(it) },
                onFailure = { mutable.value = Stage.Failed(it.message ?: "Error") },
            )
        }
    }

    fun start() {
        val intro = mutable.value as? Stage.Intro ?: return
        if (intro.simulation.steps.isEmpty()) return
        startedAt = clock()
        mutable.value = Stage.Playing(intro.simulation, 0, emptyMap())
    }

    /** Records the first answer given to the current step; later taps on the same step are ignored. */
    fun answer(optionId: String) {
        val playing = mutable.value as? Stage.Playing ?: return
        if (playing.answered) return
        mutable.value = playing.copy(answers = playing.answers + (playing.step.id to optionId))
    }

    fun next() {
        val playing = mutable.value as? Stage.Playing ?: return
        if (!playing.answered || playing.isLast) return
        mutable.value = playing.copy(stepIndex = playing.stepIndex + 1)
    }

    fun finish() {
        val playing = mutable.value as? Stage.Playing ?: return
        if (!playing.answered || !playing.isLast) return
        mutableSubmitError.value = null
        mutable.value = Stage.Submitting(playing.simulation)
        viewModelScope.launch {
            repository.submit(playing.simulation, playing.answers, startedAt, clock()).fold(
                onSuccess = { mutable.value = Stage.Finished(playing.simulation, it) },
                onFailure = {
                    mutableSubmitError.value = it.message ?: "Error"
                    mutable.value = playing
                },
            )
        }
    }
}
