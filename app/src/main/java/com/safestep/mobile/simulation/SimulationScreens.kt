package com.safestep.mobile.simulation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safestep.mobile.R
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.ui.ErrorState
import com.safestep.mobile.ui.LoadContent
import com.safestep.mobile.ui.LocalContainer
import com.safestep.mobile.ui.RemoteImage
import com.safestep.mobile.ui.SectionTitle
import com.safestep.mobile.ui.StatCard
import com.safestep.mobile.ui.StatRow

@Composable
fun difficultyLabel(raw: String): String = when (raw.uppercase()) {
    "BASIC" -> stringResource(R.string.difficulty_basic)
    "INTERMEDIATE" -> stringResource(R.string.difficulty_intermediate)
    "ADVANCED" -> stringResource(R.string.difficulty_advanced)
    else -> raw
}

@Composable
fun SimulationListScreen(onOpen: (String) -> Unit) {
    val container = LocalContainer.current
    val model: SimulationListViewModel = viewModel(factory = viewModelFactory { initializer { SimulationListViewModel(container.simulations) } })
    val state by model.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = model::search,
            label = { Text(stringResource(R.string.search_simulations)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("simulation_search"),
        )
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.filters.emergencyType == null && state.filters.difficulty == null,
                    onClick = { model.filterByType(null); model.filterByDifficulty(null) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
            items(state.difficulties) { difficulty ->
                FilterChip(
                    selected = state.filters.difficulty == difficulty,
                    onClick = { model.filterByDifficulty(if (state.filters.difficulty == difficulty) null else difficulty) },
                    label = { Text(difficultyLabel(difficulty)) },
                )
            }
            items(state.emergencyTypes) { type ->
                FilterChip(
                    selected = state.filters.emergencyType == type,
                    onClick = { model.filterByType(if (state.filters.emergencyType == type) null else type) },
                    label = { Text(type) },
                )
            }
        }
        LoadContent(state.load, onRetry = model::load) {
            val visible = state.visible
            if (visible.isEmpty()) {
                ErrorState(stringResource(R.string.no_simulations_found))
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("simulation_list"),
                ) {
                    items(visible, key = { it.id }) { SimulationCard(it, onClick = { onOpen(it.id) }) }
                }
            }
        }
    }
}

