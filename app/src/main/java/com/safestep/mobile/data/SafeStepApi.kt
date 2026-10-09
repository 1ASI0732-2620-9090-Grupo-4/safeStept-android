package com.safestep.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

data class AnswerOption(val id: String, val label: String, val feedback: String)
data class SimulationStep(
    val id: String,
    val prompt: String,
    val correctOptionId: String,
    val options: List<AnswerOption>,
)
data class Simulation(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val steps: List<SimulationStep>,
)
data class Product(val id: String, val name: String, val category: String, val price: String)
data class Progress(val level: Int, val xp: Int, val safeCoins: Int, val completedSimulations: Int)

class SafeStepApi(baseUrl: String) {
    private val root = baseUrl.trim().trimEnd('/')
    private var accessToken: String? = null

    val isAuthenticated: Boolean get() = !accessToken.isNullOrBlank()

    suspend fun signUp(username: String, password: String) {
        val body = JSONObject().put("username", username).put("password", password)
        request("/authentication/sign-up", "POST", body, authenticated = false)
    }

    suspend fun signIn(username: String, password: String) {
        val body = JSONObject().put("username", username).put("password", password)
        val response = JSONObject(request("/authentication/sign-in", "POST", body, authenticated = false))
        accessToken = response.optString("token").takeIf { it.isNotBlank() }
            ?: throw IOException("The API did not return an access token")
    }

    fun signOut() {
        accessToken = null
    }

    suspend fun simulations(): List<Simulation> = parseSimulations(request("/simulations"))

    suspend fun simulation(id: String): Simulation =
        parseSimulation(JSONObject(request("/simulations/${encodeSegment(id)}")))

    suspend fun submitAttempt(simulationId: String, attempt: AttemptSubmission) {
        val errors = JSONArray()
        attempt.errors.forEach { error ->
            errors.put(
                JSONObject()
                    .put("stepNumber", error.stepNumber)
                    .put("error", error.message)
                    .put("severity", "LOW"),
            )
        }
        val body = JSONObject()
            .put("mode", "practice")
            .put("startedAt", attempt.startedAt.toString())
            .put("completedAt", Instant.now().toString())
            .put("score", attempt.score)
            .put("totalSteps", attempt.totalSteps)
            .put("correctSteps", attempt.correctSteps)
            .put("timeElapsed", attempt.elapsedSeconds)
            .put("errors", errors)
        request("/simulations/${encodeSegment(simulationId)}/attempts", "POST", body)
    }

    suspend fun progress(): Progress {
        val data = JSONObject(request("/gamification/summary/me"))
        return Progress(
            data.optInt("level"),
            data.optInt("xp"),
            data.optInt("safeCoins"),
            data.optInt("completedSimulations"),
        )
    }

    suspend fun products(): List<Product> {
        val data = JSONArray(request("/commerce/products"))
        return (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            Product(
                item.optString("id"),
                item.optString("name"),
                item.optString("category"),
                item.optString("price"),
            )
        }
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): String = withContext(Dispatchers.IO) {
        val connection = (URL("$root$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
            if (authenticated) {
                accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            doOutput = body != null
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("HTTP $status: ${response.take(180)}")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeSegment(segment: String): String =
        java.net.URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")

    companion object {
        fun parseSimulations(raw: String): List<Simulation> {
            val array = JSONArray(raw)
            return (0 until array.length()).map { parseSimulation(array.getJSONObject(it)) }
        }

        fun parseSimulation(item: JSONObject): Simulation {
            val steps = item.optJSONArray("steps") ?: JSONArray()
            return Simulation(
                item.optString("id"),
                item.optString("title"),
                item.optString("description"),
                item.optString("difficulty"),
                (0 until steps.length()).map { stepIndex ->
                    val step = steps.getJSONObject(stepIndex)
                    val options = step.optJSONArray("options") ?: JSONArray()
                    SimulationStep(
                        step.optString("id"),
                        step.optString("prompt"),
                        step.optString("correctOptionId"),
                        (0 until options.length()).map { optionIndex ->
                            val option = options.getJSONObject(optionIndex)
                            AnswerOption(
                                option.optString("id"),
                                option.optString("label"),
                                option.optString("feedback"),
                            )
                        },
                    )
                },
            )
        }
    }
}
