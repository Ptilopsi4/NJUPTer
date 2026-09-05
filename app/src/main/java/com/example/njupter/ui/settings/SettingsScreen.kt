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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
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
import com.example.njupter.ui.LocalActivityContext
import com.example.njupter.ui.settings.component.SettingsSectionCard
import com.example.njupter.ui.settings.dialog.ReminderLeadDialog
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
    hideFromRecents: Boolean = false,
    onThemeSettingsClick: () -> Unit,
    onLanguageSelectClick: () -> Unit,
    onTimetableSettingsClick: () -> Unit,
    onWidgetSettingsClick: () -> Unit,
    onToggleCurrentTimeIndicator: (Boolean) -> Unit,
    onToggleHideFromRecents: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onExactAlarmsEnabled: () -> Unit = {},
    reminderLeadMinutes: Int = 10,
    onReminderLeadMinutesChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    // 语言覆写后 LocalContext 可能不是 Activity，跳系统设置必须用真 Activity
    val activityContext = LocalActivityContext.current ?: context
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
    var exactAlarmEnabled by remember {
        mutableStateOf(
            runCatching { canScheduleExactAlarms(context) }.getOrDefault(false)
        )
    }
    var showReminderLeadDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationEnabled = runCatching { isNotificationPermissionGranted(context) }.getOrDefault(true)
                batteryWhitelistEnabled = runCatching { isIgnoringBatteryOptimizations(context) }.getOrDefault(false)
                // 精确闹钟刚被授予时，把已排的降级提醒重排为精确闹钟
                val exactEnabled = runCatching { canScheduleExactAlarms(context) }.getOrDefault(false)
                if (exactEnabled && !exactAlarmEnabled) {
                    onExactAlarmsEnabled()
                }
                exactAlarmEnabled = exactEnabled
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
                emphasized = true,
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
            icon = SettingsIcon.Vector(Icons.Default.VisibilityOff),
            title = stringResource(R.string.hide_from_recents),
            description = stringResource(R.string.hide_from_recents_summary),
            checked = hideFromRecents,
            onToggle = { onToggleHideFromRecents(!hideFromRecents) }
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Notifications),
            title = stringResource(R.string.notification_permission),
            description = stringResource(R.string.notification_permission_summary),
            value = if (notificationEnabled) {
                stringResource(R.string.granted)
            } else {
                stringResource(R.string.not_granted)
            },
            onClick = {
                if (!openNotificationSettings(activityContext)) {
                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show()
                }
            }
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Timer),
            title = stringResource(R.string.reminder_lead_time),
            description = stringResource(R.string.reminder_lead_time_summary),
            value = stringResource(R.string.reminder_lead_min_value, reminderLeadMinutes),
            onClick = { showReminderLeadDialog = true }
        ),
    )

    // 提醒链路：豁免 + 精确闹钟配合，确保息屏/待机时提醒准时
    val reminderReliabilityItems = listOf(
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.BatterySaver),
            title = stringResource(R.string.battery_optimization),
            description = stringResource(R.string.battery_optimization_summary),
            value = if (batteryWhitelistEnabled) {
                stringResource(R.string.granted)
            } else {
                stringResource(R.string.not_granted)
            },
            onClick = {
                if (!openBatteryOptimizationSettings(activityContext)) {
                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show()
                }
            }
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.BatterySaver),
            title = stringResource(R.string.background_high_power),
            description = stringResource(R.string.background_high_power_summary),
            onClick = {
                if (!openBackgroundHighPowerSettings(activityContext)) {
                    Toast.makeText(context, R.string.cannot_open_settings, Toast.LENGTH_SHORT).show()
                }
            }
        ),
        SettingsItem.Navigation(
            icon = SettingsIcon.Vector(Icons.Default.Alarm),
            title = stringResource(R.string.exact_alarm),
            description = stringResource(R.string.exact_alarm_summary),
            value = if (exactAlarmEnabled) {
                stringResource(R.string.granted)
            } else {
                stringResource(R.string.not_granted)
            },
            onClick = {
                if (!openExactAlarmSettings(activityContext)) {
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
        ),
        SettingsSection(
            title = stringResource(R.string.reminder_reliability),
            items = reminderReliabilityItems
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

    if (showReminderLeadDialog) {
        ReminderLeadDialog(
            initialMinutes = reminderLeadMinutes,
            onDismiss = { showReminderLeadDialog = false },
            onConfirm = { minutes ->
                showReminderLeadDialog = false
                onReminderLeadMinutesChange(minutes)
            }
        )
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

private fun canScheduleExactAlarms(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return try {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as? android.app.AlarmManager
        alarmManager?.canScheduleExactAlarms() ?: false
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

private fun openBackgroundHighPowerSettings(context: android.content.Context): Boolean {
    // 后台高耗电是 ROM 专属设置，无公开 API，只能引导用户到应用详情页手动开启
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    return startActivitySafely(context, fallbackIntent)
}

private fun openExactAlarmSettings(context: android.content.Context): Boolean {
    val requestIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        null
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    if (requestIntent == null) {
        return startActivitySafely(context, fallbackIntent)
    }
    return startActivitySafely(context, requestIntent, fallbackIntent)
}

/**
 * 依次尝试候选 intent，任一成功即返回。
 * 不用 resolveActivity 预判：API 30+ 没有 <queries> 声明时对系统页
 * resolveActivity 会返回 null，预判会造成误报失败。
 */
private fun startActivitySafely(
    context: android.content.Context,
    primaryIntent: Intent,
    secondaryIntent: Intent? = null
): Boolean {
    val candidates = listOfNotNull(primaryIntent, secondaryIntent)
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.e(
                "SettingsScreen",
                "startActivity failed action=${intent.action} data=${intent.data}",
                e
            )
            // 尝试下一个候选
        }
    }
    return false
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
