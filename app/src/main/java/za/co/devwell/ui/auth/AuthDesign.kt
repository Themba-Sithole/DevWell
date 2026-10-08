package za.co.devwell.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.devwell.R

internal val AuthGreen = Color(0xFF2D5633)
internal val AuthBackground = Color(0xFFEEF2EC)
internal val AuthMuted = Color(0xFF657D78)
internal val AuthBorder = Color(0xFFD9E3DA)
internal val AuthShape = RoundedCornerShape(14.dp)

@Composable
internal fun Brand(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.devwell_logo), "DevWell", modifier = modifier.width(150.dp).height(62.dp))
}

@Composable
internal fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthGreen,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFB7C8BC),
            disabledContentColor = Color.White,
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PrivacyNote(text: String) {
    Row(Modifier.fillMaxWidth().border(1.dp, AuthBorder, RoundedCornerShape(12.dp))
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = AuthGreen)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AuthMuted)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AuthMuted)
        Text(value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun AuthFeedback(error: String?, message: String? = null) {
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    message?.let { Text(it, color = AuthGreen, style = MaterialTheme.typography.bodyMedium) }
}
