package com.example.model

data class Duty(
    val className: String = "None",
    val subject: String = "None",
    val role: String = "primary"
)

data class SubstituteAssignment(
    val absentTeacher: String,
    val period: Int,
    val className: String,
    val subject: String,
    val substituteTeacher: String
)

data class TimetableData(
    val schoolName: String = "GHSS BAMA BALA",
    val academicSession: String = "Session: 2026 - 2027",
    val totalPeriods: Int = 8,
    val classes: List<String> = listOf("Class 6", "Class 7", "Class 8", "Class 9", "Class 10"),
    val subjects: List<String> = listOf(
        "Math", "English", "Urdu", "Science", "Chemistry", "Physics",
        "Biology", "Computer Sci", "Islamiat", "Pak Studies", "Geography",
        "Arabic", "THQ", "Sports"
    ),
    val teachers: List<String> = listOf(
        "M. Ahmad", "Hassan Tariq", "Mansoor Ahmad", "Shahid Yasin",
        "Shafqat Rasool", "Ishq e Rasool", "Ahsan Saleem"
    ),
    val incharges: Map<String, String> = mapOf(
        "Class 6" to "Shafqat Rasool",
        "Class 7" to "Ahsan Saleem",
        "Class 8" to "Ishq e Rasool",
        "Class 9" to "Mansoor Ahmad",
        "Class 10" to "Shahid Yasin"
    ),
    val teacherRoster: Map<String, List<Duty>> = emptyMap(),
    val subjectSchedules: Map<String, String> = mapOf(
        "Chemistry" to "Mon - Wed",
        "Physics" to "Thu - Sat",
        "Biology" to "Mon - Wed",
        "Computer Sci" to "Thu - Sat"
    ),
    val classExtraSubjects: Map<String, Map<Int, String>> = emptyMap(),
    val isMasterLocked: Boolean = false,
    val lockPin: String = "1234",
    // date -> absent teachers
    val temporaryLeaves: Map<String, List<String>> = emptyMap(),
    // date -> Map<absentTeacher, Map<periodString, substituteTeacher>>
    val temporarySubstitutes: Map<String, Map<String, Map<String, String>>> = emptyMap()
)
