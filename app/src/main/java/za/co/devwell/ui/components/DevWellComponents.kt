package za.co.devwell.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import za.co.devwell.ui.theme.*

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium, color = Ink)
    if (subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun BackHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceMuted)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, "Back",
                Modifier.size(18.dp), tint = DevGreen,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun DevWellCard(
    modifier: Modifier = Modifier,
    padding: Dp = 20.dp,
    containerColor: Color = CardWhite,
    borderColor: Color = BorderGreen,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun PrivacyNotice(
    text: String = "Your individual check-ins are private. Managers only see anonymised team summaries after at least 5 responses.",
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SurfaceMuted, RoundedCornerShape(12.dp))
            .border(1.dp, BorderGreen, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp), tint = DevGreen)
        Text(text, style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}

enum class ButtonVariant { Primary, Outline, Ghost }

@Composable
fun DevWellButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Primary,
    height: Dp = 50.dp,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick, modifier.heightIn(min = height), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = DevGreen,
                contentColor = Color.White,
                disabledContainerColor = PaleGreen,
                disabledContentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        ButtonVariant.Outline -> OutlinedButton(
            onClick, modifier.heightIn(min = height), enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, DevGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevGreen),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        ButtonVariant.Ghost -> TextButton(
            onClick, modifier.heightIn(min = height), enabled = enabled, shape = shape,
            colors = ButtonDefaults.textButtonColors(contentColor = DevGreen),
        ) {
            if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class ChipSkin { Neutral, Green, Amber, Red }

@Composable
fun Chip(text: String, skin: ChipSkin = ChipSkin.Green, modifier: Modifier = Modifier) {
    val (bg, fg, border) = when (skin) {
        ChipSkin.Neutral -> Triple(SurfaceMuted, InkMuted, BorderGreen)
        ChipSkin.Green -> Triple(AccentSurface, DevGreen, TrackGreen)
        ChipSkin.Amber -> Triple(AmberSurface, AmberText, AmberBorder)
        ChipSkin.Red -> Triple(RedSurface, RedText, RedBorder)
    }
    Box(
        modifier
            .background(bg, CircleShape)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = fg,
        )
    }
}

@Composable
fun SegmentedProgress(
    total: Int,
    current: Int,
    modifier: Modifier = Modifier,
    filledColor: Color = DevGreen,
    trackColor: Color = TrackGreen,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .background(
                        if (i < current) filledColor else trackColor,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
fun Avatar(
    initials: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    background: Color = DevGreen,
    contentColor: Color = Color.White,
) {
    Box(
        modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = contentColor,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = InkMuted,
    )
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = DevGreen,
    background: Color = AccentSurface,
    border: Color = TrackGreen,
) {
    Box(
        modifier.size(size).background(background, RoundedCornerShape(8.dp))
            .border(1.dp, border, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size((size.value * 0.5f).dp), tint = tint)
    }
}

@Composable
fun PrefRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
            }
        }
        if (trailing != null) trailing()
        else Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = DevGreen,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = PaleGreen,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun DevWellDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = SurfaceMuted, thickness = 1.dp)
}

@Composable
fun DevWellTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    suffix: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = InkMuted) } },
        suffix = suffix,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DevGreen,
            unfocusedBorderColor = BorderGreen,
            focusedLabelColor = DevGreen,
            unfocusedLabelColor = InkMuted,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            cursorColor = DevGreen,
            focusedContainerColor = InputSurface,
            unfocusedContainerColor = InputSurface,
            disabledContainerColor = InputSurface,
            focusedPlaceholderColor = InkMuted,
            unfocusedPlaceholderColor = InkMuted,
        ),
    )
}

internal fun String.initials(): String =
    trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

internal fun greetingByHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
