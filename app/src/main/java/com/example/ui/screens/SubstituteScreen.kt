package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TimetableData
import com.example.ui.TimetableViewModel
import com.example.ui.components.SectionHeader
import com.example.util.PrintHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubstituteScreen(
    viewModel: TimetableViewModel,
    data: TimetableData
) {
    val context = LocalContext.current
    val currentDate by viewModel.currentDate.collectAsState()
    val absentTeachers = data.temporaryLeaves[currentDate] ?: emptyList()
    val dateSubs = data.temporarySubstitutes[currentDate] ?: emptyMap()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date & Print Bar
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Date: $currentDate",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Daily Temporary Substitutes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            PrintHelper.printSubstituteTimetable(
                                context = context,
                                data = data,
                                dateKey = currentDate,
                                absentTeachers = absentTeachers,
                                substitutes = dateSubs
                            )
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print Sheet")
                    }
                }
            }
        }

        // Absent Teachers Selection
        item {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(
                        title = "Mark Absent Teachers (${absentTeachers.size})",
                        subtitle = "Tap a teacher to mark them on leave today",
                        icon = Icons.Default.Person
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        data.teachers.forEach { teacher ->
                            val isAbsent = absentTeachers.contains(teacher)
                            FilterChip(
                                selected = isAbsent,
                                onClick = { viewModel.toggleTeacherLeave(currentDate, teacher) },
                                label = { Text(teacher) },
                                leadingIcon = {
                                    if (isAbsent) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Periods Needing Substitutes
        item {
            Text(
                text = "Substitute Assignments",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (absentTeachers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No teachers marked on leave today. All staff are present!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Find all affected duties
            val affectedDuties = mutableListOf<Triple<String, Int, com.example.model.Duty>>()
            absentTeachers.forEach { absentTeacher ->
                for (p in 0 until data.totalPeriods) {
                    val duty = data.teacherRoster[absentTeacher]?.getOrNull(p)
                    if (duty != null && duty.className != "None") {
                        affectedDuties.add(Triple(absentTeacher, p, duty))
                    }
                }
            }

            if (affectedDuties.isEmpty()) {
                item {
                    Text(
                        text = "Absent teachers had no scheduled classes today.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(affectedDuties) { (absentTeacher, period, duty) ->
                    val currentSub = dateSubs[absentTeacher]?.get(period.toString()) ?: "Unassigned"
                    val freeTeachers = viewModel.getFreeTeachers(period, currentDate)

                    ElevatedCard(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Period ${period + 1}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${duty.className} - ${duty.subject}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Text(
                                    text = "$absentTeacher (Leave)",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Substitute: ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                var subMenuExpanded by remember { mutableStateOf(false) }

                                Box {
                                    FilledTonalButton(
                                        onClick = { subMenuExpanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (currentSub == "Unassigned") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(currentSub)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }

                                    DropdownMenu(
                                        expanded = subMenuExpanded,
                                        onDismissRequest = { subMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Unassigned") },
                                            onClick = {
                                                viewModel.setSubstitute(currentDate, absentTeacher, period, "None")
                                                subMenuExpanded = false
                                            }
                                        )
                                        // Available free teachers
                                        if (freeTeachers.isEmpty()) {
                                            DropdownMenuItem(
                                                text = { Text("No Free Teachers in this period", color = MaterialTheme.colorScheme.outline) },
                                                onClick = { subMenuExpanded = false }
                                            )
                                        } else {
                                            freeTeachers.forEach { freeTeacher ->
                                                DropdownMenuItem(
                                                    text = { Text("$freeTeacher (Free)") },
                                                    onClick = {
                                                        viewModel.setSubstitute(currentDate, absentTeacher, period, freeTeacher)
                                                        subMenuExpanded = false
                                                    }
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
