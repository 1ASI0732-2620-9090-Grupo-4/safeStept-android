package com.safestep.mobile.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safestep.mobile.R
import com.safestep.mobile.core.GamificationRepository
import com.safestep.mobile.core.network.MissionDto
import com.safestep.mobile.core.network.SimulationDto
import com.safestep.mobile.core.network.SummaryDto
import com.safestep.mobile.simulation.SimulationCard
import com.safestep.mobile.simulation.SimulationRepository
import com.safestep.mobile.ui.Load
import com.safestep.mobile.ui.LoadContent
import com.safestep.mobile.ui.LocalContainer
import com.safestep.mobile.ui.SectionTitle
import com.safestep.mobile.ui.StatCard
import com.safestep.mobile.ui.StatRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val summary: SummaryDto,
    val dailyMissions: List<MissionDto>,
    val suggested: SimulationDto?,
)

class HomeViewModel(
    private val gamification: GamificationRepository,
    private val simulations: SimulationRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val state: StateFlow<Load<HomeData>> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = Load.Loading
        viewModelScope.launch {
            coroutineScope {
                val summary = async { gamification.summary() }
                val missions = async { gamification.missions() }
                val list = async { simulations.list() }
                val summaryResult = summary.await()
                mutable.value = summaryResult.fold(
                    onSuccess = { data ->
                        val all = list.await().getOrDefault(emptyList())
                        Load.Ready(
                            HomeData(
                                summary = data,
                                dailyMissions = missions.await().getOrDefault(emptyList()).filter { it.cadence.equals("DAILY", true) }.take(3),
                                // Prefer something already started, then the first one not finished.
                                suggested = all.firstOrNull { it.completion in 1..99 } ?: all.firstOrNull { it.completion == 0 },
                            ),
                        )
                    },
                    onFailure = { Load.Failed(it.message ?: "Error") },
                )
            }
        }
    }
}

@Composable
fun HomeScreen(username: String, onOpenSimulation: (String) -> Unit, onSimulations: () -> Unit, onProgress: () -> Unit) {
    val container = LocalContainer.current
    val model: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.gamification, container.simulations) } })
    val state by model.state.collectAsStateWithLifecycle()
    LoadContent(state, onRetry = model::load) { data ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.greeting, username), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.level_number, data.summary.level), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { (data.summary.xp % 1000) / 1000f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.xp_to_next, 1000 - data.summary.xp % 1000), style = MaterialTheme.typography.bodySmall)
                }
            }
            StatRow {
                StatCard(stringResource(R.string.safecoins), data.summary.safeCoins.toString(), Modifier.weight(1f).testTag("home_coins"))
                StatCard(stringResource(R.string.streak), data.summary.streak.toString(), Modifier.weight(1f))
                StatCard(stringResource(R.string.completed), data.summary.completedSimulations.toString(), Modifier.weight(1f))
            }
            data.suggested?.let {
                SectionTitle(stringResource(R.string.continue_training))
                SimulationCard(it, onClick = { onOpenSimulation(it.id) })
            }
            if (data.dailyMissions.isNotEmpty()) {
                SectionTitle(stringResource(R.string.daily_missions))
                data.dailyMissions.forEach { mission ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(mission.title, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(progress = { (mission.progress.toFloat() / mission.goal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${mission.progress}/${mission.goal} · +${mission.rewardXp} XP · +${mission.rewardCoins} SafeCoins", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(onClick = onSimulations, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.all_simulations)) }
            OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.view_progress)) }
        }
    }
}
