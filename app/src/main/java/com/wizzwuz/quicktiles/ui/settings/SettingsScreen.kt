package com.wizzwuz.quicktiles.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wizzwuz.quicktiles.BuildConfig
import com.wizzwuz.quicktiles.util.DiagnosticsInfo
import com.wizzwuz.quicktiles.util.LogManager
import com.wizzwuz.quicktiles.util.LogStats
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var diagnostics by remember { mutableStateOf(ShizukuHelper.getDiagnostics(context)) }
    var logStats by remember { mutableStateOf<LogStats?>(null) }
    var logContent by remember { mutableStateOf("Loading logs...") }
    var showClearDialog by remember { mutableStateOf(false) }

    val logUpdateTrigger by LogManager.updateFlow.collectAsState()

    // Refresh logs whenever trigger or screen loads
    LaunchedEffect(logUpdateTrigger) {
        diagnostics = ShizukuHelper.getDiagnostics(context)
        logStats = LogManager.getStats()
        logContent = LogManager.readLogs(300)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            ShizukuHelper.updateStatus()
                            diagnostics = ShizukuHelper.getDiagnostics(context)
                            logStats = LogManager.getStats()
                            logContent = LogManager.readLogs(300)
                            Toast.makeText(context, "Refreshed diagnostics & logs", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. App Build Information Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QuickSetTiles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (BuildConfig.DEBUG) "DEBUG BUILD" else "RELEASE BUILD",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (BuildConfig.DEBUG) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                    Text(
                        text = "Version: ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Package: ${context.packageName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Shizuku / Shevery Diagnostics Section
            Text(
                text = "Privileged Service Diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (diagnostics.isShizukuRunning && diagnostics.hasShizukuPermission)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else if (diagnostics.isShizukuRunning)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (diagnostics.isShizukuRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (diagnostics.isShizukuRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = if (diagnostics.isShizukuRunning) "Shizuku Service Running" else "Shizuku Service Disconnected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DiagnosticRow(
                            label = "Permission Status",
                            value = if (diagnostics.hasShizukuPermission) "Granted (UID 2000 ADB)" else "Not Granted",
                            isOk = diagnostics.hasShizukuPermission
                        )
                        DiagnosticRow(
                            label = "Shizuku Version / UID",
                            value = if (diagnostics.isShizukuRunning) "v${diagnostics.shizukuVersion} (UID ${diagnostics.shizukuUid})" else "Unavailable",
                            isOk = diagnostics.isShizukuRunning
                        )
                        DiagnosticRow(
                            label = "Shevery App",
                            value = if (diagnostics.isSheveryInstalled) "Installed (com.hamondev.shevery)" else "Not Detected",
                            isOk = diagnostics.isSheveryInstalled
                        )
                        DiagnosticRow(
                            label = "Superuser (su) Binary",
                            value = if (diagnostics.isRootBinaryPresent) "Present in PATH" else "Not Found",
                            isOk = diagnostics.isRootBinaryPresent
                        )
                    }

                    // Action buttons for Shizuku / Shevery
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (diagnostics.isShizukuRunning && !diagnostics.hasShizukuPermission) {
                            Button(
                                onClick = {
                                    ShizukuHelper.requestShizukuPermission()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Grant Permission", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val pm = context.packageManager
                                val sheveryIntent = pm.getLaunchIntentForPackage("com.hamondev.shevery")
                                val shizukuIntent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                val target = sheveryIntent ?: shizukuIntent
                                if (target != null) {
                                    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(target)
                                } else {
                                    Toast.makeText(context, "Neither Shevery nor Shizuku app is installed.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Manager", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. Error Logging Section (log.txt)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Error Log (log.txt)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                logStats?.let { stats ->
                    Text(
                        text = "${stats.formattedSize} • ${stats.lineCount} lines",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Errors and shell command output are automatically logged to log.txt.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Export / Share Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // EXPORT LOG FILE BUTTON: Debug APK Only!
                        if (BuildConfig.DEBUG) {
                            FilledTonalButton(
                                onClick = {
                                    val file = LogManager.getLogFileForExport()
                                    if (file != null && file.exists()) {
                                        try {
                                            val uri: Uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                putExtra(Intent.EXTRA_SUBJECT, "QuickSetTiles log.txt")
                                                putExtra(Intent.EXTRA_TEXT, "QuickSetTiles Debug Log File (v${BuildConfig.VERSION_NAME})")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Export log.txt via"))
                                        } catch (e: Throwable) {
                                            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Log file is empty or not found.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export log.txt (Debug)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // In Release builds: Inform user that file export is debug-only
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Exporting log.txt file is enabled in Debug APK only.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        // Copy to Clipboard Button
                        OutlinedButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QuickSetTiles Log", logContent))
                                Toast.makeText(context, "Copied log.txt to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        }

                        // Clear Logs Button
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Monospace Log Content Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 320.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = logContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for clearing logs
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Log File?") },
            text = { Text("Are you sure you want to erase all entries from log.txt? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        coroutineScope.launch {
                            LogManager.clearLogs()
                            logStats = LogManager.getStats()
                            logContent = LogManager.readLogs(300)
                            Toast.makeText(context, "Log file cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DiagnosticRow(
    label: String,
    value: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
