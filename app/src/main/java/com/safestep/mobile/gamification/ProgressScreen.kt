package com.safestep.mobile.gamification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.safestep.mobile.core.network.AnalyticsSummaryDto
import com.safestep.mobile.core.network.BadgeDto
import com.safestep.mobile.core.network.CertificateDto
import com.safestep.mobile.core.network.CoinTransactionDto
import com.safestep.mobile.core.network.LeaderboardEntryDto
import com.safestep.mobile.core.network.MissionDto
import com.safestep.mobile.core.network.SummaryDto
import com.safestep.mobile.ui.EmptyState
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

data class ProgressData(
    val summary: SummaryDto,
    val statistics: AnalyticsSummaryDto,
    val missions: List<MissionDto>,
    val badges: List<BadgeDto>,
    val leaderboard: List<LeaderboardEntryDto>,
    val transactions: List<CoinTransactionDto>,
    val certificates: List<CertificateDto>,
)

class ProgressViewModel(private val repository: GamificationRepository) : ViewModel() {
    private val mutable = MutableStateFlow<Load<ProgressData>>(Load.Loading)
    val state: StateFlow<Load<ProgressData>> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = Load.Loading
        viewModelScope.launch {
            coroutineScope {
                val summary = async { repository.summary() }
                val statistics = async { repository.statistics() }
                val missions = async { repository.missions() }
                val badges = async { repository.badges() }
                val leaderboard = async { repository.leaderboard() }
                val transactions = async { repository.coinTransactions() }
                val certificates = async { repository.certificates() }
                // The summary is what the screen cannot do without; the rest degrades to empty lists.
                mutable.value = summary.await().fold(
                    onSuccess = {
                        Load.Ready(
                            ProgressData(
                                it,
                                statistics.await().getOrDefault(AnalyticsSummaryDto()),
                                missions.await().getOrDefault(emptyList()),
                                badges.await().getOrDefault(emptyList()),
                                leaderboard.await().getOrDefault(emptyList()),
                                transactions.await().getOrDefault(emptyList()).sortedByDescending { t -> t.createdAt.orEmpty() },
                                certificates.await().getOrDefault(emptyList()),
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
fun ProgressScreen(username: String) {
    val container = LocalContainer.current
    val model: ProgressViewModel = viewModel(factory = viewModelFactory { initializer { ProgressViewModel(container.gamification) } })
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_summary, R.string.tab_missions, R.string.tab_badges, R.string.tab_ranking, R.string.tab_coins)
    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) }, modifier = Modifier.testTag("progress_tab_$index"))
            }
        }
        LoadContent(state, onRetry = model::load) { data ->
            when (tab) {
                0 -> SummaryTab(data)
                1 -> MissionsTab(data.missions)
                2 -> BadgesTab(data.badges)
                3 -> RankingTab(data.leaderboard, username)
                else -> CoinsTab(data.transactions, data.summary.safeCoins)
            }
        }
    }
}

@Composable
private fun SummaryTab(data: ProgressData) {
    val summary = data.summary
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatRow {
            StatCard(stringResource(R.string.level), summary.level.toString(), Modifier.weight(1f))
            StatCard("XP", summary.xp.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.safecoins), summary.safeCoins.toString(), Modifier.weight(1f).testTag("progress_coins"))
        }
        StatRow {
            StatCard(stringResource(R.string.streak), summary.streak.toString(), Modifier.weight(1f))
            StatCard(stringResource(R.string.completed), summary.completedSimulations.toString(), Modifier.weight(1f).testTag("progress_completed"))
        }
        if (data.statistics.weeklyActivity.isNotEmpty()) {
            SectionTitle(stringResource(R.string.weekly_activity))
            val maxXp = data.statistics.weeklyActivity.maxOf { it.xp }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                data.statistics.weeklyActivity.forEach { day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((72 * day.xp / maxXp).coerceAtLeast(2).dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                        Text(day.day.take(3), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (data.statistics.skillProgress.isNotEmpty()) {
            SectionTitle(stringResource(R.string.skills))
            data.statistics.skillProgress.forEach {
                Text(it.skill, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { it.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (data.statistics.performanceByDifficulty.any { it.completed > 0 }) {
            SectionTitle(stringResource(R.string.performance_by_difficulty))
            data.statistics.performanceByDifficulty.filter { it.completed > 0 }.forEach {
                Text("${it.difficulty}: ${it.completed} · ${it.accuracy}%")
            }
        }
        if (data.statistics.commonMistakes.isNotEmpty()) {
            SectionTitle(stringResource(R.string.common_mistakes))
            data.statistics.commonMistakes.forEach {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${it.topic} (${it.mistakes})", fontWeight = FontWeight.SemiBold)
                        Text(it.recommendation, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        SectionTitle(stringResource(R.string.certificates))
        if (data.certificates.isEmpty()) {
            Text(stringResource(R.string.no_certificates), style = MaterialTheme.typography.bodySmall)
        } else {
            data.certificates.forEach {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                        Column {
                            Text(it.moduleName, fontWeight = FontWeight.SemiBold)
                            Text("${it.achievementLevel} · ${it.score}% · ${it.verificationCode}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionsTab(missions: List<MissionDto>) {
    if (missions.isEmpty()) return EmptyState(stringResource(R.string.no_missions))
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(missions, key = { it.id }) { mission ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${mission.title} · ${cadenceLabel(mission.cadence)}", fontWeight = FontWeight.SemiBold)
                    Text(mission.instructions, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (mission.progress.toFloat() / mission.goal.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${mission.progress}/${mission.goal} · +${mission.rewardXp} XP · +${mission.rewardCoins} SafeCoins", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun cadenceLabel(raw: String): String = when (raw.uppercase()) {
    "DAILY" -> stringResource(R.string.cadence_daily)
    "WEEKLY" -> stringResource(R.string.cadence_weekly)
    else -> raw
}

@Composable
private fun BadgesTab(badges: List<BadgeDto>) {
    if (badges.isEmpty()) return EmptyState(stringResource(R.string.no_badges))
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(badges, key = { it.id }) { badge ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (badge.unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (badge.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock, null)
                    Column {
                        Text("${badge.name} · ${badge.rarity.lowercase().replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.SemiBold)
                        Text(if (badge.unlocked) badge.description else badge.unlockRequirement, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingTab(entries: List<LeaderboardEntryDto>, username: String) {
    if (entries.isEmpty()) return EmptyState(stringResource(R.string.no_ranking))
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries, key = { it.rank }) { entry ->
            val mine = entry.name.equals(username, ignoreCase = true)
            Row(
                Modifier.fillMaxWidth()
                    .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${entry.rank}", fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                Text(entry.name, modifier = Modifier.weight(1f))
                Text("${entry.xp} XP · ${entry.streak}d")
            }
        }
    }
}

@Composable
private fun CoinsTab(transactions: List<CoinTransactionDto>, balance: Int) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StatCard(stringResource(R.string.balance), balance.toString(), Modifier.fillMaxWidth()) }
        if (transactions.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_transactions)) }
        }
        items(transactions, key = { it.id }) { transaction ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(transaction.simulationTitle, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.transaction_detail, (transaction.accuracy * 100).toInt(), (transaction.multiplier * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("+${transaction.earnedCoins}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
