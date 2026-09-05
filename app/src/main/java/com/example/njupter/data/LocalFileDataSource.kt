package com.example.njupter.data

import android.content.Context
import java.util.UUID
import java.io.File
import com.google.gson.Gson
import com.example.njupter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地文件存储实现：支持多课表
 */
class LocalFileDataSource(private val context: Context) : TimetableDataSource {

    private val gson = Gson()
    private val oldFileName = "timetable_data.json"
    private val indexFileName = "timetables_index.json"

    private fun getIndexFile() = File(context.filesDir, indexFileName)
    private fun getDataFile(id: String) = File(context.filesDir, "timetable_$id.json")

    override suspend fun getAllTimetables(): List<TimetableMetadata> = withContext(Dispatchers.IO) {
        val indexFile = getIndexFile()
        if (indexFile.exists()) {
            try {
                val json = indexFile.readText()
                val index = gson.fromJson(json, TimetableIndex::class.java)
                return@withContext index.timetables
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext emptyList()
            }
        }

        // Migration logic
        val oldFile = File(context.filesDir, oldFileName)
        val initialList = if (oldFile.exists()) {
            val defaultMeta = TimetableMetadata(id = "default", name = context.getString(R.string.default_timetable_name), lastModified = System.currentTimeMillis())
            val newFile = getDataFile("default")
            oldFile.renameTo(newFile)
            listOf(defaultMeta)
        } else {
            emptyList()
        }

        saveIndex(initialList)
        return@withContext initialList
    }

    private suspend fun saveIndex(list: List<TimetableMetadata>) = withContext(Dispatchers.IO) {
        val file = getIndexFile()
        val json = gson.toJson(TimetableIndex(list))
        file.writeText(json)
    }

    override suspend fun createTimetable(name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>): TimetableMetadata = withContext(Dispatchers.IO) {
        val meta = TimetableMetadata(
            id = UUID.randomUUID().toString(),
            name = name,
            lastModified = System.currentTimeMillis(),
            startDate = startDate,
            totalWeeks = totalWeeks,
            sessionTimes = sessionTimes,
            showWeekends = showWeekends
        )
        val root = TimetableJsonRoot(emptyList(), emptyList())
        getDataFile(meta.id).writeText(gson.toJson(root))

        val currentList = getAllTimetables().toMutableList()
        currentList.add(meta)
        saveIndex(currentList)

        return@withContext meta
    }

    override suspend fun updateTimetableMetadata(id: String, name: String, startDate: Long, totalWeeks: Int, showWeekends: Boolean, sessionTimes: List<String>) = withContext(Dispatchers.IO) {
        val currentList = getAllTimetables().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = currentList[index]
            currentList[index] = old.copy(
                name = name,
                startDate = startDate,
                totalWeeks = totalWeeks,
                sessionTimes = sessionTimes,
                showWeekends = showWeekends,
                lastModified = System.currentTimeMillis()
            )
            saveIndex(currentList)
        }
    }

    override suspend fun loadTimetable(id: String): TimetableData = withContext(Dispatchers.IO) {
        val file = getDataFile(id)
        if (file.exists()) {
            try {
                val reader = file.reader()
                val root = gson.fromJson(reader, TimetableJsonRoot::class.java)
                reader.close()

                val domainCourses = root.courses.map {
                    CourseInfo(
                        id = it.id,
                        name = it.name,
                        teacher = it.teacher,
                        colorIndex = it.colorIndex,
                        credit = it.credit.orEmpty(),
                        courseNature = it.courseNature.orEmpty(),
                        note = it.note.orEmpty(),
                        attendanceType = it.attendanceType.orEmpty(),
                        reminderEnabled = it.reminderEnabled ?: true    // 旧数据默认开启提醒
                    )
                }
                val domainSessions = root.sessions.map {
                    CourseSession(
                        courseId = it.courseId,
                        day = it.dayOfWeek,
                        startSection = it.startNode,
                        endSection = it.startNode + it.length - 1,
                        weeks = it.weeks ?: (1..20).toList(),
                        classroom = it.classroom.orEmpty()
                    )
                }
                return@withContext TimetableData(domainCourses, domainSessions)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext TimetableData(emptyList(), emptyList())
    }

    override suspend fun saveTimetable(id: String, data: TimetableData) = withContext(Dispatchers.IO) {
        val file = getDataFile(id)
        val root = TimetableJsonRoot(
            courses = data.courses.map {
                CourseInfoJson(
                    id = it.id,
                    name = it.name,
                    teacher = it.teacher,
                    colorIndex = it.colorIndex,
                    credit = it.credit,
                    courseNature = it.courseNature,
                    note = it.note,
                    attendanceType = it.attendanceType,
                    reminderEnabled = it.reminderEnabled
                )
            },
            sessions = data.sessions.map {
                CourseSessionJson(
                    courseId = it.courseId,
                    dayOfWeek = it.day,
                    startNode = it.startSection,
                    length = it.endSection + 1 - it.startSection,
                    weeks = it.weeks,
                    classroom = it.classroom
                )
            }
        )
        val json = gson.toJson(root)
        file.writeText(json)

        val metas = getAllTimetables().toMutableList()
        val index = metas.indexOfFirst { it.id == id }
        if (index != -1) {
            val old = metas[index]
            metas[index] = old.copy(lastModified = System.currentTimeMillis())
            saveIndex(metas)
        }
    }

    override suspend fun deleteTimetable(id: String) = withContext(Dispatchers.IO) {
        getDataFile(id).delete()
        val currentList = getAllTimetables().toMutableList()
        currentList.removeAll { it.id == id }
        saveIndex(currentList)
    }
}
