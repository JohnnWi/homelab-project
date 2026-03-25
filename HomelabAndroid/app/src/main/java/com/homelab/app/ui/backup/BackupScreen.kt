package com.homelab.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.R
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportData by viewModel.exportDataEvent.collectAsState()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            val data = exportData
            if (data != null) {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(data)
                }
                viewModel.onExportDataConsumed()
                // Show success toast or scaffold snackbar ideally
            }
        } ?: viewModel.onExportDataConsumed()
    }

    LaunchedEffect(exportData) {
        if (exportData != null) {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("homelab_backup_$dateStr.homelab")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val input: InputStream? = context.contentResolver.openInputStream(it)
                val bytes = input?.readBytes()
                input?.close()
                if (bytes != null) {
                    viewModel.onFileSelectedForImport(bytes)
                }
            } catch (e: Exception) {
                // handle
            }
        }
    }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backupTitle)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupInfoTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backupInfoDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Export Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupExportTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            passwordInput = ""
                            passwordConfirm = ""
                            showExportPasswordDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.backupExportAction))
                    }
                }
            }

            // Import Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupImportTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backupImportDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.backupImportAction))
                    }
                }
            }
        }
    }

    // Dialogs
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            title = { Text(stringResource(R.string.backupExportTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backupPasswordDesc))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.backupPasswordPlaceholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = { Text(stringResource(R.string.backupPasswordConfirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordInput.isNotEmpty() && passwordConfirm.isNotEmpty() && passwordInput != passwordConfirm
                    )
                    if (passwordInput.isNotEmpty() && passwordInput.length < 6) {
                        Text(
                            text = stringResource(R.string.backupPasswordTooShort),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (passwordInput.isNotEmpty() && passwordConfirm.isNotEmpty() && passwordInput != passwordConfirm) {
                        Text(
                            text = stringResource(R.string.backupPasswordMismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startExport(passwordInput)
                        showExportPasswordDialog = false
                    },
                    enabled = passwordInput.length >= 6 && passwordInput == passwordConfirm
                ) {
                    Text(stringResource(R.string.backupExportAction))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    when (val state = uiState) {
        is BackupUiState.ImportPasswordRequired -> {
            var importPass by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                title = { Text(stringResource(R.string.backupImportDecrypt)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.backupImportPasswordDesc))
                        OutlinedTextField(
                            value = importPass,
                            onValueChange = { importPass = it },
                            label = { Text(stringResource(R.string.backupPasswordPlaceholder)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.decryptAndPreview(importPass) },
                        enabled = importPass.isNotBlank()
                    ) {
                        Text(stringResource(R.string.backupImportDecrypt))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::resetState) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is BackupUiState.ImportPreview -> {
            Dialog(onDismissRequest = viewModel::resetState) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.backupImportPreviewTitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val totalStr = stringResource(R.string.backupPreviewServices, state.previewInfo.totalFound)
                        Text(totalStr)

                        if (state.previewInfo.unknownCount > 0) {
                            val unknownStr = stringResource(R.string.backupPreviewUnknown, state.previewInfo.unknownCount)
                            Text(
                                text = unknownStr,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(12.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.backupPreviewWarning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = viewModel::resetState) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = viewModel::applyImport,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.backupImportApply))
                            }
                        }
                    }
                }
            }
        }
        is BackupUiState.Exporting, is BackupUiState.ImportDecrypting, is BackupUiState.ImportApplying -> {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = when (uiState) {
                                is BackupUiState.Exporting -> stringResource(R.string.backupExporting)
                                is BackupUiState.ImportApplying -> stringResource(R.string.backupApplying)
                                else -> stringResource(R.string.backupDecrypting)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        is BackupUiState.ImportSuccess -> {
            LaunchedEffect(Unit) {
                delay(2500)
                viewModel.resetState()
            }
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                title = { Text(stringResource(R.string.backupImportTitle)) },
                text = { Text(stringResource(R.string.backupImportSuccess)) },
                confirmButton = {
                    Button(onClick = viewModel::resetState) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = viewModel::dismissError) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
        else -> {}
    }
}
