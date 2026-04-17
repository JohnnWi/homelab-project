package com.homelab.app.ui.proxmox
import com.homelab.app.R

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxConsoleScreen(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val vncTicketState by viewModel.vncTicketState.collectAsStateWithLifecycle()
    var webViewReady by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var sslError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val consoleColor = Color(0xFF3F51B5)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(node, vmid, isQemu) {
        viewModel.fetchVncTicket(node, vmid, isQemu)
    }

    fun openInBrowser(ticketData: ProxmoxVncTicketData) {
        val instance = viewModel.instances.value.find { it.id == viewModel.instanceId }
        val baseUrl = instance?.url?.trimEnd('/') ?: ticketData.baseUrl
        // Open the Proxmox login page as fallback since external browser won't have the cookie
        val loginUrl = "$baseUrl/"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(loginUrl))
        context.startActivity(intent)
    }

    fun retryConsole() {
        viewModel.fetchVncTicket(node, vmid, isQemu)
        webViewReady = false
        loadingError = null
        sslError = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Console - ${if (isQemu) "VM" else "CT"} $vmid") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (vncTicketState is UiState.Success) {
                        val ticketData = (vncTicketState as UiState.Success<ProxmoxVncTicketData>).data
                        IconButton(onClick = { openInBrowser(ticketData) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Browser")
                        }
                    }
                    IconButton(onClick = { retryConsole() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = vncTicketState) {
                is UiState.Idle,
                is UiState.Loading,
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Fetching VNC ticket...", color = Color.Gray)
                        }
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.proxmox_failed_fetch_vnc_ticket), color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.fetchVncTicket(node, vmid, isQemu) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
                is UiState.Success -> {
                    val ticketData = state.data

                    if (sslError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("SSL Certificate Error", color = Color(0xFFFF9800), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The WebView could not verify the server's SSL certificate. This may happen with self-signed certificates.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Try opening the console in your browser instead.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { retryConsole() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry")
                                }
                                Button(onClick = { openInBrowser(ticketData) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open in Browser")
                                }
                            }
                        }
                    } else if (loadingError != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Failed to load console", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(loadingError!!, color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = { retryConsole() }) {
                                    Text("Retry")
                                }
                                Button(onClick = { openInBrowser(ticketData) }) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open in Browser")
                                }
                            }
                        }
                    } else {
                        // Set the PVEAuthCookie before loading the WebView
                        LaunchedEffect(ticketData.ticket) {
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(
                                android.webkit.WebView(context),
                                true
                            )
                            val baseUri = runCatching { java.net.URI(ticketData.baseUrl) }.getOrNull()
                            val cookieOrigin = baseUri?.scheme?.takeIf { it.isNotBlank() }?.let { scheme ->
                                val authority = baseUri.authority?.takeIf { it.isNotBlank() } ?: return@let null
                                "$scheme://$authority"
                            }
                            if (!cookieOrigin.isNullOrBlank()) {
                                val secureAttribute = if (baseUri?.scheme.equals("https", ignoreCase = true)) "; Secure" else ""
                                cookieManager.setCookie(
                                    cookieOrigin,
                                    "PVEAuthCookie=${ticketData.ticket}; Path=/$secureAttribute"
                                )
                                cookieManager.flush()
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            loadingError = null
                                            sslError = false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            webViewReady = true
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                loadingError = error?.description?.toString() ?: context.getString(R.string.error_unknown)
                                            }
                                        }

                                        @Suppress("DEPRECATION")
                                        override fun onReceivedSslError(
                                            view: WebView?,
                                            handler: android.webkit.SslErrorHandler?,
                                            error: android.net.http.SslError?
                                        ) {
                                            super.onReceivedSslError(view, handler, error)
                                            sslError = true
                                            handler?.cancel()
                                        }
                                    }

                                    loadUrl(ticketData.buildConsoleUrl())
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { webView ->
                                // No-op updates; the WebView state is managed internally
                            }
                        )

                        if (!webViewReady && loadingError == null && !sslError) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = 100.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = consoleColor.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Text(
                                        "Loading console...",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        color = consoleColor
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
