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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import za.co.devwell.model.ProjectDeadlineParser
import za.co.devwell.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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

/**
 * App-styled dialog used for create/edit forms and destructive confirmations.
 * Matches the card look (rounded 24dp, green border) and uses DevWell buttons,
 * so popups stay consistent with the rest of the UI instead of the Material default.
 */
@Composable
fun DevWellDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    DevWellDialogCard(
        onDismiss = onDismiss,
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        icon = icon,
        footer = {
            DevWellButton(
                text = dismissLabel,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Outline,
                height = 46.dp,
            )
            if (destructive) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    enabled = confirmEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RedText,
                        contentColor = Color.White,
                        disabledContainerColor = RedBorder,
                        disabledContentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        confirmLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                DevWellButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    enabled = confirmEnabled,
                    height = 46.dp,
                )
            }
        },
        body = content,
    )
}

@Composable
internal fun DevWellDialogCard(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    footer: @Composable RowScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, BorderGreen),
            shadowElevation = 10.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (icon != null) IconTile(icon, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (subtitle != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = body)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = footer)
            }
        }
    }
}

@Composable
fun PrivacyNotice(
    text: String = "Your individual check-ins are private. Managers only see anonymised team summaries after at least 5 distinct participants.",
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
    readOnly: Boolean = false,
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
        readOnly = readOnly,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevWellDateField(
    value: String,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select a date",
    enabled: Boolean = true,
    firstDate: LocalDate = LocalDate.now().minusYears(1),
    lastDate: LocalDate = LocalDate.now().plusYears(5),
) {
    var showPicker by remember { mutableStateOf(false) }
    val parsed = remember(value) {
        runCatching { ProjectDeadlineParser.parse(value) }.getOrNull()
    }

    Box(modifier) {
        DevWellTextField(
            value = value,
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            enabled = enabled,
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Pick date", tint = DevGreen)
            },
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled) { showPicker = true },
        )
    }

    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = parsed?.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis in firstDate.toUtcMillis()..lastDate.toUtcMillis()
            },
        )
        DevWellDialogCard(
            onDismiss = { showPicker = false },
            title = "Select date",
            icon = Icons.Outlined.CalendarMonth,
            footer = {
                DevWellButton(
                    text = "Cancel",
                    onClick = { showPicker = false },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Outline,
                    height = 46.dp,
                )
                DevWellButton(
                    text = "Select",
                    onClick = {
                        state.selectedDateMillis?.let { onDateSelected(it.toIsoDate()) }
                        showPicker = false
                    },
                    modifier = Modifier.weight(1f),
                    height = 46.dp,
                )
            },
        ) {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = Color.Transparent,
                    weekdayContentColor = InkMuted,
                    subheadContentColor = InkMuted,
                    navigationContentColor = DevGreen,
                    yearContentColor = Ink,
                    currentYearContentColor = DevGreen,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = DevGreen,
                    dayContentColor = Ink,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = DevGreen,
                    todayContentColor = DevGreen,
                    todayDateBorderColor = DevGreen,
                ),
            )
        }
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toIsoDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate().toString()

internal fun String.initials(): String =
    trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }

internal fun greetingByHour(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
