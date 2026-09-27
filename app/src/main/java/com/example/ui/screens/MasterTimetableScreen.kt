package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TimetableData
import com.example.ui.TimetableViewModel
import com.example.util.PrintHelper

@Composable
fun MasterTimetableScreen(
    viewModel: TimetableViewModel,
    data: TimetableData
) {
    val context = LocalContext.current
    var selectedView by remember { mutableStateOf(0) } // 0: Class Grid, 1: Teacher Grid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = selectedView == 0,
                    onClick = { selectedView = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Classes")
                }
                SegmentedButton(
                    selected = selectedView == 1,
                    onClick = { selectedView = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Teachers")
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = { PrintHelper.printMasterTimetable(context, data) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Print / PDF")
            }
        }

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
        ) {
            if (selectedView == 0) {
                // Class-wise Table
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Header Row
                    item {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            GridCell(
                                text = "Class & Incharge",
                                isHeader = true,
                                width = 130.dp
                            )
                            for (p in 1..data.totalPeriods) {
                                GridCell(
                                    text = "Period $p",
                                    isHeader = true,
                                    width = 110.dp
                                )
                            }
                        }
                    }

                    // Class Rows
                    items(data.classes) { className ->
                        val incharge = data.incharges[className] ?: "None"
                        Row(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            // Class Name Cell
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = className,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Inc: $incharge",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Period Cells
                            for (p in 0 until data.totalPeriods) {
                                val assignedTeachers = data.teachers.filter { t ->
                                    data.teacherRoster[t]?.getOrNull(p)?.className == className
                                }

                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (assignedTeachers.isEmpty()) {
                                        Text("-", color = MaterialTheme.colorScheme.outlineVariant)
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            assignedTeachers.forEach { teacher ->
                                                val subject = data.teacherRoster[teacher]?.getOrNull(p)?.subject ?: ""
                                                val schedule = data.subjectSchedules[subject] ?: ""

                                                Text(
                                                    text = teacher,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = subject,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 10.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                if (schedule.isNotEmpty()) {
                                                    Text(
                                                        text = "($schedule)",
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontSize = 9.sp,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Teacher-wise Table
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            GridCell(
                                text = "Teacher",
                                isHeader = true,
                                width = 130.dp
                            )
                            for (p in 1..data.totalPeriods) {
                                GridCell(
                                    text = "Period $p",
                                    isHeader = true,
                                    width = 110.dp
                                )
                            }
                        }
                    }

                    items(data.teachers) { teacher ->
                        val duties = data.teacherRoster[teacher] ?: List(data.totalPeriods) { com.example.model.Duty() }
                        Row(
                            modifier = Modifier
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            // Teacher Cell
                            Box(
                                modifier = Modifier
                                    .width(130.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = teacher,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }

                            for (p in 0 until data.totalPeriods) {
                                val duty = duties.getOrNull(p)
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (duty == null || duty.className == "None") {
                                        Text("Free", color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                    } else {
                                        val sched = data.subjectSchedules[duty.subject] ?: ""
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = duty.className,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = duty.subject,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp
                                            )
                                            if (sched.isNotEmpty()) {
                                                Text(
                                                    text = "($sched)",
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridCell(
    text: String,
    isHeader: Boolean = false,
    width: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isHeader) 12.sp else 11.sp,
            textAlign = TextAlign.Center,
            color = if (isHeader) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
