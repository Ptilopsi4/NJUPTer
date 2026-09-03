package com.example.njupter.ui.timetable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.example.njupter.R
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.TimetableMetadata
import com.example.njupter.data.defaultSessionTimes
import com.example.njupter.ui.theme.CourseColorTone
import com.example.njupter.ui.theme.getCourseColors
import com.example.njupter.ui.theme.isAppInDarkTheme
import com.example.njupter.ui.theme.NJUPTerTheme
import com.example.njupter.domain.getDateForWeekDay
import com.example.njupter.domain.getTodayDayOfWeek
import com.example.njupter.domain.getTodayWeekIndex
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.njupter.ui.timetable.component.CourseCard
import com.example.njupter.ui.timetable.component.CourseDetailsBottomSheet
import com.example.njupter.ui.timetable.component.EmptyGuidePlaceholder
import com.example.njupter.ui.timetable.dialog.CourseEditorDialog
import com.example.njupter.ui.timetable.dialog.TimetableConfigDialog
import kotlin.math.roundToInt

private data class NewCoursePlacement(
    val day: Int,
    val section: Int,
    val week: Int
)

private data class CourseDetailsSelection(
    val session: CourseSession,
    val course: CourseInfo
)

private data class CourseDisplayItem(
    val session: CourseSession,
    val course: CourseInfo,
    val isActiveInCurrentWeek: Boolean
)

