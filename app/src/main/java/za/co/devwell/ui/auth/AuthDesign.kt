package za.co.devwell.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
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
import za.co.devwell.ui.theme.*

internal val AuthGreen = DevGreen
internal val AuthBackground = PageBackground
internal val AuthMuted = InkMuted
internal val AuthBorder = BorderGreen
internal val AuthShape = RoundedCornerShape(12.dp)

@Composable
internal fun Brand(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.devwell_logo), "DevWell", modifier = modifier.width(150.dp).height(62.dp))
}

@Composable
internal fun PrimaryAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick, modifier.heightIn(min = 52.dp), enabled = enabled, shape = AuthShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthGreen,
            contentColor = Color.White,
            disabledContainerColor = PaleGreen,
            disabledContentColor = Color.White,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PrivacyNote(text: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(SurfaceMuted, RoundedCornerShape(12.dp))
            .border(1.dp, AuthBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = AuthGreen)
        Text(text, style = MaterialTheme.typography.bodySmall, color = AuthMuted)
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AuthMuted)
        Text(
            value, Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
fun AuthFeedback(error: String?, message: String? = null) {
    error?.let {
        Row(
            Modifier.fillMaxWidth()
                .background(RedSurface, RoundedCornerShape(12.dp))
                .border(1.dp, RedBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = RedText)
            Text(it, color = RedText, style = MaterialTheme.typography.bodyMedium)
        }
    }
    message?.let {
        Row(
            Modifier.fillMaxWidth()
                .background(AccentSurface, RoundedCornerShape(12.dp))
                .border(1.dp, TrackGreen, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(18.dp), tint = AuthGreen)
            Text(it, color = AuthGreen, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
