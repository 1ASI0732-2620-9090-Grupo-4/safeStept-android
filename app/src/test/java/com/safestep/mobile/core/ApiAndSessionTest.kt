package com.safestep.mobile.core

import com.safestep.mobile.core.network.ApiException
import com.safestep.mobile.core.network.ApiFactory
import com.safestep.mobile.core.session.InMemoryTokenStore
import com.safestep.mobile.core.session.Session
import com.safestep.mobile.core.session.SessionManager
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiAndSessionTest {
    private lateinit var server: MockWebServer
    private lateinit var sessions: SessionManager
    private lateinit var auth: AuthRepository
    private lateinit var commerce: CommerceRepository
    private lateinit var gamification: GamificationRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        sessions = SessionManager(InMemoryTokenStore())
        val api = ApiFactory(server.url("/api/v1").toString(), sessions)
        auth = AuthRepository(api.auth, sessions)
        commerce = CommerceRepository(api.commerce)
        gamification = GamificationRepository(api.gamification, api.analytics)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `sign in stores the tokens and roles`() = runTest {
        server.enqueue(json("""{"id":2,"username":"ana","token":"T1","refreshToken":"R1","roles":["ROLE_USER"]}"""))

        val result = auth.signIn("  ana ", "SecurePass123!")

        assertTrue(result.isSuccess)
        assertEquals("T1", sessions.accessToken)
        assertEquals("R1", sessions.refreshToken)
        val request = server.takeRequest()
        assertEquals("/api/v1/authentication/sign-in", request.path)
        assertTrue(request.body.readUtf8().contains("\"username\":\"ana\""))
    }

    @Test
    fun `sign up creates the account and then signs in`() = runTest {
        server.enqueue(json("""{"id":3,"username":"bea"}""", 201))
        server.enqueue(json("""{"id":3,"username":"bea","token":"T","refreshToken":"R","roles":[]}"""))

        val result = auth.signUp("bea", "SecurePass123!")

        assertTrue(result.isSuccess)
        assertEquals("/api/v1/authentication/sign-up", server.takeRequest().path)
        assertEquals("/api/v1/authentication/sign-in", server.takeRequest().path)
        assertTrue(sessions.isSignedIn)
    }

    @Test
    fun `a rejected sign in surfaces the server message and keeps the user signed out`() = runTest {
        server.enqueue(json("""{"code":"UNAUTHORIZED","message":"Bad credentials","details":"Invalid username or password"}""", 401))

        val failure = auth.signIn("ana", "wrong-password").exceptionOrNull()

        assertTrue(failure is ApiException)
        assertEquals("Invalid username or password", failure?.message)
        assertEquals(401, (failure as ApiException).status)
        assertFalse(sessions.isSignedIn)
    }

    @Test
    fun `an authenticated call carries the bearer token`() = runTest {
        sessions.start(Session("ana", "T1", "R1"))
        server.enqueue(json("""{"username":"ana","level":2,"xp":1200,"safeCoins":300,"streak":4,"completedSimulations":7}"""))

        val summary = gamification.summary().getOrThrow()

        assertEquals(300, summary.safeCoins)
        assertEquals("Bearer T1", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `an expired token is refreshed once and the call is repeated`() = runTest {
        sessions.start(Session("ana", "OLD", "R1"))
        server.enqueue(json("""{"code":"UNAUTHORIZED","message":"expired"}""", 401))
        server.enqueue(json("""{"id":2,"username":"ana","token":"NEW","refreshToken":"R2","roles":[]}"""))
        server.enqueue(json("""{"username":"ana","level":1,"xp":0,"safeCoins":5,"streak":0,"completedSimulations":0}"""))

        val summary = gamification.summary().getOrThrow()

        assertEquals(5, summary.safeCoins)
        assertEquals("Bearer OLD", server.takeRequest().getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/api/v1/authentication/refresh-token", refresh.path)
        assertEquals("Bearer NEW", server.takeRequest().getHeader("Authorization"))
        assertEquals("NEW", sessions.accessToken)
        assertEquals("R2", sessions.refreshToken)
    }

    @Test
    fun `when the refresh fails the session ends`() = runTest {
        sessions.start(Session("ana", "OLD", "R1"))
        server.enqueue(json("""{"code":"UNAUTHORIZED","message":"expired"}""", 401))
        server.enqueue(json("""{"code":"UNAUTHORIZED","message":"refresh expired"}""", 401))

        val result = gamification.summary()

        assertTrue(result.isFailure)
        assertFalse(sessions.isSignedIn)
        assertNull(sessions.accessToken)
    }

    @Test
    fun `a network failure becomes a readable error`() = runTest {
        server.shutdown()

        val failure = gamification.summary().exceptionOrNull()

        assertTrue(failure is ApiException)
        assertEquals("Network error", failure?.message)
    }

    @Test
    fun `redeeming a coupon posts to the coupon address`() = runTest {
        sessions.start(Session("ana", "T1", "R1"))
        server.enqueue(json("""{"id":"rc-1","couponId":"cpn-5","title":"5%","type":"PERCENTAGE_OFF","discountPercentage":5,"status":"AVAILABLE"}""", 201))

        val redeemed = commerce.redeemCoupon("cpn-5").getOrThrow()

        assertEquals("AVAILABLE", redeemed.status)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/commerce/coupons/cpn-5/redeem", request.path)
    }

    @Test
    fun `confirming a Stripe payment sends the session id as a query parameter`() = runTest {
        sessions.start(Session("ana", "T1", "R1"))
        server.enqueue(json("""{"id":"o-1","total":10.0,"status":"PAID","paymentStatus":"PAID","items":[]}"""))

        val order = commerce.confirmStripePayment("o-1", "cs_test_1").getOrThrow()

        assertEquals("PAID", order.status)
        assertEquals("/api/v1/commerce/orders/o-1/payments/stripe-confirm?sessionId=cs_test_1", server.takeRequest().path)
    }

    @Test
    fun `signing out revokes the refresh token and clears the session`() = runTest {
        sessions.start(Session("ana", "T1", "R1"))
        server.enqueue(json("""{"message":"logged out"}"""))

        auth.signOut()

        assertEquals("/api/v1/authentication/logout", server.takeRequest().path)
        assertFalse(sessions.isSignedIn)
    }

    @Test
    fun `unknown fields in a response are ignored`() = runTest {
        sessions.start(Session("ana", "T1", "R1"))
        server.enqueue(json("""[{"id":"cpn-5","title":"5%","costCoins":150,"type":"PERCENTAGE_OFF","discountPercentage":5,"minPurchaseAmount":null,"somethingNew":true}]"""))

        val coupons = commerce.coupons().getOrThrow()

        assertEquals(150, coupons.single().costCoins)
    }
}
