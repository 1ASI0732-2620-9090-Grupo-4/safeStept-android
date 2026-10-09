package com.safestep.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.safestep.mobile.data.AttemptScorer
import com.safestep.mobile.data.AttemptSubmission
import com.safestep.mobile.data.Product
import com.safestep.mobile.data.Progress
import com.safestep.mobile.data.SafeStepApi
import com.safestep.mobile.data.Simulation
import kotlinx.coroutines.launch
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { SafeStepScreen() }
            }
        }
    }
}

@Composable
private fun SafeStepScreen() {
    var baseUrl by remember { mutableStateOf("http://10.0.2.2:8092/api/v1") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authenticated by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var currentTab by remember { mutableStateOf("simulations") }
    var selectedSimulation by remember { mutableStateOf<Simulation?>(null) }
    var lastResult by remember { mutableStateOf<AttemptSubmission?>(null) }
    var startedAt by remember { mutableStateOf(Instant.now()) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    val simulations = remember { mutableStateListOf<Simulation>() }
    val products = remember { mutableStateListOf<Product>() }
    val answers = remember { mutableStateMapOf<String, String>() }
    val api = remember(baseUrl) { SafeStepApi(baseUrl) }
    val scope = rememberCoroutineScope()

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            message = ""
            try {
                action()
            } catch (error: Exception) {
                message = error.message ?: "Request failed"
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.pilot_notice), style = MaterialTheme.typography.bodySmall)

        if (!authenticated) {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.api_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy && username.isNotBlank() && password.length >= 8,
                    onClick = {
                        runAction {
                            api.signIn(username.trim(), password)
                            authenticated = true
                            simulations.clear()
                            simulations.addAll(api.simulations())
                        }
                    },
                ) { Text(stringResource(R.string.sign_in)) }
                OutlinedButton(
                    enabled = !busy && username.length >= 3 && password.length >= 8,
                    onClick = {
                        runAction {
                            api.signUp(username.trim(), password)
                            api.signIn(username.trim(), password)
                            authenticated = true
                            simulations.clear()
                            simulations.addAll(api.simulations())
                        }
                    },
                ) { Text(stringResource(R.string.sign_up)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    api.signOut()
                    authenticated = false
                    selectedSimulation = null
                    lastResult = null
                    currentTab = "simulations"
                    password = ""
                }) { Text(stringResource(R.string.sign_out)) }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    currentTab = "simulations"
                    selectedSimulation = null
                    runAction { simulations.clear(); simulations.addAll(api.simulations()) }
                }) { Text(stringResource(R.string.simulations)) }
                OutlinedButton(onClick = {
                    currentTab = "progress"
                    runAction { progress = api.progress() }
                }) { Text(stringResource(R.string.progress)) }
                OutlinedButton(onClick = {
                    currentTab = "catalog"
                    runAction { products.clear(); products.addAll(api.products()) }
                }) { Text(stringResource(R.string.catalog)) }
            }

            when (currentTab) {
                "simulations" -> {
                    val selected = selectedSimulation
                    if (selected == null) {
                        simulations.forEach { simulation ->
                            OutlinedButton(onClick = {
                                runAction {
                                    selectedSimulation = api.simulation(simulation.id)
                                    answers.clear()
                                    startedAt = Instant.now()
                                }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("${simulation.title} · ${simulation.difficulty}")
                            }
                        }
                    } else {
                        Text(selected.title, style = MaterialTheme.typography.titleLarge)
                        Text(selected.description)
                        selected.steps.forEachIndexed { index, step ->
                            Text("${index + 1}. ${step.prompt}", fontWeight = FontWeight.SemiBold)
                            step.options.forEach { option ->
                                Row {
                                    RadioButton(
                                        selected = answers[step.id] == option.id,
                                        onClick = { answers[step.id] = option.id },
                                    )
                                    Text(option.label, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                        Button(
                            enabled = !busy && selected.steps.isNotEmpty() && answers.size == selected.steps.size,
                            onClick = {
                                runAction {
                                    val attempt = AttemptScorer.evaluate(selected, answers, startedAt, Instant.now())
                                    api.submitAttempt(selected.id, attempt)
                                    lastResult = attempt
                                    currentTab = "result"
                                    selectedSimulation = null
                                    answers.clear()
                                }
                            },
                        ) { Text(stringResource(R.string.finish_simulation)) }
                    }
                }
                "progress" -> {
                    progress?.let {
                        Text("${stringResource(R.string.level)}: ${it.level}")
                        Text("XP: ${it.xp}")
                        Text("SafeCoins: ${it.safeCoins}")
                        Text("${stringResource(R.string.completed)}: ${it.completedSimulations}")
                    }
                }
                "result" -> {
                    lastResult?.let { result ->
                        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge)
                        Text("${result.score}% · ${result.correctSteps}/${result.totalSteps}")
                        OutlinedButton(onClick = {
                            currentTab = "progress"
                            runAction { progress = api.progress() }
                        }) { Text(stringResource(R.string.view_progress)) }
                    }
                }
                "catalog" -> products.forEach { product ->
                    Text(product.name, fontWeight = FontWeight.SemiBold)
                    Text("${product.category} · ${product.price}")
                    HorizontalDivider()
                }
            }
        }

        if (busy) CircularProgressIndicator(modifier = Modifier.width(32.dp))
        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
    }
}
