package com.example.njupter.data

import com.example.njupter.ui.animation.predictiveback.PredictiveBackAnimation
import com.example.njupter.ui.animation.predictiveback.PredictiveBackExitDirection
import com.example.njupter.ui.theme.AppThemeMode
import com.example.njupter.ui.theme.CourseColorTone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTimetableRepositoryTest {

    @Test
    fun deletingCurrentTimetableSwitchesToAnotherTimetable() = runBlocking {
        val first = metadata("first")
        val second = metadata("second")
        val dataSource = FakeTimetableDataSource(
            initialMetadata = listOf(first, second),
            initialData = mapOf(
                first.id to TimetableData(listOf(course("first-course")), emptyList()),
                second.id to TimetableData(listOf(course("second-course")), emptyList())
            )
        )
        val settings = FakeSettingsRepository(lastSelectedId = first.id)
        val repository = FileTimetableRepository(dataSource, settings)
        repository.getIsInitialized().first { it }

        repository.deleteTimetable(first.id)

        assertEquals(second.id, repository.getCurrentTimetableId().value)
        assertEquals(second.name, repository.getCurrentTimetableName().value)
        assertEquals(listOf("second-course"), repository.getCourseInfos().value.map { it.id })
        assertEquals(second.id, settings.lastSelectedTimetableIdState.value)
        assertEquals(listOf(second.id), repository.getAvailableTimetables().value.map { it.id })
    }

    @Test
    fun deletingLastTimetableClearsCurrentState() = runBlocking {
        val only = metadata("only")
        val settings = FakeSettingsRepository(lastSelectedId = only.id)
        val repository = FileTimetableRepository(
            dataSource = FakeTimetableDataSource(
                initialMetadata = listOf(only),
                initialData = mapOf(
                    only.id to TimetableData(
                        courses = listOf(course("course")),
                        sessions = listOf(CourseSession("course", 1, 1, 2, listOf(1)))
                    )
                )
            ),
            settingsRepository = settings
        )
        repository.getIsInitialized().first { it }

        repository.deleteTimetable(only.id)

        assertNull(repository.getCurrentTimetableId().value)
        assertEquals("", repository.getCurrentTimetableName().value)
        assertEquals(emptyList<CourseInfo>(), repository.getCourseInfos().value)
        assertEquals(emptyList<CourseSession>(), repository.getCourseSessions().value)
        assertNull(settings.lastSelectedTimetableIdState.value)
        assertEquals(emptyList<TimetableMetadata>(), repository.getAvailableTimetables().value)
    }

    @Test
    fun deletingTimetableDiscardsItsUndoSnapshot() = runBlocking {
        val first = metadata("first")
        val second = metadata("second")
        val repository = FileTimetableRepository(
            dataSource = FakeTimetableDataSource(
                initialMetadata = listOf(first, second),
                initialData = mapOf(
                    first.id to TimetableData(listOf(course("first-course")), emptyList()),
                    second.id to TimetableData(listOf(course("second-course")), emptyList())
                )
            ),
            settingsRepository = FakeSettingsRepository(lastSelectedId = first.id)
        )
        repository.getIsInitialized().first { it }

        repository.addCourse(course("added"))
        assertTrue(repository.getUndoAvailable().value)

        repository.deleteTimetable(first.id)

        assertFalse(repository.getUndoAvailable().value)
        repository.undoLastChange()
        assertEquals(listOf("second-course"), repository.getCourseInfos().value.map { it.id })
    }

    private fun metadata(id: String) = TimetableMetadata(
        id = id,
        name = id,
        lastModified = 1L
    )

    private fun course(id: String) = CourseInfo(
        id = id,
        name = id,
        teacher = "",
        colorIndex = 0
    )
}

