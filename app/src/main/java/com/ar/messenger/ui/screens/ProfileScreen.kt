package com.ar.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ar.messenger.ui.components.GradientAvatar
import com.ar.messenger.ui.theme.ArHeroGradient

private val quickStatuses = listOf("Available", "Busy 🔥", "At work 💻", "Sleeping 😴", "On a call 📞", "Do not disturb 🚫")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    currentName: String,
    currentStatus: String,
    onBack: () -> Unit,
    onSave: (name: String, status: String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var status by remember { mutableStateOf(currentStatus) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ArHeroGradient)
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            GradientAvatar(name = name.ifBlank { "?" }, size = 96.dp)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = status,
                onValueChange = { status = it },
                label = { Text("Status / mood") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("Quick pick:", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            FlowRowQuickStatuses(onPick = { status = it })

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { onSave(name.trim(), status.trim()) },
                enabled = name.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun FlowRowQuickStatuses(onPick: (String) -> Unit) {
    Column {
        quickStatuses.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { s ->
                    AssistChip(onClick = { onPick(s) }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
