package com.safestep.mobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun signOut() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SafeStepApp
        app.container.sessions.end()
    }

    @Test
    fun unauthenticatedScreenShowsSignInAndRegistration() {
        composeRule.onNodeWithText("SafeStep").assertIsDisplayed()
        composeRule.onNodeWithTag("sign_in").assertIsDisplayed()
        composeRule.onNodeWithTag("go_register").assertIsDisplayed()
        composeRule.onNodeWithTag("go_forgot").assertIsDisplayed()
    }

    @Test
    fun shortCredentialsAreRejectedBeforeAnyRequest() {
        composeRule.onNodeWithTag("sign_in").performClick()
        composeRule.onNodeWithText("at least 3 characters", substring = true).assertIsDisplayed()
    }
}
