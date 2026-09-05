package com.example.njupter

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.app.Activity
import android.util.Log
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.pm.PackageManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.example.njupter.data.FileTimetableRepository
import com.example.njupter.ui.timetable.TimetableScreen
import com.example.njupter.viewmodels.TimetableViewModel
import com.example.njupter.data.LocalFileDataSource
import com.example.njupter.data.SharedPreferencesSettingsRepository
import com.example.njupter.ui.settings.LanguageSelectScreen
import com.example.njupter.ui.settings.SettingsScreen
import com.example.njupter.ui.settings.TimetableSettingsScreen
import com.example.njupter.ui.settings.ThemeSettingsScreen
import com.example.njupter.ui.settings.WidgetSettingsScreen
import com.example.njupter.ui.theme.NJUPTerTheme
import com.example.njupter.ui.settings.JwxtImportScreen
import com.example.njupter.ui.settings.dialog.ImportPreviewDialog
import com.example.njupter.ui.animation.AppNavigationTransition
import com.example.njupter.ui.animation.AppPageTransition
import com.example.njupter.ui.animation.PredictiveBackSurface
import com.example.njupter.ui.animation.PredictiveBackOwner
import com.example.njupter.data.defaultSessionTimes
import com.example.njupter.notification.CourseReminderScheduler
import com.example.njupter.notification.ReminderBootstrapper
import com.example.njupter.widget.WidgetDataManager
import com.example.njupter.widget.WidgetUpdateScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import java.util.Locale

/**
 * 初始化依赖关系，连接ViewModel与UI，设置应用主题
 */

class MainActivity : ComponentActivity() {

