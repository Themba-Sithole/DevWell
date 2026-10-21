package za.co.devwell.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import za.co.devwell.MainActivity

class Member1UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) {
        val node = compose.onNodeWithText(text)
        try { node.assertIsDisplayed() } catch (_: AssertionError) { node.performScrollTo() }
        node.performClick()
    }
    private fun openLogin() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("I have an account").fetchSemanticsNodes().isNotEmpty() }
        tap("I have an account")
    }
    @Test fun invalidLoginShowsUsefulMessage() {
        openLogin()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty() }
        tap("Sign in")
        compose.onNodeWithText("Enter your email address.").assertIsDisplayed()
    }

    @Test fun registrationRetainsEmailButNotPasswordOnRotation() {
        openLogin()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Welcome back").fetchSemanticsNodes().isNotEmpty() }
        tap("New here? Create an account")
        compose.onNodeWithText("Full name").performTextInput("Alex Student")
        compose.onNodeWithText("Email").performTextInput("student@example.com")
        compose.onNodeWithText("Password", substring = false).performTextInput("password123")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Email").assertTextContains("student@example.com")
        tap("Create account")
        compose.onNodeWithText("Enter your password.").assertIsDisplayed()
    }
}