@OptIn(ExperimentalMaterial3Api::class) // 使用实验性的 Material3 API
@Composable
fun TimetableScreen(
    courseInfos: List<CourseInfo>,
    courseSessions: List<CourseSession>,
    timetables: List<TimetableMetadata> = emptyList(),
    currentTimetableName: String = "",
    currentTimetableId: String? = null,
    currentStartDate: Long = System.currentTimeMillis(),
    currentTotalWeeks: Int = 20,
    currentWeek: Int = 1,
    sessionTimes: List<String> = emptyList(),
    showWeekends: Boolean = true,
    showNonCurrentWeekCourses: Boolean = false,
    enableCurrentTimeIndicator: Boolean = true,
    courseColorTone: CourseColorTone = CourseColorTone.STANDARD,
    isLoading: Boolean = false,
    onAddCourse: (CourseInfo) -> Unit = {},
    onAddSession: (CourseSession) -> Unit = {},
    onUpdateCourse: (CourseInfo) -> Unit = {},
    onUpdateSession: (CourseSession, CourseSession) -> Unit = { _, _ -> },
    onDeleteSession: (CourseSession) -> Unit = {},
    onSwitchTimetable: (String) -> Unit = {},
    onDeleteTimetable: (String) -> Unit = {},
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onCurrentWeekChange: (Int) -> Unit = {},
    onCreateTimetable: (String, Long, Int, Boolean, List<String>) -> Unit = { _, _, _, _, _ -> },
    onImportClick: (() -> Unit)? = null
) {
    val sectionHeight = 60.dp
    val sidebarWidth = 50.dp
    val headerHeight = 45.dp
    val scope = rememberCoroutineScope()

    val currentCourseColors = getCourseColors(courseColorTone)
    val isDark = isAppInDarkTheme()
    val courseMap = remember(courseInfos) { courseInfos.associateBy { it.id } }

    // Auto 色分配：避免手动色后，按课程名哈希为种子做线性探测，
    // 保证 Auto 课程之间（及与手动色）尽量不撞色
    val autoColorIndices = remember(courseInfos, currentCourseColors.size) {
        val used = mutableSetOf<Int>()
        courseInfos.forEach { info ->
            if (info.colorIndex in used.indices) used.add(info.colorIndex)
        }
        val map = mutableMapOf<String, Int>()
        courseInfos.filter { it.colorIndex == -1 }.forEach { info ->
            var idx = (info.name.hashCode() and Int.MAX_VALUE) % currentCourseColors.size
            var probe = 0
            while (idx in used && probe < currentCourseColors.size) {
                idx = (idx + 1) % currentCourseColors.size
                probe++
            }
            used.add(idx)
            map[info.id] = idx
        }
        map
    }

    val gridBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val gridHeaderBg = MaterialTheme.colorScheme.surface
    val gridContentBg = MaterialTheme.colorScheme.background

    var showDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<CourseSession?>(null) }
    var editingCourse by remember { mutableStateOf<CourseInfo?>(null) }
    var newCoursePlacement by remember { mutableStateOf<NewCoursePlacement?>(null) }
    var courseDetailsSelection by remember { mutableStateOf<CourseDetailsSelection?>(null) }

    val daysCount = if (showWeekends) 7 else 5
    val dayLabels = if (showWeekends) {
        listOf(
            stringResource(R.string.day_mon),
            stringResource(R.string.day_tue),
            stringResource(R.string.day_wed),
            stringResource(R.string.day_thu),
            stringResource(R.string.day_fri),
            stringResource(R.string.day_sat),
            stringResource(R.string.day_sun)
        )
    } else {
        listOf(
            stringResource(R.string.day_mon),
            stringResource(R.string.day_tue),
            stringResource(R.string.day_wed),
            stringResource(R.string.day_thu),
            stringResource(R.string.day_fri)
        )
    }
    val maxSection = 12
    val initialPage = remember(currentTimetableId, currentTotalWeeks, currentWeek) {
        (currentWeek - 1).coerceIn(0, (currentTotalWeeks - 1).coerceAtLeast(0))
    }

    val pagerState = key(currentTimetableId, currentTotalWeeks) {
        rememberPagerState(initialPage = initialPage, pageCount = { currentTotalWeeks })
    }
    val todayWeekIndex = remember(currentStartDate, currentTotalWeeks) {
        getTodayWeekIndex(currentStartDate, currentTotalWeeks)
    }
    val todayDayOfWeek = remember { getTodayDayOfWeek() }
    val nowMillis = rememberCurrentTimeMillis()
    val nowMinuteOfDay = remember(nowMillis) {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }
    val currentSectionPosition = remember(sessionTimes, nowMinuteOfDay) {
        findCurrentSectionPosition(sessionTimes, nowMinuteOfDay)
    }

    var showTimetableSheet by remember { mutableStateOf(false) }
    var showNewTimetableDialog by remember { mutableStateOf(false) }
    var timetableToDelete by remember { mutableStateOf<TimetableMetadata?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val gridScrollState = rememberScrollState()

    val showCurrentTimeIndicator = enableCurrentTimeIndicator && todayDayOfWeek <= daysCount && currentSectionPosition != null
    val currentSectionIndex = currentSectionPosition?.first
    val currentSectionProgress = currentSectionPosition?.second ?: 0f
    val currentTimeLineOffset = if (showCurrentTimeIndicator && currentSectionIndex != null) {
        sectionHeight * (currentSectionIndex + currentSectionProgress)
    } else {
        0.dp
    }
    val undoDoneMessage = stringResource(R.string.undo_done)

    if (showNewTimetableDialog) {
        TimetableConfigDialog(
            onDismiss = { showNewTimetableDialog = false },
            onConfirm = { name, startDate, weeks, showWeekends, times ->
                onCreateTimetable(name, startDate, weeks, showWeekends, times)
                showNewTimetableDialog = false
            },
            onImportClick = {
                showNewTimetableDialog = false
                onImportClick?.invoke()
            }
        )
    }

    if (showTimetableSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showTimetableSheet = false },
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.select_timetable),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    items(timetables) { meta ->
                        val isCurrent = meta.id == currentTimetableId
                        ListItem(
                            headlineContent = { Text(meta.name) },
                            supportingContent = {
                                val date = java.util.Date(meta.lastModified)
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                                Text(stringResource(R.string.last_modified, format.format(date)))
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isCurrent) {
                                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.cd_selected))
                                    }
                                    IconButton(onClick = { timetableToDelete = meta }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.cd_delete_timetable),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onSwitchTimetable(meta.id)
                                showTimetableSheet = false
                            }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

                TextButton(
                    onClick = {
                        showTimetableSheet = false
                        showNewTimetableDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_timetable))
                }
            }
        }
    }

    // 删除课表确认弹窗
    timetableToDelete?.let { meta ->
        AlertDialog(
            onDismissRequest = { timetableToDelete = null },
            title = { Text(stringResource(R.string.delete_timetable_title, meta.name)) },
            text = { Text(stringResource(R.string.delete_timetable_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTimetable(meta.id)
                        // 删除当前课表时直接关闭选择面板，避免残留引用
                        if (meta.id == currentTimetableId) showTimetableSheet = false
                        timetableToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { timetableToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    courseDetailsSelection?.let { selection ->
        CourseDetailsBottomSheet(
            course = selection.course,
            session = selection.session,
            sessionTimes = sessionTimes,
            onDismiss = { courseDetailsSelection = null },
            onEdit = {
                courseDetailsSelection = null
                newCoursePlacement = null
                editingSession = selection.session
                editingCourse = selection.course
                showDialog = true
            }
        )
    }

    // Show empty state if no timetables exist
    if (timetables.isEmpty()) {
        EmptyGuidePlaceholder(
            onCreateTimetable = { showNewTimetableDialog = true }
        )
        return
    }

    LaunchedEffect(pagerState, currentTimetableId) {
        if (currentTimetableId == null) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .collect { page ->
                onCurrentWeekChange(page + 1)
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = null,
                                onClick = { showTimetableSheet = true }
                            )
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentTimetableName.ifEmpty { stringResource(R.string.timetable) },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.week, pagerState.currentPage + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                onUndo()
                                scope.launch {
                                    snackbarHostState.showSnackbar(undoDoneMessage)
                                }
                            },
                            enabled = canUndo
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringResource(R.string.cd_undo)
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_settings)
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            // “今天”按钮的槽位尺寸保持恒定：条件增删会让 FAB 宽度逐帧变化，进而每帧重测
            // pager 里的全部课表格子（翻页收尾那一下发顿的源头）。这里只动 alpha。
            val todayTargetWeek = todayWeekIndex
            val showTodayFab = todayTargetWeek != null && pagerState.settledPage != todayTargetWeek
            val todayFabAlpha by animateFloatAsState(
                targetValue = if (showTodayFab) 1f else 0f,
                animationSpec = tween(200),
                label = "todayFabAlpha"
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { alpha = todayFabAlpha }
                        .then(
                            if (showTodayFab) {
                                Modifier
                            } else {
                                Modifier.semantics { hideFromAccessibility() }
                            }
                        )
                ) {
                    FloatingActionButton(
                        onClick = {
                            // 隐形的只是槽位：不在本周时点击不做任何事
                            if (showTodayFab) {
                                todayTargetWeek?.let { week ->
                                    scope.launch {
                                        pagerState.animateScrollToPage(week)
                                    }
                                }
                            }
                        },
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp,
                            focusedElevation = 0.dp,
                            hoveredElevation = 0.dp
                        )
                    ) {
                        Text(text = stringResource(R.string.today))
                    }
                }

                FloatingActionButton(
                    onClick = {
                        showDialog = true
                        editingSession = null
                        editingCourse = null
                        newCoursePlacement = null
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_course))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .verticalScroll(gridScrollState)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight + sectionHeight * maxSection)
            ) {
                // 侧栏与周次无关，只组一份；竖向滚动也整表共享，各周对齐同一位置
                TimetableSectionSidebar(
                    modifier = Modifier.width(sidebarWidth),
                    headerHeight = headerHeight,
                    headerBg = gridHeaderBg,
                    gridBg = gridContentBg,
                    sessionTimes = sessionTimes,
                    maxSection = maxSection,
                    currentSectionIndex = currentSectionIndex,
                    highlightCurrentSection = showCurrentTimeIndicator
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // Android 15+ 的 ARR 默认把 App 压在 60Hz；该节点只在需要重绘时投票，
                        // 所以滑动/惯性期间才会抬到高刷，静止页面不会长期占用高帧率。
                        .preferredFrameRate(FrameRateCategory.High),
                    beyondViewportPageCount = 1,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    val currentWeek = page + 1

                    val sessionsByDay = remember(
                        courseSessions,
                        courseMap,
                        currentWeek,
                        daysCount,
                        showNonCurrentWeekCourses
                    ) {
                        val map = mutableMapOf<Int, List<CourseDisplayItem>>()
                        for (day in 1..daysCount) {
                            map[day] = courseSessions
                                .filter { session ->
                                    session.day == day && (
                                        showNonCurrentWeekCourses || session.weeks.contains(currentWeek)
                                    )
                                }
                                .mapNotNull { session ->
                                    courseMap[session.courseId]?.let { course ->
                                        CourseDisplayItem(
                                            session = session,
                                            course = course,
                                            isActiveInCurrentWeek = session.weeks.contains(currentWeek)
                                        )
                                    }
                                }
                                // Disjoint-week sessions may occupy the same cell. Draw active courses
                                // last so a grey inactive card can never cover this week's course.
                                .sortedWith(
                                    compareBy<CourseDisplayItem> { it.isActiveInCurrentWeek }
                                        .thenBy { it.session.startSection }
                                        .thenBy { it.session.endSection }
                                )
                        }
                        map
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(gridHeaderBg)
                        ) {

                            dayLabels.forEachIndexed { index, dayLabel ->
                                val dateString = getDateForWeekDay(
                                    currentStartDate,
                                    currentWeek,
                                    index + 1
                                )
                                val isToday = todayWeekIndex == page && todayDayOfWeek == index + 1

                                val cellContainerColor = if (isToday) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    Color.Transparent
                                }
                                val dayTextColor = if (isToday) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                val dateTextColor = if (isToday) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(45.dp)
                                        .padding(horizontal = 2.dp, vertical = 3.dp)
                                        .then(
                                            if (isToday) {
                                                Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(cellContainerColor)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayLabel,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = dayTextColor
                                        )
                                        Text(
                                            text = dateString,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = dateTextColor,
                                            fontWeight = FontWeight.Normal
                                        )
                    }
                }
            }
        }

                // Grid Body
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sectionHeight * maxSection)
                        .background(gridContentBg)
                ) {
                    // Course content area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // 1. Grid lines. Draw every line once so adjacent cells do not
                        // double their opacity, and use the same proportional boundaries
                        // as course cards to avoid density-dependent rounding drift.
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 0.5.dp.toPx()
                            val halfStroke = strokeWidth / 2f

                            for (section in 0..maxSection) {
                                val y = (size.height * section / maxSection)
                                    .coerceIn(halfStroke, size.height - halfStroke)
                                drawLine(
                                    color = gridBorderColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                                    strokeWidth = strokeWidth
                                )
                            }

                            for (day in 0..daysCount) {
                                val x = (size.width * day / daysCount)
                                    .coerceIn(halfStroke, size.width - halfStroke)
                                drawLine(
                                    color = gridBorderColor,
                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                    strokeWidth = strokeWidth
                                )
                            }
                        }

                        // 2. Empty-cell interaction layer: one gesture detector for the whole grid
                        //    instead of one pointer-input node per cell. Course cards are drawn
                        //    afterwards and therefore keep priority for pointer input in occupied areas.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(daysCount, maxSection, sessionsByDay) {
                                    detectTapGestures(onDoubleTap = { tap ->
                                        val day = gridCellIndex(tap.x, size.width.toFloat(), daysCount)
                                        val section = gridCellIndex(tap.y, size.height.toFloat(), maxSection)
                                        val isOccupied = day == 0 || section == 0 ||
                                            sessionsByDay[day]
                                                .orEmpty()
                                                .any { item ->
                                                    section in item.session.startSection..item.session.endSection
                                                }
                                        if (!isOccupied) {
                                            editingSession = null
                                            editingCourse = null
                                            newCoursePlacement = NewCoursePlacement(
                                                day = day,
                                                section = section,
                                                week = currentWeek
                                            )
                                            showDialog = true
                                        }
                                    })
                                }
                        )

                        // 3. Course Content
                        Row(modifier = Modifier.fillMaxSize()) {
                            (1..daysCount).forEach { day ->
                                CourseDayColumn(
                                    sessions = sessionsByDay[day].orEmpty(),
                                    maxSection = maxSection,
                                    colorsList = currentCourseColors,
                                    autoColorIndices = autoColorIndices,
                                    onCourseClick = { session, course ->
                                        courseDetailsSelection = CourseDetailsSelection(
                                            session = session,
                                            course = course
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }

                        // 4. Current time line. Paint-only translation keeps the per-minute tick
                        // out of the measure pass.
                        if (showCurrentTimeIndicator) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationY = currentTimeLineOffset.toPx()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .offset(x = (-4).dp, y = (-2.5).dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }

            }
        }
            }
        }

        if (showDialog) {
            CourseEditorDialog(
                initialSession = editingSession,
                initialCourse = editingCourse,
                existingCourses = courseInfos,
                existingSessions = courseSessions,
                colorsList = currentCourseColors,
                isDarkTheme = isDark,
                totalWeeks = currentTotalWeeks,
                maxSection = sessionTimes.size.coerceAtLeast(2),
                initialDay = newCoursePlacement?.day ?: 1,
                initialStartSection = newCoursePlacement?.section ?: 1,
                initialEndSection = newCoursePlacement?.section ?: 2,
                initialWeeks = newCoursePlacement?.let { setOf(it.week) }
                    ?: (1..currentTotalWeeks).toSet(),
                onDismiss = {
                    showDialog = false
                    newCoursePlacement = null
                },
                onSave = { info, session, createNewCourse ->
                    if (createNewCourse) {
                        onAddCourse(info)
                        onAddSession(session)
                    } else {
                        if (editingCourse != null && editingCourse != info) onUpdateCourse(info)
                        if (editingSession != null && editingSession != session) onUpdateSession(
                            editingSession!!,
                            session
                        )
                    }
                    showDialog = false
                    newCoursePlacement = null
                },
                onDelete = {
                    if (editingSession != null) {
                        onDeleteSession(editingSession!!)
                    }
                    showDialog = false
                    newCoursePlacement = null
                }
            )
        }
    }
}

