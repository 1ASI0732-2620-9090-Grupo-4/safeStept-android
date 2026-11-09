package com.safestep.mobile

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Shared set-up of the system tests: the real app drives the real backend. They need the API running
 * on the host at port 8092 (reachable from the emulator as 10.0.2.2) with the seeded catalogue.
 * Every test creates its own player, so they can be repeated against the same database.
 */
abstract class SystemTestBase {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val api = "http://10.0.2.2:8092/api/v1"
    protected val password = "SecurePass123!"
    protected lateinit var username: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun createPlayer() {
        username = "droid-" + UUID.randomUUID().toString().take(8)
        call("POST", "/authentication/sign-up", JSONObject().put("username", username).put("password", password))
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SafeStepApp
        app.container.sessions.end()
    }

    @After
    fun closeApp() {
        scenario?.close()
    }

    // ---- helpers

    protected fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): String {
        val connection = URL(api + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream.bufferedReader().use { it.readText() }
    }

    /** Correct option of every step of a simulation, read from the API so the test can answer right. */
    protected fun correctOptions(simulationId: String): List<String> {
        val token = JSONObject(call("POST", "/authentication/sign-in", JSONObject().put("username", username).put("password", password))).getString("token")
        val steps: JSONArray = JSONObject(call("GET", "/simulations/$simulationId", token = token)).getJSONArray("steps")
        return (0 until steps.length()).map { steps.getJSONObject(it).getString("correctOptionId") }
    }

    protected fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun waitFor(tag: String, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun waitForText(text: String, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    protected fun tap(tag: String) {
        waitFor(tag)
        val node = compose.onNodeWithTag(tag)
        // Scrolling only helps inside scrollable columns; elsewhere the node is already on screen.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    protected fun type(tag: String, text: String) {
        waitFor(tag)
        compose.onNodeWithTag(tag).performTextInput(text)
    }

    protected fun signIn() {
        type("username", username)
        type("password", password)
        tap("sign_in")
        waitFor("tab_home")
    }

    protected fun playSimulation(simulationId: String) {
        val answers = correctOptions(simulationId)
        tap("tab_simulations")
        tap("simulation_$simulationId")
        tap("start_simulation")
        answers.forEachIndexed { index, option ->
            waitFor("step_prompt")
            tap("option_$option")
            if (index < answers.lastIndex) tap("next_step") else tap("finish_simulation")
        }
        waitFor("result_score")
    }

    /** Saves a screenshot of the device in the app's external files folder (pulled with adb for the report). */
    protected fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screens").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
