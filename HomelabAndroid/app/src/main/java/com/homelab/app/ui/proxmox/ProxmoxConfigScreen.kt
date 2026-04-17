package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxConfigScreen(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val guestConfigState by viewModel.guestConfigState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var cores by remember { mutableStateOf("") }
    var memory by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(node, vmid, isQemu) {
        viewModel.fetchGuestConfig(node, vmid, isQemu)
    }

    val nameKey = if (isQemu) "name" else "hostname"
    val nameLabel = if (isQemu) "Name" else "Hostname"
    val nameHelp = if (isQemu) "Display name for the guest" else "Hostname for the container"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Config - ${if (isQemu) "VM" else "CT"} $vmid") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSaving = true
                            saveError = null
                            val configMap = mutableMapOf<String, String>()
                            if (name.isNotBlank()) configMap[nameKey] = name.trim()
                            if (cores.isNotBlank()) configMap["cores"] = cores.trim()
                            if (memory.isNotBlank()) configMap["memory"] = memory.trim()
                            viewModel.updateGuestConfig(
                                node = node,
                                vmid = vmid,
                                isQemu = isQemu,
                                config = configMap,
                                onSuccess = { onNavigateBack() }
                            )
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = guestConfigState) {
                is UiState.Idle,
                is UiState.Loading,
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Loading config...", color = Color.Gray)
                        }
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Error: ${state.message}", color = Color.Red)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { viewModel.fetchGuestConfig(node, vmid, isQemu) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    val rawConfig = state.data

                    // Populate fields from raw config on first load
                    LaunchedEffect(rawConfig) {
                        name = rawConfig[nameKey] ?: ""
                        cores = rawConfig["cores"] ?: ""
                        memory = rawConfig["memory"]?.let { mStr ->
                            runCatching { mStr.toInt().toString() }.getOrNull() ?: mStr
                        } ?: ""
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Name field
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(nameLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text(nameHelp) }
                        )

                        Divider()

                        // CPU field
                        Text(
                            "CPU",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = cores,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) cores = it },
                            label = { Text("Cores") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = { Text("Number of CPU cores") }
                        )

                        Divider()

                        // Memory field
                        Text(
                            "Memory",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = memory,
                            onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) memory = it },
                            label = { Text("Memory (MB)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            supportingText = { Text("RAM in megabytes") }
                        )

                        Spacer(Modifier.height(32.dp))

                        // Save button at bottom
                        Button(
                            onClick = {
                                isSaving = true
                                saveError = null
                                val configMap = mutableMapOf<String, String>()
                                if (name.isNotBlank()) configMap[nameKey] = name.trim()
                                if (cores.isNotBlank()) configMap["cores"] = cores.trim()
                                if (memory.isNotBlank()) configMap["memory"] = memory.trim()
                                viewModel.updateGuestConfig(
                                    node = node,
                                    vmid = vmid,
                                    isQemu = isQemu,
                                    config = configMap,
                                    onSuccess = { onNavigateBack() }
                                )
                            },
                            enabled = !isSaving && (name.isNotBlank() || cores.isNotBlank() || memory.isNotBlank()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save Changes")
                        }

                        // Error display
                        saveError?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.1f)
                                )
                            ) {
                                Text(
                                    error,
                                    modifier = Modifier.padding(12.dp),
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Observe config state changes for error handling after save
    LaunchedEffect(guestConfigState) {
        if (guestConfigState is UiState.Error) {
            isSaving = false
            saveError = (guestConfigState as UiState.Error).message
        }
    }
}
