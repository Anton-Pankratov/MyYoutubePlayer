package kg.dev.shared.core.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceSettingsDialog(
    preferences: AppearancePreferences,
    onDismissRequest: () -> Unit,
) {
    val appearance by preferences.appearance.collectAsState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 480.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                Text("Appearance", style = MediaTheme.typography.screenTitle)
                Text(
                    "Make Luma feel like yours.",
                    style = MediaTheme.typography.secondaryBody,
                    color = MediaTheme.colors.textSecondary,
                )
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MediaSpacing.xl),
            ) {
                AppearanceSection("THEME", "Choose how the interface follows your device.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                        AppThemeMode.entries.forEach { mode ->
                            val label = when (mode) {
                                AppThemeMode.System -> "System"
                                AppThemeMode.Light -> "Light"
                                AppThemeMode.Dark -> "Dark"
                            }
                            SelectableTile(
                                label = label,
                                selected = appearance.themeMode == mode,
                                modifier = Modifier.weight(1f),
                                onClick = { preferences.setThemeMode(mode) },
                            )
                        }
                    }
                }
                AppearanceSection("ACCENT COLOR", "A little color where it matters.") {
                    AppColorPalette.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                            row.forEach { palette ->
                                SelectableTile(
                                    label = palette.label,
                                    selected = appearance.palette == palette,
                                    modifier = Modifier.weight(1f),
                                    swatch = palettePreviewColor(palette),
                                    onClick = { preferences.setPalette(palette) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismissRequest) { Text("Done") } },
        containerColor = MediaTheme.colors.surface,
        titleContentColor = MediaTheme.colors.textPrimary,
        textContentColor = MediaTheme.colors.textSecondary,
        shape = MediaShapes.dialog,
    )
}

@Composable
private fun AppearanceSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
        Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.xxs)) {
            Text(title, style = MediaTheme.typography.label, color = MediaTheme.colors.primary)
            Text(description, style = MediaTheme.typography.secondaryBody, color = MediaTheme.colors.textSecondary)
        }
        content()
    }
}

@Composable
private fun SelectableTile(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    swatch: Color? = null,
    onClick: () -> Unit,
) {
    val shape = MediaShapes.medium
    Row(
        modifier
            .semantics { this.selected = selected }
            .background(if (selected) MediaTheme.colors.surfaceSelected else MediaTheme.colors.surfaceElevated, shape)
            .border(1.dp, if (selected) MediaTheme.colors.primary else MediaTheme.colors.divider, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MediaSpacing.sm, vertical = MediaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.xs),
    ) {
        if (swatch != null) Box(Modifier.size(18.dp).background(swatch, CircleShape))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MediaTheme.typography.button,
            color = MediaTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected && swatch != null) {
            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), tint = MediaTheme.colors.primary)
        }
    }
}
