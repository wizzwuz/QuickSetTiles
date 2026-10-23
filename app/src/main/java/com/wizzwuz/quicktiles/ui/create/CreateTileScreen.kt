package com.wizzwuz.quicktiles.ui.create

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.model.TileVisualState
import com.wizzwuz.quicktiles.util.IconProcessor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTileScreen(
    initialTile: TileItem? = null,
    onBack: () -> Unit,
    onOpenActivityPicker: () -> Unit,
    onSaveTile: (TileItem) -> Unit
) {
    val context = LocalContext.current

    var tileName by remember { mutableStateOf(initialTile?.name ?: "") }
    var selectedType by remember { mutableStateOf<TileType?>(initialTile?.type) }
    var targetData by remember { mutableStateOf(initialTile?.targetData ?: "") }

    var visualState by remember { mutableStateOf(initialTile?.visualState ?: TileVisualState.ENABLED) }
    var onCommand by remember {
        mutableStateOf(
            initialTile?.onCommand?.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState != TileVisualState.DISABLED) initialTile.targetData else ""
            } ?: (if (initialTile?.type == TileType.SHELL && initialTile.visualState != TileVisualState.DISABLED) initialTile.targetData else "")
        )
    }
    var offCommand by remember {
        mutableStateOf(
            initialTile?.offCommand?.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState == TileVisualState.DISABLED) initialTile.targetData else ""
            } ?: (if (initialTile?.type == TileType.SHELL && initialTile.visualState == TileVisualState.DISABLED) initialTile.targetData else "")
        )
    }
    var collapseOnTap by remember { mutableStateOf(initialTile?.collapseOnTap ?: true) }
    var currentToggleOn by remember { mutableStateOf(initialTile?.currentToggleOn ?: true) }
    var onTileName by remember { mutableStateOf(initialTile?.onTileName ?: "") }
    var offTileName by remember { mutableStateOf(initialTile?.offTileName ?: "") }
    var onIconConfig by remember { mutableStateOf(initialTile?.onIconConfig) }
    var offIconConfig by remember { mutableStateOf(initialTile?.offIconConfig) }

    // Icon state
    var iconConfig by remember { mutableStateOf(initialTile?.iconConfig ?: IconConfig()) }
    var isCustomizeExpanded by remember { mutableStateOf(false) }

    // Dialog state
    var showAppPicker by remember { mutableStateOf(false) }
    var showWebsiteDialog by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var showIntentDialog by remember { mutableStateOf(false) }
    var showShellDialog by remember { mutableStateOf(false) }
    var showPresetsDialog by remember { mutableStateOf(false) }

    // File Pickers
    val customImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = IconProcessor.saveCustomImage(context, it)
            if (savedPath != null) {
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = savedPath
                )
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Throwable) {
                // Ignore if not persistable
            }
            selectedType = TileType.FILES
            targetData = it.toString()
            if (tileName.isBlank()) {
                val fileName = it.lastPathSegment?.substringAfterLast('/') ?: "File Tile"
                tileName = fileName
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Throwable) {
                // Ignore if not persistable
            }
            selectedType = TileType.FOLDERS
            targetData = it.toString()
            if (tileName.isBlank()) {
                val folderName = it.lastPathSegment?.substringAfterLast(':') ?: "Folder Tile"
                tileName = folderName
            }
        }
    }

    // Dynamic Icon Bitmap Preview
    val previewBitmap = remember(iconConfig) {
        IconProcessor.processIconBitmap(context, iconConfig, targetPixelSize = 120)
    }

    val saveTileAction = {
        if (tileName.isBlank()) {
            Toast.makeText(context, "Please enter a tile name", Toast.LENGTH_SHORT).show()
        } else if (selectedType == null) {
            Toast.makeText(context, "Please select and configure a tile type", Toast.LENGTH_SHORT).show()
        } else {
            val effectiveTargetData = if (selectedType == TileType.SHELL) {
                when (visualState) {
                    TileVisualState.TOGGLE -> onCommand.ifBlank { offCommand.ifBlank { targetData } }
                    TileVisualState.DISABLED -> offCommand.ifBlank { onCommand.ifBlank { targetData } }
                    TileVisualState.ENABLED -> onCommand.ifBlank { targetData }
                }
            } else {
                targetData
            }

            if (effectiveTargetData.isBlank()) {
                Toast.makeText(context, "Please configure tile command or target data", Toast.LENGTH_SHORT).show()
            } else {
                val tile = (initialTile ?: TileItem(
                    name = tileName.trim(),
                    type = selectedType!!,
                    targetData = effectiveTargetData.trim(),
                    iconConfig = iconConfig,
                    isActive = true,
                    visualState = visualState,
                    onCommand = onCommand.trim(),
                    offCommand = offCommand.trim(),
                    currentToggleOn = currentToggleOn,
                    collapseOnTap = collapseOnTap,
                    onTileName = onTileName.trim(),
                    offTileName = offTileName.trim(),
                    onIconConfig = onIconConfig,
                    offIconConfig = offIconConfig
                )).copy(
                    name = tileName.trim(),
                    type = selectedType!!,
                    targetData = effectiveTargetData.trim(),
                    iconConfig = iconConfig,
                    visualState = visualState,
                    onCommand = onCommand.trim(),
                    offCommand = offCommand.trim(),
                    currentToggleOn = currentToggleOn,
                    collapseOnTap = collapseOnTap,
                    onTileName = onTileName.trim(),
                    offTileName = offTileName.trim(),
                    onIconConfig = onIconConfig,
                    offIconConfig = offIconConfig
                )
                onSaveTile(tile)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialTile == null) "Create Quick Tile" else "Edit Quick Tile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = saveTileAction) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            // 1. Text Field for Tile Name
            OutlinedTextField(
                value = tileName,
                onValueChange = { tileName = it },
                label = { Text("Tile Name") },
                placeholder = { Text("Enter tile name") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                trailingIcon = {
                    if (tileName.isNotEmpty()) {
                        IconButton(onClick = { tileName = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 2. Icon Category Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Icon Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Live Icon Preview Box
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Tile Icon Preview",
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        // Icon Pickers Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = { customImagePicker.launch("image/*") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick File", fontSize = 12.sp)
                                }

                                FilledTonalButton(
                                    onClick = { showPresetsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Presets", fontSize = 12.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { isCustomizeExpanded = !isCustomizeExpanded },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isCustomizeExpanded) "Hide Sliders" else "Customize Sliders", fontSize = 12.sp)
                            }
                        }
                    }

                    // Expandable Sliders for Customization: outline thickness, outline threshold, icon size
                    AnimatedVisibility(visible = isCustomizeExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Icon Customization Controls", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                            // Outline Style Selector
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Outline Style", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        "OUTLINE" to "Outline",
                                        "GLOW" to "Glow",
                                        "FROSTED" to "Frosted",
                                        "SOLID" to "Solid"
                                    ).forEach { (styleKey, label) ->
                                        FilterChip(
                                            selected = iconConfig.outlineStyle.equals(styleKey, ignoreCase = true),
                                            onClick = { iconConfig = iconConfig.copy(outlineStyle = styleKey) },
                                            label = { Text(label, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Outline Thickness Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Outline Thickness", fontSize = 12.sp)
                                    Text("${String.format("%.1f", iconConfig.outlineThickness)} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = iconConfig.outlineThickness,
                                    onValueChange = { iconConfig = iconConfig.copy(outlineThickness = it) },
                                    valueRange = 0f..10f
                                )
                            }

                            // Outline Threshold Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Outline Threshold", fontSize = 12.sp)
                                    Text("${(iconConfig.outlineThreshold * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = iconConfig.outlineThreshold,
                                    onValueChange = { iconConfig = iconConfig.copy(outlineThreshold = it) },
                                    valueRange = 0.05f..1.0f
                                )
                            }

                            // Icon Size Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Icon Size", fontSize = 12.sp)
                                    Text("${iconConfig.iconSize.toInt()} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = iconConfig.iconSize,
                                    onValueChange = { iconConfig = iconConfig.copy(iconSize = it) },
                                    valueRange = 16f..64f
                                )
                            }
                        }
                    }
                }
            }

            // Current Configuration Status
            if (selectedType != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Type: ${selectedType?.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = targetData.take(60),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. Tile Types Selection Section
            Text(
                text = "Select Tile Type",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 8 Tile Type Buttons arranged cleanly
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Apps",
                        subtitle = "Select any app",
                        icon = Icons.Default.Apps,
                        isSelected = selectedType == TileType.APPS,
                        onClick = { showAppPicker = true }
                    )

                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Website",
                        subtitle = "Open URL in browser",
                        icon = Icons.Default.Language,
                        isSelected = selectedType == TileType.WEBSITE,
                        onClick = { showWebsiteDialog = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Shortcut",
                        subtitle = "Inbuilt app shortcut",
                        icon = Icons.Default.OpenInNew,
                        isSelected = selectedType == TileType.SHORTCUT,
                        onClick = { showShortcutDialog = true }
                    )

                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Activity",
                        subtitle = "Inspect & test run",
                        icon = Icons.Default.Extension,
                        isSelected = selectedType == TileType.ACTIVITY,
                        onClick = { onOpenActivityPicker() }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Intent",
                        subtitle = "Custom action/intent",
                        icon = Icons.Default.Send,
                        isSelected = selectedType == TileType.INTENT,
                        onClick = { showIntentDialog = true }
                    )

                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Shell",
                        subtitle = "Shizuku & root script",
                        icon = Icons.Default.Terminal,
                        isSelected = selectedType == TileType.SHELL,
                        onClick = { showShellDialog = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Files",
                        subtitle = "Open selected file",
                        icon = Icons.Default.InsertDriveFile,
                        isSelected = selectedType == TileType.FILES,
                        onClick = { filePicker.launch(arrayOf("*/*")) }
                    )

                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Folders",
                        subtitle = "Open directory",
                        icon = Icons.Default.Folder,
                        isSelected = selectedType == TileType.FOLDERS,
                        onClick = { folderPicker.launch(null) }
                    )
                }
            }

            // --- TILE BEHAVIOR AND APPEARANCE (ENABLED, DISABLED, OR BOTH) ---
            if (selectedType != null && selectedType != TileType.SHELL) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Tile Behavior & Appearance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Choose whether this tile appears enabled, disabled, or toggles between both states on tap",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = visualState == TileVisualState.ENABLED,
                                onClick = { visualState = TileVisualState.ENABLED },
                                label = { Text("Enabled", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = visualState == TileVisualState.DISABLED,
                                onClick = { visualState = TileVisualState.DISABLED },
                                label = { Text("Disabled", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = visualState == TileVisualState.TOGGLE,
                                onClick = { visualState = TileVisualState.TOGGLE },
                                label = { Text("Both (Toggle)", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- COLLAPSE PANEL ON CLICK (BELOW TILE CATEGORIES) ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { collapseOnTap = !collapseOnTap }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Collapse panel on click",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Automatically close Quick Settings notification shade after tapping this tile",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = collapseOnTap,
                        onCheckedChange = { collapseOnTap = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = saveTileAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (initialTile == null) "Create Quick Tile" else "Save Changes", fontSize = 16.sp)
            }
        }
    }

    // Modal Sheets and Dialogs
    if (showAppPicker) {
        AppPickerSheet(
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                showAppPicker = false
                selectedType = TileType.APPS
                targetData = app.packageName
                if (tileName.isBlank()) tileName = app.label
                // Immediately draw outline of its icon element and set it as default tile icon!
                iconConfig = IconConfig(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = app.packageName,
                    outlineThickness = 2.5f,
                    outlineThreshold = 0.4f,
                    iconSize = 32f,
                    outlineStyle = "OUTLINE"
                )
            }
        )
    }

    if (showWebsiteDialog) {
        WebsiteDialog(
            initialName = tileName,
            initialUrl = if (selectedType == TileType.WEBSITE) targetData else "https://",
            onDismiss = { showWebsiteDialog = false },
            onConfirm = { name, url ->
                showWebsiteDialog = false
                selectedType = TileType.WEBSITE
                targetData = url
                if (tileName.isBlank()) tileName = name
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = "web"
                )
            }
        )
    }

    if (showShortcutDialog) {
        ShortcutDialog(
            onDismiss = { showShortcutDialog = false },
            onSelect = { name, shortcutUri, pkg ->
                showShortcutDialog = false
                selectedType = TileType.SHORTCUT
                targetData = shortcutUri
                if (tileName.isBlank()) tileName = name
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = pkg
                )
            }
        )
    }

    if (showIntentDialog) {
        IntentDialog(
            initialName = tileName,
            initialAction = if (selectedType == TileType.INTENT) targetData else "",
            onDismiss = { showIntentDialog = false },
            onConfirm = { name, action, pkg ->
                showIntentDialog = false
                selectedType = TileType.INTENT
                targetData = if (pkg.isNotBlank()) "intent:#Intent;action=$action;package=$pkg;end" else action
                if (tileName.isBlank()) tileName = name
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = "settings"
                )
            }
        )
    }

    if (showShellDialog) {
        ShellDialog(
            initialName = tileName,
            initialCommand = targetData,
            initialOnCommand = onCommand,
            initialOffCommand = offCommand,
            initialVisualState = visualState,
            initialOnTileName = onTileName,
            initialOffTileName = offTileName,
            initialOnIconConfig = onIconConfig,
            initialOffIconConfig = offIconConfig,
            onDismiss = { showShellDialog = false },
            onConfirm = { name, cmd, onCmd, offCmd, vState, onName, offName, onIcon, offIcon ->
                showShellDialog = false
                selectedType = TileType.SHELL
                targetData = cmd
                onCommand = onCmd
                offCommand = offCmd
                visualState = vState
                onTileName = onName
                offTileName = offName
                onIconConfig = onIcon
                offIconConfig = offIcon
                if (tileName.isBlank()) tileName = name
                onIcon?.let { iconConfig = it }
            }
        )
    }

    if (showPresetsDialog) {
        IconPresetsDialog(
            currentKey = iconConfig.sourceData,
            onDismiss = { showPresetsDialog = false },
            onSelectPreset = { key ->
                showPresetsDialog = false
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = key
                )
            },
            onSelectCustomFile = { path ->
                showPresetsDialog = false
                iconConfig = iconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = path
                )
            }
        )
    }
}

@Composable
fun TileTypeButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
