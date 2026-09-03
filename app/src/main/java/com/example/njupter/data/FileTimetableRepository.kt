package com.example.njupter.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 基于文件存储的课表仓库实现，持有StateFlow，调用 DataSource完成持久化
 */

class FileTimetableRepository(
    private val dataSource: TimetableDataSource,    // 负责实际的文件读写
    private val settingsRepository: SettingsRepository
) : TimetableRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 持有5个MutableStateFlow，private 是因为外部只能读取，对外只暴露Flow，Mutable 是因为内部有权修改
    private val _courseInfos = MutableStateFlow<List<CourseInfo>>(emptyList())
    private val _courseSessions = MutableStateFlow<List<CourseSession>>(emptyList())

    private val _availableTimetables = MutableStateFlow<List<TimetableMetadata>>(emptyList())
    private val _currentTimetableName = MutableStateFlow("")
    private val _currentTimetableId = MutableStateFlow<String?>(null)

    private val _isInitialized = MutableStateFlow(false)

    // --- 撤销快照栈（仅内存，本次运行有效；按课表隔离） ---
    private data class UndoSnapshot(
        val timetableId: String,
        val courses: List<CourseInfo>,
        val sessions: List<CourseSession>,
        val timestamp: Long
    )

    private val undoStacks = mutableMapOf<String, ArrayDeque<UndoSnapshot>>()
    private val _undoAvailable = MutableStateFlow(false)

    override fun getUndoAvailable(): StateFlow<Boolean> = _undoAvailable.asStateFlow()

    private companion object {
        const val MAX_UNDO_STEPS = 20
        // 时间窗口内（毫秒）的连续修改视为同一次用户操作，合并为一个撤销单元
        const val UNDO_MERGE_WINDOW_MS = 500L
    }

    // 每次课程数据变更前压入当前状态快照
    private fun pushUndo() {
        val id = _currentTimetableId.value ?: return
        val stack = undoStacks.getOrPut(id) { ArrayDeque() }
        stack.addLast(
            UndoSnapshot(
                timetableId = id,
                courses = _courseInfos.value,
                sessions = _courseSessions.value,
                timestamp = System.currentTimeMillis()
            )
        )
        while (stack.size > MAX_UNDO_STEPS) stack.removeFirst()
        refreshUndoAvailable()
    }

    private fun refreshUndoAvailable() {
        _undoAvailable.value = undoStacks[_currentTimetableId.value]?.isNotEmpty() == true
    }

    override fun getIsInitialized(): StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        repositoryScope.launch {
            refreshTimetableList()
            val lastId = settingsRepository.peekLastSelectedTimetableId()
            if (lastId != null && _availableTimetables.value.any { it.id == lastId }) {
                switchTimetable(lastId)
            } else {
                val first = _availableTimetables.value.firstOrNull()
                if (first != null) {
                    switchTimetable(first.id)
                }
            }
            _isInitialized.value = true
        }
    }

    private suspend fun refreshTimetableList() {
        _availableTimetables.value = dataSource.getAllTimetables()
    }
    
    private suspend fun saveData() {
        val id = _currentTimetableId.value ?: return
        // 已注释掉的启动一个新的协程任务，在IO线程中执行保存操作，避免阻塞主线程
        // 但是把持久化操作丢到后台线程，不保证完成时间、不保证顺序、不保证成功
        // CoroutineScope(Dispatchers.IO).launch {
        // 那就 suspend + 上层控制
            dataSource.saveTimetable(
                id,
                TimetableData(
                    courses = _courseInfos.value,
                    sessions = _courseSessions.value
                )
            )
        // }
    }

    // asStateFlow() 将 MutableStateFlow 转换为只读的 StateFlow，外部只能订阅，不能修改
    override fun getCourseInfos(): StateFlow<List<CourseInfo>> = _courseInfos.asStateFlow()  // 想知道当前状态，类内部直接访问 _courseInfos.value 就行了，类外部想订阅状态变化，使用 getCourseInfos() 返回的 Flow 来观察
    override fun getCourseSessions(): StateFlow<List<CourseSession>> = _courseSessions.asStateFlow()
    
    override fun getAvailableTimetables(): StateFlow<List<TimetableMetadata>> = _availableTimetables.asStateFlow()
    override fun getCurrentTimetableName(): StateFlow<String> = _currentTimetableName.asStateFlow()
    override fun getCurrentTimetableId(): StateFlow<String?> = _currentTimetableId.asStateFlow()

    override fun getCurrentTimetable(): Flow<TimetableMetadata?> =
        combine(_availableTimetables, _currentTimetableId) { list, id ->
            list.find { it.id == id }
        }

    override suspend fun switchTimetable(id: String) {
        if (id == _currentTimetableId.value) return
        
        val metas = _availableTimetables.value
        val meta = metas.find { it.id == id } ?: return
        
        _currentTimetableId.value = id
        _currentTimetableName.value = meta.name
        
        val data = dataSource.loadTimetable(id)
        _courseInfos.value = data.courses
        _courseSessions.value = data.sessions

        settingsRepository.setLastSelectedTimetableId(id)
        refreshUndoAvailable()
    }

    override suspend fun createTimetable(name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>) {
        val meta = dataSource.createTimetable(name, startDate, totalWeeks, showWeekends, sessionTimes)
        
        // 强制更新内存中的列表，确保新创建的表立即可用
        val currentList = dataSource.getAllTimetables()
        _availableTimetables.value = currentList
        
        // 如果列表中找不到刚创建的表，手动添加进去以确保 switchTimetable 成功
        if (_availableTimetables.value.none { it.id == meta.id }) {
            _availableTimetables.value = _availableTimetables.value + meta
        }

        switchTimetable(meta.id)
    }

    override suspend fun updateTimetableMetadata(id: String, name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, showNonCurrentWeekCourses: Boolean, sessionTimes: List<String>) {
        dataSource.updateTimetableMetadata(id, name, startDate, totalWeeks, showWeekends, showNonCurrentWeekCourses, sessionTimes)
        
        if (id == _currentTimetableId.value) {
            _currentTimetableName.value = name
        }
        refreshTimetableList()
    }

    override suspend fun deleteTimetable(id: String) {
        // 删除当前课表时切换到另一张；若没有其他课表则清空当前状态进入空态
        val others = _availableTimetables.value.filterNot { it.id == id }
        if (id == _currentTimetableId.value) {
            val next = others.firstOrNull()
            if (next != null) {
                switchTimetable(next.id)
            } else {
                _currentTimetableId.value = null
                _currentTimetableName.value = ""
                _courseInfos.value = emptyList()
                _courseSessions.value = emptyList()
                settingsRepository.setLastSelectedTimetableId(null)
            }
        }
        undoStacks.remove(id)
        dataSource.deleteTimetable(id)
        refreshTimetableList()
        refreshUndoAvailable()
    }

    override suspend fun addCourse(course: CourseInfo) {
        pushUndo()  // 先快照当前状态
        _courseInfos.update { current ->    // 先改内存中的状态持有
            current + course
        }
        saveData()  // 再统一写入
    }

    override suspend fun addSession(session: CourseSession) {
        pushUndo()
        _courseSessions.update { current ->
            current + session
        }
        saveData()
    }

    override suspend fun updateCourse(course: CourseInfo) {
        pushUndo()
        _courseInfos.update { current ->
            current.map { if (it.id == course.id) course else it }
        }
        saveData()
    }

    override suspend fun updateSession(oldSession: CourseSession, newSession: CourseSession) {
        pushUndo()
        _courseSessions.update { current ->
            current.map {
                // CourseSession 没有唯一 ID，需要比较所有字段或对象引用
                if (it == oldSession) newSession else it
            }
        }
        saveData()
    }

    override suspend fun deleteSession(session: CourseSession) {
        pushUndo()
        _courseSessions.update { current ->
            current - session
        }
        saveData()
    }

    override suspend fun importTimetableData(newCourses: List<CourseInfo>, newSessions: List<CourseSession>) {
        pushUndo()
        _courseInfos.update { current ->
            current + newCourses
        }
        _courseSessions.update { current ->
            current + newSessions
        }
        saveData()
    }

    override suspend fun undoLastChange() {
        val currentId = _currentTimetableId.value ?: return
        val stack = undoStacks[currentId]
        if (stack == null || stack.isEmpty()) {
            refreshUndoAvailable()
            return
        }

        // 收集与栈顶时间相邻（同一撤销单元）的快照
        val baseTs = stack.last().timestamp
        val popped = mutableListOf<UndoSnapshot>()
        while (stack.isNotEmpty()) {
            val top = stack.last()
            if (popped.isEmpty()) {
                popped.add(stack.removeLast())
            } else if (top.timestamp >= baseTs - UNDO_MERGE_WINDOW_MS) {
                popped.add(stack.removeLast())
            } else {
                break
            }
        }

        // 恢复该单元中最早的状态（整组回退）
        val snapshot = popped.last()
        _courseInfos.value = snapshot.courses
        _courseSessions.value = snapshot.sessions
        saveData()
        refreshUndoAvailable()
    }
}
