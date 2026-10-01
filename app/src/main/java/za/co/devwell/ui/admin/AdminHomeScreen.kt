package za.co.devwell.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.DevWellUser
import za.co.devwell.ui.components.DevWellCard
import za.co.devwell.ui.components.PageTitle
import za.co.devwell.ui.components.PrivacyNotice

@Composable
fun AdminHomeScreen(
    stats: Map<String, Int>,
    users: List<DevWellUser>,
    onSetUserStatus: (String, Boolean) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Platform administration") }
        item {
            PrivacyNotice("The admin role manages access but does not expose individual wellbeing responses.")
        }
        items(stats.toList()) { (label, value) ->
            DevWellCard {
                Text("$value", style = MaterialTheme.typography.headlineMedium)
                Text(label)
            }
        }
        item { PageTitle("Users") }
        items(users) { user ->
            DevWellCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(user.name, fontWeight = FontWeight.Bold)
                        Text(user.email)
                        Text("${user.role} · ${user.status}")
                    }
                    val isActive = user.status == AccountStatus.ACTIVE
                    Button(onClick = { onSetUserStatus(user.id, !isActive) }) {
                        Text(if (isActive) "Suspend" else "Activate")
                    }
                }
            }
        }
        item {
            DevWellCard {
                Text("Temporary credentials", fontWeight = FontWeight.Bold)
                Text("Generate email and temporary password for first-time access.")
            }
        }
    }
}