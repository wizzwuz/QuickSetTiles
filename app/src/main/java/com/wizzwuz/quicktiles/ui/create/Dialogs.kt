package com.wizzwuz.quicktiles.ui.create

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
    onSelect: (name: String, intentUri: String, iconData: String) -> Unit
) {
    val context = LocalContext.current
    var shortcuts by remember { mutableStateOf<List<ShortcutProviderItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        shortcuts = AppHelper.getShortcutProviders(context)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select App Shortcut") },
        text = {
            Box(modifier = Modifier.height(350.dp).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (shortcuts.isEmpty()) {
                    Text(
                        "No shortcut providers found on this device.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(shortcuts) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val shortcutIntentUri = "intent:#Intent;component=${item.packageName}/${item.activityName};end"
                                        onSelect(item.appLabel, shortcutIntentUri, item.packageName)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val bmp = remember(item.icon) { IconProcessor.drawableToBitmap(item.icon) }
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = item.appLabel,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Column {
                                        Text(item.appLabel, fontWeight = FontWeight.Bold, maxLines = 1)
                                        Text(item.packageName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
            trailingIcon = {
                IconButton(
                    onClick = { showFullscreenEditor = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text(
                        text = "<>",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
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
        offIconConfig: IconConfig?
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

    var showPresetPickerForOn by remember { mutableStateOf(false) }
    var showPresetPickerForOff by remember { mutableStateOf(false) }

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
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configure Shell Tile")
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
                        label = { Text("Enabled", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = visualState == TileVisualState.DISABLED,
                        onClick = { visualState = TileVisualState.DISABLED },
                        label = { Text("Disabled", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = visualState == TileVisualState.TOGGLE,
                        onClick = { visualState = TileVisualState.TOGGLE },
                        label = { Text("Both", fontSize = 11.sp) },
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
                Text("Tile Icon(s)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (visualState == TileVisualState.TOGGLE) {
                    // Enabled Icon row
                    val onBmp = remember(onIconConfig) { IconProcessor.processIconBitmap(context, onIconConfig, 40) }
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
                                    Text("Preset: ${onIconConfig.sourceData}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val offBmp = remember(offIconConfig) { IconProcessor.processIconBitmap(context, offIconConfig, 40) }
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
                                    Text("Preset: ${offIconConfig.sourceData}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val bmp = remember(currentCfg) { IconProcessor.processIconBitmap(context, currentCfg, 40) }
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
                                    Text("Preset: ${currentCfg.sourceData}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            offIconConfig
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
    onSelectPreset: (key: String) -> Unit
) {
    val context = LocalContext.current
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
                    val bmp = remember(key) {
                        IconProcessor.processIconBitmap(
                            context,
                            IconConfig(sourceType = IconSourceType.PRESET, sourceData = key)
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
