package kg.dev.shared.core.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun AppearanceSettingsDialog(
    preferences: AppearancePreferences,
    onDismissRequest: () -> Unit,
) {
    val appearance by preferences.appearance.collectAsState()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Appearance", style = MediaTheme.typography.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.lg)) {
                AppearanceSection("Theme") {
                    AppThemeMode.entries.forEach { mode ->
                        SelectionRow(
                            label = when (mode) {
                                AppThemeMode.System -> "System default"
                                AppThemeMode.Light -> "Light"
                                AppThemeMode.Dark -> "Dark"
                            },
                            selected = appearance.themeMode == mode,
                            onClick = { preferences.setThemeMode(mode) },
                        )
                    }
                }
                AppearanceSection("Color palette") {
                    AppColorPalette.entries.forEach { palette ->
                        PaletteRow(
                            palette = palette,
                            selected = appearance.palette == palette,
                            onClick = { preferences.setPalette(palette) },
                        )
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
private fun AppearanceSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
        Text(title, style = MediaTheme.typography.label, color = MediaTheme.colors.textTertiary)
        content()
    }
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$label${if (selected) ", selected" else ""}" }
            .padding(vertical = MediaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MediaTheme.typography.secondaryBody, color = MediaTheme.colors.textPrimary)
    }
}

@Composable
private fun PaletteRow(palette: AppColorPalette, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${palette.label} palette${if (selected) ", selected" else ""}" }
            .padding(vertical = MediaSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm),
    ) {
        Box(Modifier.size(MediaSpacing.lg).background(palettePreviewColor(palette), CircleShape))
        Text(palette.label, Modifier.weight(1f), style = MediaTheme.typography.secondaryBody, color = MediaTheme.colors.textPrimary)
        RadioButton(selected = selected, onClick = null)
    }
}
