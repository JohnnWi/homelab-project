package com.homelab.app.ui.proxmox
import com.homelab.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxStorageIso
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState

private fun createCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxGuestCreateScreen(
    onNavigateBack: () -> Unit,
    onNavigateToJournal: (String) -> Unit = {},
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isoListState by viewModel.isoListState.collectAsStateWithLifecycle()
    val nextVmidState by viewModel.nextVmidState.collectAsStateWithLifecycle()
    val createResultState by viewModel.guestCreateResultState.collectAsStateWithLifecycle()
    val nodesState by viewModel.nodesState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor

    // Fetch nodes on load
    LaunchedEffect(Unit) {
        viewModel.fetchNodes()
    }

    // Form state
    var selectedNode by remember { mutableStateOf("") }
    var isQemu by remember { mutableStateOf(true) } // true = VM, false = LXC
    var vmid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var ostype by remember { mutableStateOf("l26") } // l26 for Linux 64-bit

    // Media
    var selectedStorage by remember { mutableStateOf("") }
    var selectedIso by remember { mutableStateOf<String?>(null) }
    var useTemplate by remember { mutableStateOf(false) }
    var templateId by remember { mutableStateOf("") }

    // Hardware
    var cores by remember { mutableStateOf("2") }
    var memory by remember { mutableStateOf("2048") }
    var diskSize by remember { mutableStateOf("32") }
    var networkBridge by remember { mutableStateOf("vmbr0") }

    // UI state
    var showIsoPicker by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var successUpid by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    val nodes = when (nodesState) {
        is com.homelab.app.util.UiState.Success -> (nodesState as com.homelab.app.util.UiState.Success<List<String>>).data
        else -> emptyList()
    }

    // Fetch next VMID on load
    LaunchedEffect(Unit) {
        viewModel.fetchNextVmid()
    }

    // Handle next VMID result
    LaunchedEffect(nextVmidState) {
        if (vmid.isBlank()) {
        if (nextVmidState is UiState.Success) {
            vmid = (nextVmidState as UiState.Success).data
        }
        }
    }

    // Handle ISO list result
    LaunchedEffect(isoListState) {
        // ISO list is fetched when storage changes
    }

    // Handle create result
    LaunchedEffect(createResultState) {
        when (val state = createResultState) {
            is UiState.Success -> {
                successUpid = state.data
                showSuccessDialog = true
            }
            is UiState.Error -> {
                createError = state.message
            }
            else -> {}
        }
    }

    fun fetchIsos() {
        if (selectedNode.isNotBlank() && selectedStorage.isNotBlank()) {
            viewModel.fetchIsoList(
                node = selectedNode,
                storage = selectedStorage,
                content = if (isQemu) "iso" else "vztmpl"
            )
        }
    }

    fun createGuest() {
        if (selectedNode.isBlank()) {
            createError = "Node is required"
            return
        }
        if (vmid.isBlank()) {
            createError = "VMID is required"
            return
        }
        if (name.isBlank()) {
            createError = "Name is required"
            return
        }

        val body = mutableMapOf<String, String>()

        if (isQemu) {
            // VM creation
            body["vmid"] = vmid
            body["name"] = name
            body["ostype"] = ostype
            body["cores"] = cores
            body["memory"] = memory
            body["net0"] = "e1000,bridge=$networkBridge"

            if (!useTemplate && selectedIso != null) {
                body["cdrom"] = selectedIso!!
            }
            if (useTemplate && templateId.isNotBlank()) {
                body["clone"] = templateId
            }

            body["scsihw"] = "virtio-scsi-pci"
            body["scsi0"] = "${selectedStorage.ifBlank { "local-lvm" }}:${diskSize}"
        } else {
            // LXC creation
            body["vmid"] = vmid
            body["hostname"] = name
            body["cores"] = cores
            body["memory"] = memory
            body["net0"] = "name=eth0,bridge=$networkBridge"
            body["rootfs"] = "${selectedStorage.ifBlank { "local-lvm" }}:${diskSize}"

            if (!useTemplate && selectedIso != null) {
                body["ostemplate"] = selectedIso!!
            }
            if (useTemplate && templateId.isNotBlank()) {
                body["clone"] = templateId
            }
        }

        viewModel.createGuest(
            node = selectedNode,
            isQemu = isQemu,
            body = body
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_guest_create)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToJournal(selectedNode.ifBlank { "pve" }) }) {
                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.proxmox_journal))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Guest Type Selector
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = createCardColor(isDark, serviceColor)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Guest Type", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = isQemu,
                                    onClick = { isQemu = true },
                                    label = { Text("VM (QEMU)") },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = !isQemu,
                                    onClick = { isQemu = false },
                                    label = { Text("Container (LXC)") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Section 1: Basics
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = createCardColor(isDark, serviceColor)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Basics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = selectedNode,
                                onValueChange = { selectedNode = it },
                                label = { Text("Node") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = vmid,
                                    onValueChange = { vmid = it },
                                    label = { Text("VMID") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                Button(
                                    onClick = { viewModel.fetchNextVmid() },
                                    enabled = selectedNode.isNotBlank(),
                                    modifier = Modifier.align(Alignment.Bottom)
                                ) {
                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Next ID")
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (isQemu) {
                                Spacer(Modifier.height(8.dp))
                                var osExpanded by remember { mutableStateOf(false) }
                                val osOptions = listOf(
                                    "l26" to "Linux 6.x - 2.6 Kernel",
                                    "l24" to "Linux 2.4 Kernel",
                                    "win11" to "Microsoft Windows 11",
                                    "win10" to "Microsoft Windows 10",
                                    "win8" to "Microsoft Windows 8/2012",
                                    "win7" to "Microsoft Windows 7/2008",
                                    "w2k8" to "Microsoft Windows Vista/2008",
                                    "wxp" to "Microsoft Windows XP/2003",
                                    "w2k" to "Microsoft Windows 2000",
                                    "other" to "Other"
                                )

                                ExposedDropdownMenuBox(
                                    expanded = osExpanded,
                                    onExpandedChange = { osExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = osOptions.find { it.first == ostype }?.second ?: ostype,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("OS Type") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = osExpanded) },
                                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = osExpanded,
                                        onDismissRequest = { osExpanded = false }
                                    ) {
                                        osOptions.forEach { (value, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    ostype = value
                                                    osExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Media / Template
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = createCardColor(isDark, serviceColor)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Media / Template", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = useTemplate,
                                    onCheckedChange = { useTemplate = it }
                                )
                                Text("Use Template / Clone")
                            }

                            if (useTemplate) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = templateId,
                                    onValueChange = { templateId = it },
                                    label = { Text("Template VMID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = selectedStorage,
                                    onValueChange = {
                                        selectedStorage = it
                                        selectedIso = null
                                    },
                                    label = { Text(if (isQemu) "ISO Storage" else "Template Storage") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { fetchIsos() }) {
                                            Icon(Icons.Default.Refresh, contentDescription = if (isQemu) "Load ISOs" else "Load Templates")
                                        }
                                    }
                                )

                                Spacer(Modifier.height(8.dp))

                                // ISO Picker
                                when (val state = isoListState) {
                                    is UiState.Loading -> {
                                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                                    }
                                    is UiState.Success -> {
                                        val isos = state.data
                                        if (isos.isEmpty()) {
                                            Text(
                                                if (isQemu) "No ISOs found on $selectedStorage" else "No templates found on $selectedStorage",
                                                color = Color.Gray
                                            )
                                        } else {
                                            var isoExpanded by remember { mutableStateOf(false) }
                                            ExposedDropdownMenuBox(
                                                expanded = isoExpanded,
                                                onExpandedChange = { isoExpanded = it }
                                            ) {
                                                OutlinedTextField(
                                                    value = selectedIso?.substringAfterLast("/") ?: if (isQemu) "(Select ISO)" else "(Select Template)",
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    label = { Text(if (isQemu) "ISO Image" else "Template") },
                                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isoExpanded) },
                                                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = isoExpanded,
                                                    onDismissRequest = { isoExpanded = false }
                                                ) {
                                                    isos.forEach { iso ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Column {
                                                                    Text(iso.name)
                                                                    Text(iso.formattedSize, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                                }
                                                            },
                                                            onClick = {
                                                                selectedIso = iso.volid
                                                                isoExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    is UiState.Error -> {
                                        Text(state.message, color = MaterialTheme.colorScheme.error)
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                // Section 3: Hardware
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = createCardColor(isDark, serviceColor)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Hardware", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = cores,
                                onValueChange = { cores = it },
                                label = { Text("CPU Cores") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = memory,
                                onValueChange = { memory = it },
                                label = { Text("Memory (MiB)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = diskSize,
                                onValueChange = { diskSize = it },
                                label = { Text("Disk Size (GiB)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = networkBridge,
                                onValueChange = { networkBridge = it },
                                label = { Text("Network Bridge") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                // Create Button
                item {
                    Button(
                        onClick = { createGuest() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = selectedNode.isNotBlank() && vmid.isNotBlank() && name.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create ${if (isQemu) "VM" else "Container"}", fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Loading overlay
            if (createResultState is UiState.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Creating guest...")
                        }
                    }
                }
            }
        }
    }

    // Success Dialog
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                viewModel.clearGuestCreateResult()
            },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green) },
            title = { Text("Guest Created Successfully") },
            text = { Text("Task UPID: $successUpid") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        viewModel.clearGuestCreateResult()
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Error Snackbar
    createError?.let { error ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { createError = null }) {
                    Text("Dismiss", color = Color.White)
                }
            }
        ) {
            Text(error, color = Color.White)
        }
    }
}
