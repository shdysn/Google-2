package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.TimetableData
import com.example.ui.TimetableViewModel
import com.example.ui.components.AddItemDialog
import com.example.ui.components.RemovableChip
import com.example.ui.components.SectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    viewModel: TimetableViewModel,
    data: TimetableData
) {
    var schoolName by remember(data.schoolName) { mutableStateOf(data.schoolName) }
    var sessionName by remember(data.academicSession) { mutableStateOf(data.academicSession) }

    var showAddClassDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var selectedSubjectForSchedule by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "School & Session Info",
                        subtitle = "Basic information shown on printouts and header",
                        icon = Icons.Default.Info
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = schoolName,
                        onValueChange = {
                            schoolName = it
                            viewModel.updateSchoolInfo(it, sessionName, data.totalPeriods)
                        },
                        label = { Text("School / College Name") },
                        enabled = !data.isMasterLocked,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = sessionName,
                        onValueChange = {
                            sessionName = it
                            viewModel.updateSchoolInfo(schoolName, it, data.totalPeriods)
                        },
                        label = { Text("Academic Session") },
                        enabled = !data.isMasterLocked,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Periods Daily",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${data.totalPeriods} Periods per day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (data.totalPeriods > 1 && !data.isMasterLocked) {
                                        viewModel.updateSchoolInfo(schoolName, sessionName, data.totalPeriods - 1)
                                    }
                                },
                                enabled = !data.isMasterLocked && data.totalPeriods > 1
                            ) {
                                Text("-", style = MaterialTheme.typography.titleLarge)
                            }
                            Text(
                                text = "${data.totalPeriods}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    if (data.totalPeriods < 15 && !data.isMasterLocked) {
                                        viewModel.updateSchoolInfo(schoolName, sessionName, data.totalPeriods + 1)
                                    }
                                },
                                enabled = !data.isMasterLocked && data.totalPeriods < 15
                            ) {
                                Text("+", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }

        // Classes Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Classes (${data.classes.size})",
                        subtitle = "Add classes or sections taught at school",
                        icon = Icons.Default.Place,
                        actionButton = {
                            if (!data.isMasterLocked) {
                                FilledTonalButton(
                                    onClick = { showAddClassDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        data.classes.forEach { className ->
                            RemovableChip(
                                text = className,
                                onDelete = { viewModel.removeClass(className) },
                                isLocked = data.isMasterLocked
                            )
                        }
                    }
                }
            }
        }

        // Subjects Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Subjects (${data.subjects.size})",
                        subtitle = "Tap subject chip to set day schedule (e.g. Mon-Wed)",
                        icon = Icons.Default.Star,
                        actionButton = {
                            if (!data.isMasterLocked) {
                                FilledTonalButton(
                                    onClick = { showAddSubjectDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        data.subjects.forEach { subjectName ->
                            val sched = data.subjectSchedules[subjectName]
                            RemovableChip(
                                text = subjectName,
                                subText = sched,
                                onClick = {
                                    if (!data.isMasterLocked) {
                                        selectedSubjectForSchedule = subjectName
                                    }
                                },
                                onDelete = { viewModel.removeSubject(subjectName) },
                                isLocked = data.isMasterLocked
                            )
                        }
                    }
                }
            }
        }

        // Teachers Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Teachers (${data.teachers.size})",
                        subtitle = "Staff members who are assigned teaching periods",
                        icon = Icons.Default.Person,
                        actionButton = {
                            if (!data.isMasterLocked) {
                                FilledTonalButton(
                                    onClick = { showAddTeacherDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        data.teachers.forEach { teacherName ->
                            RemovableChip(
                                text = teacherName,
                                onDelete = { viewModel.removeTeacher(teacherName) },
                                isLocked = data.isMasterLocked
                            )
                        }
                    }
                }
            }
        }

        // Class Incharges Section
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Class Incharges",
                        subtitle = "Assign incharge teacher for each classroom",
                        icon = Icons.Default.AccountBox
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    data.classes.forEach { className ->
                        val currentIncharge = data.incharges[className] ?: "None"
                        var expanded by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = className,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Box {
                                OutlinedButton(
                                    onClick = { if (!data.isMasterLocked) expanded = true },
                                    enabled = !data.isMasterLocked,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(currentIncharge)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("None") },
                                        onClick = {
                                            viewModel.setClassIncharge(className, "None")
                                            expanded = false
                                        }
                                    )
                                    data.teachers.forEach { teacher ->
                                        DropdownMenuItem(
                                            text = { Text(teacher) },
                                            onClick = {
                                                viewModel.setClassIncharge(className, teacher)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }

    if (showAddClassDialog) {
        AddItemDialog(
            title = "Add New Class",
            placeholder = "e.g. Class 11",
            onDismiss = { showAddClassDialog = false },
            onConfirm = {
                viewModel.addClass(it)
                showAddClassDialog = false
            }
        )
    }

    if (showAddSubjectDialog) {
        AddItemDialog(
            title = "Add New Subject",
            placeholder = "e.g. General Science",
            onDismiss = { showAddSubjectDialog = false },
            onConfirm = {
                viewModel.addSubject(it)
                showAddSubjectDialog = false
            }
        )
    }

    if (showAddTeacherDialog) {
        AddItemDialog(
            title = "Add New Teacher",
            placeholder = "e.g. M. Usman",
            onDismiss = { showAddTeacherDialog = false },
            onConfirm = {
                viewModel.addTeacher(it)
                showAddTeacherDialog = false
            }
        )
    }

    selectedSubjectForSchedule?.let { subject ->
        val currentSched = data.subjectSchedules[subject] ?: "All Days"
        val scheduleOptions = listOf("All Days", "Mon - Wed", "Thu - Sat", "Mon, Wed, Fri", "Tue, Thu, Sat")

        AlertDialog(
            onDismissRequest = { selectedSubjectForSchedule = null },
            title = { Text("Day Schedule for $subject") },
            text = {
                Column {
                    Text("Select which days this subject is taught:")
                    Spacer(modifier = Modifier.height(12.dp))
                    scheduleOptions.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentSched == opt),
                                onClick = {
                                    viewModel.setSubjectSchedule(subject, opt)
                                    selectedSubjectForSchedule = null
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(opt)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedSubjectForSchedule = null }) {
                    Text("Close")
                }
            }
        )
    }
}
