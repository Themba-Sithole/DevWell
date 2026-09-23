package za.co.devwell.auth

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import za.co.devwell.MainActivity

class Member1UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun preview(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Welcome back").fetchSemanticsNodes().isNotEmpty() }
        tap("Student preview (debug only)")
        tap(text)
    }

    @Test fun invalidLoginShowsUsefulMessage() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Sign in").fetchSemanticsNodes().isNotEmpty() }
        tap("Sign in")
        compose.onNodeWithText("Enter your email address.").assertIsDisplayed()
    }

    @Test fun onboardingRequiresMatchingCompanyAndTeamThenWaitsForApproval() {
        preview("Preview new engineer")
        compose.onNodeWithText("Submit join request").assertIsNotEnabled()
        compose.onNodeWithText("Pending company").assertDoesNotExist()
        tap("DevWell"); tap("Mobile Team")
        compose.onNodeWithText("Submit join request").assertIsEnabled()
        tap("Acme")
        compose.onNodeWithText("Submit join request").assertIsNotEnabled()
        compose.onNodeWithText("Mobile Team").assertDoesNotExist()
        tap("Backend Team"); tap("Submit join request")
        compose.onNodeWithText("Waiting for approval").assertIsDisplayed()
        compose.onNodeWithText("Projects").assertDoesNotExist()
        tap("Simulate rejection")
        compose.onNodeWithText("Request not approved").assertIsDisplayed()
        tap("Simulate approval")
        compose.onNodeWithText("Projects").assertIsDisplayed()
    }

    @Test fun managerOnlySeesManagerTabsAndCanSignOut() {
        preview("Preview manager")
        compose.onNodeWithText("Projects").assertDoesNotExist()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("Dashboard", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun adminCanOpenProfileAndSignOut() {
        preview("Preview admin")
        compose.onNodeWithText("Projects").assertDoesNotExist()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Sign out").performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test fun registrationRetainsEmailButNotPasswordOnRotation() {
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
