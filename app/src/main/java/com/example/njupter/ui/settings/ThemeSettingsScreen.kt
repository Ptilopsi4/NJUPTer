package com.example.njupter.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.njupter.R
import com.example.njupter.ui.animation.predictiveback.PredictiveBackAnimation
import com.example.njupter.ui.animation.predictiveback.PredictiveBackExitDirection
import com.example.njupter.ui.settings.component.SettingsSectionCard
import com.example.njupter.ui.settings.model.SettingsIcon
import com.example.njupter.ui.settings.model.SettingsItem
import com.example.njupter.ui.settings.model.SettingsSection
import com.example.njupter.ui.theme.AppThemeMode
import com.example.njupter.ui.theme.CourseColorTone

private enum class ThemeChoiceDialog {
    THEME_MODE,
    COLOR_TONE,
    PREDICTIVE_ANIMATION,
    EXIT_DIRECTION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    themeMode: AppThemeMode,
    dynamicColorEnabled: Boolean,
    courseColorTone: CourseColorTone,
    predictiveBackAnimation: PredictiveBackAnimation,
    predictiveBackExitDirection: PredictiveBackExitDirection,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onCourseColorToneChange: (CourseColorTone) -> Unit,
    onPredictiveBackAnimationChange: (PredictiveBackAnimation) -> Unit,
    onPredictiveBackExitDirectionChange: (PredictiveBackExitDirection) -> Unit,
    onBack: () -> Unit
) {
    var choiceDialog by remember { mutableStateOf<ThemeChoiceDialog?>(null) }

    val appearanceItems = listOf(
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.DarkMode),
            title = stringResource(R.string.theme_mode),
            description = stringResource(R.string.theme_mode_summary),
            value = themeMode.label(),
            onClick = { choiceDialog = ThemeChoiceDialog.THEME_MODE }
        ),
        SettingsItem.Toggle(
            icon = SettingsIcon.Vector(Icons.Default.ColorLens),
            title = stringResource(R.string.dynamic_color),
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stringResource(R.string.dynamic_color_summary)
            } else {
                stringResource(R.string.dynamic_color_unavailable)
            },
            checked = dynamicColorEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            onToggle = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    onDynamicColorChange(!dynamicColorEnabled)
                }
            }
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Palette),
            title = stringResource(R.string.card_color_tone),
            description = stringResource(R.string.card_color_tone_summary),
            value = courseColorTone.label(),
            onClick = { choiceDialog = ThemeChoiceDialog.COLOR_TONE }
        )
    )

    val motionItems = buildList {
        add(
            SettingsItem.Navigation(
                icon = SettingsIcon.Vector(Icons.Default.Animation),
                title = stringResource(R.string.predictive_back_animation),
                description = stringResource(R.string.predictive_back_animation_summary),
                value = predictiveBackAnimation.label(),
                onClick = { choiceDialog = ThemeChoiceDialog.PREDICTIVE_ANIMATION }
            )
        )
        if (predictiveBackAnimation != PredictiveBackAnimation.NONE) {
            add(
                SettingsItem.Navigation(
                    icon = SettingsIcon.Vector(Icons.Default.SwapHoriz),
                    title = stringResource(R.string.predictive_back_exit_direction),
                    description = stringResource(R.string.predictive_back_exit_direction_summary),
                    value = predictiveBackExitDirection.label(),
                    onClick = { choiceDialog = ThemeChoiceDialog.EXIT_DIRECTION }
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding(),
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                SettingsSectionCard(
                    section = SettingsSection(
                        title = stringResource(R.string.appearance_settings),
                        items = appearanceItems
                    )
                )
            }
            item {
                SettingsSectionCard(
                    section = SettingsSection(
                        title = stringResource(R.string.motion_settings),
                        items = motionItems
                    )
                )
            }
        }
    }

    when (choiceDialog) {
        ThemeChoiceDialog.THEME_MODE -> {
            val values = AppThemeMode.entries
            SingleChoiceSettingsDialog(
                title = stringResource(R.string.theme_mode),
                labels = values.map { it.label() },
                selectedIndex = values.indexOf(themeMode),
                onDismiss = { choiceDialog = null },
                onConfirm = { index ->
                    values.getOrNull(index)?.let(onThemeModeChange)
                    choiceDialog = null
                }
            )
        }

        ThemeChoiceDialog.COLOR_TONE -> {
            val values = CourseColorTone.entries
            SingleChoiceSettingsDialog(
                title = stringResource(R.string.card_color_tone),
                labels = values.map { it.label() },
                selectedIndex = values.indexOf(courseColorTone),
                onDismiss = { choiceDialog = null },
                onConfirm = { index ->
                    values.getOrNull(index)?.let(onCourseColorToneChange)
                    choiceDialog = null
                }
            )
        }

        ThemeChoiceDialog.PREDICTIVE_ANIMATION -> {
            val values = PredictiveBackAnimation.entries
            SingleChoiceSettingsDialog(
                title = stringResource(R.string.predictive_back_animation),
                labels = values.map { it.label() },
                selectedIndex = values.indexOf(predictiveBackAnimation),
                onDismiss = { choiceDialog = null },
                onConfirm = { index ->
                    values.getOrNull(index)?.let(onPredictiveBackAnimationChange)
                    choiceDialog = null
                }
            )
        }

        ThemeChoiceDialog.EXIT_DIRECTION -> {
            val values = PredictiveBackExitDirection.entries
            SingleChoiceSettingsDialog(
                title = stringResource(R.string.predictive_back_exit_direction),
                labels = values.map { it.label() },
                selectedIndex = values.indexOf(predictiveBackExitDirection),
                onDismiss = { choiceDialog = null },
                onConfirm = { index ->
                    values.getOrNull(index)?.let(onPredictiveBackExitDirectionChange)
                    choiceDialog = null
                }
            )
        }

        null -> Unit
    }
}

