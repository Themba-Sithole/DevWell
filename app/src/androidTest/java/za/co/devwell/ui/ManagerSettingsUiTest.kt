package za.co.devwell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import za.co.devwell.model.Team
import za.co.devwell.model.UserRole
import za.co.devwell.ui.manager.JoinRequestsScreen
import za.co.devwell.ui.manager.TeamSettingsScreen
import za.co.devwell.ui.settings.SettingsScreen
import za.co.devwell.ui.theme.DevWellTheme

/**
 * Guards the manager-facing settings: team settings no longer offer a time zone or a
 * check-in schedule, "What my manager sees" is not shown to managers, and every screen
 * pushed onto the stack exposes a back button.
 */
class ManagerSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    private val team = Team(id = "t1", companyId = "c1", name = "Mobile Team")

    private fun settings(role: UserRole) {
        compose.setContent {
            DevWellTheme {
                // Tall box: the whole settings list is composed, so nothing hides below the fold.
                Box(Modifier.requiredHeight(3000.dp)) {
                    SettingsScreen(
                        role = role,
                        name = "Test user",
                        email = "user@devwell.example",
                        onAccountProfile = {},
                        onManagerVisibility = {},
                        onNotifications = {},
                        onHelpAbout = {},
                    )
                }
            }
        }
    }

    @Test fun managerSettingsHideWhatMyManagerSees() {
        settings(UserRole.MANAGER)
        // Sanity check that the settings list rendered at all.
        compose.onNodeWithText("Help & about").assertExists()
        compose.onNodeWithText("What my manager sees").assertDoesNotExist()
    }

    @Test fun engineerSettingsStillShowWhatMyManagerSees() {
        settings(UserRole.ENGINEER)
        compose.onNodeWithText("What my manager sees").assertExists()
    }

    @Test fun teamSettingsDropTimezoneAndScheduleAndKeepBack() {
        // Rendered in a tall box so the whole list is composed at once: a field sitting below
        // the normal fold cannot slip past the absence checks below.
        compose.setContent {
            DevWellTheme {
                Box(Modifier.requiredHeight(3000.dp)) {
                    TeamSettingsScreen(team = team, onSave = {})
                }
            }
        }

        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithText("GENERAL").assertExists()
        compose.onNodeWithText("Time zone").assertDoesNotExist()
        compose.onNodeWithText("Check-in schedule").assertDoesNotExist()
        compose.onNodeWithText("WELLBEING ALERTS").assertExists()
        compose.onNodeWithText("Save changes").assertExists()
    }

    @Test fun joinRequestsExposeBackButton() {
        compose.setContent {
            DevWellTheme {
                Box(Modifier.requiredHeight(3000.dp)) {
                    JoinRequestsScreen(requests = emptyList(), onReview = { _, _ -> })
                }
            }
        }
        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onNodeWithText("No pending requests").assertExists()
    }
}
