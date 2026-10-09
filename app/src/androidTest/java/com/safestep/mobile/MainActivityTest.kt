package com.safestep.mobile

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun unauthenticatedScreenShowsSignInAndRegistration() {
        composeRule.onNodeWithText("SafeStep").assertExists()
        composeRule.onNodeWithText("Sign in").assertExists()
        composeRule.onNodeWithText("Create account").assertExists()
    }
}