@Composable
private fun SingleChoiceSettingsDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var pendingIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                labels.forEachIndexed { index, label ->
                    val shape = RoundedCornerShape(
                        topStart = if (index == 0) 18.dp else 6.dp,
                        topEnd = if (index == 0) 18.dp else 6.dp,
                        bottomStart = if (index == labels.lastIndex) 18.dp else 6.dp,
                        bottomEnd = if (index == labels.lastIndex) 18.dp else 6.dp
                    )
                    Surface(
                        shape = shape,
                        color = if (index == pendingIndex) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingIndex = index }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = index == pendingIndex,
                                onClick = null
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pendingIndex) }) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun AppThemeMode.label(): String = when (this) {
    AppThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
    AppThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
    AppThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
}

@Composable
private fun CourseColorTone.label(): String = when (this) {
    CourseColorTone.SOFT -> stringResource(R.string.card_color_tone_soft)
    CourseColorTone.STANDARD -> stringResource(R.string.card_color_tone_standard)
    CourseColorTone.VIVID -> stringResource(R.string.card_color_tone_vivid)
}

@Composable
private fun PredictiveBackAnimation.label(): String = when (this) {
    PredictiveBackAnimation.NONE -> stringResource(R.string.predictive_back_animation_none)
    PredictiveBackAnimation.AOSP -> stringResource(R.string.predictive_back_animation_aosp)
    PredictiveBackAnimation.MIUIX -> stringResource(R.string.predictive_back_animation_miuix)
    PredictiveBackAnimation.SCALE -> stringResource(R.string.predictive_back_animation_scale)
    PredictiveBackAnimation.KSU_CLASSIC ->
        stringResource(R.string.predictive_back_animation_ksu_classic)
}

@Composable
private fun PredictiveBackExitDirection.label(): String = when (this) {
    PredictiveBackExitDirection.FOLLOW_GESTURE ->
        stringResource(R.string.predictive_back_exit_direction_follow_gesture)
    PredictiveBackExitDirection.ALWAYS_RIGHT ->
        stringResource(R.string.predictive_back_exit_direction_always_right)
    PredictiveBackExitDirection.ALWAYS_LEFT ->
        stringResource(R.string.predictive_back_exit_direction_always_left)
}
