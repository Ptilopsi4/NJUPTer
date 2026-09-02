package com.example.njupter.ui.timetable

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
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
    val scope = rememberCoroutineScope()

    val currentCourseColors = getCourseColors()
    val isDark = isAppInDarkTheme()
    val courseMap = remember(courseInfos) { courseInfos.associateBy { it.id } }

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
            Row(
                modifier = Modifier
                    .animateContentSize(animationSpec = tween(200)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (todayWeekIndex != null && pagerState.currentPage != todayWeekIndex) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(todayWeekIndex)
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 0.dp),
            verticalAlignment = Alignment.Top
        ) { page ->
            val currentWeek = page + 1
            val pageScrollState = rememberScrollState()

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

            val showCurrentTimeIndicator = enableCurrentTimeIndicator && todayDayOfWeek <= daysCount && currentSectionPosition != null
            val currentSectionIndex = currentSectionPosition?.first
            val currentSectionProgress = currentSectionPosition?.second ?: 0f
            val currentTimeLineOffset = if (showCurrentTimeIndicator && currentSectionIndex != null) {
                sectionHeight * (currentSectionIndex + currentSectionProgress)
            } else {
                0.dp
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScrollState)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(gridHeaderBg)
                ) {
                    Box(
                        modifier = Modifier
                            .width(sidebarWidth)
                            .height(45.dp)
                    )

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
                                .clip(MaterialTheme.shapes.small)
                                .background(cellContainerColor),
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
                    // Sidebar
                    Column(modifier = Modifier.width(sidebarWidth).fillMaxHeight()) {
                        (1..maxSection).forEach { section ->
                            val isCurrentSection = showCurrentTimeIndicator && currentSectionIndex == section - 1
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
                                    .clip(MaterialTheme.shapes.small)
                                    .background(sectionContainerColor),
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

                        // 2. Empty-cell interaction layer. Course cards are drawn afterwards and
                        // therefore keep priority for pointer input in occupied areas.
                        Row(modifier = Modifier.fillMaxSize()) {
                            (1..daysCount).forEach { day ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    (1..maxSection).forEach { section ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .combinedClickable(
                                                    interactionSource = null,
                                                    indication = null,
                                                    onClick = {},
                                                    onDoubleClick = {
                                                        val isOccupied = sessionsByDay[day]
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
                                                    }
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Course Content
                        Row(modifier = Modifier.fillMaxSize()) {
                            (1..daysCount).forEach { day ->
                                CourseDayColumn(
                                    sessions = sessionsByDay[day].orEmpty(),
                                    maxSection = maxSection,
                                    colorsList = currentCourseColors,
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

                        // 4. Current time line
                        if (showCurrentTimeIndicator) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = currentTimeLineOffset)
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

        if (showDialog) {
            CourseEditorDialog(
                initialSession = editingSession,
                initialCourse = editingCourse,
                existingCourses = courseInfos,
                existingSessions = courseSessions,
                colorsList = currentCourseColors,
                isDarkTheme = isDark,
                totalWeeks = currentTotalWeeks,
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

@Composable
private fun CourseDayColumn(
    sessions: List<CourseDisplayItem>,
    maxSection: Int,
    colorsList: List<Color>,
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
