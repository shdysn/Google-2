package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.TimetableRepository
import com.example.model.Duty
import com.example.model.TimetableData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimetableViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TimetableRepository(application.applicationContext)

    private val _data = MutableStateFlow(repository.loadData())
    val data: StateFlow<TimetableData> = _data.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _currentDate = MutableStateFlow(getTodayDateKey())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun setCurrentDate(date: String) {
        _currentDate.value = date
    }

    private fun updateAndPersist(transform: (TimetableData) -> TimetableData) {
        val updated = transform(_data.value)
        _data.value = updated
        viewModelScope.launch {
            repository.saveData(updated)
        }
    }

    fun updateSchoolInfo(name: String, session: String, periods: Int) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val validPeriods = periods.coerceIn(1, 15)
            // Adjust roster size if periods changed
            val newRoster = current.teacherRoster.mapValues { (_, duties) ->
                val list = duties.toMutableList()
                while (list.size < validPeriods) list.add(Duty())
                if (list.size > validPeriods) list.subList(0, validPeriods) else list
            }
            current.copy(
                schoolName = name.trim().ifEmpty { current.schoolName },
                academicSession = session.trim().ifEmpty { current.academicSession },
                totalPeriods = validPeriods,
                teacherRoster = newRoster
            )
        }
    }

    fun addClass(className: String) {
        if (_data.value.isMasterLocked) return
        val name = className.trim()
        if (name.isEmpty() || _data.value.classes.contains(name)) return
        updateAndPersist { current ->
            current.copy(classes = current.classes + name)
        }
    }

    fun removeClass(className: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            // Clear duties for this class
            val newRoster = current.teacherRoster.mapValues { (_, duties) ->
                duties.map { if (it.className == className) Duty() else it }
            }
            val newIncharges = current.incharges.filterKeys { it != className }
            current.copy(
                classes = current.classes.filter { it != className },
                teacherRoster = newRoster,
                incharges = newIncharges
            )
        }
    }

    fun addSubject(subjectName: String) {
        if (_data.value.isMasterLocked) return
        val name = subjectName.trim()
        if (name.isEmpty() || _data.value.subjects.contains(name)) return
        updateAndPersist { current ->
            current.copy(subjects = current.subjects + name)
        }
    }

    fun removeSubject(subjectName: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val newRoster = current.teacherRoster.mapValues { (_, duties) ->
                duties.map { if (it.subject == subjectName) it.copy(subject = "None") else it }
            }
            current.copy(
                subjects = current.subjects.filter { it != subjectName },
                subjectSchedules = current.subjectSchedules.filterKeys { it != subjectName },
                teacherRoster = newRoster
            )
        }
    }

    fun setSubjectSchedule(subject: String, schedule: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val map = current.subjectSchedules.toMutableMap()
            if (schedule.isEmpty() || schedule == "All Days") {
                map.remove(subject)
            } else {
                map[subject] = schedule
            }
            current.copy(subjectSchedules = map)
        }
    }

    fun addTeacher(teacherName: String) {
        if (_data.value.isMasterLocked) return
        val name = teacherName.trim()
        if (name.isEmpty() || _data.value.teachers.contains(name)) return
        updateAndPersist { current ->
            val newRoster = current.teacherRoster.toMutableMap()
            newRoster[name] = List(current.totalPeriods) { Duty() }
            current.copy(
                teachers = current.teachers + name,
                teacherRoster = newRoster
            )
        }
    }

    fun removeTeacher(teacherName: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val newRoster = current.teacherRoster.toMutableMap().apply { remove(teacherName) }
            val newIncharges = current.incharges.mapValues { (_, v) -> if (v == teacherName) "None" else v }
            current.copy(
                teachers = current.teachers.filter { it != teacherName },
                teacherRoster = newRoster,
                incharges = newIncharges
            )
        }
    }

    fun setClassIncharge(className: String, teacherName: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val map = current.incharges.toMutableMap()
            if (teacherName == "None") map.remove(className) else map[className] = teacherName
            current.copy(incharges = map)
        }
    }

    fun assignDuty(teacherName: String, period: Int, className: String, subject: String) {
        if (_data.value.isMasterLocked) return
        updateAndPersist { current ->
            val roster = current.teacherRoster.toMutableMap()
            val duties = (roster[teacherName] ?: List(current.totalPeriods) { Duty() }).toMutableList()

            while (duties.size <= period) duties.add(Duty())
            duties[period] = Duty(className = className, subject = subject)
            roster[teacherName] = duties

            current.copy(teacherRoster = roster)
        }
    }

    fun toggleLock(pin: String): Boolean {
        val current = _data.value
        if (current.isMasterLocked) {
            // Unlocking requires PIN
            if (pin == current.lockPin) {
                updateAndPersist { it.copy(isMasterLocked = false) }
                return true
            }
            return false
        } else {
            // Locking
            updateAndPersist { it.copy(isMasterLocked = true) }
            return true
        }
    }

    fun updatePin(oldPin: String, newPin: String): Boolean {
        val current = _data.value
        if (oldPin == current.lockPin && newPin.length in 4..8) {
            updateAndPersist { it.copy(lockPin = newPin) }
            return true
        }
        return false
    }

    // Temporary / Substitute features
    fun toggleTeacherLeave(dateKey: String, teacherName: String) {
        updateAndPersist { current ->
            val leavesMap = current.temporaryLeaves.toMutableMap()
            val currentLeaves = (leavesMap[dateKey] ?: emptyList()).toMutableList()
            if (currentLeaves.contains(teacherName)) {
                currentLeaves.remove(teacherName)
            } else {
                currentLeaves.add(teacherName)
            }
            leavesMap[dateKey] = currentLeaves
            current.copy(temporaryLeaves = leavesMap)
        }
    }

    fun setSubstitute(dateKey: String, absentTeacher: String, period: Int, substituteTeacher: String) {
        updateAndPersist { current ->
            val subs = current.temporarySubstitutes.toMutableMap()
            val dateSubs = (subs[dateKey] ?: emptyMap()).toMutableMap()
            val teacherSubs = (dateSubs[absentTeacher] ?: emptyMap()).toMutableMap()

            if (substituteTeacher.isEmpty() || substituteTeacher == "None") {
                teacherSubs.remove(period.toString())
            } else {
                teacherSubs[period.toString()] = substituteTeacher
            }

            dateSubs[absentTeacher] = teacherSubs
            subs[dateKey] = dateSubs
            current.copy(temporarySubstitutes = subs)
        }
    }

    fun getFreeTeachers(period: Int, dateKey: String): List<String> {
        val current = _data.value
        val absentList = current.temporaryLeaves[dateKey] ?: emptyList()
        val subsForDate = current.temporarySubstitutes[dateKey] ?: emptyMap()

        // Teachers assigned as substitute in this period
        val alreadySubbing = subsForDate.values.mapNotNull { it[period.toString()] }

        return current.teachers.filter { teacher ->
            val isAbsent = absentList.contains(teacher)
            val regularDuty = current.teacherRoster[teacher]?.getOrNull(period)
            val isBusyInRegularClass = regularDuty != null && regularDuty.className != "None"
            val isBusyAsSub = alreadySubbing.contains(teacher)

            !isAbsent && !isBusyInRegularClass && !isBusyAsSub
        }
    }

    companion object {
        fun getTodayDateKey(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