/**
 * Maps a pointer position inside the grid content area to its 1-based cell index
 * (day column or section row); returns 0 when the position falls outside the grid.
 */
internal fun gridCellIndex(positionPx: Float, extentPx: Float, count: Int): Int {
    if (extentPx <= 0f || count <= 0 || positionPx < 0f || positionPx >= extentPx) return 0
    return ((positionPx * count) / extentPx).toInt().coerceIn(0, count - 1) + 1
}


/**
 * 节次侧栏：内容与周次无关，所以放在 pager 外只组一份。
 * 挂在每页里时，邻页首次构图会连带重做 12×(Box+Column+2~3 Text) 的测量。
 */
@Composable
private fun TimetableSectionSidebar(
    modifier: Modifier = Modifier,
    headerHeight: Dp,
    headerBg: Color,
    gridBg: Color,
    sessionTimes: List<String>,
    maxSection: Int,
    currentSectionIndex: Int?,
    highlightCurrentSection: Boolean
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(gridBg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(headerBg)
        )

        (1..maxSection).forEach { section ->
            val isCurrentSection = highlightCurrentSection && currentSectionIndex == section - 1
            val sectionContainerColor = if (isCurrentSection) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 2.dp, vertical = 3.dp)
                    .then(
                        if (isCurrentSection) {
                            Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(sectionContainerColor)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = section.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrentSection) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (section - 1 < sessionTimes.size && sessionTimes[section - 1].isNotEmpty()) {
                        val timeStr = sessionTimes[section - 1]
                        val parts = timeStr.split("-")
                        if (parts.size == 2) {
                            Text(
                                text = parts[0],
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                textAlign = TextAlign.Center,
                                color = if (isCurrentSection) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = parts[1],
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                textAlign = TextAlign.Center,
                                color = if (isCurrentSection) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                textAlign = TextAlign.Center,
                                color = if (isCurrentSection) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseDayColumn(
    sessions: List<CourseDisplayItem>,
    maxSection: Int,
    colorsList: List<Color>,
    autoColorIndices: Map<String, Int>,
    onCourseClick: (CourseSession, CourseInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            sessions.forEach { item ->
                CourseCard(
                    course = item.course,
                    colorsList = colorsList,
                    isActiveInCurrentWeek = item.isActiveInCurrentWeek,
                    autoColorIndex = autoColorIndices[item.course.id],
                    onClick = { onCourseClick(item.session, item.course) }
                )
            }
        }
    ) { measurables, constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight

        val placements = measurables.mapIndexed { index, measurable ->
            val session = sessions[index].session
            val startSection = session.startSection.coerceIn(1, maxSection)
            val endSection = session.endSection.coerceIn(startSection, maxSection)
            val top = (layoutHeight.toFloat() * (startSection - 1) / maxSection).roundToInt()
            val bottom = (layoutHeight.toFloat() * endSection / maxSection).roundToInt()
            val cardHeight = (bottom - top).coerceAtLeast(1)
            val placeable = measurable.measure(
                Constraints.fixed(width = layoutWidth, height = cardHeight)
            )
            placeable to top
        }

        layout(layoutWidth, layoutHeight) {
            placements.forEach { (placeable, top) ->
                placeable.placeRelative(x = 0, y = top)
            }
        }
    }
}

@Composable
private fun rememberCurrentTimeMillis(tickMs: Long = 30_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(tickMs)
            now = System.currentTimeMillis()
        }
    }

    return now
}

internal fun findCurrentSectionPosition(sessionTimes: List<String>, currentMinuteOfDay: Int): Pair<Int, Float>? {
    val validSections = sessionTimes.mapIndexedNotNull { index, timeStr ->
        val parts = timeStr.split("-")
        if (parts.size != 2) return@mapIndexedNotNull null

        val startMinute = parseMinuteOfDay(parts[0]) ?: return@mapIndexedNotNull null
        val endMinute = parseMinuteOfDay(parts[1]) ?: return@mapIndexedNotNull null
        if (endMinute <= startMinute) return@mapIndexedNotNull null

        Triple(index, startMinute, endMinute)
    }

    validSections.forEach { (index, startMinute, endMinute) ->

        if (currentMinuteOfDay in startMinute until endMinute) {
            val progress = (currentMinuteOfDay - startMinute).toFloat() / (endMinute - startMinute).toFloat()
            return index to progress.coerceIn(0f, 1f)
        }

        if (currentMinuteOfDay < startMinute) {
            return index to 0f
        }
    }

    // There is no later section after the last class period. Keep the enabled
    // indicator visible on the final end boundary instead of hiding it.
    return validSections.lastOrNull()?.let { (index, _, _) -> index to 1f }
}

private fun parseMinuteOfDay(text: String): Int? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour * 60 + minute
}

@Preview(showBackground = true, widthDp = 420, heightDp = 860)
@Composable
fun TimetableScreenPreview() {
    val sampleCourses = listOf(
        CourseInfo(
            id = "c1",
            name = "Data Structures",
            teacher = "Prof. Li",
            classroom = "A-203",
            colorIndex = 0
        ),
        CourseInfo(
            id = "c2",
            name = "Mobile Development",
            teacher = "Prof. Wang",
            classroom = "B-512",
            colorIndex = 2
        )
    )

    val sampleSessions = listOf(
        CourseSession(
            courseId = "c1",
            day = 1,
            startSection = 1,
            endSection = 2,
            weeks = (1..16).toList()
        ),
        CourseSession(
            courseId = "c2",
            day = 3,
            startSection = 5,
            endSection = 6,
            weeks = (1..16).toList()
        )
    )

    val sampleTimetables = listOf(
        TimetableMetadata(
            id = "preview",
            name = "2026 Spring",
            lastModified = System.currentTimeMillis(),
            totalWeeks = 16,
            sessionTimes = defaultSessionTimes,
            showWeekends = true
        )
    )

    NJUPTerTheme {
        TimetableScreen(
            courseInfos = sampleCourses,
            courseSessions = sampleSessions,
            timetables = sampleTimetables,
            currentTimetableName = "2026 Spring",
            currentTimetableId = "preview",
            currentStartDate = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000,
            currentTotalWeeks = 16,
            currentWeek = 2,
            sessionTimes = defaultSessionTimes,
            showWeekends = true,
            isLoading = false
        )
    }
}
