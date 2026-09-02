package com.example.njupter.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * 课表数据仓库接口，Flow使数据源支持订阅，数据变化时会主动推送给MainViewModel
 */
interface TimetableRepository {
    fun getCourseInfos(): StateFlow<List<CourseInfo>>
    fun getCourseSessions(): StateFlow<List<CourseSession>>
    fun getAvailableTimetables(): StateFlow<List<TimetableMetadata>>
    fun getCurrentTimetableName(): StateFlow<String>
    fun getCurrentTimetableId(): StateFlow<String?>
    fun getCurrentTimetable(): Flow<TimetableMetadata?>
    fun getIsInitialized(): StateFlow<Boolean>
    fun getUndoAvailable(): StateFlow<Boolean>
    
    suspend fun switchTimetable(id: String)
    suspend fun createTimetable(name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>)
    suspend fun updateTimetableMetadata(id: String, name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, showNonCurrentWeekCourses: Boolean, sessionTimes: List<String>)
    suspend fun deleteTimetable(id: String)

    suspend fun addCourse(course: CourseInfo)
    suspend fun addSession(session: CourseSession)
    suspend fun updateCourse(course: CourseInfo)
    suspend fun updateSession(oldSession: CourseSession, newSession: CourseSession)
    suspend fun deleteSession(session: CourseSession)

    // 新增：批量导入课表数据
    suspend fun importTimetableData(newCourses: List<CourseInfo>, newSessions: List<CourseSession>)

    // 撤销最近的课程数据修改（仅本次运行会话内有效）
    suspend fun undoLastChange()
}