@Composable
fun SimulationCard(simulation: SimulationDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("simulation_${simulation.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(simulation.imageUrl, Modifier.size(72.dp), simulation.title)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(simulation.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("${simulation.emergencyType} · ${difficultyLabel(simulation.difficulty)}", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Timer, null, Modifier.size(14.dp))
                    Text(stringResource(R.string.minutes, simulation.durationMinutes), style = MaterialTheme.typography.labelSmall)
                    Text("· +${simulation.xpReward} XP · +${simulation.coinReward} SafeCoins", style = MaterialTheme.typography.labelSmall)
                }
                if (simulation.completion > 0) {
                    LinearProgressIndicator(progress = { simulation.completion / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** One simulation from the intro to the result, driven by [SimulationFlowViewModel]. */
@Composable
fun SimulationScreen(simulationId: String, onClose: () -> Unit, onProgress: () -> Unit, onStore: () -> Unit) {
    val container = LocalContainer.current
    val model: SimulationFlowViewModel = viewModel(
        key = simulationId,
        factory = viewModelFactory { initializer { SimulationFlowViewModel(simulationId, container.simulations) } },
    )
    val stage by model.stage.collectAsStateWithLifecycle()
    val submitError by model.submitError.collectAsStateWithLifecycle()
    when (val current = stage) {
        Stage.Loading -> LoadingBox()
        is Stage.Failed -> ErrorState(current.message, onRetry = model::load)
        is Stage.Intro -> IntroContent(current.simulation, onStart = model::start)
        is Stage.Playing -> PlayingContent(current, submitError, model::answer, model::next, model::finish)
        is Stage.Submitting -> LoadingBox()
        is Stage.Finished -> ResultContent(current, onClose, onProgress, onStore)
    }
}

@Composable
private fun LoadingBox() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun IntroContent(simulation: SimulationDto, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RemoteImage(simulation.imageUrl, Modifier.fillMaxWidth().height(160.dp), simulation.title)
        Text(simulation.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${simulation.emergencyType} · ${difficultyLabel(simulation.difficulty)}", color = MaterialTheme.colorScheme.primary)
        Text(simulation.description)
        StatRow {
            StatCard(stringResource(R.string.reward_xp), "+${simulation.xpReward}", Modifier.weight(1f))
            StatCard(stringResource(R.string.reward_coins), "+${simulation.coinReward}", Modifier.weight(1f))
            StatCard(stringResource(R.string.duration), stringResource(R.string.minutes, simulation.durationMinutes), Modifier.weight(1f))
        }
        if (simulation.learningGoals.isNotEmpty()) {
            SectionTitle(stringResource(R.string.learning_goals))
            simulation.learningGoals.forEach { goal ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(goal)
                }
            }
        }
        Text(stringResource(R.string.reward_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onStart, enabled = simulation.steps.isNotEmpty(), modifier = Modifier.fillMaxWidth().testTag("start_simulation")) {
            Text(stringResource(R.string.start_simulation))
        }
    }
}

@Composable
private fun PlayingContent(
    playing: Stage.Playing,
    submitError: String?,
    onAnswer: (String) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val step = playing.step
    val chosen = playing.answers[step.id]
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.step_of, playing.stepIndex + 1, playing.simulation.steps.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        LinearProgressIndicator(progress = { (playing.stepIndex + (if (playing.answered) 1 else 0)) / playing.simulation.steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(step.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("step_prompt"))
        step.options.forEach { option ->
            val revealed = chosen != null
            val isCorrect = option.id == step.correctOptionId
            val isChosen = option.id == chosen
            val container = when {
                revealed && isCorrect -> MaterialTheme.colorScheme.secondaryContainer
                revealed && isChosen -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !revealed) { onAnswer(option.id) }.testTag("option_${option.id}"),
                colors = CardDefaults.cardColors(containerColor = container),
                border = BorderStroke(1.dp, if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (revealed && isCorrect) Icon(Icons.Filled.CheckCircle, stringResource(R.string.correct), tint = MaterialTheme.colorScheme.secondary)
                        if (revealed && isChosen && !isCorrect) Icon(Icons.Filled.Cancel, stringResource(R.string.incorrect), tint = MaterialTheme.colorScheme.error)
                        Text(option.label, modifier = Modifier.weight(1f))
                    }
                    if (revealed && (isChosen || isCorrect) && option.feedback.isNotBlank()) {
                        Text(option.feedback, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (submitError != null) Text(submitError, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("submit_error"))
        if (playing.answered) {
            if (playing.isLast) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().testTag("finish_simulation")) { Text(stringResource(R.string.finish_simulation)) }
            } else {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("next_step")) { Text(stringResource(R.string.next_step)) }
            }
        }
    }
}

@Composable
private fun ResultContent(finished: Stage.Finished, onClose: () -> Unit, onProgress: () -> Unit, onStore: () -> Unit) {
    val outcome = finished.outcome
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${outcome.score.score}%", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("result_score"))
        Text(pluralStringResource(R.plurals.correct_steps, outcome.score.correctSteps, outcome.score.correctSteps, outcome.score.totalSteps))
        StatRow {
            StatCard(stringResource(R.string.reward_xp), "+${outcome.earnedXp}", Modifier.weight(1f).testTag("result_xp"))
            StatCard(stringResource(R.string.reward_coins), "+${outcome.earnedCoins}", Modifier.weight(1f).testTag("result_coins"))
        }
        Text(
            when {
                !outcome.successful -> stringResource(R.string.reward_below_threshold)
                outcome.multiplier < 1.0 -> stringResource(R.string.reward_repeated, (outcome.multiplier * 100).toInt())
                else -> stringResource(R.string.reward_full)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (outcome.leveledUp) Text(stringResource(R.string.level_up, outcome.summary.level), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text(stringResource(R.string.new_balance, outcome.summary.safeCoins), style = MaterialTheme.typography.bodyMedium)
        if (finished.simulation.productSuggestions.isNotEmpty()) {
            OutlinedButton(onClick = onStore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.see_suggested_products)) }
        }
        OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_progress)) }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("back_to_simulations")) { Text(stringResource(R.string.back_to_simulations)) }
    }
}
