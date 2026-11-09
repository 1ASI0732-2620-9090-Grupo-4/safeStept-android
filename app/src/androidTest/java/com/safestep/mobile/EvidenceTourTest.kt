package com.safestep.mobile

import org.junit.Test

/**
 * Walks through every screen of the app against the real API and saves a screenshot of each one;
 * the images are the evidence for the report. Run it on demand: it also fails if a screen breaks.
 */
class EvidenceTourTest : SystemTestBase() {
    @Test
    fun tourOfTheWholeApp() {
        launch()
        waitFor("sign_in")
        shot("01-sign-in")
        tap("go_register")
        waitFor("sign_up")
        shot("02-register")
        tap("go_login")
        tap("go_forgot")
        waitFor("request_reset")
        shot("03-forgot-password")
        tap("back_to_login")
        signIn()
        waitForText("Level")
        shot("04-home")

        tap("tab_simulations")
        waitFor("simulation_list")
        shot("05-simulations")
        tap("simulation_rcp-basico")
        waitFor("start_simulation")
        shot("06-simulation-intro")
        tap("start_simulation")
        waitFor("step_prompt")
        val answers = correctOptions("rcp-basico")
        shot("07-simulation-step")
        tap("option_${answers[0]}")
        shot("08-simulation-feedback")
        answers.drop(1).forEachIndexed { index, option ->
            tap("next_step")
            waitFor("step_prompt")
            tap("option_$option")
        }
        tap("finish_simulation")
        waitFor("result_score")
        shot("09-simulation-result")
        tap("back_to_simulations")
        playSimulation("rcp-basico")
        tap("back_to_simulations")

        tap("tab_progress")
        waitFor("progress_coins")
        shot("10-progress-summary")
        for ((index, name) in listOf(1 to "missions", 2 to "badges", 3 to "ranking", 4 to "coins")) {
            tap("progress_tab_$index")
            compose.waitForIdle()
            shot("1${index}-progress-$name")
        }

        tap("tab_store")
        waitFor("product_list")
        shot("15-store-products")
        tap("store_tab_1")
        compose.waitForIdle()
        shot("16-store-kits")
        tap("store_tab_2")
        waitFor("coupon_balance")
        shot("17-store-coupons")
        tap("redeem_cpn-5")
        waitFor("coupon_redeemed")
        shot("18-coupon-redeemed")
        tap("store_tab_0")
        tap("product_mochila-emergencia")
        waitFor("add_to_cart")
        shot("19-product-detail")
        tap("add_to_cart")
        waitFor("added_to_cart")
        tap("back")
        tap("open_cart")
        waitFor("cart_list")
        tap("apply_cpn-5")
        shot("20-cart-with-coupon")

        tap("tab_profile")
        waitFor("save_profile")
        shot("21-profile")
    }
}
