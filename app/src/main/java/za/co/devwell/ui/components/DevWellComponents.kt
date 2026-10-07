package za.co.devwell.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
fun DevWellCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun PrivacyNotice(
    text: String = "Your individual check-ins are private. Managers only see anonymous team summaries after at least 5 participants.",
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
