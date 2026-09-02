package com.example.njupter.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.njupter.data.CourseInfo
import com.example.njupter.data.CourseSession
import com.example.njupter.data.SettingsRepository
import com.example.njupter.data.TimetableMetadata
import com.example.njupter.data.TimetableRepository
import com.example.njupter.data.import.JwxtParser
import com.example.njupter.domain.getTodayWeekIndex
import com.example.njupter.domain.import.TimetableImportMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 该 ViewModel 负责管理课程表相关的 UI 状态和业务逻辑。它从 TimetableRepository 获取课程信息、课程安排和时间表列表，并将它们组合成一个统一的 UI 状态流（uiState）。UI 层可以观察这个状态流来更新界面。
 */
data class TimetableUiState(
    val courseInfos: List<CourseInfo> = emptyList(),
    val sessions: List<CourseSession> = emptyList(),
    val isLoading: Boolean = false,
    val timetables: List<TimetableMetadata> = emptyList(),
    val currentTimetableName: String = "",
    val currentTimetableId: String? = null,
    val currentStartDate: Long = System.currentTimeMillis(),
    val currentTotalWeeks: Int = 20,
    val currentWeek: Int = 1,
    val showWeekends: Boolean = false,
    val showNonCurrentWeekCourses: Boolean = false,
    val currentSessionTimes: List<String> = emptyList(),
    
    // Import state
    val importResult: TimetableImportMatcher.ImportResult? = null,
    val isImporting: Boolean = false,
    val importError: String? = null
)

