package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Duty
import com.example.model.TimetableData
import com.example.ui.TimetableViewModel

@Composable
fun AssignDutiesScreen(
    viewModel: TimetableViewModel,
    data: TimetableData
) {
    var viewMode by remember { mutableStateOf(0) } // 0: By Teacher, 1: By Class
    var selectedTeacher by remember(data.teachers) { mutableStateOf(data.teachers.firstOrNull() ?: "") }
    var selectedClass by remember(data.classes) { mutableStateOf(data.classes.firstOrNull() ?: "") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Mode Switch
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            SegmentedButton(
                selected = viewMode == 0,
                onClick = { viewMode = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("By Teacher")
            }
            SegmentedButton(
                selected = viewMode == 1,
                onClick = { viewMode = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("By Class")
            }
        }

        if (viewMode == 0) {
            // Teacher Selector Horizontal Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data.teachers.forEach { teacher ->
                    val load = data.teacherRoster[teacher]?.count { it.className != "None" } ?: 0
                    FilterChip(
                        selected = teacher == selectedTeacher,
                        onClick = { selectedTeacher = teacher },
                        label = {
                            Text("$teacher ($load/${data.totalPeriods})")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTeacher.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Add teachers in Setup first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val duties = data.teacherRoster[selectedTeacher] ?: List(data.totalPeriods) { Duty() }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(data.totalPeriods) { period ->
                        val duty = duties.getOrNull(period) ?: Duty()
                        val isAssigned = duty.className != "None"

                        PeriodDutyCard(
                            periodNumber = period + 1,
                            isAssigned = isAssigned,
                            currentClass = duty.className,
                            currentSubject = duty.subject,
                            classes = listOf("None") + data.classes,
                            subjects = listOf("None") + data.subjects,
                            isLocked = data.isMasterLocked,
                            onClassSelected = { newClass ->
                                viewModel.assignDuty(
                                    teacherName = selectedTeacher,
                                    period = period,
                                    className = newClass,
                                    subject = if (newClass == "None") "None" else duty.subject
                                )
                            },
                            onSubjectSelected = { newSubject ->
                                viewModel.assignDuty(
                                    teacherName = selectedTeacher,
                                    period = period,
                                    className = duty.className,
                                    subject = newSubject
                                )
                            },
                            onClear = {
                                viewModel.assignDuty(
                                    teacherName = selectedTeacher,
                                    period = period,
                                    className = "None",
                                    subject = "None"
                                )
                            }
                        )
                    }
                }
            }
        } else {
            // Class View
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data.classes.forEach { className ->
                    FilterChip(
                        selected = className == selectedClass,
                        onClick = { selectedClass = className },
                        label = { Text(className) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedClass.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Add classes in Setup first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(data.totalPeriods) { period ->
                        val assignedTeachers = data.teachers.filter { t ->
                            data.teacherRoster[t]?.getOrNull(period)?.className == selectedClass
                        }
                        val assignedTeacher = assignedTeachers.firstOrNull() ?: "None"
                        val assignedSubject = if (assignedTeacher != "None") {
                            data.teacherRoster[assignedTeacher]?.getOrNull(period)?.subject ?: "None"
                        } else "None"

                        ClassPeriodCard(
                            periodNumber = period + 1,
                            currentTeacher = assignedTeacher,
                            currentSubject = assignedSubject,
                            teachers = listOf("None") + data.teachers,
                            subjects = listOf("None") + data.subjects,
                            isLocked = data.isMasterLocked,
                            onTeacherSelected = { newTeacher ->
                                if (assignedTeacher != "None") {
                                    viewModel.assignDuty(assignedTeacher, period, "None", "None")
                                }
                                if (newTeacher != "None") {
                                    viewModel.assignDuty(newTeacher, period, selectedClass, assignedSubject)
                                }
                            },
                            onSubjectSelected = { newSubject ->
                                if (assignedTeacher != "None") {
                                    viewModel.assignDuty(assignedTeacher, period, selectedClass, newSubject)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodDutyCard(
    periodNumber: Int,
    isAssigned: Boolean,
    currentClass: String,
    currentSubject: String,
    classes: List<String>,
    subjects: List<String>,
    isLocked: Boolean,
    onClassSelected: (String) -> Unit,
    onSubjectSelected: (String) -> Unit,
    onClear: () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isAssigned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAssigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Period $periodNumber",
                            color = if (isAssigned) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAssigned) "Assigned" else "Free Period",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAssigned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAssigned && !isLocked) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear duty",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Class Dropdown
                var classExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!isLocked) classExpanded = true },
                        enabled = !isLocked,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentClass == "None") "Select Class" else currentClass,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = classExpanded,
                        onDismissRequest = { classExpanded = false }
                    ) {
                        classes.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    onClassSelected(c)
                                    classExpanded = false
                                }
                            )
                        }
                    }
                }

                // Subject Dropdown
                var subjectExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!isLocked && currentClass != "None") subjectExpanded = true },
                        enabled = !isLocked && currentClass != "None",
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentSubject == "None") "Select Subject" else currentSubject,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    onSubjectSelected(s)
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassPeriodCard(
    periodNumber: Int,
    currentTeacher: String,
    currentSubject: String,
    teachers: List<String>,
    subjects: List<String>,
    isLocked: Boolean,
    onTeacherSelected: (String) -> Unit,
    onSubjectSelected: (String) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Period $periodNumber",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                var teacherExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!isLocked) teacherExpanded = true },
                        enabled = !isLocked,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentTeacher == "None") "Assign Teacher" else currentTeacher,
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = teacherExpanded,
                        onDismissRequest = { teacherExpanded = false }
                    ) {
                        teachers.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t) },
                                onClick = {
                                    onTeacherSelected(t)
                                    teacherExpanded = false
                                }
                            )
                        }
                    }
                }

                var subExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { if (!isLocked && currentTeacher != "None") subExpanded = true },
                        enabled = !isLocked && currentTeacher != "None",
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentSubject == "None") "Assign Subject" else currentSubject,
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = subExpanded,
                        onDismissRequest = { subExpanded = false }
                    ) {
                        subjects.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    onSubjectSelected(s)
                                    subExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
