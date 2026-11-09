package com.safestep.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * System tests of the Android app: the real app drives the real backend. They need the API running
 * on the host at port 8092 (reachable from the emulator as 10.0.2.2) with the seeded catalogue.
 * Every test creates its own player, so they can be repeated against the same database.
 */
class SystemFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val api = "http://10.0.2.2:8092/api/v1"
    private val password = "SecurePass123!"
    private lateinit var username: String
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

    private fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): String {
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
    private fun correctOptions(simulationId: String): List<String> {
        val token = JSONObject(call("POST", "/authentication/sign-in", JSONObject().put("username", username).put("password", password))).getString("token")
        val steps: JSONArray = JSONObject(call("GET", "/simulations/$simulationId", token = token)).getJSONArray("steps")
        return (0 until steps.length()).map { steps.getJSONObject(it).getString("correctOptionId") }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitFor(tag: String, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForText(text: String, timeoutMs: Long = 15_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tap(tag: String) {
        waitFor(tag)
        val node = compose.onNodeWithTag(tag)
        // Scrolling only helps inside scrollable columns; elsewhere the node is already on screen.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun type(tag: String, text: String) {
        waitFor(tag)
        compose.onNodeWithTag(tag).performTextInput(text)
    }

    private fun signIn() {
        type("username", username)
        type("password", password)
        tap("sign_in")
        waitFor("tab_home")
    }

    private fun playSimulation(simulationId: String) {
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

    // ---- scenarios

    @Test
    fun anUnauthenticatedVisitorSeesTheSignInForm() {
        launch()
        waitFor("sign_in")
        compose.onNodeWithTag("sign_in").assertIsDisplayed()
        compose.onNodeWithTag("go_register").assertIsDisplayed()
    }

    @Test
    fun aWrongPasswordIsRejectedAndTheVisitorStaysOnTheForm() {
        launch()
        type("username", username)
        type("password", "WrongPass999!")
        tap("sign_in")
        waitFor("auth_error")
        compose.onNodeWithTag("sign_in").assertIsDisplayed()
    }

    @Test
    fun aVisitorCanCreateAnAccountFromTheApp() {
        val fresh = "droid-new-" + UUID.randomUUID().toString().take(6)
        launch()
        tap("go_register")
        type("username", fresh)
        type("password", password)
        type("confirm_password", password)
        tap("sign_up")
        waitFor("tab_home")
    }

    @Test
    fun aMismatchedConfirmationIsRejectedBeforeCallingTheServer() {
        launch()
        tap("go_register")
        type("username", "droid-mismatch")
        type("password", password)
        type("confirm_password", password + "x")
        tap("sign_up")
        waitForText("do not match")
    }

    @Test
    fun aPlayerCompletesASimulationAndTheServerRewardIsShown() {
        launch()
        signIn()
        playSimulation("rcp-basico")
        compose.onNodeWithTag("result_score").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
        // a perfect first attempt pays the full reward of rcp-basico
        compose.onNodeWithTag("result_coins").assertIsDisplayed()
        waitForText("+101")
        tap("tab_progress")
        waitFor("progress_coins")
        waitForText("101")
    }

    @Test
    fun theCoinsOfTwoSimulationsBuyACouponAndTheCartAppliesIt() {
        launch()
        signIn()
        // 101 coins for the first perfect pass, 50 more for the second: 151 is enough for the 150-coin coupon
        playSimulation("rcp-basico")
        tap("back_to_simulations")
        playSimulation("rcp-basico")
        tap("tab_store")
        tap("store_tab_2")
        waitFor("coupon_balance")
        waitForText("151")
        tap("redeem_cpn-5")
        waitFor("coupon_redeemed")

        // add a product, then the cart offers the coupon and shows the discounted total
        tap("store_tab_0")
        tap("product_mochila-emergencia")
        tap("add_to_cart")
        waitFor("added_to_cart")
        tap("back")
        tap("open_cart")
        waitFor("cart_list")
        tap("apply_cpn-5")
        compose.onNodeWithTag("cart_total").assertIsDisplayed()
        waitForText("151")
    }
}