class TimetableViewModel(
    private val repository: TimetableRepository,
    private val settingsRepository: SettingsRepository,
    private val onWidgetRefresh: (Context) -> Unit = {}
) : ViewModel() {

    private var appContext: Context? = null

    private val _currentWeek = MutableStateFlow(1)

    init {
        viewModelScope.launch {
            combine(
                repository.getCurrentTimetable(),
                settingsRepository.getLastWeekRecords()
            ) { currentMeta, lastWeekRecords ->
                val safeTotalWeeks = currentMeta?.totalWeeks?.takeIf { it > 0 } ?: 20
                val safeStartDate = currentMeta?.startDate ?: System.currentTimeMillis()
                val timetableId = currentMeta?.id

                val restoredWeek = timetableId
                    ?.let { id -> lastWeekRecords[id] }
                    ?: getTodayWeekIndex(safeStartDate, safeTotalWeeks)?.plus(1)
                    ?: 1

                restoredWeek.coerceIn(1, safeTotalWeeks)
            }.collect { week ->
                _currentWeek.value = week
            }
        }
    }

    private val timetableState = combine(
        combine(
            repository.getCourseInfos(),
            repository.getCourseSessions(),
            repository.getAvailableTimetables()
        ) { courses, sessions, timetables -> Triple(courses, sessions, timetables) },
        combine(
            repository.getCurrentTimetableName(),
            repository.getCurrentTimetableId(),
            repository.getCurrentTimetable()
        ) { currentName, currentId, currentMeta -> Triple(currentName, currentId, currentMeta) },
        combine(
            repository.getIsInitialized(),
            _currentWeek
        ) { initialized, currentWeek -> initialized to currentWeek }
    ) { courseData, currentData, stateData ->
        TimetableBundle(
            courses = courseData.first,
            sessions = courseData.second,
            timetables = courseData.third,
            currentName = currentData.first,
            currentId = currentData.second,
            currentMeta = currentData.third,
            initialized = stateData.first,
            currentWeek = stateData.second
        )
    }

    val uiState: StateFlow<TimetableUiState> = timetableState.map { bundle ->     // 数据层从这里开始 --> FileTimetableRepository
        // 处理缺失元数据的默认值
        val safeTotalWeeks = bundle.currentMeta?.totalWeeks?.takeIf { it > 0 } ?: 20
        // If startDate is 0 (1970), maybe default to now? Or let user see 1970 to fix it.
        // User complained about 1970-01-01. Providing a default if 0 seems appropriate.
        val safeStartDate = if (bundle.currentMeta?.startDate != null && bundle.currentMeta.startDate > 0) bundle.currentMeta.startDate else System.currentTimeMillis()
        val safeSessionTimes = bundle.currentMeta?.nonNullSessionTimes ?: TimetableMetadata("", "", 0).nonNullSessionTimes
        val safeShowWeekends = bundle.currentMeta?.showWeekends ?: true
        val safeShowNonCurrentWeekCourses = bundle.currentMeta?.showNonCurrentWeekCourses ?: false

        TimetableUiState(
            courseInfos = bundle.courses,
            sessions = bundle.sessions,
            isLoading = !bundle.initialized,
            timetables = bundle.timetables,
            currentTimetableName = bundle.currentName,
            currentTimetableId = bundle.currentId,
            currentStartDate = safeStartDate,
            currentTotalWeeks = safeTotalWeeks,
            currentWeek = bundle.currentWeek.coerceIn(1, safeTotalWeeks),
            showWeekends = safeShowWeekends,
            showNonCurrentWeekCourses = safeShowNonCurrentWeekCourses,
            currentSessionTimes = safeSessionTimes
        )
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimetableUiState(isLoading = true)
    )

    // A separate StateFlow for import process so we can overlay it on top of the combined flow above.
    private val _importState = MutableStateFlow(ImportState())
    val importState = _importState.asStateFlow()

    data class ImportState(
        val isImporting: Boolean = false,
        val result: TimetableImportMatcher.ImportResult? = null,
        val error: String? = null
    )

    private data class TimetableBundle(
        val courses: List<CourseInfo>,
        val sessions: List<CourseSession>,
        val timetables: List<TimetableMetadata>,
        val currentName: String,
        val currentId: String?,
        val currentMeta: TimetableMetadata?,
        val initialized: Boolean,
        val currentWeek: Int
    )

    fun processTimetableImport(html: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _importState.value = ImportState(isImporting = true)
            try {
                val parser = JwxtParser()
                val remoteCourses = parser.parseHtml(html)

                val matcher = TimetableImportMatcher()
                
                // For a new timetable, we match against empty lists to treat all courses as new
                val result = matcher.matchAndConvert(remoteCourses, emptyList(), emptyList())
                
                _importState.value = ImportState(result = result)
            } catch (e: Exception) {
                _importState.value = ImportState(error = e.message ?: "Unknown error")
            }
        }
    }

    fun clearImportState() {
        _importState.value = ImportState()
    }

    fun createTimetable(name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>) {
        viewModelScope.launch {
            repository.createTimetable(name, startDate, totalWeeks, showWeekends, sessionTimes)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun updateTimetableMetadata(id: String, name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, showNonCurrentWeekCourses: Boolean, sessionTimes: List<String>) {
        viewModelScope.launch {
            repository.updateTimetableMetadata(id, name, startDate, totalWeeks, showWeekends, showNonCurrentWeekCourses, sessionTimes)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun switchTimetable(id: String) {
        viewModelScope.launch {
            repository.switchTimetable(id)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun deleteTimetable(id: String) {
        viewModelScope.launch {
            repository.deleteTimetable(id)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun setCurrentWeek(week: Int) {
        viewModelScope.launch {
            val currentTimetableId = repository.getCurrentTimetableId().value ?: return@launch
            val totalWeeks = repository.getCurrentTimetable()
                .first()
                ?.totalWeeks
                ?.takeIf { it > 0 }
                ?: 20
            val safeWeek = week.coerceIn(1, totalWeeks)
            if (_currentWeek.value != safeWeek) {
                _currentWeek.value = safeWeek
            }
            settingsRepository.setLastWeekForTimetable(currentTimetableId, safeWeek)
        }
    }

    fun addCourse(course: CourseInfo) {
        viewModelScope.launch {
            repository.addCourse(course)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun addSession(session: CourseSession) {
        viewModelScope.launch {
            repository.addSession(session)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun updateCourse(course: CourseInfo) {
        viewModelScope.launch {
            repository.updateCourse(course)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun updateSession(oldSession: CourseSession, newSession: CourseSession) {
        viewModelScope.launch {
            repository.updateSession(oldSession, newSession)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun deleteSession(session: CourseSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    fun createAndImportTimetable(name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>, newCourses: List<CourseInfo>, newSessions: List<CourseSession>) {
        viewModelScope.launch {
            repository.createTimetable(name, startDate, totalWeeks, showWeekends, sessionTimes)
            // The active timetable is automatically switched inside createTimetable,
            // so we can now safely import.
            repository.importTimetableData(newCourses, newSessions)
            appContext?.let { onWidgetRefresh(it) }
        }
    }

    // Factory模式用于依赖注入
    companion object {
        fun provideFactory(
            repository: TimetableRepository,
            settingsRepository: SettingsRepository,
            appContext: Context? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TimetableViewModel(
                    repository,
                    settingsRepository,
                    onWidgetRefresh = { ctx -> com.example.njupter.widget.WidgetDataManager.refreshWidget(ctx) }
                ).also { it.appContext = appContext }
                    as T
            }
        }
    }
}
