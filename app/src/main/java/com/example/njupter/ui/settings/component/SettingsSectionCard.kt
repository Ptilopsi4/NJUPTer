package com.example.njupter.ui.settings.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.njupter.ui.settings.model.SettingsIcon
import com.example.njupter.ui.settings.model.SettingsItem
import com.example.njupter.ui.settings.model.SettingsSection

/**
 * MD3 segmented settings group inspired by ReSukiSU's settings surface. Items remain independent
 * surfaces: the small gaps and asymmetric radii communicate grouping without one oversized card.
 */
@Composable
fun SettingsSectionCard(
    section: SettingsSection,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SettingsSectionHeader(
            title = section.title,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp)
        )

        section.items.forEachIndexed { index, item ->
            val first = index == 0
            val last = index == section.items.lastIndex
            SettingsSegment(
                item = item,
                topRadius = if (first) 20 else 6,
                bottomRadius = if (last) 20 else 6
            )
        }
    }
}

@Composable
private fun SettingsSegment(
    item: SettingsItem,
    topRadius: Int,
    bottomRadius: Int
) {
    val emphasized = (item as? SettingsItem.Navigation)?.emphasized == true
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shapeSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.72f,
        stiffness = 700f
    )
    val animatedTop by animateDpAsState(
        targetValue = (if (pressed) 20 else topRadius).dp,
        animationSpec = shapeSpec,
        label = "settingsTopCorner"
    )
    val animatedBottom by animateDpAsState(
        targetValue = (if (pressed) 20 else bottomRadius).dp,
        animationSpec = shapeSpec,
        label = "settingsBottomCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "settingsPressScale"
    )
    val shape: Shape = RoundedCornerShape(
        topStart = animatedTop,
        topEnd = animatedTop,
        bottomStart = animatedBottom,
        bottomEnd = animatedBottom
    )

    val clickAction = when (item) {
        is SettingsItem.Navigation -> item.onClick
        is SettingsItem.Toggle -> item.onToggle
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        color = if (emphasized) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    onClick = clickAction
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsItemIcon(item.icon, emphasized)
            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                item.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (item) {
                is SettingsItem.Navigation -> {
                    item.value?.takeIf { it.isNotBlank() }?.let { value ->
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(20.dp)
                    )
                }

                is SettingsItem.Toggle -> {
                    Switch(
                        checked = item.checked,
                        onCheckedChange = null,
                        interactionSource = interactionSource,
                        thumbContent = null,
                        colors = SwitchDefaults.colors()
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsItemIcon(icon: SettingsIcon, emphasized: Boolean) {
    val tint = if (emphasized) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    when (icon) {
        is SettingsIcon.Vector -> Icon(
            imageVector = icon.imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        is SettingsIcon.Drawable -> Icon(
            painter = painterResource(id = icon.resId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
