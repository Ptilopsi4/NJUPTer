package com.example.njupter.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

@Composable
fun AppBottomBar(
    currentTab: Int,
    settingsMainSelected: Boolean,
    floating: Boolean,
    blurEnabled: Boolean,
    hazeState: HazeState,
    onTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = if (floating) RoundedCornerShape(28.dp) else RectangleShape
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val barModifier = modifier
        .fillMaxWidth()
        .then(
            if (floating) {
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(
            if (blurEnabled) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = 24.dp
                    tints = listOf(HazeTint(surfaceColor.copy(alpha = 0.72f)))
                    noiseFactor = 0.04f
                }
            } else {
                Modifier
            }
        )

    Surface(
        modifier = barModifier,
        shape = shape,
        color = if (blurEnabled) Color.Transparent else surfaceColor,
        tonalElevation = if (floating) 3.dp else 0.dp,
        shadowElevation = if (floating) 10.dp else 0.dp,
        border = if (floating) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        } else {
            null
        }
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = if (floating) {
                WindowInsets(0, 0, 0, 0)
            } else {
                NavigationBarDefaults.windowInsets
            }
        ) {
            NavigationBarItem(
                selected = currentTab == 0,
                onClick = onTimetableClick,
                icon = {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = stringResource(R.string.cd_timetable)
                    )
                }
            )
            NavigationBarItem(
                selected = currentTab == 1 && settingsMainSelected,
                onClick = onSettingsClick,
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.cd_settings)
                    )
                }
            )
        }
    }
}
