package com.wizzwuz.quicksettiles.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.wizzwuz.quicksettiles.BuildConfig
import com.wizzwuz.quicksettiles.ui.theme.AppThemeMode
import com.wizzwuz.quicksettiles.ui.theme.ThemeManager
import com.wizzwuz.quicksettiles.util.BackupRestoreManager
import com.wizzwuz.quicksettiles.util.DiagnosticsInfo
import com.wizzwuz.quicksettiles.util.LogManager
import com.wizzwuz.quicksettiles.util.LogStats
import com.wizzwuz.quicksettiles.util.RootManagerType
import com.wizzwuz.quicksettiles.util.ShizukuHelper
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val repository = remember { com.wizzwuz.quicksettiles.data.TileRepository.getInstance(context) }
    val isPrivateLaunchable by repository.privateActivityLaunchableFlow.collectAsState()

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
                title = { Text("Settings") },
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
            // 1. App Theme Flyout Menu Card
            var showThemeFlyout by remember { mutableStateOf(false) }
            val currentTheme by ThemeManager.themeFlow.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeFlyout = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = when (currentTheme) {
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.AMOLED -> Icons.Default.Brightness1
                                    AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "Theme",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentTheme.title,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = { showThemeFlyout = true }) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Choose Theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showThemeFlyout,
                        onDismissRequest = { showThemeFlyout = false }
                    ) {
                        AppThemeMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.title,
                                        fontWeight = if (mode == currentTheme) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (mode) {
                                            AppThemeMode.LIGHT -> Icons.Default.LightMode
                                            AppThemeMode.DARK -> Icons.Default.DarkMode
                                            AppThemeMode.AMOLED -> Icons.Default.Brightness1
                                            AppThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        },
                                        contentDescription = null,
                                        tint = if (mode == currentTheme) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = {
                                    if (mode == currentTheme) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    ThemeManager.setTheme(context, mode)
                                    showThemeFlyout = false
                                }
                            )
                        }
                    }
                }
            }

            // 2b. Tile Layout Style Toggle Card
            val isGridViewEnabled by repository.gridViewFlow.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            repository.setGridViewEnabled(!isGridViewEnabled)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridViewEnabled) Icons.Default.GridView else Icons.Default.ViewAgenda,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Tile Layout Style",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isGridViewEnabled) "Grid View" else "Classic Card View",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isGridViewEnabled,
                        onCheckedChange = {
                            repository.setGridViewEnabled(it)
                        }
                    )
                }
            }

            // 2c. Grid Dimensions & Arrangement Configuration Card (visible only when Grid View is enabled)
            AnimatedVisibility(visible = isGridViewEnabled) {
                val isGridDynamic by repository.gridDynamicFlow.collectAsState()
                val gridColumns by repository.gridColumnsFlow.collectAsState()
                val gridRows by repository.gridRowsFlow.collectAsState()

                var columnsInput by remember(gridColumns) { mutableStateOf("$gridColumns") }
                var rowsInput by remember(gridRows) { mutableStateOf("$gridRows") }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Grid Dimensions",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isGridDynamic) "Dynamic arrangement" else "$gridColumns columns × $gridRows rows",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Option called Dynamic
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isGridDynamic,
                                onClick = { repository.setGridDynamic(true) },
                                label = {
                                    Text(
                                        text = "Dynamic",
                                        fontWeight = if (isGridDynamic) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = !isGridDynamic,
                                onClick = { repository.setGridDynamic(false) },
                                label = {
                                    Text(
                                        text = "Custom Grid",
                                        fontWeight = if (!isGridDynamic) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isGridDynamic) {
                            Text(
                                text = "Dynamic (original grid arrangement when app is resized or in split-screen mode)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = columnsInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(2)
                                        columnsInput = digits
                                        val num = digits.toIntOrNull()
                                        if (num != null && num in 1..12) {
                                            repository.setGridColumns(num)
                                        }
                                    },
                                    label = { Text("Columns") },
                                    placeholder = { Text("3") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = rowsInput,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(2)
                                        rowsInput = digits
                                        val num = digits.toIntOrNull()
                                        if (num != null && num in 1..12) {
                                            repository.setGridRows(num)
                                        }
                                    },
                                    label = { Text("Rows") },
                                    placeholder = { Text("4") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "Enter number of columns and rows to arrange the tile grid.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Categories Display Toggle Card
            val showCategories by repository.showCategoriesFlow.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            repository.setShowCategories(!showCategories)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Categories",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (showCategories) "Categories panel enabled" else "Categories panel disabled",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = showCategories,
                        onCheckedChange = {
                            repository.setShowCategories(it)
                        }
                    )
                }
            }

            // 4. Private Activity Launching Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                repository.setPrivateActivityLaunchable(!isPrivateLaunchable)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Private Activity Launchable",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Allow selecting and launching non-exported/private activities using privileged access (Shizuku, Superuser Root, or Activity Launcher Plugin).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isPrivateLaunchable,
                            onCheckedChange = {
                                repository.setPrivateActivityLaunchable(it)
                            }
                        )
                    }

                    if (isPrivateLaunchable) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Execution Engine: ${if (diagnostics.isRootGranted) "Root access: Granted" else if (diagnostics.hasShizukuPermission) "Shizuku Service" else "Permission Required"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 4. Backup & Restore Card
            var showRestoreConfirmDialog by remember { mutableStateOf(false) }
            var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
            var restoreTileCountPreview by remember { mutableStateOf(0) }
            var restoreIconCountPreview by remember { mutableStateOf(0) }
            var restoreShellCountPreview by remember { mutableStateOf(0) }

            val exportBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/octet-stream")
            ) { uri ->
                if (uri != null) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            BackupRestoreManager.createBackup(context, output)
                        }
                        Toast.makeText(context, "Backup exported successfully (.bak)!", Toast.LENGTH_LONG).show()
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            val importBackupLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    try {
                        val peekResult = BackupRestoreManager.peekBackup(context, uri)
                        if (peekResult.isSuccess) {
                            val preview = peekResult.getOrThrow()
                            restoreTileCountPreview = preview.tileCount
                            restoreIconCountPreview = preview.iconCount
                            restoreShellCountPreview = preview.shellTileCount
                            pendingRestoreUri = uri
                            showRestoreConfirmDialog = true
                        } else {
                            Toast.makeText(context, "Failed to read backup: ${peekResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Throwable) {
                        Toast.makeText(context, "Failed to read backup: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Backup & Restore",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Export or restore your quick settings tiles, custom icons, and application settings (.bak).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                exportBackupLauncher.launch("QuickSetTiles_backup_$timestamp.bak")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                importBackupLauncher.launch(arrayOf("*/*", "application/octet-stream", "application/zip", "application/json"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (showRestoreConfirmDialog && pendingRestoreUri != null) {
                AlertDialog(
                    onDismissRequest = {
                        showRestoreConfirmDialog = false
                        pendingRestoreUri = null
                    },
                    title = { Text("Restore Backup?") },
                    text = {
                        val iconText = if (restoreIconCountPreview > 0) " and $restoreIconCountPreview custom icons" else ""
                        val shellWarning = if (restoreShellCountPreview > 0) "\n\n⚠️ Caution: This backup contains $restoreShellCountPreview shell script tile(s). Only restore backups from trusted sources." else ""
                        Text("This backup contains $restoreTileCountPreview tiles$iconText and application settings. Restoring will apply these settings and configure your tiles. Do you want to proceed?$shellWarning")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val uri = pendingRestoreUri
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                                if (uri != null) {
                                    val result = BackupRestoreManager.restoreBackup(context, uri)
                                    if (result.isSuccess) {
                                        val summary = result.getOrNull()
                                        val iconsSummary = if ((summary?.restoredIconsCount ?: 0) > 0) " (${summary?.restoredIconsCount} icons restored)" else ""
                                        Toast.makeText(
                                            context,
                                            "Restored ${summary?.tileCount ?: 0} tiles$iconsSummary and settings successfully!",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Restore error: ${result.exceptionOrNull()?.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        ) {
                            Text("Restore")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showRestoreConfirmDialog = false
                                pendingRestoreUri = null
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // 5. Privileged Access / Root & Shizuku Diagnostics
            Text(
                text = "Privileged Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Root Access Card — shown prominently when root is granted
            if (diagnostics.isRootGranted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Root Access Granted",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Manager: ${diagnostics.rootManagerType.displayName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            DiagnosticRow(
                                label = "Root Manager",
                                value = diagnostics.rootManagerType.displayName,
                                isOk = true
                            )
                            DiagnosticRow(
                                label = "Superuser (su) Binary",
                                value = "Present & Authorized (UID 0)",
                                isOk = true
                            )
                            DiagnosticRow(
                                label = "Active Execution Engine",
                                value = "Root (su / UID 0) — Highest Priority",
                                isOk = true
                            )
                        }
                        Text(
                            text = "Shell commands will use root access automatically. Shizuku is optional.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Shizuku diagnostics card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        diagnostics.isRootGranted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        diagnostics.isShizukuRunning && diagnostics.hasShizukuPermission ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        diagnostics.isShizukuRunning ->
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    }
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
                        if (diagnostics.isRootGranted) {
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Optional (Root Active)", fontSize = 10.sp) }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DiagnosticRow(
                            label = "Permission Status",
                            value = if (diagnostics.hasShizukuPermission) "Granted (UID 2000 ADB)" else if (diagnostics.isRootGranted) "Not needed (root active)" else "Not Granted",
                            isOk = diagnostics.hasShizukuPermission || diagnostics.isRootGranted
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
                        if (!diagnostics.isRootGranted) {
                            DiagnosticRow(
                                label = "Superuser (su) Binary",
                                value = if (diagnostics.isRootBinaryPresent) "Present in PATH" else "Not Found",
                                isOk = diagnostics.isRootBinaryPresent
                            )
                        }
                    }

                    // Only show Shizuku permission button if root is NOT available
                    if (!diagnostics.isRootGranted) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!diagnostics.hasShizukuPermission) {
                                Button(
                                    onClick = { ShizukuHelper.requestShizukuPermission(context) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Request Shizuku Permission", fontSize = 12.sp)
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
            }

            // About Section (Under Privileged Access)
            var isAboutExpanded by rememberSaveable { mutableStateOf(false) }

            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAboutExpanded) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { isAboutExpanded = !isAboutExpanded }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "About QuickSetTiles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAboutExpanded) "Tap to hide details" else "Version ${BuildConfig.VERSION_NAME} • Tap to view details",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = if (isAboutExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isAboutExpanded) "Collapse About" else "Expand About",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isAboutExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AboutSection(context = context)
                }
            }

            // Error Logging Section (log.txt) - Debug Build Only
            if (BuildConfig.DEBUG) {
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
                                Text("Export log.txt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun AboutSection(context: Context) {
    val appIconBitmap = remember(context) {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, 160, 160)
            drawable.draw(canvas)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    // 1. Top Center Card
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // App Icon
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap.asImageBitmap(),
                    contentDescription = "App Icon",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DashboardCustomize,
                        contentDescription = "App Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // App Name
            Text(
                text = "QuickSetTiles",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Tagline
            Text(
                text = "This app is made entirely with Google Antigravity",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Version Pill
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
            ) {
                Text(
                    text = "Version ${BuildConfig.VERSION_NAME}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }

    // 2. Section: Made by
    Text(
        text = "Made by",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Developer row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wizzwuz"))
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wizzwuz",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "github.com/wizzwuz",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open in browser",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Source code row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/wizzwuz/QuickSetTiles"))
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Source code",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "github.com/wizzwuz/QuickSetTiles",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open in browser",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // Support Section
    Text(
        text = "Support",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/wizzwuz"))
                    context.startActivity(intent)
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFDD00)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "☕",
                    fontSize = 20.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Buy me a coffee",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "buymeacoffee.com/wizzwuz",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = "Support on Buy Me A Coffee",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // 3. Section: License
    Text(
        text = "License",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "GNU General Public License v3.0",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Open source",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Free and open-source copyleft software. Permissions are conditioned on making available complete source code of licensed works and modifications under GNU GPL v3.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.html"))
                        context.startActivity(intent)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Read the full license",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    // 4. Section: Credits
    Text(
        text = "Credits",
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        val creditsList = remember {
            listOf(
                Triple("Activity Launcher Shizuku Plugin", "GPL 3.0", "Inspiration for launching private activities via Shizuku" to "https://github.com/ActivityLauncher/ActivityLauncherShizukuPlugin"),
                Triple("Shizuku", "Apache 2.0", "Elevated permissions interface (ADB shell / Root) for rootless execution" to "https://github.com/RikkaApps/Shizuku"),
                Triple("AndroidSVG", "Apache 2.0", "Vector parsing & rendering engine rasterizing 57,000+ preset icons" to "https://github.com/BigBadaboom/androidsvg"),
                Triple("Coil", "Apache 2.0", "Fast, lightweight Kotlin coroutine-first image loading library" to "https://github.com/coil-kt/coil"),
                Triple("ProGuard", "GPL 2.0", "Optimizer, shrinker, and obfuscator minifying release APK builds" to "https://github.com/Guardsquare/proguard"),
                Triple("Material Symbols", "Apache 2.0", "57,000+ Material vector glyph dataset across Outlined, Rounded & Sharp" to "https://icon-sets.iconify.design/material-symbols/"),
                Triple("Google Material Design Icons", "Apache 2.0", "Official baseline Material Design vector icon specification" to "https://github.com/google/material-design-icons"),
                Triple("Kotlin & Coroutines", "Apache 2.0", "Programming language, Jetpack Compose compiler, and asynchronous I/O" to "https://github.com/JetBrains/kotlin"),
                Triple("Gradle", "Apache 2.0", "Build automation system and wrapper powering CI and APK assembly" to "https://github.com/gradle/gradle"),
                Triple("Pillow (PIL)", "HPND", "Python imaging library used for CI adaptive launcher icon synthesis" to "https://github.com/python-pillow/Pillow")
            )
        }

        Column {
            creditsList.forEachIndexed { index, (name, license, details) ->
                val (description, url) = details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = license,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Open $name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (index < creditsList.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                }
            }
        }
    }
}
