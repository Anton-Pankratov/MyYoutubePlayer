package kg.dev.shared.core.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun AppSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MediaTheme.colors.background, content = content)
}

@Composable
fun ScreenHeader(
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
            Text(title, style = MediaTheme.typography.screenTitle, color = MediaTheme.colors.textPrimary)
            supportingText?.let {
                Text(it, style = MediaTheme.typography.secondaryBody, color = MediaTheme.colors.textSecondary)
            }
        }
        action?.invoke()
    }
}

@Composable
fun MediaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    enabled: Boolean = true
) {
    val colors = MediaTheme.colors
    var isFocused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = MediaTheme.typography.body.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier
            .fillMaxWidth()
            .clip(MediaShapes.medium)
            .background(colors.surfaceElevated)
            .onFocusChanged { isFocused = it.isFocused }
            .border(1.dp, if (isFocused) colors.primary else colors.divider, MediaShapes.medium)
            .padding(horizontal = MediaSpacing.md, vertical = 14.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
                Icon(Icons.Outlined.Search, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MediaTheme.typography.body, color = colors.textTertiary)
                    inner()
                }
            }
        }
    )
}

@Composable
fun MediaThumbnail(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
    circular: Boolean = false
) {
    val shape = if (circular) CircleShape else MediaShapes.thumbnail
    Box(
        modifier = modifier
            .aspectRatio(if (circular) 1f else aspectRatio)
            .clip(shape)
            .background(MediaTheme.colors.surfaceInteractive),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                Icons.Outlined.BrokenImage,
                contentDescription,
                tint = MediaTheme.colors.textTertiary,
                modifier = Modifier.size(if (circular) 28.dp else 32.dp)
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
fun ProviderBadge(text: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MediaTheme.colors.surfaceInteractive, shape = MediaShapes.small) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = MediaSpacing.xs, vertical = MediaSpacing.xxs),
            style = MediaTheme.typography.label,
            color = MediaTheme.colors.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = StatePanel(title, message, modifier, actionLabel, onAction, false)

@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) = StatePanel(title, message, modifier, if (onRetry == null) null else "Try again", onRetry, true)

@Composable
private fun StatePanel(
    title: String,
    message: String,
    modifier: Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    isError: Boolean
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = MediaSpacing.huge, horizontal = MediaSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)
    ) {
        Text(title, style = MediaTheme.typography.sectionTitle, color = if (isError) MediaTheme.colors.error else MediaTheme.colors.textPrimary)
        Text(
            message,
            style = MediaTheme.typography.secondaryBody,
            color = MediaTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MediaSpacing.xxs))
            Button(
                onClick = onAction,
                shape = MediaShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MediaTheme.colors.primary, contentColor = MediaTheme.colors.onPrimary)
            ) {
                if (isError) Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                Text(actionLabel, style = MediaTheme.typography.button, modifier = Modifier.padding(horizontal = MediaSpacing.xs))
            }
        }
    }
}

@Composable
fun LoadingMediaCard(modifier: Modifier = Modifier, compact: Boolean = false) {
    val base = MediaTheme.colors.surfaceElevated
    if (compact) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MediaSpacing.md)) {
            Box(Modifier.size(132.dp, 78.dp).clip(MediaShapes.thumbnail).background(base))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MediaSpacing.xs)) {
                SkeletonLine(0.9f)
                SkeletonLine(0.65f)
                SkeletonLine(0.4f)
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MediaSpacing.sm)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MediaShapes.thumbnail).background(base))
            SkeletonLine(0.9f)
            SkeletonLine(0.55f)
        }
    }
}

@Composable
private fun SkeletonLine(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction).height(12.dp).clip(MediaShapes.small).background(MediaTheme.colors.surfaceInteractive))
}

@Composable
fun PrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MediaShapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = MediaTheme.colors.primary, contentColor = MediaTheme.colors.onPrimary)
    ) {
        leading?.invoke()
        Text(label, style = MediaTheme.typography.button, modifier = Modifier.padding(horizontal = MediaSpacing.xs))
    }
}

@Composable
fun InteractiveSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .clip(MediaShapes.medium)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .clickable(interactionSource, indication = null, role = Role.Button, onClick = onClick),
        color = Color.Transparent,
        shape = MediaShapes.medium,
        content = content
    )
}

@Composable
fun MetadataText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MediaTheme.typography.metadata,
        color = MediaTheme.colors.textTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun CompactProgress() {
    CircularProgressIndicator(
        modifier = Modifier.size(22.dp),
        color = MediaTheme.colors.primary,
        strokeWidth = 2.dp
    )
}
