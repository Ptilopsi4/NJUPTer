/*
    控制层：决定有哪些条目
 */
package com.example.njupter.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.njupter.R
import com.example.njupter.ui.settings.component.SettingsSectionCard
import com.example.njupter.ui.settings.model.SettingsItem
import com.example.njupter.ui.settings.model.SettingsSection
import android.widget.Toast
import com.example.njupter.ui.theme.NJUPTerTheme
import com.example.njupter.ui.theme.AppThemeMode
import com.example.njupter.ui.settings.model.SettingsIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTimetableId: String?,
    currentTimetableName: String,
    currentLanguageTag: String,
    currentThemeMode: AppThemeMode,
    enableCurrentTimeIndicator: Boolean,
    onThemeSettingsClick: () -> Unit,
    onLanguageSelectClick: () -> Unit,
    onTimetableSettingsClick: () -> Unit,
    onWidgetSettingsClick: () -> Unit,
    onToggleCurrentTimeIndicator: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationEnabled by remember { 
        mutableStateOf(
            runCatching { isNotificationPermissionGranted(context) }.getOrDefault(true)
        ) 
    }
    var batteryWhitelistEnabled by remember { 
        mutableStateOf(
            runCatching { isIgnoringBatteryOptimizations(context) }.getOrDefault(false)
        ) 
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationEnabled = runCatching { isNotificationPermissionGranted(context) }.getOrDefault(true)
                batteryWhitelistEnabled = runCatching { isIgnoringBatteryOptimizations(context) }.getOrDefault(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentLanguageLabel = if (currentLanguageTag.startsWith("zh")) {
        stringResource(R.string.language_zh)
    } else {
        stringResource(R.string.language_en)
    }

    val timetableSectionItems = if (currentTimetableId == null) {
        emptyList()
    } else {
        listOf(
            SettingsItem.Navigation(
                icon = SettingsIcon.Vector(Icons.Default.CalendarMonth),
                title = stringResource(R.string.cur_timetable_settings),
                value = currentTimetableName,
                onClick = onTimetableSettingsClick
            )
        )
    }

    val appSectionItems = listOf(
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Palette),
            title = stringResource(R.string.theme_settings),
            description = stringResource(R.string.theme_settings_summary),
            value = when (currentThemeMode) {
                AppThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                AppThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                AppThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
            },
            onClick = onThemeSettingsClick
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Widgets),
            title = stringResource(R.string.widget_settings),
            description = stringResource(R.string.widget_settings_summary),
            onClick = onWidgetSettingsClick
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Language),
            title = stringResource(R.string.language),
            description = stringResource(R.string.language_settings_summary),
            value = currentLanguageLabel,
            onClick = onLanguageSelectClick
        ),
        SettingsItem.Toggle(
            icon = SettingsIcon.Vector(Icons.Default.AccessTime),
            title = stringResource(R.string.current_time_indicator),
            description = stringResource(R.string.current_time_indicator_summary),
            checked = enableCurrentTimeIndicator,
            onToggle = { onToggleCurrentTimeIndicator(!enableCurrentTimeIndicator) }
        ),
        SettingsItem.Toggle(
            icon = SettingsIcon.Vector(Icons.Default.Notifications),
            title = stringResource(R.string.notification_permission),
            description = stringResource(R.string.notification_permission_summary),
            checked = notificationEnabled,
            onToggle = {
                val ok = openNotificationSettings(context)
                if (!ok) {
                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show()
                }
            }
        ),
        SettingsItem.Toggle(
            icon = SettingsIcon.Vector(Icons.Default.BatterySaver),
            title = stringResource(R.string.battery_optimization),
            description = stringResource(R.string.battery_optimization_summary),
            checked = batteryWhitelistEnabled,
            onToggle = {
                val ok = openBatteryOptimizationSettings(context)
                if (!ok) {
                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show()
                }
            }
        )
    )

    val settingsSections = listOf(
        SettingsSection(
            title = stringResource(R.string.current_timetable_settings),
            items = timetableSectionItems
        ),
        SettingsSection(
            title = stringResource(R.string.app_settings),
            items = appSectionItems     // 条目定义
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (currentTimetableId == null) {
                 item {
                     Text(
                         text = stringResource(R.string.no_timetable_desc),
                         modifier = Modifier.padding(horizontal = 8.dp),
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.error
                     )
                 }
            }

            items(settingsSections.size) { index ->
                val section = settingsSections[index]
                if (section.items.isNotEmpty()) {
                    SettingsSectionCard(section = section)
                }
            }
        }
    }
}

private fun isNotificationPermissionGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    return try {
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } catch (e: Exception) {
        false
    }
}

private fun openNotificationSettings(context: android.content.Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    return startActivitySafely(context, intent, fallbackIntent)
}

private fun openBatteryOptimizationSettings(context: android.content.Context): Boolean {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val finalFallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }

    if (startActivitySafely(context, requestIntent, fallbackIntent)) return true
    return startActivitySafely(context, finalFallbackIntent)
}

private fun startActivitySafely(
    context: android.content.Context,
    primaryIntent: Intent,
    secondaryIntent: Intent? = null
): Boolean {
    return try {
        val launchIntent = when {
            primaryIntent.resolveActivity(context.packageManager) != null -> primaryIntent
            secondaryIntent != null && secondaryIntent.resolveActivity(context.packageManager) != null -> secondaryIntent
            else -> return false
        }
        context.startActivity(launchIntent)
        true
    } catch (_: Exception) {
        false
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    NJUPTerTheme {
        SettingsScreen(
            currentTimetableId = "1",
            currentTimetableName = "2026 春季学期",
            currentLanguageTag = "zh",
            currentThemeMode = AppThemeMode.SYSTEM,
            enableCurrentTimeIndicator = true,
            onThemeSettingsClick = {},
            onLanguageSelectClick = {},
            onTimetableSettingsClick = {},
            onWidgetSettingsClick = {},
            onToggleCurrentTimeIndicator = {},
            onBack = {}
        )
    }
}
