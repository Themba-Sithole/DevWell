package za.co.devwell.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.ui.components.DevWellTextField
import za.co.devwell.ui.components.devWellScrollbar
import za.co.devwell.viewmodel.AuthUiState

/**
 * Onboarding for a manager who registered but has no company yet. Creating the
 * company and its first team activates the account and gives the manager a team
 * that engineers can request to join.
 */
@Composable
fun ManagerOnboardingScreen(
    state: AuthUiState,
    onCreateCompany: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    var companyName by rememberSaveable { mutableStateOf("") }
    var teamName by rememberSaveable { mutableStateOf("") }
    var step by rememberSaveable { mutableIntStateOf(1) }
    val stepScroll = rememberScrollState()

    Column(Modifier.fillMaxSize().background(AuthBackground).navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().background(AuthGreen).statusBarsPadding().padding(24.dp)) {
            Brand(Modifier.background(AuthBackground, AuthShape).padding(horizontal = 8.dp))
            Spacer(Modifier.height(28.dp))
            Text(
                "Welcome, ${state.user?.name?.substringBefore(' ') ?: "there"}!",
                color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            )
            Text(
                "Let's set up your company.",
                color = Color(0xFFC1D7BD), modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(2) { index ->
                    Box(
                        Modifier.weight(1f).height(6.dp)
                            .background(if (index < step) Color.White else Color(0xFF709174), CircleShape),
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f).devWellScrollbar(stepScroll).verticalScroll(stepScroll).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("STEP $step OF 2", style = MaterialTheme.typography.labelMedium, color = AuthMuted)
            Text(
                when (step) { 1 -> "What is your company?"; else -> "Name your first team" },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Text(
                when (step) {
                    1 -> "Engineers will find this company when they sign up and request to join."
                    else -> "Your team is where engineers work. You approve each engineer who joins it."
                },
                style = MaterialTheme.typography.bodySmall, color = AuthMuted,
            )
            AuthFeedback(state.error, state.message)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (step) {
                1 -> DevWellTextField(
                    companyName, { companyName = it }, label = "Company name", placeholder = "Acme Software",
                    singleLine = true, enabled = !state.busy,
                )
                else -> {
                    DevWellTextField(
                        teamName, { teamName = it }, label = "First team name", placeholder = "Platform Engineering",
                        singleLine = true, enabled = !state.busy,
                    )
                    Surface(
                        shape = AuthShape, color = Color.White,
                        border = BorderStroke(1.dp, AuthBorder), shadowElevation = 1.dp,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            DetailRow("Your name", state.user?.name.orEmpty())
                            DetailRow("Company", companyName.trim())
                            DetailRow("First team", teamName.trim())
                        }
                    }
                    PrivacyNote(
                        "Your company becomes visible to every DevWell user. Only engineers you approve " +
                            "can see your team's anonymous insights."
                    )
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 1) OutlinedButton(
                    onClick = { step-- }, enabled = !state.busy, shape = AuthShape,
                    border = BorderStroke(1.dp, AuthGreen), modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("Back", color = AuthGreen) }
                PrimaryAction(
                    if (state.busy) "Creating..." else if (step == 1) "Continue" else "Create company",
                    modifier = Modifier.weight(1f),
                    enabled = !state.busy && if (step == 1) companyName.isNotBlank() else teamName.isNotBlank(),
                ) {
                    if (step == 1) step++ else onCreateCompany(companyName, teamName)
                }
            }
            TextButton(onClick = onSignOut, enabled = !state.busy) {
                Text("Sign out", color = AuthMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
