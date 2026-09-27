package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.TimetableViewModel
import com.example.ui.components.LockStatusBanner
import com.example.ui.screens.AssignDutiesScreen
import com.example.ui.screens.MasterTimetableScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SubstituteScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TimetableViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                TimetableApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableApp(viewModel: TimetableViewModel) {
    val data by viewModel.data.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != 0) {
        viewModel.setSelectedTab(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = data.schoolName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = data.academicSession,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Lock / Unlock button
                    IconButton(
                        onClick = {
                            if (data.isMasterLocked) {
                                pinInput = ""
                                pinError = false
                                showPinDialog = true
                            } else {
                                viewModel.toggleLock("")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (data.isMasterLocked) Icons.Default.Lock else Icons.Default.Check,
                            contentDescription = if (data.isMasterLocked) "Unlock" else "Lock",
                            tint = if (data.isMasterLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Setup") },
                    label = { Text("Setup") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Duties") },
                    label = { Text("Duties") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    icon = { Icon(Icons.Default.Menu, contentDescription = "Master") },
                    label = { Text("Master") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.setSelectedTab(3) },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Substitutes") },
                    label = { Text("Daily") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LockStatusBanner(
                isLocked = data.isMasterLocked,
                onToggleLock = {
                    if (data.isMasterLocked) {
                        pinInput = ""
                        pinError = false
                        showPinDialog = true
                    } else {
                        viewModel.toggleLock("")
                    }
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> SetupScreen(viewModel, data)
                    1 -> AssignDutiesScreen(viewModel, data)
                    2 -> MasterTimetableScreen(viewModel, data)
                    3 -> SubstituteScreen(viewModel, data)
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Enter PIN to Unlock") },
            text = {
                Column {
                    Text("Enter administrator PIN (Default: 1234):", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            pinInput = it
                            pinError = false
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        isError = pinError,
                        supportingText = if (pinError) {
                            { Text("Incorrect PIN. Try again.", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = viewModel.toggleLock(pinInput)
                        if (success) {
                            showPinDialog = false
                        } else {
                            pinError = true
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
