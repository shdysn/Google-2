package com.example.data

import android.content.Context
import com.example.model.Duty
import com.example.model.TimetableData
import org.json.JSONArray
import org.json.JSONObject

class TimetableRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("smart_timetable_prefs", Context.MODE_PRIVATE)
    private val keyData = "timetable_data_json"

    fun loadData(): TimetableData {
        val jsonStr = prefs.getString(keyData, null) ?: return getSeedData()
        return try {
            deserializeJson(jsonStr)
        } catch (e: Exception) {
            getSeedData()
        }
    }

    fun saveData(data: TimetableData) {
        try {
            val jsonStr = serializeJson(data)
            prefs.edit().putString(keyData, jsonStr).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun serializeJson(data: TimetableData): String {
        val root = JSONObject()
        root.put("schoolName", data.schoolName)
        root.put("academicSession", data.academicSession)
        root.put("totalPeriods", data.totalPeriods)
        root.put("isMasterLocked", data.isMasterLocked)
        root.put("lockPin", data.lockPin)

        val classesArr = JSONArray()
        data.classes.forEach { classesArr.put(it) }
        root.put("classes", classesArr)

        val subjectsArr = JSONArray()
        data.subjects.forEach { subjectsArr.put(it) }
        root.put("subjects", subjectsArr)

        val teachersArr = JSONArray()
        data.teachers.forEach { teachersArr.put(it) }
        root.put("teachers", teachersArr)

        val inchargesObj = JSONObject()
        data.incharges.forEach { (k, v) -> inchargesObj.put(k, v) }
        root.put("incharges", inchargesObj)

        val schedulesObj = JSONObject()
        data.subjectSchedules.forEach { (k, v) -> schedulesObj.put(k, v) }
        root.put("subjectSchedules", schedulesObj)

        val rosterObj = JSONObject()
        data.teacherRoster.forEach { (teacher, duties) ->
            val dutyArr = JSONArray()
            duties.forEach { duty ->
                val d = JSONObject()
                d.put("class", duty.className)
                d.put("subject", duty.subject)
                d.put("role", duty.role)
                dutyArr.put(d)
            }
            rosterObj.put(teacher, dutyArr)
        }
        root.put("teacherRoster", rosterObj)

        val leavesObj = JSONObject()
        data.temporaryLeaves.forEach { (date, teachers) ->
            val arr = JSONArray()
            teachers.forEach { arr.put(it) }
            leavesObj.put(date, arr)
        }
        root.put("temporaryLeaves", leavesObj)

        val subsObj = JSONObject()
        data.temporarySubstitutes.forEach { (date, teacherMap) ->
            val tmObj = JSONObject()
            teacherMap.forEach { (teacher, pMap) ->
                val pObj = JSONObject()
                pMap.forEach { (p, sub) -> pObj.put(p, sub) }
                tmObj.put(teacher, pObj)
            }
            subsObj.put(date, tmObj)
        }
        root.put("temporarySubstitutes", subsObj)

        return root.toString()
    }

    private fun deserializeJson(jsonStr: String): TimetableData {
        val root = JSONObject(jsonStr)
        val schoolName = root.optString("schoolName", "GHSS BAMA BALA")
        val academicSession = root.optString("academicSession", "Session: 2026 - 2027")
        val totalPeriods = root.optInt("totalPeriods", 8)
        val isMasterLocked = root.optBoolean("isMasterLocked", false)
        val lockPin = root.optString("lockPin", "1234")

        val classesList = mutableListOf<String>()
        val classesArr = root.optJSONArray("classes")
        if (classesArr != null) {
            for (i in 0 until classesArr.length()) classesList.add(classesArr.getString(i))
        }

        val subjectsList = mutableListOf<String>()
        val subjectsArr = root.optJSONArray("subjects")
        if (subjectsArr != null) {
            for (i in 0 until subjectsArr.length()) subjectsList.add(subjectsArr.getString(i))
        }

        val teachersList = mutableListOf<String>()
        val teachersArr = root.optJSONArray("teachers")
        if (teachersArr != null) {
            for (i in 0 until teachersArr.length()) teachersList.add(teachersArr.getString(i))
        }

        val inchargesMap = mutableMapOf<String, String>()
        val inchargesObj = root.optJSONObject("incharges")
        inchargesObj?.keys()?.forEach { k ->
            inchargesMap[k] = inchargesObj.getString(k)
        }

        val schedulesMap = mutableMapOf<String, String>()
        val schedulesObj = root.optJSONObject("subjectSchedules")
        schedulesObj?.keys()?.forEach { k ->
            schedulesMap[k] = schedulesObj.getString(k)
        }

        val rosterMap = mutableMapOf<String, List<Duty>>()
        val rosterObj = root.optJSONObject("teacherRoster")
        rosterObj?.keys()?.forEach { teacher ->
            val dutyArr = rosterObj.getJSONArray(teacher)
            val dList = mutableListOf<Duty>()
            for (i in 0 until dutyArr.length()) {
                val dObj = dutyArr.getJSONObject(i)
                dList.add(
                    Duty(
                        className = dObj.optString("class", "None"),
                        subject = dObj.optString("subject", "None"),
                        role = dObj.optString("role", "primary")
                    )
                )
            }
            rosterMap[teacher] = dList
        }

        val leavesMap = mutableMapOf<String, List<String>>()
        val leavesObj = root.optJSONObject("temporaryLeaves")
        leavesObj?.keys()?.forEach { date ->
            val arr = leavesObj.getJSONArray(date)
            val lList = mutableListOf<String>()
            for (i in 0 until arr.length()) lList.add(arr.getString(i))
            leavesMap[date] = lList
        }

        val subsMap = mutableMapOf<String, Map<String, Map<String, String>>>()
        val subsObj = root.optJSONObject("temporarySubstitutes")
        subsObj?.keys()?.forEach { date ->
            val tmObj = subsObj.getJSONObject(date)
            val innerTeacherMap = mutableMapOf<String, Map<String, String>>()
            tmObj.keys().forEach { t ->
                val pObj = tmObj.getJSONObject(t)
                val pMap = mutableMapOf<String, String>()
                pObj.keys().forEach { p -> pMap[p] = pObj.getString(p) }
                innerTeacherMap[t] = pMap
            }
            subsMap[date] = innerTeacherMap
        }

        return TimetableData(
            schoolName = schoolName,
            academicSession = academicSession,
            totalPeriods = totalPeriods,
            classes = if (classesList.isEmpty()) listOf("Class 6", "Class 7", "Class 8", "Class 9", "Class 10") else classesList,
            subjects = if (subjectsList.isEmpty()) listOf("Math", "English", "Urdu", "Science") else subjectsList,
            teachers = if (teachersList.isEmpty()) listOf("M. Ahmad", "Hassan Tariq", "Mansoor Ahmad", "Shahid Yasin") else teachersList,
            incharges = inchargesMap,
            teacherRoster = rosterMap,
            subjectSchedules = schedulesMap,
            isMasterLocked = isMasterLocked,
            lockPin = lockPin,
            temporaryLeaves = leavesMap,
            temporarySubstitutes = subsMap
        )
    }

    private fun getSeedData(): TimetableData {
        val teachers = listOf(
            "M. Ahmad", "Hassan Tariq", "Mansoor Ahmad", "Shahid Yasin",
            "Shafqat Rasool", "Ishq e Rasool", "Ahsan Saleem"
        )
        val roster = mutableMapOf<String, List<Duty>>()

        roster["M. Ahmad"] = listOf(
            Duty("None", "None"),
            Duty("Class 9", "Physics"),
            Duty("Class 10", "Physics"),
            Duty("Class 8", "Science"),
            Duty("None", "None"),
            Duty("Class 7", "Science"),
            Duty("Class 6", "Math"),
            Duty("None", "None")
        )

        roster["Hassan Tariq"] = listOf(
            Duty("Class 10", "Math"),
            Duty("Class 9", "Math"),
            Duty("Class 8", "Math"),
            Duty("None", "None"),
            Duty("Class 7", "Math"),
            Duty("None", "None"),
            Duty("Class 6", "Science"),
            Duty("None", "None")
        )

        roster["Mansoor Ahmad"] = listOf(
            Duty("Class 9", "Chemistry"),
            Duty("Class 10", "Chemistry"),
            Duty("None", "None"),
            Duty("Class 9", "Biology"),
            Duty("Class 10", "Biology"),
            Duty("None", "None"),
            Duty("Class 8", "English"),
            Duty("None", "None")
        )

        roster["Shahid Yasin"] = listOf(
            Duty("Class 8", "English"),
            Duty("None", "None"),
            Duty("Class 7", "English"),
            Duty("Class 6", "English"),
            Duty("Class 9", "English"),
            Duty("Class 10", "English"),
            Duty("None", "None"),
            Duty("None", "None")
        )

        roster["Shafqat Rasool"] = listOf(
            Duty("Class 6", "Urdu"),
            Duty("Class 7", "Urdu"),
            Duty("Class 8", "Urdu"),
            Duty("None", "None"),
            Duty("Class 9", "Urdu"),
            Duty("Class 10", "Urdu"),
            Duty("None", "None"),
            Duty("None", "None")
        )

        roster["Ishq e Rasool"] = listOf(
            Duty("None", "None"),
            Duty("Class 6", "Islamiat"),
            Duty("Class 9", "Islamiat"),
            Duty("Class 10", "Islamiat"),
            Duty("Class 8", "Islamiat"),
            Duty("Class 7", "Islamiat"),
            Duty("Class 6", "Arabic"),
            Duty("None", "None")
        )

        roster["Ahsan Saleem"] = listOf(
            Duty("Class 7", "Pak Studies"),
            Duty("Class 8", "Geography"),
            Duty("None", "None"),
            Duty("Class 7", "Arabic"),
            Duty("None", "None"),
            Duty("Class 9", "Pak Studies"),
            Duty("Class 10", "Pak Studies"),
            Duty("None", "None")
        )

        return TimetableData(
            schoolName = "GHSS BAMA BALA",
            academicSession = "Session: 2026 - 2027",
            totalPeriods = 8,
            classes = listOf("Class 6", "Class 7", "Class 8", "Class 9", "Class 10"),
            subjects = listOf(
                "Math", "English", "Urdu", "Science", "Chemistry", "Physics",
                "Biology", "Computer Sci", "Islamiat", "Pak Studies", "Geography",
                "Arabic", "THQ", "Sports"
            ),
            teachers = teachers,
            incharges = mapOf(
                "Class 6" to "Shafqat Rasool",
                "Class 7" to "Ahsan Saleem",
                "Class 8" to "Ishq e Rasool",
                "Class 9" to "Mansoor Ahmad",
                "Class 10" to "Shahid Yasin"
            ),
            teacherRoster = roster,
            subjectSchedules = mapOf(
                "Chemistry" to "Mon - Wed",
                "Physics" to "Thu - Sat",
                "Biology" to "Mon - Wed",
                "Computer Sci" to "Thu - Sat"
            )
        )
    }
}