    private companion object {
        const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    }
    private fun applyLocaleToActivityResources(languageTag: String) {
        val locale = when {
            languageTag.startsWith("zh") -> Locale.SIMPLIFIED_CHINESE
            languageTag.startsWith("en") -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplash = true
        splashScreen.setKeepOnScreenCondition { keepSplash }
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.view.animate()
                .alpha(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { splashScreenViewProvider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()  // 全面屏适配
        requestPeakDisplayMode()  // 每 App 帧率策略下，显式要求窗口用最高刷新率模式

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
        }

        val dataSource = LocalFileDataSource(this)
        val settingsRepository = SharedPreferencesSettingsRepository(this)
        val repository = FileTimetableRepository(dataSource, settingsRepository)    // 实例化TimetableRepository，传入MainActivity的Context来读取assets下的JSON
        val reminderScheduler = CourseReminderScheduler(this, settingsRepository)

        lifecycleScope.launch {
            ReminderBootstrapper.rescheduleCurrentTimetable(applicationContext)
        }

        WidgetUpdateScheduler.scheduleMidnightRefresh(this)

        val viewModel by viewModels<TimetableViewModel> {
            TimetableViewModel.provideFactory(repository, settingsRepository, this@MainActivity)
        }
        var lastWidgetTimetableId: String? = null
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState ->
                keepSplash = uiState.isLoading
                // 只在课表 id 变化（首载/切换/新建/删除）时刷 widget；
                // 课程编辑后的刷新由 viewModel.onWidgetRefresh 负责，
                // 切周次、撤销等无关变更不再触发读盘+Glance 重组
                if (!uiState.isLoading && uiState.currentTimetableId != null &&
                    uiState.currentTimetableId != lastWidgetTimetableId
                ) {
                    lastWidgetTimetableId = uiState.currentTimetableId
                    WidgetDataManager.refreshWidget(this@MainActivity)
                }
            }
        }

        setContent {
            val appThemeMode by settingsRepository.getAppThemeMode().collectAsState(
                initial = settingsRepository.peekAppThemeMode()
            )
            val dynamicColorEnabled by settingsRepository.getDynamicColorEnabled().collectAsState(
                initial = settingsRepository.peekDynamicColorEnabled()
            )
            val predictiveBackAnimation by settingsRepository.getPredictiveBackAnimation()
                .collectAsState(initial = settingsRepository.peekPredictiveBackAnimation())
            val predictiveBackExitDirection by settingsRepository.getPredictiveBackExitDirection()
                .collectAsState(initial = settingsRepository.peekPredictiveBackExitDirection())
            val courseColorTone by settingsRepository.getCourseColorTone().collectAsState(
                initial = settingsRepository.peekCourseColorTone()
            )
            val reminderLeadMinutes by settingsRepository.getReminderLeadMinutes().collectAsState(
                initial = settingsRepository.peekReminderLeadMinutes()
            )
            val hideFromRecents by settingsRepository.getHideFromRecents().collectAsState(
                initial = settingsRepository.peekHideFromRecents()
            )

            // excludeFromRecents 是 task 级属性，开关变化时立即应用到当前 task
            LaunchedEffect(hideFromRecents) {
                applyHideFromRecents(hideFromRecents)
            }

            NJUPTerTheme(
                themeMode = appThemeMode,
                dynamicColor = dynamicColorEnabled
            ) {
                val uiState by viewModel.uiState.collectAsState()   // 观察状态，将StateFlow转换成Compose的State
                val importState by viewModel.importState.collectAsState()   // 同上
                val appLanguageTag by settingsRepository.getAppLanguageTag().collectAsState(initial = settingsRepository.peekAppLanguageTag())
                val enableCurrentTimeIndicator by settingsRepository.getEnableCurrentTimeIndicator().collectAsState(initial = true)
                val scope = rememberCoroutineScope()
                val baseContext = LocalContext.current
                var currentTab by remember { mutableStateOf(0) }
                var showJwxtImport by remember { mutableStateOf(false) }
                var settingsSubPage by remember { mutableStateOf("main") }

                // Only keyed on languageTag — other config changes (dark mode, font scale)
                // don't affect string resolution from the context, so we avoid unnecessary
                // createConfigurationContext calls.
                val configuration = LocalConfiguration.current
                val localizedContext = remember(baseContext, appLanguageTag, configuration) {
                    val locale = when {
                        appLanguageTag.startsWith("zh") -> Locale.SIMPLIFIED_CHINESE
                        appLanguageTag.startsWith("en") -> Locale.ENGLISH
                        else -> null
                    }
                    if (locale == null) {
                        baseContext
                    } else {
                        val config = Configuration(configuration)
                        config.setLocale(locale)
                        baseContext.createConfigurationContext(config)
                    }
                }

                LaunchedEffect(appLanguageTag) {
                    applyLocaleToActivityResources(appLanguageTag)
                }

                CompositionLocalProvider(LocalContext provides localizedContext) {
                    // 导入预览对话框
                    importState.result?.let { result ->
                        ImportPreviewDialog(
                            importResult = result,
                            semesterRange = importState.semesterRange,
                            onConfirm = { name, startDate, totalWeeks ->
                                viewModel.createAndImportTimetable(
                                    name = name,
                                    startDate = startDate,
                                    totalWeeks = totalWeeks,
                                    showWeekends = true,
                                    sessionTimes = defaultSessionTimes,
                                    newCourses = result.newCourses,
                                    newSessions = result.newSessions
                                )
                                viewModel.clearImportState()
                                showJwxtImport = false
                            },
                            onDismiss = {
                                viewModel.clearImportState()
                            }
                        )
                    }

                    importState.error?.let { error ->
                        AlertDialog(
                            onDismissRequest = viewModel::clearImportState,
                            title = { Text(stringResource(R.string.import_failed)) },
                            text = { Text(error) },
                            confirmButton = {
                                TextButton(onClick = viewModel::clearImportState) {
                                    Text(stringResource(R.string.confirm))
                                }
                            }
                        )
                    }

                // Reschedule reminders whenever timetable data settles.
                // snapshotFlow + debounce 合并连续变更（如导入 N 门课只重排一次），
                // 同时保证课程开关/名称/教室等编辑后立即同步到已排闹钟。
                // key 必须覆盖排课的全部输入：除课程数据外还包括起止日期/总周数/节次时间，
                // 否则单独修改这些设置不会重排，旧闹钟会按旧时间触发。
                // 无课表时同样要调用：scheduleUpcomingReminders 先 clearAll 再对 null id 早退，
                // 跳过调用会让已删除课表的提醒留在系统里继续触发。
                LaunchedEffect(uiState.isLoading) {
                    if (!uiState.isLoading) {
                        snapshotFlow {
                            listOf(
                                uiState.currentTimetableId,
                                uiState.currentStartDate,
                                uiState.currentTotalWeeks,
                                uiState.currentSessionTimes,
                                uiState.courseInfos,
                                uiState.sessions
                            )
                        }
                            .debounce(500)
                            .collect {
                                reminderScheduler.scheduleUpcomingReminders(
                                    courseInfos = uiState.courseInfos,
                                    sessions = uiState.sessions,
                                    currentTimetableId = uiState.currentTimetableId,
                                    startDate = uiState.currentStartDate,
                                    totalWeeks = uiState.currentTotalWeeks,
                                    sessionTimes = uiState.currentSessionTimes
                                )
                            }
                    }
                }

                    PredictiveBackSurface(
                        enabled = showJwxtImport || settingsSubPage != "main" || currentTab == 1,
                        owner = when {
                            showJwxtImport -> PredictiveBackOwner.IMPORT_PAGE
                            settingsSubPage != "main" || currentTab == 1 -> PredictiveBackOwner.SETTINGS_PAGE
                            else -> null
                        },
                        animation = predictiveBackAnimation,
                        exitDirection = predictiveBackExitDirection,
                        onBack = {
                            when {
                                showJwxtImport -> showJwxtImport = false
                                settingsSubPage != "main" -> settingsSubPage = "main"
                                currentTab == 1 -> {
                                    currentTab = 0
                                    settingsSubPage = "main"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppPageTransition(
                            showImport = showJwxtImport,
                            modifier = Modifier.fillMaxSize(),
                            importContent = {
                            JwxtImportScreen(
                                isActive = showJwxtImport,
                                onBack = { showJwxtImport = false },
                                onSemesterInfoObtained = { range ->
                                    Log.d("JwxtImport", "semester range received: $range")
                                    viewModel.setSemesterRange(range)
                                },
                                onTimetableHtmlObtained = { html ->
                                    viewModel.processTimetableImport(html)
                                }
                            )
                            },
                            mainContent = {
                            Scaffold { innerPadding ->
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .consumeWindowInsets(innerPadding)
                                    ) {
                                    AppNavigationTransition(
                                        currentTab = currentTab,
                                        settingsSubPage = settingsSubPage,
                                        modifier = Modifier.fillMaxSize()
                                    ) { tab, subPage ->
                                        when {
                                            tab == 0 -> {
                                                TimetableScreen(
                                                    courseInfos = uiState.courseInfos,
                                                    courseSessions = uiState.sessions,
                                                    timetables = uiState.timetables,
                                                    currentTimetableName = uiState.currentTimetableName,
                                                    currentTimetableId = uiState.currentTimetableId,
                                                    currentStartDate = uiState.currentStartDate,
                                                    currentTotalWeeks = uiState.currentTotalWeeks,
                                                    currentWeek = uiState.currentWeek,
                                                    sessionTimes = uiState.currentSessionTimes,
                                                    showWeekends = uiState.showWeekends,
                                                    enableCurrentTimeIndicator = enableCurrentTimeIndicator,
                                                    isLoading = uiState.isLoading,
                                                    courseColorTone = courseColorTone,
                                                    onAddCourse = viewModel::addCourse,
                                                    onAddSession = viewModel::addSession,
                                                    onUpdateCourse = viewModel::updateCourse,
                                                    onUpdateSession = viewModel::updateSession,
                                                    onDeleteSession = viewModel::deleteSession,
                                                    onSwitchTimetable = viewModel::switchTimetable,
                                                    onDeleteTimetable = viewModel::deleteTimetable,
                                                    canUndo = uiState.canUndo,
                                                    onUndo = viewModel::undoLastChange,
                                                    onSettingsClick = {
                                                        currentTab = 1
                                                        settingsSubPage = "main"
                                                    },
                                                    onCurrentWeekChange = viewModel::setCurrentWeek,
                                                    onCreateTimetable = viewModel::createTimetable,
                                                    onImportClick = { showJwxtImport = true }
                                                )
                                            }
                                            subPage == "theme" -> {
                                                ThemeSettingsScreen(
                                                    themeMode = appThemeMode,
                                                    dynamicColorEnabled = dynamicColorEnabled,
                                                    courseColorTone = courseColorTone,
                                                    predictiveBackAnimation = predictiveBackAnimation,
                                                    predictiveBackExitDirection = predictiveBackExitDirection,
                                                    onThemeModeChange = { mode ->
                                                        scope.launch {
                                                            settingsRepository.setAppThemeMode(mode)
                                                        }
                                                    },
                                                    onDynamicColorChange = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setDynamicColorEnabled(enabled)
                                                        }
                                                    },
                                                    onCourseColorToneChange = { tone ->
                                                        scope.launch {
                                                            settingsRepository.setCourseColorTone(tone)
                                                        }
                                                    },
                                                    onPredictiveBackAnimationChange = { animation ->
                                                        scope.launch {
                                                            settingsRepository.setPredictiveBackAnimation(animation)
                                                        }
                                                    },
                                                    onPredictiveBackExitDirectionChange = { direction ->
                                                        scope.launch {
                                                            settingsRepository.setPredictiveBackExitDirection(direction)
                                                        }
                                                    },
                                                    onBack = { settingsSubPage = "main" }
                                                )
                                            }
                                            subPage == "language" -> {
                                                LanguageSelectScreen(
                                                    currentLanguageTag = appLanguageTag,
                                                    onBack = { settingsSubPage = "main" },  // {settingsSubPage = "main"}这个东西叫做无参lambda，表示被调用时要执行的语句
                                                    onSelectLanguage = { languageTag ->
                                                        scope.launch {
                                                            settingsRepository.setAppLanguageTag(languageTag)
                                                            // lambda 的写法是： { 参数列表 -> 函数体 }
                                                            // -> 左边把参数接住，右边是lambda被调用时要执行的代码
                                                        }
                                                    }
                                                )
                                            }
                                            subPage == "timetable" -> {
                                                TimetableSettingsScreen(
                                                    currentTimetableName = uiState.currentTimetableName,
                                                    currentStartDate = uiState.currentStartDate,
                                                    currentTotalWeeks = uiState.currentTotalWeeks,
                                                    currentShowWeekends = uiState.showWeekends,
                                                    currentSessionTimes = uiState.currentSessionTimes,
                                                    onBack = { settingsSubPage = "main" },
                                                    onSave = { name, startDate, weeks, showWeekends, sessionTimes ->
                                                        uiState.currentTimetableId?.let { timetableId ->
                                                            viewModel.updateTimetableMetadata(
                                                                timetableId,
                                                                name,
                                                                startDate,
                                                                weeks,
                                                                showWeekends,
                                                                sessionTimes
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                            subPage == "widget" -> {
                                                WidgetSettingsScreen(
                                                    onBack = { settingsSubPage = "main" }
                                                )
                                            }
                                            else -> {
                                                SettingsScreen(
                                                    currentTimetableId = uiState.currentTimetableId,
                                                    currentTimetableName = uiState.currentTimetableName,
                                                    currentLanguageTag = appLanguageTag,
                                                    currentThemeMode = appThemeMode,
                                                    enableCurrentTimeIndicator = enableCurrentTimeIndicator,
                                                    hideFromRecents = hideFromRecents,
                                                    onThemeSettingsClick = { settingsSubPage = "theme" },
                                                    onLanguageSelectClick = { settingsSubPage = "language" },
                                                    onTimetableSettingsClick = { settingsSubPage = "timetable" },
                                                    onWidgetSettingsClick = { settingsSubPage = "widget" },
                                                    onToggleCurrentTimeIndicator = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setEnableCurrentTimeIndicator(enabled)
                                                        }
                                                    },
                                                    onToggleHideFromRecents = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setHideFromRecents(enabled)
                                                        }
                                                    },
                                                    onExactAlarmsEnabled = {
                                                        // 精确闹钟刚被授予：把替换已排的非精确提醒为精确闹钟
                                                        scope.launch {
                                                            reminderScheduler.scheduleUpcomingReminders(
                                                                courseInfos = uiState.courseInfos,
                                                                sessions = uiState.sessions,
                                                                currentTimetableId = uiState.currentTimetableId,
                                                                startDate = uiState.currentStartDate,
                                                                totalWeeks = uiState.currentTotalWeeks,
                                                                sessionTimes = uiState.currentSessionTimes
                                                            )
                                                        }
                                                    },
                                                    reminderLeadMinutes = reminderLeadMinutes,
                                                    onReminderLeadMinutesChange = { minutes ->
                                                        // 提前时间已变：保存后重排已排闹钟
                                                        scope.launch {
                                                            settingsRepository.setReminderLeadMinutes(minutes)
                                                            reminderScheduler.scheduleUpcomingReminders(
                                                                courseInfos = uiState.courseInfos,
                                                                sessions = uiState.sessions,
                                                                currentTimetableId = uiState.currentTimetableId,
                                                                startDate = uiState.currentStartDate,
                                                                totalWeeks = uiState.currentTotalWeeks,
                                                                sessionTimes = uiState.currentSessionTimes
                                                            )
                                                        }
                                                    },
                                                    onBack = { currentTab = 0 }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                        )
                    }
                }
            }
        }
    }

    /**
     * API 29+ 才有动态开关（AppTask.setExcludeFromRecents）；更低版本只能靠
     * manifest 静态声明，无法运行时切换，忽略。
     */
    private fun applyHideFromRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        activityManager.appTasks.firstOrNull()?.setExcludeFromRecents(hide)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 授予通知权限后立即重排：scheduler 在无权限时只做了 clearAll 就早退，
        // 不补这一步的话，同会话内授予后要等下次冷启动才真正排上提醒
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                ReminderBootstrapper.rescheduleCurrentTimetable(applicationContext)
            }
        }
    }
}

/**
 * 请求本窗口使用机型支持的最高刷新率。
 * 部分 OEM 会用自身的每 App 帧率策略覆盖 AOSP 的 ARR（View 投票、touch boost），
 * 所以除 preferredRefreshRate 之外还要显式给出显示模式 id。
 */
private fun Activity.requestPeakDisplayMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val peak = display?.supportedModes
        ?.filter { it.refreshRate > 60f }
        ?.maxByOrNull { it.refreshRate }
        ?: return
    window.attributes = window.attributes.apply {
        preferredRefreshRate = peak.refreshRate
        preferredDisplayModeId = peak.modeId
    }
}
