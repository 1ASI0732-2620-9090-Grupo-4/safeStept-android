package com.safestep.mobile.simulation

import com.safestep.mobile.core.network.ApiFactory
import com.safestep.mobile.core.session.InMemoryTokenStore
import com.safestep.mobile.core.session.Session
import com.safestep.mobile.core.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: SimulationRepository

    private val simulationJson = """
        {"id":"rcp-basico","title":"RCP","emergencyType":"RCP","difficulty":"BASIC","durationMinutes":18,
         "xpReward":420,"coinReward":101,"description":"d","learningGoals":[],
         "steps":[
           {"id":"s1","prompt":"p1","correctOptionId":"a","options":[{"id":"a","label":"A","feedback":"ok"},{"id":"b","label":"B","feedback":"no"}]},
           {"id":"s2","prompt":"p2","correctOptionId":"b","options":[{"id":"a","label":"A","feedback":"no"},{"id":"b","label":"B","feedback":"ok"}]}
         ],"productSuggestions":[]}
    """.trimIndent()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer().apply { start() }
        val sessions = SessionManager(InMemoryTokenStore(Session("ana", "T", "R")))
        val api = ApiFactory(server.url("/api/v1").toString(), sessions)
        repository = SimulationRepository(api.simulation, api.gamification)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun summary(coins: Int, xp: Int, level: Int = 1) =
        """{"username":"ana","level":$level,"xp":$xp,"safeCoins":$coins,"streak":1,"completedSimulations":1}"""

    private fun model() = SimulationFlowViewModel("rcp-basico", repository) { Instant.parse("2026-11-08T10:00:00Z") }

    private suspend fun SimulationFlowViewModel.awaitStage(predicate: (Stage) -> Boolean) =
        withTimeout(5_000) { stage.first(predicate) }

    @Test
    fun `a full run sends the attempt and reports what the server granted`() = runBlocking {
        server.enqueue(json(simulationJson))
        val model = model()
        model.awaitStage { it is Stage.Intro }
        model.start()
        model.answer("a")
        model.next()
        model.answer("b")
        // summary before, attempt, summary after, coin history
        server.enqueue(json(summary(coins = 10, xp = 100)))
        server.enqueue(json("""{"id":"att-1","scenarioId":"rcp-basico","score":100,"totalSteps":2,"correctSteps":2}""", 201))
        server.enqueue(json(summary(coins = 111, xp = 520, level = 1)))
        server.enqueue(json("""[{"id":"attempt-att-1","simulationId":"rcp-basico","simulationTitle":"RCP","baseCoins":101,"multiplier":1.0,"accuracy":1.0,"earnedCoins":101,"successful":true,"createdAt":"2026-11-08T10:00:00Z"}]"""))
        model.finish()

        val finished = model.awaitStage { it is Stage.Finished } as Stage.Finished
        val outcome = finished.outcome
        assertEquals(100, outcome.score.score)
        assertEquals(101, outcome.earnedCoins)
        assertEquals(420, outcome.earnedXp)
        assertEquals(1.0, outcome.multiplier, 0.0001)
        assertTrue(outcome.successful)
        assertEquals(111, outcome.summary.safeCoins)

        server.takeRequest() // simulation detail
        server.takeRequest() // summary before
        val attempt = server.takeRequest()
        assertEquals("/api/v1/simulations/rcp-basico/attempts", attempt.path)
        val body = attempt.body.readUtf8()
        assertTrue(body.contains("\"score\":100"))
        assertTrue(body.contains("\"correctSteps\":2"))
    }

    @Test
    fun `a failed attempt is reported as not successful and the answers are kept on a send error`() = runBlocking {
        server.enqueue(json(simulationJson))
        val model = model()
        model.awaitStage { it is Stage.Intro }
        model.start()
        model.answer("b")
        model.next()
        model.answer("a")
        server.enqueue(json(summary(coins = 0, xp = 0)))
        server.enqueue(json("""{"code":"BUSINESS_RULE_VIOLATION","message":"x","details":"Server unavailable"}""", 500))
        model.finish()

        val playing = model.awaitStage { it is Stage.Playing && it.answers.size == 2 } as Stage.Playing
        assertEquals(2, playing.answers.size)
        assertEquals("Server unavailable", model.submitError.value)
    }

    @Test
    fun `an answer cannot be changed and the next step needs an answer first`() = runBlocking {
        server.enqueue(json(simulationJson))
        val model = model()
        model.awaitStage { it is Stage.Intro }
        model.start()

        model.next()
        assertEquals(0, (model.stage.value as Stage.Playing).stepIndex)
        model.answer("b")
        model.answer("a")
        assertEquals("b", (model.stage.value as Stage.Playing).answers["s1"])
        model.next()
        assertEquals(1, (model.stage.value as Stage.Playing).stepIndex)
        assertFalse((model.stage.value as Stage.Playing).answered)
    }

    @Test
    fun `a missing simulation shows the error instead of crashing`() = runBlocking {
        server.enqueue(json("""{"code":"NOT_FOUND","message":"x","details":"Simulation not found"}""", 404))
        val model = model()

        val failed = model.awaitStage { it is Stage.Failed } as Stage.Failed

        assertEquals("Simulation not found", failed.message)
    }
}
