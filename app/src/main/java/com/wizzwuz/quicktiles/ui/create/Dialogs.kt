package com.wizzwuz.quicktiles.ui.create

import android.graphics.Bitmap
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.IconProcessor
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

@Composable
fun ShellDialog(
    initialName: String = "",
    initialCommand: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, command: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var command by remember { mutableStateOf(initialCommand) }
    val shizukuStatus by ShizukuHelper.statusFlow.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shell Script Tile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Script / Command Name") },
                    placeholder = { Text("e.g. Restart SystemUI") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Shell Script / Commands") },
                    placeholder = { Text("echo 'Hello'\ninput tap 500 500") },
                    minLines = 4,
                    maxLines = 6,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                val isPermRequired = shizukuStatus == com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (shizukuStatus) {
                            com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> MaterialTheme.colorScheme.primaryContainer
                            com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> MaterialTheme.colorScheme.tertiaryContainer
                            com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = when (shizukuStatus) {
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.AUTHORIZED -> "Shizuku: Active & Authorized (UID 2000)"
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.PERMISSION_REQUIRED -> "Shizuku: Running (Permission Required)"
                                    com.wizzwuz.quicktiles.util.ShizukuStatus.NOT_RUNNING -> "Shizuku: Not Running (Will use Root / sh)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (isPermRequired) {
                            Button(
                                onClick = {
                                    ShizukuHelper.requestShizukuPermission()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Request Shizuku Permission", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (command.isNotBlank()) {
                        val finalName = if (name.isNotBlank()) name else "Shell Command"
                        onConfirm(finalName, command.trim())
                    }
                },
                enabled = command.isNotBlank()
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
