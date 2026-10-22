package za.co.devwell.ui.engineer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.ui.components.*
import za.co.devwell.ui.theme.*
import androidx.compose.ui.graphics.ImageBitmap

@Composable
fun EngineerHomeScreen(
    name: String,
    onCheckIn: () -> Unit,
    onTrends: () -> Unit,
    onInsights: () -> Unit,
    onTeamOverview: () -> Unit,
    onProfile: () -> Unit,
    checkedInToday: Boolean = false,
    photo: ImageBitmap? = null,
) {
    val firstName = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifEmpty { "there" }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(20.dp).devWellScrollbar(listState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(BrandGradient))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greetingByHour(hourNow()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("$firstName \uD83D\uDC4B", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A quick check-in keeps you and your team in sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.clickable(onClick = onProfile)) {
                    Avatar(
                        name.initials(),
                        size = 46.dp,
                        background = Color.White,
                        contentColor = DevGreen,
                        image = photo,
                    )
                }
            }
        }
        item {
            DevWellCard {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "How are you feeling today?",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        color = Ink,
                    )
                    Icon(
                        Icons.Outlined.Eco, null,
                        Modifier.size(52.dp).alpha(0.5f),
                        tint = TrackGreen,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (checkedInToday) {
                        "You've checked in today. Made a mistake? You can update your answers."
                    } else {
                        "Your check-in helps you and your team stay healthy and balanced."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(14.dp), tint = InkMuted)
                    Spacer(Modifier.width(6.dp))
                    Text("Takes about 2 minutes", style = MaterialTheme.typography.labelMedium, color = InkMuted)
                }
                Spacer(Modifier.height(16.dp))
                DevWellButton(
                    if (checkedInToday) "Update today's check-in" else "Start check-in", onCheckIn,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Outlined.PlayArrow,
                )
            }
        }
        item {
            DevWellCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Today's focus")
                        Spacer(Modifier.height(6.dp))
                        Text("Protect your focus time", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Try 2 × 25 min deep work sessions with your notifications paused.",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkMuted,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconTile(Icons.Outlined.Laptop, size = 46.dp)
                }
            }
        }
        item {
            SectionLabel("Quick links")
            Spacer(Modifier.height(4.dp))
            DevWellCard(padding = 0.dp) {
                QuickLink(Icons.AutoMirrored.Outlined.ShowChart, "My trends", "See your check-in history", onTrends)
                DevWellDivider()
                QuickLink(Icons.Outlined.Insights, "Insights", "Supportive patterns from your check-ins", onInsights)
                DevWellDivider()
                QuickLink(Icons.Outlined.Groups, "Team overview", "Anonymised team data", onTeamOverview)
            }
        }
        item { PrivacyNotice() }
    }
}

@Composable
private fun QuickLink(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight, null,
            Modifier.size(20.dp), tint = InkMuted,
        )
    }
}

private fun hourNow(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
