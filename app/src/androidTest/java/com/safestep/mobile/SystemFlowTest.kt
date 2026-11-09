package com.safestep.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test
import java.util.UUID

/** System tests of the Android app: the real app drives the real backend (see [SystemTestBase]). */
class SystemFlowTest : SystemTestBase() {
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
