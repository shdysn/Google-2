package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.model.TimetableData

object PrintHelper {

    fun printMasterTimetable(context: Context, data: TimetableData) {
        val html = buildMasterHtml(data)
        printHtml(context, "Master Timetable - ${data.schoolName}", html)
    }

    fun printSubstituteTimetable(
        context: Context,
        data: TimetableData,
        dateKey: String,
        absentTeachers: List<String>,
        substitutes: Map<String, Map<String, String>>
    ) {
        val html = buildSubstituteHtml(data, dateKey, absentTeachers, substitutes)
        printHtml(context, "Substitute Timetable - $dateKey", html)
    }

    private fun printHtml(context: Context, jobName: String, htmlContent: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printWebView = WebView(context).apply {
            settings.javaScriptEnabled = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = createPrintDocumentAdapter(jobName)
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print(jobName, printAdapter, attributes)
                }
            }
        }
        printWebView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun buildMasterHtml(data: TimetableData): String {
        val periods = data.totalPeriods
        val thCells = (1..periods).joinToString("") { "<th>Period $it</th>" }

        val classRows = data.classes.joinToString("") { className ->
            val incharge = data.incharges[className] ?: "None"
            val tdCells = (0 until periods).joinToString("") { p ->
                val assignedTeachers = data.teachers.filter { t ->
                    data.teacherRoster[t]?.getOrNull(p)?.className == className
                }
                if (assignedTeachers.isEmpty()) {
                    "<td><span style='color:#94a3b8;'>-</span></td>"
                } else {
                    val details = assignedTeachers.joinToString("<br>") { t ->
                        val sub = data.teacherRoster[t]?.getOrNull(p)?.subject ?: ""
                        val sched = data.subjectSchedules[sub] ?: ""
                        val schedSpan = if (sched.isNotEmpty()) " <small style='color:#6366f1;'>($sched)</small>" else ""
                        "<strong>$t</strong><br><span style='color:#0f766e;'>$sub</span>$schedSpan"
                    }
                    "<td>$details</td>"
                }
            }
            "<tr><td><strong>$className</strong><br><small>Incharge: $incharge</small></td>$tdCells</tr>"
        }

        val teacherRows = data.teachers.joinToString("") { teacher ->
            val tdCells = (0 until periods).joinToString("") { p ->
                val duty = data.teacherRoster[teacher]?.getOrNull(p)
                if (duty != null && duty.className != "None") {
                    val sched = data.subjectSchedules[duty.subject] ?: ""
                    val schedSpan = if (sched.isNotEmpty()) " <small style='color:#6366f1;'>($sched)</small>" else ""
                    "<td><strong>${duty.className}</strong><br><span style='color:#0f766e;'>${duty.subject}</span>$schedSpan</td>"
                } else {
                    "<td><span style='color:#94a3b8;'>Free</span></td>"
                }
            }
            "<tr><td><strong>$teacher</strong></td>$tdCells</tr>"
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>${data.schoolName}</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 15px; color: #1e293b; }
                    .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #4f46e5; padding-bottom: 10px; }
                    .header h1 { margin: 0; font-size: 22px; color: #1e1b4b; }
                    .header p { margin: 4px 0 0; font-size: 13px; color: #64748b; }
                    table { width: 100%; border-collapse: collapse; margin-bottom: 25px; font-size: 11px; }
                    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: center; }
                    th { background-color: #f1f5f9; color: #334155; font-weight: bold; }
                    tr:nth-child(even) { background-color: #f8fafc; }
                    .section-title { font-size: 14px; font-weight: bold; margin: 15px 0 8px; color: #4338ca; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>${data.schoolName}</h1>
                    <p>${data.academicSession} | Master School Timetable</p>
                </div>

                <div class="section-title">Class-Wise Master Timetable</div>
                <table>
                    <thead>
                        <tr><th>Class & Incharge</th>$thCells</tr>
                    </thead>
                    <tbody>
                        $classRows
                    </tbody>
                </table>

                <div class="section-title">Teacher-Wise Master Timetable</div>
                <table>
                    <thead>
                        <tr><th>Teacher Name</th>$thCells</tr>
                    </thead>
                    <tbody>
                        $teacherRows
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildSubstituteHtml(
        data: TimetableData,
        dateKey: String,
        absentTeachers: List<String>,
        substitutes: Map<String, Map<String, String>>
    ): String {
        val rows = mutableListOf<String>()

        absentTeachers.forEach { absentTeacher ->
            for (p in 0 until data.totalPeriods) {
                val duty = data.teacherRoster[absentTeacher]?.getOrNull(p)
                if (duty != null && duty.className != "None") {
                    val subTeacher = substitutes[absentTeacher]?.get(p.toString()) ?: "Unassigned"
                    rows.add(
                        """
                        <tr>
                            <td><strong>Period ${p + 1}</strong></td>
                            <td>${duty.className}</td>
                            <td>${duty.subject}</td>
                            <td style="color:#dc2626;font-weight:bold;">$absentTeacher (On Leave)</td>
                            <td style="color:#16a34a;font-weight:bold;">$subTeacher</td>
                        </tr>
                        """.trimIndent()
                    )
                }
            }
        }

        val tableContent = if (rows.isEmpty()) {
            "<tr><td colspan='5' style='text-align:center;color:#64748b;padding:20px;'>No absent teachers or duties for $dateKey.</td></tr>"
        } else {
            rows.joinToString("")
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <title>Substitute Timetable</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 20px; color: #1e293b; }
                    .header { text-align: center; margin-bottom: 20px; border-bottom: 2px solid #0d9488; padding-bottom: 10px; }
                    .header h1 { margin: 0; font-size: 20px; color: #0f766e; }
                    .header p { margin: 4px 0 0; font-size: 13px; color: #64748b; }
                    table { width: 100%; border-collapse: collapse; margin-top: 15px; font-size: 12px; }
                    th, td { border: 1px solid #cbd5e1; padding: 8px 10px; text-align: left; }
                    th { background-color: #f0fdfa; color: #134e4a; font-weight: bold; }
                    tr:nth-child(even) { background-color: #f8fafc; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>${data.schoolName}</h1>
                    <p>Daily Substitute / Temporary Duties | Date: <strong>$dateKey</strong></p>
                </div>
                <table>
                    <thead>
                        <tr>
                            <th>Period</th>
                            <th>Class</th>
                            <th>Subject</th>
                            <th>Absent Teacher</th>
                            <th>Assigned Substitute</th>
                        </tr>
                    </thead>
                    <tbody>
                        $tableContent
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()
    }
}
