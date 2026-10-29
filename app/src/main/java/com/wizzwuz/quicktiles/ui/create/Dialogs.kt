package com.wizzwuz.quicktiles.ui.create

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.wizzwuz.quicktiles.model.ShellStep
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.model.TileVisualState
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.CommandResult
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.PopupToastHelper
import com.wizzwuz.quicktiles.util.ShellMode
import com.wizzwuz.quicktiles.util.ShortcutProviderItem
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.launch

@Composable
fun WebsiteDialog(
    initialName: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl.ifBlank { "https://" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Website Tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Website Name") },
                    placeholder = { Text("e.g. GitHub") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website URL") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        val finalName = if (name.isNotBlank()) name else url.substringAfter("://").substringBefore("/")
                        onConfirm(finalName, url.trim())
                    }
                },
                enabled = url.isNotBlank() && url != "https://"
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ShortcutDialog(
    onDismiss: () -> Unit,
    onSelect: (name: String, intentUri: String, packageName: String, iconBitmap: Bitmap?) -> Unit
) {
    val context = LocalContext.current
    var appsWithShortcuts by remember { mutableStateOf<List<com.wizzwuz.quicktiles.util.AppWithShortcuts>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val expandedApps = remember { mutableStateMapOf<String, Boolean>() }

    // Instant cached display + background refresh every time dialog opens
    LaunchedEffect(Unit) {
        val cached = com.wizzwuz.quicktiles.util.AppHelper.getCachedShortcuts()
        if (cached != null) {
            appsWithShortcuts = cached
            isLoading = false
        }
        val fresh = com.wizzwuz.quicktiles.util.AppHelper.refreshAppsWithShortcuts(context)
        appsWithShortcuts = fresh
        isLoading = false
    }

    // Filter apps based on search
    val filteredApps = remember(appsWithShortcuts, searchQuery) {
        if (searchQuery.isBlank()) {
            appsWithShortcuts
        } else {
            val q = searchQuery.trim().lowercase()
            appsWithShortcuts.filter { app ->
                app.appLabel.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.shortcuts.any { s ->
                    s.label.lowercase().contains(q) ||
                    (s.longLabel?.lowercase()?.contains(q) == true)
                }
            }
        }
    }

    // Auto-expand apps when searching so matching shortcuts are visible
    LaunchedEffect(searchQuery, filteredApps) {
        if (searchQuery.isNotBlank()) {
            filteredApps.forEach { app -> expandedApps[app.packageName] = true }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                // Header with title and close X button
                Row(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                        Text(
                            text = "App Shortcuts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isLoading) {
                            Text(
                                text = "${filteredApps.size} apps with shortcuts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = androidx.compose.ui.Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps & shortcuts...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.Search,
                            contentDescription = "Search",
                            modifier = androidx.compose.ui.Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Filled.Close,
                                    contentDescription = "Clear",
                                    modifier = androidx.compose.ui.Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                                Text("Loading app shortcuts...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    filteredApps.isEmpty() && appsWithShortcuts.isEmpty() -> {
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No apps with shortcuts found on this device.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(24.dp)
                            )
                        }
                    }
                    filteredApps.isEmpty() -> {
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No results for \"$searchQuery\"",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = androidx.compose.ui.Modifier.padding(24.dp)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 12.dp, top = 4.dp)
                        ) {
                            items(filteredApps) { app ->
                                val isExpanded = expandedApps[app.packageName] == true
                                val matchingShortcuts = if (searchQuery.isBlank()) app.shortcuts else {
                                    val q = searchQuery.trim().lowercase()
                                    app.shortcuts.filter { s ->
                                        s.label.lowercase().contains(q) ||
                                        (s.longLabel?.lowercase()?.contains(q) == true)
                                    }.ifEmpty { app.shortcuts }
                                }

                                Card(
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    // App header row (tap to expand/collapse)
                                    Row(
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedApps[app.packageName] = !isExpanded }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // App icon
                                        val appBmp = remember(app.packageName) {
                                            IconProcessor.drawableToBitmap(app.appIcon, 80, 80)
                                        }
                                        Image(
                                            bitmap = appBmp.asImageBitmap(),
                                            contentDescription = app.appLabel,
                                            modifier = androidx.compose.ui.Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )

                                        // App name + shortcut count
                                        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                            Text(
                                                text = app.appLabel,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${app.shortcuts.size} shortcut${if (app.shortcuts.size != 1) "s" else ""}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Dropdown arrow
                                        Icon(
                                            imageVector = if (isExpanded)
                                                androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp
                                            else
                                                androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Expanded shortcuts list
                                    if (isExpanded) {
                                        HorizontalDivider(
                                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                        Column(
                                            modifier = androidx.compose.ui.Modifier.padding(bottom = 6.dp)
                                        ) {
                                            matchingShortcuts.forEach { shortcut ->
                                                Row(
                                                    modifier = androidx.compose.ui.Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            onSelect(
                                                                shortcut.label,
                                                                shortcut.intentUri,
                                                                shortcut.packageName,
                                                                shortcut.iconBitmap
                                                            )
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    // Shortcut icon or fallback
                                                    if (shortcut.iconBitmap != null) {
                                                        Image(
                                                            bitmap = shortcut.iconBitmap.asImageBitmap(),
                                                            contentDescription = shortcut.label,
                                                            modifier = androidx.compose.ui.Modifier
                                                                .size(32.dp)
                                                                .clip(RoundedCornerShape(6.dp))
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = androidx.compose.material.icons.Icons.Filled.OpenInNew,
                                                            contentDescription = shortcut.label,
                                                            modifier = androidx.compose.ui.Modifier.size(32.dp),
                                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                        )
                                                    }

                                                    // Shortcut label + long label
                                                    Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                                                        Text(
                                                            text = shortcut.label,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 13.sp,
                                                            maxLines = 1
                                                        )
                                                        if (!shortcut.longLabel.isNullOrBlank() && shortcut.longLabel != shortcut.label) {
                                                            Text(
                                                                text = shortcut.longLabel,
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }

                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Filled.ChevronRight,
                                                        contentDescription = "Select",
                                                        modifier = androidx.compose.ui.Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
}


@Composable
fun IntentDialog(
    initialName: String = "",
    initialAction: String = "",
    initialPackage: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, action: String, targetPackage: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var action by remember { mutableStateOf(initialAction.ifBlank { "android.settings.SETTINGS" }) }
    var targetPackage by remember { mutableStateOf(initialPackage) }

    val popularActions = listOf(
        "Settings" to "android.settings.SETTINGS",
        "Wi-Fi" to "android.settings.WIFI_SETTINGS",
        "Bluetooth" to "android.settings.BLUETOOTH_SETTINGS",
        "Developer" to "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
        "Display" to "android.settings.DISPLAY_SETTINGS"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Intent Tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tile Name") },
                    placeholder = { Text("e.g. Wi-Fi Settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text("Intent Action") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = { targetPackage = it },
                    label = { Text("Target Package (Optional)") },
                    placeholder = { Text("e.g. com.android.settings") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Suggestions:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    popularActions.take(3).forEach { (label, act) ->
                        SuggestionChip(
                            onClick = {
                                action = act
                                if (name.isBlank()) name = label
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (action.isNotBlank()) {
                        val finalName = if (name.isNotBlank()) name else action.substringAfterLast('.')
                        onConfirm(finalName, action.trim(), targetPackage.trim())
                    }
                },
                enabled = action.isNotBlank()
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenScriptEditorDialog(
    title: String,
    initialScript: String,
    selectedMode: ShellMode = ShellMode.AUTO,
    onDismissAndSave: (String) -> Unit
) {
    var script by remember { mutableStateOf(initialScript) }
    var currentMode by remember { mutableStateOf(selectedMode) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<CommandResult?>(null) }

    BackHandler {
        onDismissAndSave(script)
    }

    Dialog(
        onDismissRequest = { onDismissAndSave(script) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Fullscreen Script Editor",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { onDismissAndSave(script) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back & Save")
                        }
                    },
                    actions = {
                        Button(
                            onClick = { onDismissAndSave(script) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 13.sp)
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Execution Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = {
                            if (script.isNotBlank() && !isTesting) {
                                isTesting = true
                                testResult = null
                                coroutineScope.launch {
                                    val res = ShizukuHelper.executeCommand(script.trim(), currentMode)
                                    testResult = res
                                    isTesting = false
                                    if (res.output.isNotBlank()) {
                                        val lines = res.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                        for (line in lines) {
                                            PopupToastHelper.showToast(context, line)
                                            if (lines.size > 1) {
                                                kotlinx.coroutines.delay(1200)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = script.isNotBlank() && !isTesting,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Running...", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Script", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShellMode.values().forEach { mode ->
                        FilterChip(
                            selected = currentMode == mode,
                            onClick = { currentMode = mode },
                            label = { Text(mode.name, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = script,
                    onValueChange = {
                        script = it
                        testResult = null
                    },
                    placeholder = { Text("echo 'Hello'\ninput tap 500 500\npm list packages") },
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                testResult?.let { result ->
                    val isSuccess = result.exitCode == 0
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuccess)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isSuccess) "Exit 0 (Success)" else "Exit ${result.exitCode}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "${result.executionMode} • ${result.durationMs}ms",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (result.output.isNotBlank()) result.output else "(No output produced)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShellCommandInputField(
    label: String,
    command: String,
    onCommandChange: (String) -> Unit,
    selectedMode: ShellMode = ShellMode.AUTO,
    placeholder: String = "e.g. input tap 500 500\npm list packages",
    minLines: Int = 2,
    maxLines: Int = 5,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<CommandResult?>(null) }
    var showFullscreenEditor by remember { mutableStateOf(false) }

    if (showFullscreenEditor) {
        FullscreenScriptEditorDialog(
            title = label,
            initialScript = command,
            selectedMode = selectedMode,
            onDismissAndSave = { updated ->
                showFullscreenEditor = false
                onCommandChange(updated)
            }
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { showFullscreenEditor = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(26.dp)
            ) {
                Text(
                    text = "<> Expand",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        OutlinedTextField(
            value = command,
            onValueChange = {
                onCommandChange(it)
                testResult = null
            },
            placeholder = { Text(placeholder) },
            minLines = minLines,
            maxLines = maxLines,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    if (command.isNotBlank() && !isTesting) {
                        isTesting = true
                        testResult = null
                        coroutineScope.launch {
                            val res = ShizukuHelper.executeCommand(
                                command = command.trim(),
                                mode = selectedMode
                            )
                            testResult = res
                            isTesting = false
                            if (res.output.isNotBlank()) {
                                val lines = res.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                for (line in lines) {
                                    PopupToastHelper.showToast(context, line)
                                    if (lines.size > 1) {
                                        kotlinx.coroutines.delay(1200)
                                    }
                                }
                            }
                        }
                    }
                },
                enabled = command.isNotBlank() && !isTesting,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(32.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Testing...", fontSize = 11.sp)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Command", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        testResult?.let { result ->
            val isSuccess = result.exitCode == 0
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isSuccess) "Exit 0 (Success)" else "Exit ${result.exitCode}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "${result.executionMode} • ${result.durationMs}ms",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(6.dp)
                    ) {
                        Text(
                            text = if (result.output.isNotBlank()) result.output else "(No stdout or stderr produced)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 6
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultipleShellDialog(
    initialSteps: List<ShellStep> = emptyList(),
    initialShowPopups: Boolean = false,
    onDismiss: () -> Unit,
    onBackToSingleShell: (() -> Unit)? = null,
    onConfirm: (steps: List<ShellStep>, showPopups: Boolean) -> Unit
) {
    val context = LocalContext.current
    var steps by remember {
        mutableStateOf(
            if (initialSteps.isNotEmpty()) {
                initialSteps.toMutableList()
            } else {
                mutableListOf(
                    ShellStep(name = "Shell 1", command = "", visualState = TileVisualState.ENABLED),
                    ShellStep(name = "Shell 2", command = "", visualState = TileVisualState.ENABLED)
                )
            }
        )
    }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var showPopups by remember { mutableStateOf(initialShowPopups) }
    var advanceExpanded by remember { mutableStateOf(false) }
    val shizukuStatus by ShizukuHelper.statusFlow.collectAsState()
    var isRootGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isRootGranted = ShizukuHelper.isRootGranted() }

    var showPresetPickerForStep by remember { mutableStateOf(false) }

    val safeIndex = selectedIndex.coerceIn(0, (steps.size - 1).coerceAtLeast(0))
    val currentStep = if (steps.isNotEmpty()) steps[safeIndex] else ShellStep()

    if (showPresetPickerForStep && steps.isNotEmpty()) {
        IconPresetsDialog(
            currentKey = currentStep.iconConfig.sourceData,
            onDismiss = { showPresetPickerForStep = false },
            onSelectPreset = { key ->
                showPresetPickerForStep = false
                val updated = steps.toMutableList()
                updated[safeIndex] = updated[safeIndex].copy(
                    iconConfig = updated[safeIndex].iconConfig.copy(
                        sourceType = IconSourceType.PRESET,
                        sourceData = key
                    )
                )
                steps = updated
            },
            onSelectCustomFile = { path ->
                showPresetPickerForStep = false
                val updated = steps.toMutableList()
                updated[safeIndex] = updated[safeIndex].copy(
                    iconConfig = updated[safeIndex].iconConfig.copy(
                        sourceType = IconSourceType.CUSTOM_FILE,
                        sourceData = path
                    )
                )
                steps = updated
            },
            onSelectAppIcon = { pkg ->
                showPresetPickerForStep = false
                val updated = steps.toMutableList()
                updated[safeIndex] = updated[safeIndex].copy(
                    iconConfig = IconConfig(
                        sourceType = IconSourceType.APP_ICON,
                        sourceData = pkg,
                        outlineThickness = 2.5f,
                        outlineThreshold = 0.4f,
                        iconSize = 32f,
                        outlineStyle = "OUTLINE"
                    )
                )
                steps = updated
            }
        )
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Multiple Shells", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (onBackToSingleShell != null) {
                    OutlinedButton(
                        onClick = onBackToSingleShell,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Single shell", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Horizontal list of shells with "+" button to the right of added shells
                Text("Shells (${steps.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { index, step ->
                        val isSelected = index == safeIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedIndex = index },
                            label = {
                                Text(
                                    text = if (step.name.isNotBlank()) step.name else "Shell ${index + 1}",
                                    fontSize = 12.sp
                                )
                            },
                            trailingIcon = if (steps.size > 1) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                if (steps.size > 1) {
                                                    val newSteps = steps.toMutableList()
                                                    newSteps.removeAt(index)
                                                    steps = newSteps
                                                    if (selectedIndex >= newSteps.size) {
                                                        selectedIndex = newSteps.size - 1
                                                    }
                                                }
                                            }
                                    )
                                }
                            } else null
                        )
                    }

                    // Small square shape "+" button shifting to right of added shells
                    Surface(
                        onClick = {
                            val nextNum = steps.size + 1
                            val newSteps = steps.toMutableList()
                            newSteps.add(ShellStep(name = "Shell $nextNum", command = "", visualState = TileVisualState.ENABLED))
                            steps = newSteps
                            selectedIndex = newSteps.size - 1
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Shell",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (steps.isNotEmpty()) {
                    // Visual State: Enabled, Disabled (NO "Both" button!)
                    Text("Tile Appearance & State", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentStep.visualState == TileVisualState.ENABLED,
                            onClick = {
                                val updated = steps.toMutableList()
                                updated[safeIndex] = updated[safeIndex].copy(visualState = TileVisualState.ENABLED)
                                steps = updated
                            },
                            label = { Text("Enabled", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentStep.visualState == TileVisualState.DISABLED,
                            onClick = {
                                val updated = steps.toMutableList()
                                updated[safeIndex] = updated[safeIndex].copy(visualState = TileVisualState.DISABLED)
                                steps = updated
                            },
                            label = { Text("Disabled", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Tile Name for this shell
                    OutlinedTextField(
                        value = currentStep.name,
                        onValueChange = { newName ->
                            val updated = steps.toMutableList()
                            updated[safeIndex] = updated[safeIndex].copy(name = newName)
                            steps = updated
                        },
                        label = { Text("Tile Name") },
                        placeholder = { Text("e.g. Shell ${safeIndex + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tile Icon for this shell
                    Text("Tile Icon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val stepBmp = remember(currentStep.iconConfig, isDark) {
                        IconProcessor.processIconBitmap(context, currentStep.iconConfig, 40, isDarkTheme = isDark)
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(bitmap = stepBmp.asImageBitmap(), contentDescription = "Icon", modifier = Modifier.size(32.dp))
                                Column {
                                    Text(if (currentStep.visualState == TileVisualState.DISABLED) "Disabled Icon" else "Enabled Icon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (currentStep.iconConfig.sourceType == IconSourceType.CUSTOM_FILE) "File: ${currentStep.iconConfig.sourceData.substringAfterLast('/')}" else "Preset: ${currentStep.iconConfig.sourceData}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showPresetPickerForStep = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }

                    // Shell Execution Mode
                    val currentMode = try { ShellMode.valueOf(currentStep.executionMode) } catch (e: Exception) { ShellMode.AUTO }
                    Text("Execution Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ShellMode.values().forEach { mode ->
                            FilterChip(
                                selected = currentMode == mode,
                                onClick = {
                                    val updated = steps.toMutableList()
                                    updated[safeIndex] = updated[safeIndex].copy(executionMode = mode.name)
                                    steps = updated
                                },
                                label = { Text(mode.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Execution access status card
                    if (isRootGranted) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ Root access: Granted",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        val isPermRequired = shizukuStatus == com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (shizukuStatus) {
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = when (shizukuStatus) {
                                        com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> "✓ Shizuku: Connected & Authorized"
                                        com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> "⚠ Shizuku: Permission Required"
                                        com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> "ℹ Shizuku: Not Running (Will use Root or sh)"
                                    },
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                                if (isPermRequired) {
                                    Button(
                                        onClick = { ShizukuHelper.requestShizukuPermission() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Grant Shizuku Permission", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Shell Command for this step
                    ShellCommandInputField(
                        label = if (currentStep.visualState == TileVisualState.DISABLED) "Shell Command (Disabled state)" else "Shell Command (Enabled state)",
                        command = currentStep.command,
                        onCommandChange = { newCmd ->
                            val updated = steps.toMutableList()
                            updated[safeIndex] = updated[safeIndex].copy(command = newCmd)
                            steps = updated
                        },
                        selectedMode = currentMode,
                        placeholder = "e.g. echo 'Running step ${safeIndex + 1}'"
                    )

                    // Collapse panel toggle for this shell
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = steps.toMutableList()
                                updated[safeIndex] = updated[safeIndex].copy(collapsePanel = !currentStep.collapsePanel)
                                steps = updated
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Collapse panel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Collapse notification shade / tray after executing this shell",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = currentStep.collapsePanel,
                                onCheckedChange = { checked ->
                                    val updated = steps.toMutableList()
                                    updated[safeIndex] = updated[safeIndex].copy(collapsePanel = checked)
                                    steps = updated
                                }
                            )
                        }
                    }
                }

                // Advance option dropdown menu
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advanceExpanded = !advanceExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Advance option", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                imageVector = if (advanceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        if (advanceExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pop-up message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Output echo commands as pop-up messages. If or else outputs multiple messages separately.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showPopups,
                                    onCheckedChange = { showPopups = it }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = steps.isNotEmpty() && steps.any { it.command.isNotBlank() }
            Button(
                onClick = {
                    if (canSave) {
                        onConfirm(steps, showPopups)
                    }
                },
                enabled = canSave
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShellDialog(
    initialName: String = "",
    initialCommand: String = "",
    initialOnCommand: String = "",
    initialOffCommand: String = "",
    initialVisualState: TileVisualState = TileVisualState.ENABLED,
    initialOnTileName: String = "",
    initialOffTileName: String = "",
    initialOnIconConfig: IconConfig? = null,
    initialOffIconConfig: IconConfig? = null,
    initialShowPopups: Boolean = false,
    initialMultiShellSteps: List<ShellStep> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        targetData: String,
        onCommand: String,
        offCommand: String,
        visualState: TileVisualState,
        onTileName: String,
        offTileName: String,
        onIconConfig: IconConfig?,
        offIconConfig: IconConfig?,
        showPopups: Boolean,
        multiShellSteps: List<ShellStep>
    ) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialName) }
    var visualState by remember { mutableStateOf(initialVisualState) }
    var onTileName by remember { mutableStateOf(initialOnTileName.ifBlank { initialName }) }
    var offTileName by remember { mutableStateOf(initialOffTileName.ifBlank { initialName }) }
    var onIconConfig by remember { mutableStateOf(initialOnIconConfig ?: IconConfig(sourceType = IconSourceType.PRESET, sourceData = "terminal")) }
    var offIconConfig by remember { mutableStateOf(initialOffIconConfig ?: IconConfig(sourceType = IconSourceType.PRESET, sourceData = "power")) }
    var onCommand by remember { mutableStateOf(initialOnCommand.ifBlank { initialCommand }) }
    var offCommand by remember { mutableStateOf(initialOffCommand) }
    var selectedMode by remember { mutableStateOf(ShellMode.AUTO) }
    val shizukuStatus by ShizukuHelper.statusFlow.collectAsState()
    var isRootGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isRootGranted = ShizukuHelper.isRootGranted() }

    var showPopups by remember { mutableStateOf(initialShowPopups) }
    var showMultipleShellDialog by remember { mutableStateOf(false) }
    var advanceExpanded by remember { mutableStateOf(false) }

    var showPresetPickerForOn by remember { mutableStateOf(false) }
    var showPresetPickerForOff by remember { mutableStateOf(false) }

    if (showMultipleShellDialog) {
        MultipleShellDialog(
            initialSteps = initialMultiShellSteps,
            initialShowPopups = showPopups,
            onDismiss = { showMultipleShellDialog = false },
            onBackToSingleShell = { showMultipleShellDialog = false },
            onConfirm = { steps, popups ->
                showMultipleShellDialog = false
                val firstStep = steps.firstOrNull()
                val primaryName = firstStep?.name?.ifBlank { "Multiple Shell" } ?: "Multiple Shell"
                val primaryCmd = firstStep?.command ?: ""
                val primaryIcon = firstStep?.iconConfig
                val primaryVisual = firstStep?.visualState ?: TileVisualState.ENABLED
                onConfirm(
                    primaryName,
                    primaryCmd,
                    primaryCmd,
                    "",
                    primaryVisual,
                    primaryName,
                    primaryName,
                    primaryIcon,
                    null,
                    popups,
                    steps
                )
            }
        )
    }

    if (showPresetPickerForOn) {
        IconPresetsDialog(
            currentKey = onIconConfig.sourceData,
            onDismiss = { showPresetPickerForOn = false },
            onSelectPreset = { key ->
                showPresetPickerForOn = false
                onIconConfig = onIconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = key
                )
            },
            onSelectCustomFile = { path ->
                showPresetPickerForOn = false
                onIconConfig = onIconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = path
                )
            },
            onSelectAppIcon = { pkg ->
                showPresetPickerForOn = false
                onIconConfig = IconConfig(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = pkg,
                    outlineThickness = 2.5f,
                    outlineThreshold = 0.4f,
                    iconSize = 32f,
                    outlineStyle = "OUTLINE"
                )
            }
        )
    }

    if (showPresetPickerForOff) {
        IconPresetsDialog(
            currentKey = offIconConfig.sourceData,
            onDismiss = { showPresetPickerForOff = false },
            onSelectPreset = { key ->
                showPresetPickerForOff = false
                offIconConfig = offIconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = key
                )
            },
            onSelectCustomFile = { path ->
                showPresetPickerForOff = false
                offIconConfig = offIconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = path
                )
            },
            onSelectAppIcon = { pkg ->
                showPresetPickerForOff = false
                offIconConfig = IconConfig(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = pkg,
                    outlineThickness = 2.5f,
                    outlineThreshold = 0.4f,
                    iconSize = 32f,
                    outlineStyle = "OUTLINE"
                )
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Configure Shell Tile",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { showMultipleShellDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Multiple shell", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Visual State: Enabled, Disabled, Toggle (Both)
                Text("Tile Appearance & State", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = visualState == TileVisualState.ENABLED,
                        onClick = { visualState = TileVisualState.ENABLED },
                        label = { Text("Enabled", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = visualState == TileVisualState.DISABLED,
                        onClick = { visualState = TileVisualState.DISABLED },
                        label = { Text("Disabled", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = visualState == TileVisualState.TOGGLE,
                        onClick = { visualState = TileVisualState.TOGGLE },
                        label = { Text("Both", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Tile Name(s) based on visual state
                if (visualState == TileVisualState.TOGGLE) {
                    OutlinedTextField(
                        value = onTileName,
                        onValueChange = { onTileName = it },
                        label = { Text("Tile Name (when Enabled / ON)") },
                        placeholder = { Text("e.g. WiFi On") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = offTileName,
                        onValueChange = { offTileName = it },
                        label = { Text("Tile Name (when Disabled / OFF)") },
                        placeholder = { Text("e.g. WiFi Off") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Tile Name") },
                        placeholder = { Text("e.g. Restart SystemUI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Tile Icon(s) based on visual state
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Text("Tile Icon(s)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (visualState == TileVisualState.TOGGLE) {
                    // Enabled Icon row
                    val onBmp = remember(onIconConfig, isDark) { IconProcessor.processIconBitmap(context, onIconConfig, 40, isDarkTheme = isDark) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(bitmap = onBmp.asImageBitmap(), contentDescription = "Enabled Icon", modifier = Modifier.size(32.dp))
                                Column {
                                    Text("Enabled Icon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (onIconConfig.sourceType == IconSourceType.CUSTOM_FILE) "File: ${onIconConfig.sourceData.substringAfterLast('/')}" else "Preset: ${onIconConfig.sourceData}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showPresetPickerForOn = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }

                    // Disabled Icon row
                    val offBmp = remember(offIconConfig, isDark) { IconProcessor.processIconBitmap(context, offIconConfig, 40, isDarkTheme = isDark) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(bitmap = offBmp.asImageBitmap(), contentDescription = "Disabled Icon", modifier = Modifier.size(32.dp))
                                Column {
                                    Text("Disabled Icon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (offIconConfig.sourceType == IconSourceType.CUSTOM_FILE) "File: ${offIconConfig.sourceData.substringAfterLast('/')}" else "Preset: ${offIconConfig.sourceData}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { showPresetPickerForOff = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // Single icon
                    val currentCfg = if (visualState == TileVisualState.DISABLED) offIconConfig else onIconConfig
                    val bmp = remember(currentCfg, isDark) { IconProcessor.processIconBitmap(context, currentCfg, 40, isDarkTheme = isDark) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Tile Icon", modifier = Modifier.size(32.dp))
                                Column {
                                    Text(if (visualState == TileVisualState.DISABLED) "Disabled Icon" else "Enabled Icon", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (currentCfg.sourceType == IconSourceType.CUSTOM_FILE) "File: ${currentCfg.sourceData.substringAfterLast('/')}" else "Preset: ${currentCfg.sourceData}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    if (visualState == TileVisualState.DISABLED) showPresetPickerForOff = true else showPresetPickerForOn = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Change", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Shell Mode Selection
                Text("Execution Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ShellMode.values().forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.name, fontSize = 11.sp) }
                        )
                    }
                }

                // Execution access status card
                if (isRootGranted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✓ Root access: Granted",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    // Shizuku status indicator card
                    val isPermRequired = shizukuStatus == com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when (shizukuStatus) {
                                com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = when (shizukuStatus) {
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> "✓ Shizuku: Connected & Authorized"
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> "⚠ Shizuku: Permission Required"
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> "ℹ Shizuku: Not Running (Will use Root or sh)"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                            if (isPermRequired) {
                                Button(
                                    onClick = { ShizukuHelper.requestShizukuPermission() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Grant Shizuku Permission", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                // Commands based on visual state
                if (visualState == TileVisualState.TOGGLE) {
                    ShellCommandInputField(
                        label = "Enabled Command (turning ON)",
                        command = onCommand,
                        onCommandChange = { onCommand = it },
                        selectedMode = selectedMode,
                        placeholder = "e.g. svc wifi enable"
                    )
                    ShellCommandInputField(
                        label = "Disabled Command (turning OFF)",
                        command = offCommand,
                        onCommandChange = { offCommand = it },
                        selectedMode = selectedMode,
                        placeholder = "e.g. svc wifi disable"
                    )
                } else if (visualState == TileVisualState.ENABLED) {
                    ShellCommandInputField(
                        label = "Shell Command (Enabled state)",
                        command = onCommand,
                        onCommandChange = { onCommand = it },
                        selectedMode = selectedMode,
                        placeholder = "e.g. echo 'Tile active'"
                    )
                } else {
                    ShellCommandInputField(
                        label = "Shell Command (Disabled state)",
                        command = offCommand,
                        onCommandChange = { offCommand = it },
                        selectedMode = selectedMode,
                        placeholder = "e.g. echo 'Tile inactive'"
                    )
                }

                // Advance option dropdown menu
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { advanceExpanded = !advanceExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Advance option", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(
                                imageVector = if (advanceExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        if (advanceExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pop-up message", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Output echo commands as pop-up messages. If or else outputs multiple messages separately.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showPopups,
                                    onCheckedChange = { showPopups = it }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val canSave = when (visualState) {
                TileVisualState.TOGGLE -> onCommand.isNotBlank() || offCommand.isNotBlank()
                TileVisualState.ENABLED -> onCommand.isNotBlank()
                TileVisualState.DISABLED -> offCommand.isNotBlank()
            }
            Button(
                onClick = {
                    if (canSave) {
                        val finalPrimaryName = if (visualState == TileVisualState.TOGGLE) {
                            onTileName.ifBlank { offTileName.ifBlank { "Shell Toggle Tile" } }
                        } else {
                            name.ifBlank { "Shell Command" }
                        }
                        val effectiveCmd = when (visualState) {
                            TileVisualState.TOGGLE -> onCommand.ifBlank { offCommand }
                            TileVisualState.DISABLED -> offCommand
                            TileVisualState.ENABLED -> onCommand
                        }
                        onConfirm(
                            finalPrimaryName,
                            effectiveCmd.trim(),
                            onCommand.trim(),
                            offCommand.trim(),
                            visualState,
                            onTileName.trim(),
                            offTileName.trim(),
                            onIconConfig,
                            offIconConfig,
                            showPopups,
                            emptyList()
                        )
                    }
                },
                enabled = canSave
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun IconPresetsDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSelectPreset: (key: String) -> Unit,
    onSelectCustomFile: ((filePath: String) -> Unit)? = null,
    onSelectAppIcon: ((packageName: String) -> Unit)? = null
) {
    val context = LocalContext.current
    var showAppPickerInPresets by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = IconProcessor.saveCustomImage(context, it)
            if (savedPath != null) {
                onSelectCustomFile?.invoke(savedPath)
            }
        }
    }

    if (showAppPickerInPresets) {
        AppPickerSheet(
            onDismiss = { showAppPickerInPresets = false },
            onAppSelected = { app ->
                showAppPickerInPresets = false
                onSelectAppIcon?.invoke(app.packageName)
                onDismiss()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Preset Icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(280.dp).fillMaxWidth()
            ) {
                items(IconProcessor.PRESET_ICONS) { (key, label) ->
                    val isSelected = currentKey == key
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val bmp = remember(key, isDark) {
                        IconProcessor.processIconBitmap(
                            context,
                            IconConfig(sourceType = IconSourceType.PRESET, sourceData = key),
                            isDarkTheme = isDark
                        )
                    }
                    Card(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectPreset(key) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp).fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = label,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(label, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onSelectCustomFile != null) {
                    OutlinedButton(
                        onClick = { imagePicker.launch("*/*") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick File", fontSize = 11.sp)
                    }
                }
                if (onSelectAppIcon != null) {
                    OutlinedButton(
                        onClick = { showAppPickerInPresets = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("From Apps", fontSize = 11.sp)
                    }
                }
            }
        }
    )
}
