package za.co.devwell.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import za.co.devwell.model.AccountStatus
import za.co.devwell.model.DevWellUser
import za.co.devwell.model.UserRole
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*

@Composable
fun AdminHomeScreen(
    stats: Map<String, Int>,
    users: List<DevWellUser>,
    adminUserId: String,
    isLoading: Boolean,
    busyUserId: String?,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    onSetUserStatus: (String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageTitle("Platform administration", "Manage accounts and platform access") }
        item {
            PrivacyNotice("The admin role manages access but does not expose individual wellbeing responses.")
        }
        if (errorMessage != null) {
            item {
                DevWellCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onDismissError) { Text("Dismiss") }
                }
            }
        }
        if (isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            TextButton(onClick = onRefresh, enabled = !isLoading) { Text("Refresh accounts") }
        }
        if (stats.isNotEmpty()) {
            items(stats.toList().chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) ->
                        DevWellCard(
                            modifier = Modifier.weight(1f),
                            padding = 14.dp,
                        ) {
                            Text(
                                value.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = DevGreen,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { SectionLabel("Users") }
        if (users.isEmpty() && !isLoading) {
            item {
                DevWellCard {
                    Text("No accounts to display.", color = InkMuted)
                }
            }
        }
        items(users) { user ->
            DevWellCard(padding = 14.dp) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(user.name.initials(), size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            user.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Text(user.email, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip(user.role.name.lowercase().replaceFirstChar { it.uppercase() }, skin = ChipSkin.Neutral)
                            Chip(
                                user.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                skin = when (user.status.name) {
                                    "ACTIVE" -> ChipSkin.Green
                                    "PENDING" -> ChipSkin.Amber
                                    else -> ChipSkin.Red
                                },
                            )
                        }
                    }
                    val canChangeStatus = user.id != adminUserId &&
                        user.role != UserRole.ADMIN &&
                        user.status in setOf(AccountStatus.ACTIVE, AccountStatus.SUSPENDED)
                    if (canChangeStatus) {
                        val isActive = user.status == AccountStatus.ACTIVE
                        OutlinedButton(
                            onClick = { onSetUserStatus(user.id, !isActive) },
                            enabled = busyUserId == null,
                            modifier = Modifier.heightIn(min = 36.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isActive) RedBorder else TrackGreen,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isActive) RedText else DevGreen,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                if (busyUserId == user.id) "Updating…"
                                else if (isActive) "Suspend" else "Reactivate",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        item {
            DevWellCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    IconTile(Icons.Outlined.Password, size = 40.dp)
                    Column {
                        Text(
                            "Account provisioning",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "New accounts and privileged roles are provisioned through trusted backend operations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                }
            }
        }
    }
}
