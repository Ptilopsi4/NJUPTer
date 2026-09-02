package com.example.njupter

import android.Manifest
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.example.njupter.ui.component.AppBottomBar
import com.example.njupter.data.defaultSessionTimes
import com.example.njupter.notification.CourseReminderScheduler
import com.example.njupter.notification.ReminderBootstrapper
import com.example.njupter.widget.WidgetDataManager
import com.example.njupter.widget.WidgetUpdateScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * 初始化依赖关系，连接ViewModel与UI，设置应用主题
 */

class MainActivity : ComponentActivity() {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        val dataSource = LocalFileDataSource(this)
        val settingsRepository = SharedPreferencesSettingsRepository(this)
        val repository = FileTimetableRepository(dataSource, settingsRepository)    // 实例化TimetableRepository，传入MainActivity的Context来读取assets下的JSON
        val reminderScheduler = CourseReminderScheduler(this)

        lifecycleScope.launch {
            ReminderBootstrapper.rescheduleCurrentTimetable(applicationContext)
        }

        WidgetUpdateScheduler.scheduleMidnightRefresh(this)

        val viewModel by viewModels<TimetableViewModel> {
            TimetableViewModel.provideFactory(repository, settingsRepository, this@MainActivity)
        }

        lifecycleScope.launch {
            viewModel.uiState.collectLatest { uiState ->
                keepSplash = uiState.isLoading
                if (!uiState.isLoading && uiState.currentTimetableId != null) {
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
            val floatingBottomBarEnabled by settingsRepository.getFloatingBottomBarEnabled()
                .collectAsState(initial = settingsRepository.peekFloatingBottomBarEnabled())
            val bottomBarBlurEnabled by settingsRepository.getBottomBarBlurEnabled()
                .collectAsState(initial = settingsRepository.peekBottomBarBlurEnabled())
            val predictiveBackAnimation by settingsRepository.getPredictiveBackAnimation()
                .collectAsState(initial = settingsRepository.peekPredictiveBackAnimation())
            val predictiveBackExitDirection by settingsRepository.getPredictiveBackExitDirection()
                .collectAsState(initial = settingsRepository.peekPredictiveBackExitDirection())

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
                val layoutDirection = LocalLayoutDirection.current
                val bottomBarHazeState = rememberHazeState()
                val bottomBarOverlaysContent =
                    floatingBottomBarEnabled || bottomBarBlurEnabled
                val navigationBarInset = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                val bottomOverlayPadding = if (bottomBarOverlaysContent) {
                    (if (floatingBottomBarEnabled) 100.dp else 88.dp) + navigationBarInset
                } else {
                    0.dp
                }
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
                            onConfirm = { name ->
                                viewModel.createAndImportTimetable(
                                    name = name,
                                    startDate = System.currentTimeMillis(),
                                    totalWeeks = 20,
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

                // Reschedule reminders when timetable identity changes.
                // courseInfos and sessions are NOT keys — the repository emits them
                // on every mutation, which would reschedule N times per import/add.
                // ReminderScheduler reads current repo state when it fires, so we only
                // need to trigger on structural changes.
                val reminderKey = uiState.isLoading to uiState.currentTimetableId
                LaunchedEffect(reminderKey) {
                    if (!uiState.isLoading && uiState.currentTimetableId != null) {
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

                    PredictiveBackSurface(
                        enabled = showJwxtImport || settingsSubPage != "main",
                        owner = when {
                            showJwxtImport -> PredictiveBackOwner.IMPORT_PAGE
                            settingsSubPage != "main" -> PredictiveBackOwner.SETTINGS_PAGE
                            else -> null
                        },
                        animation = predictiveBackAnimation,
                        exitDirection = predictiveBackExitDirection,
                        onBack = {
                            when {
                                showJwxtImport -> showJwxtImport = false
                                settingsSubPage != "main" -> settingsSubPage = "main"
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
                                onTimetableHtmlObtained = { html ->
                                    viewModel.processTimetableImport(html)
                                }
                            )
                            },
                            mainContent = {
                            Scaffold(
                                bottomBar = {
                                    if (!bottomBarOverlaysContent) {
                                        AppBottomBar(
                                            currentTab = currentTab,
                                            settingsMainSelected = settingsSubPage == "main",
                                            floating = false,
                                            blurEnabled = false,
                                            hazeState = bottomBarHazeState,
                                            onTimetableClick = {
                                                currentTab = 0
                                                settingsSubPage = "main"
                                            },
                                            onSettingsClick = { currentTab = 1 }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                val scenePadding = PaddingValues(
                                    start = innerPadding.calculateStartPadding(layoutDirection),
                                    top = innerPadding.calculateTopPadding(),
                                    end = innerPadding.calculateEndPadding(layoutDirection),
                                    bottom = if (bottomBarOverlaysContent) {
                                        0.dp
                                    } else {
                                        innerPadding.calculateBottomPadding()
                                    }
                                )
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(scenePadding)
                                            .consumeWindowInsets(innerPadding)
                                            .then(
                                                if (bottomBarBlurEnabled) {
                                                    Modifier.hazeSource(bottomBarHazeState)
                                                } else {
                                                    Modifier
                                                }
                                            )
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
                                                    showNonCurrentWeekCourses = uiState.showNonCurrentWeekCourses,
                                                    enableCurrentTimeIndicator = enableCurrentTimeIndicator,
                                                    bottomOverlayPadding = bottomOverlayPadding,
                                                    isLoading = uiState.isLoading,
                                                    onAddCourse = viewModel::addCourse,
                                                    onAddSession = viewModel::addSession,
                                                    onUpdateCourse = viewModel::updateCourse,
                                                    onUpdateSession = viewModel::updateSession,
                                                    onDeleteSession = viewModel::deleteSession,
                                                    onSwitchTimetable = viewModel::switchTimetable,
                                                    onDeleteTimetable = viewModel::deleteTimetable,
                                                    onCurrentWeekChange = viewModel::setCurrentWeek,
                                                    onCreateTimetable = viewModel::createTimetable,
                                                    onImportClick = { showJwxtImport = true }
                                                )
                                            }
                                            subPage == "theme" -> {
                                                ThemeSettingsScreen(
                                                    themeMode = appThemeMode,
                                                    dynamicColorEnabled = dynamicColorEnabled,
                                                    floatingBottomBarEnabled = floatingBottomBarEnabled,
                                                    bottomBarBlurEnabled = bottomBarBlurEnabled,
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
                                                    onFloatingBottomBarChange = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setFloatingBottomBarEnabled(enabled)
                                                        }
                                                    },
                                                    onBottomBarBlurChange = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setBottomBarBlurEnabled(enabled)
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
                                                    onBack = { settingsSubPage = "main" },
                                                    bottomContentPadding = bottomOverlayPadding
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
                                                    },
                                                    bottomContentPadding = bottomOverlayPadding
                                                )
                                            }
                                            subPage == "timetable" -> {
                                                TimetableSettingsScreen(
                                                    currentTimetableName = uiState.currentTimetableName,
                                                    currentStartDate = uiState.currentStartDate,
                                                    currentTotalWeeks = uiState.currentTotalWeeks,
                                                    currentShowWeekends = uiState.showWeekends,
                                                    currentShowNonCurrentWeekCourses = uiState.showNonCurrentWeekCourses,
                                                    currentSessionTimes = uiState.currentSessionTimes,
                                                    onBack = { settingsSubPage = "main" },
                                                    onSave = { name, startDate, weeks, showWeekends, showNonCurrentWeekCourses, sessionTimes ->
                                                        uiState.currentTimetableId?.let { timetableId ->
                                                            viewModel.updateTimetableMetadata(
                                                                timetableId,
                                                                name,
                                                                startDate,
                                                                weeks,
                                                                showWeekends,
                                                                showNonCurrentWeekCourses,
                                                                sessionTimes
                                                            )
                                                        }
                                                    },
                                                    bottomContentPadding = bottomOverlayPadding
                                                )
                                            }
                                            subPage == "widget" -> {
                                                WidgetSettingsScreen(
                                                    onBack = { settingsSubPage = "main" },
                                                    bottomContentPadding = bottomOverlayPadding
                                                )
                                            }
                                            else -> {
                                                SettingsScreen(
                                                    currentTimetableId = uiState.currentTimetableId,
                                                    currentTimetableName = uiState.currentTimetableName,
                                                    currentLanguageTag = appLanguageTag,
                                                    currentThemeMode = appThemeMode,
                                                    enableCurrentTimeIndicator = enableCurrentTimeIndicator,
                                                    onThemeSettingsClick = { settingsSubPage = "theme" },
                                                    onLanguageSelectClick = { settingsSubPage = "language" },
                                                    onTimetableSettingsClick = { settingsSubPage = "timetable" },
                                                    onWidgetSettingsClick = { settingsSubPage = "widget" },
                                                    onToggleCurrentTimeIndicator = { enabled ->
                                                        scope.launch {
                                                            settingsRepository.setEnableCurrentTimeIndicator(enabled)
                                                        }
                                                    },
                                                    bottomContentPadding = bottomOverlayPadding
                                                )
                                            }
                                        }
                                    }
                                }

                                if (bottomBarOverlaysContent) {
                                    AppBottomBar(
                                        currentTab = currentTab,
                                        settingsMainSelected = settingsSubPage == "main",
                                        floating = floatingBottomBarEnabled,
                                        blurEnabled = bottomBarBlurEnabled,
                                        hazeState = bottomBarHazeState,
                                        onTimetableClick = {
                                            currentTab = 0
                                            settingsSubPage = "main"
                                        },
                                        onSettingsClick = { currentTab = 1 },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
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
}