private class FakeTimetableDataSource(
    initialMetadata: List<TimetableMetadata>,
    initialData: Map<String, TimetableData>
) : TimetableDataSource {
    private val metadata = initialMetadata.toMutableList()
    private val data = initialData.toMutableMap()

    override suspend fun getAllTimetables(): List<TimetableMetadata> = metadata.toList()

    override suspend fun createTimetable(
        name: String,
        startDate: Long,
        totalWeeks: Int,
        showWeekends: Boolean,
        sessionTimes: List<String>
    ): TimetableMetadata = error("Not needed")

    override suspend fun updateTimetableMetadata(
        id: String,
        name: String,
        startDate: Long,
        totalWeeks: Int,
        showWeekends: Boolean,
        sessionTimes: List<String>
    ) = Unit

    override suspend fun loadTimetable(id: String): TimetableData =
        data[id] ?: TimetableData(emptyList(), emptyList())

    override suspend fun saveTimetable(id: String, data: TimetableData) {
        this.data[id] = data
    }

    override suspend fun deleteTimetable(id: String) {
        metadata.removeAll { it.id == id }
        data.remove(id)
    }
}

private class FakeSettingsRepository(lastSelectedId: String?) : SettingsRepository {
    val lastSelectedTimetableIdState = MutableStateFlow(lastSelectedId)
    private val showWeekends = MutableStateFlow(false)
    private val languageTag = MutableStateFlow("")
    private val lastWeekRecords = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val currentTimeIndicator = MutableStateFlow(true)
    private val themeMode = MutableStateFlow(AppThemeMode.SYSTEM)
    private val dynamicColor = MutableStateFlow(true)
    private val courseColorTone = MutableStateFlow(CourseColorTone.STANDARD)
    private val predictiveBackAnimation = MutableStateFlow(PredictiveBackAnimation.SCALE)
    private val predictiveBackExitDirection =
        MutableStateFlow(PredictiveBackExitDirection.FOLLOW_GESTURE)
    private val reminderLeadMinutes = MutableStateFlow(listOf(SettingsRepository.DEFAULT_REMINDER_LEAD_MINUTES))
    private val hideFromRecents = MutableStateFlow(false)

    override fun getShowWeekends() = showWeekends
    override suspend fun setShowWeekends(show: Boolean) { showWeekends.value = show }
    override fun getAppLanguageTag() = languageTag
    override suspend fun setAppLanguageTag(languageTag: String) { this.languageTag.value = languageTag }
    override fun getLastSelectedTimetableId() = lastSelectedTimetableIdState
    override suspend fun setLastSelectedTimetableId(id: String?) {
        lastSelectedTimetableIdState.value = id
    }
    override fun getLastWeekRecords() = lastWeekRecords
    override suspend fun setLastWeekForTimetable(id: String, week: Int) {
        lastWeekRecords.value = lastWeekRecords.value + (id to week)
    }
    override fun getEnableCurrentTimeIndicator() = currentTimeIndicator
    override suspend fun setEnableCurrentTimeIndicator(enabled: Boolean) {
        currentTimeIndicator.value = enabled
    }
    override fun getAppThemeMode() = themeMode
    override suspend fun setAppThemeMode(mode: AppThemeMode) { themeMode.value = mode }
    override fun getDynamicColorEnabled() = dynamicColor
    override suspend fun setDynamicColorEnabled(enabled: Boolean) { dynamicColor.value = enabled }
    override fun getCourseColorTone() = courseColorTone
    override suspend fun setCourseColorTone(tone: CourseColorTone) { courseColorTone.value = tone }
    override fun getPredictiveBackAnimation() = predictiveBackAnimation
    override suspend fun setPredictiveBackAnimation(animation: PredictiveBackAnimation) {
        predictiveBackAnimation.value = animation
    }
    override fun getPredictiveBackExitDirection() = predictiveBackExitDirection
    override suspend fun setPredictiveBackExitDirection(direction: PredictiveBackExitDirection) {
        predictiveBackExitDirection.value = direction
    }
    override fun getReminderLeadMinutes() = reminderLeadMinutes
    override suspend fun setReminderLeadMinutes(minutes: List<Int>) {
        reminderLeadMinutes.value = minutes
    }
    override fun getHideFromRecents() = hideFromRecents
    override suspend fun setHideFromRecents(enabled: Boolean) {
        hideFromRecents.value = enabled
    }
}
