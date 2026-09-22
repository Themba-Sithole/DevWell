package za.co.devwell.ui.engineer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

@Composable
fun EngineerHomeScreen(
    name: String,
    onCheckIn: () -> Unit,
    onManagerPreview: () -> Unit,
    onAiAssistant: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PageTitle(
                "Good morning, $name",
                "Your check-in helps you and your team stay healthy and balanced.",
            )
        }
        item {
            DevWellCard {
                Text("How are you feeling today?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("A private check-in takes about 2 minutes.")
                Spacer(Modifier.height(18.dp))
                Button(onClick = onCheckIn) { Text("Start check-in") }
            }
        }
        item {
            DevWellCard {
                Text("Today's focus", style = MaterialTheme.typography.titleMedium)
                Text("Protect your focus time", fontWeight = FontWeight.Bold)
                Text("Try 2 × 25 min deep work sessions")
            }
        }
        item { PrivacyNotice() }
        item {
            OutlinedButton(onClick = onManagerPreview, Modifier.fillMaxWidth()) {
                Text("What my manager sees")
            }
        }
        item {
            TextButton(onClick = onAiAssistant, Modifier.fillMaxWidth()) {
                Text("Ask DevWell assistant")
            }
        }
    }
}
