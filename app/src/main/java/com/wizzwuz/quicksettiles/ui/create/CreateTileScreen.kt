package com.wizzwuz.quicksettiles.ui.create

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicksettiles.model.IconConfig
import com.wizzwuz.quicksettiles.data.TileRepository
import com.wizzwuz.quicksettiles.model.IconSourceType
import com.wizzwuz.quicksettiles.model.ShellStep
import com.wizzwuz.quicksettiles.model.TileItem
import com.wizzwuz.quicksettiles.model.TileType
import com.wizzwuz.quicksettiles.model.TileVisualState
import com.wizzwuz.quicksettiles.util.IconProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

private val IconConfigSaver: Saver<IconConfig, String> = Saver(
    save = { it.toJson().toString() },
    restore = { str ->
        try {
            IconConfig.fromJson(JSONObject(str))
        } catch (_: Throwable) {
            IconConfig()
        }
    }
)

private val NullableIconConfigSaver: Saver<IconConfig?, String> = Saver(
    save = { it?.toJson()?.toString() },
    restore = { str ->
        try {
            IconConfig.fromJson(JSONObject(str))
        } catch (_: Throwable) {
            null
        }
    }
)

private val ShellStepListSaver: Saver<List<ShellStep>, String> = Saver(
    save = { steps ->
        val arr = JSONArray()
        steps.forEach { arr.put(it.toJson()) }
        arr.toString()
    },
    restore = { str ->
        try {
            val arr = JSONArray(str)
            (0 until arr.length()).map { ShellStep.fromJson(arr.getJSONObject(it)) }
        } catch (_: Throwable) {
            emptyList()
        }
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTileScreen(
    initialTile: TileItem? = null,
    onBack: () -> Unit,
    onOpenActivityPicker: () -> Unit,
    onSaveTile: (TileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var tileName by rememberSaveable { mutableStateOf(initialTile?.name ?: "") }
    var selectedType by rememberSaveable { mutableStateOf<TileType?>(initialTile?.type) }
    var targetData by rememberSaveable { mutableStateOf(initialTile?.targetData ?: "") }

    var visualState by rememberSaveable { mutableStateOf(initialTile?.visualState ?: TileVisualState.ENABLED) }
    var onCommand by rememberSaveable {
        mutableStateOf(
            initialTile?.onCommand?.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState != TileVisualState.DISABLED) initialTile.targetData else ""
            } ?: (if (initialTile?.type == TileType.SHELL && initialTile.visualState != TileVisualState.DISABLED) initialTile.targetData else "")
        )
    }
    var offCommand by rememberSaveable {
        mutableStateOf(
            initialTile?.offCommand?.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState == TileVisualState.DISABLED) initialTile.targetData else ""
            } ?: (if (initialTile?.type == TileType.SHELL && initialTile.visualState == TileVisualState.DISABLED) initialTile.targetData else "")
        )
    }
    var collapseOnTap by rememberSaveable { mutableStateOf(initialTile?.collapseOnTap ?: true) }
    var allowOnLockScreen by rememberSaveable { mutableStateOf(initialTile?.allowOnLockScreen ?: true) }
    var hideFromLockScreen by rememberSaveable { mutableStateOf(initialTile?.hideFromLockScreen ?: false) }
    var isLockScreenOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var currentToggleOn by rememberSaveable { mutableStateOf(initialTile?.currentToggleOn ?: true) }
    var onTileName by rememberSaveable { mutableStateOf(initialTile?.onTileName ?: "") }
    var offTileName by rememberSaveable { mutableStateOf(initialTile?.offTileName ?: "") }
    var onIconConfig by rememberSaveable(stateSaver = NullableIconConfigSaver) { mutableStateOf(initialTile?.onIconConfig) }
    var offIconConfig by rememberSaveable(stateSaver = NullableIconConfigSaver) { mutableStateOf(initialTile?.offIconConfig) }
    var showPopups by rememberSaveable { mutableStateOf(initialTile?.showPopups ?: false) }
    var multiShellSteps by rememberSaveable(stateSaver = ShellStepListSaver) { mutableStateOf(initialTile?.multiShellSteps ?: emptyList()) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    // Icon state
    var iconConfig by rememberSaveable(stateSaver = IconConfigSaver) { mutableStateOf(initialTile?.iconConfig ?: IconConfig()) }
    var isCustomizeExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog state
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var showAppIconPicker by rememberSaveable { mutableStateOf(false) }
    var showWebsiteDialog by rememberSaveable { mutableStateOf(false) }
    var showShortcutDialog by rememberSaveable { mutableStateOf(false) }
    var showIntentDialog by rememberSaveable { mutableStateOf(false) }
    var showShellDialog by rememberSaveable { mutableStateOf(false) }
    var showMultipleShellDialog by rememberSaveable { mutableStateOf(false) }
    var showPresetsDialog by rememberSaveable { mutableStateOf(false) }
    var showCollectionCreator by rememberSaveable { mutableStateOf(initialTile?.type == TileType.COLLECTION) }
    var showSplitAppsDialog by rememberSaveable { mutableStateOf(initialTile?.type == TileType.SPLIT_APPS) }
    var showContactsDialog by rememberSaveable { mutableStateOf(false) }
    var contactPickerMode by rememberSaveable { mutableStateOf("dial") }

    LaunchedEffect(initialTile) {
        if (initialTile != null) {
            tileName = initialTile.name
            selectedType = initialTile.type
            targetData = initialTile.targetData
            visualState = initialTile.visualState
            onCommand = initialTile.onCommand.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState != TileVisualState.DISABLED) initialTile.targetData else ""
            }
            offCommand = initialTile.offCommand.ifBlank {
                if (initialTile.type == TileType.SHELL && initialTile.visualState == TileVisualState.DISABLED) initialTile.targetData else ""
            }
            collapseOnTap = initialTile.collapseOnTap
            allowOnLockScreen = initialTile.allowOnLockScreen
            hideFromLockScreen = initialTile.hideFromLockScreen
            currentToggleOn = initialTile.currentToggleOn
            onTileName = initialTile.onTileName
            offTileName = initialTile.offTileName
            onIconConfig = initialTile.onIconConfig
            offIconConfig = initialTile.offIconConfig
            showPopups = initialTile.showPopups
            multiShellSteps = initialTile.multiShellSteps
            iconConfig = initialTile.iconConfig
        }
    }

    // File Pickers - support all image formats including SVG, PNG, WebP, JPG, GIF
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

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data?.data != null) {
            val contactUri = result.data!!.data!!
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI
            )
            try {
                context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                        val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                        val number = if (numberIndex >= 0) cursor.getString(numberIndex) ?: "" else ""
                        val photoUriStr = if (photoIndex >= 0) cursor.getString(photoIndex) else null

                        tileName = name.ifBlank { number }
                        selectedType = TileType.CONTACTS
                        val mode = contactPickerMode
                        targetData = if (mode == "dial") "tel:$number" else "smsto:$number"

                        coroutineScope.launch(Dispatchers.IO) {
                            var photoLoaded = false
                            if (!photoUriStr.isNullOrBlank()) {
                                try {
                                    val photoUri = Uri.parse(photoUriStr)
                                    context.contentResolver.openInputStream(photoUri)?.use { stream ->
                                        val bmp = BitmapFactory.decodeStream(stream)
                                        if (bmp != null) {
                                            val savedPath = IconProcessor.saveBitmapToFile(context, bmp, "contact_${System.currentTimeMillis()}")
                                            if (savedPath != null) {
                                                iconConfig = iconConfig.copy(
                                                    sourceType = IconSourceType.CUSTOM_FILE,
                                                    sourceData = savedPath
                                                )
                                                photoLoaded = true
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {}
                            }
                            if (!photoLoaded) {
                                val presetName = if (mode == "dial") "phone" else "chat"
                                iconConfig = iconConfig.copy(
                                    sourceType = IconSourceType.PRESET,
                                    sourceData = presetName
                                )
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "Error picking contact: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dynamic Icon Bitmap Preview
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val previewBitmap = remember(iconConfig, isDark) {
        IconProcessor.processIconBitmap(context, iconConfig, targetPixelSize = 120, isDarkTheme = isDark)
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

            if (selectedType != TileType.COLLECTION && effectiveTargetData.isBlank()) {
                Toast.makeText(context, "Please configure tile command or target data", Toast.LENGTH_SHORT).show()
            } else {
                val finalName = tileName.trim()
                val tile = (initialTile ?: TileItem(
                    name = finalName,
                    type = selectedType!!,
                    targetData = effectiveTargetData.trim(),
                    iconConfig = iconConfig,
                    isActive = true,
                    isAddedToQs = false,
                    visualState = visualState,
                    onCommand = onCommand.trim(),
                    offCommand = offCommand.trim(),
                    currentToggleOn = currentToggleOn,
                    collapseOnTap = collapseOnTap,
                    onTileName = finalName,
                    offTileName = finalName,
                    onIconConfig = onIconConfig,
                    offIconConfig = offIconConfig,
                    showPopups = showPopups,
                    multiShellSteps = multiShellSteps,
                    allowOnLockScreen = allowOnLockScreen,
                    hideFromLockScreen = hideFromLockScreen
                )).copy(
                    name = finalName,
                    type = selectedType!!,
                    targetData = effectiveTargetData.trim(),
                    iconConfig = iconConfig,
                    visualState = visualState,
                    onCommand = onCommand.trim(),
                    offCommand = offCommand.trim(),
                    currentToggleOn = currentToggleOn,
                    collapseOnTap = collapseOnTap,
                    showPopups = showPopups,
                    multiShellSteps = multiShellSteps,
                    allowOnLockScreen = allowOnLockScreen,
                    hideFromLockScreen = hideFromLockScreen,
                    onTileName = when (visualState) {
                        TileVisualState.TOGGLE -> if (onTileName.isNotBlank() && onTileName != initialTile?.name) onTileName.trim() else finalName
                        else -> finalName
                    },
                    offTileName = when (visualState) {
                        TileVisualState.TOGGLE -> if (offTileName.isNotBlank() && offTileName != initialTile?.name) offTileName.trim() else finalName
                        else -> finalName
                    },
                    onIconConfig = when (visualState) {
                        TileVisualState.TOGGLE -> (onIconConfig ?: iconConfig)
                        TileVisualState.ENABLED -> (onIconConfig ?: iconConfig)
                        TileVisualState.DISABLED -> iconConfig
                    },
                    offIconConfig = when (visualState) {
                        TileVisualState.TOGGLE -> offIconConfig
                        TileVisualState.DISABLED -> (offIconConfig ?: iconConfig)
                        TileVisualState.ENABLED -> null
                    }
                )
                onSaveTile(tile)
            }
        }
    }

    if (showDeleteConfirmation && initialTile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Tile") },
            text = { Text("Are you sure you want to delete '${initialTile.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        val repo = TileRepository.getInstance(context)
                        repo.deleteTile(initialTile.id)
                        showDeleteConfirmation = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCollectionCreator) {
        CollectionCreatorScreen(
            initialTile = if (initialTile?.type == TileType.COLLECTION) initialTile else null,
            onBack = {
                if (initialTile?.type == TileType.COLLECTION) {
                    onBack()
                } else {
                    showCollectionCreator = false
                }
            },
            onSaveCollection = { collectionTile: TileItem, memberTiles: List<TileItem> ->
                val repo = TileRepository.getInstance(context)
                memberTiles.forEach { member ->
                    repo.saveTile(member.copy(isActive = false, slotNumber = -1, isAddedToQs = false))
                }
                onSaveTile(collectionTile)
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    if (initialTile != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Tile",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { customImagePicker.launch("*/*") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pick File", fontSize = 11.sp)
                                }

                                FilledTonalButton(
                                    onClick = { showPresetsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Presets", fontSize = 11.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { isCustomizeExpanded = !isCustomizeExpanded },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isCustomizeExpanded) "Hide" else "Customize", fontSize = 12.sp)
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
                                    valueRange = 16f..160f
                                )
                            }
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
                        title = if (selectedType == TileType.SPLIT_APPS) "Split Apps" else "Apps",
                        subtitle = if (selectedType == TileType.SPLIT_APPS) "Dual split screen apps" else "Select any app",
                        icon = if (selectedType == TileType.SPLIT_APPS) Icons.Default.ViewStream else Icons.Default.Apps,
                        isSelected = selectedType == TileType.APPS || selectedType == TileType.SPLIT_APPS,
                        onClick = {
                            if (selectedType == TileType.SPLIT_APPS) {
                                showSplitAppsDialog = true
                            } else {
                                showAppPicker = true
                            }
                        }
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
                        onClick = {
                            if (multiShellSteps.isNotEmpty()) {
                                showMultipleShellDialog = true
                            } else {
                                showShellDialog = true
                            }
                        }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Contacts",
                        subtitle = "Dial or message",
                        icon = Icons.Default.Call,
                        isSelected = selectedType == TileType.CONTACTS,
                        onClick = { showContactsDialog = true }
                    )

                    TileTypeButton(
                        modifier = Modifier.weight(1f),
                        title = "Collections",
                        subtitle = "Create a collection",
                        icon = Icons.Default.Collections,
                        isSelected = selectedType == TileType.COLLECTION,
                        onClick = { showCollectionCreator = true; selectedType = TileType.COLLECTION }
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
                                label = { Text("Enabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = visualState == TileVisualState.DISABLED,
                                onClick = { visualState = TileVisualState.DISABLED },
                                label = { Text("Disabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = visualState == TileVisualState.TOGGLE,
                                onClick = { visualState = TileVisualState.TOGGLE },
                                label = { Text("Both", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
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
                    }
                    Switch(
                        checked = collapseOnTap,
                        onCheckedChange = { collapseOnTap = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- ALLOW ON LOCK SCREEN (BELOW COLLAPSE PANEL ON CLICK) ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!allowOnLockScreen) {
                                    isLockScreenOptionsExpanded = !isLockScreenOptionsExpanded
                                } else {
                                    allowOnLockScreen = false
                                    isLockScreenOptionsExpanded = true
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Allow on lock screen",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!allowOnLockScreen) {
                                    Icon(
                                        imageVector = if (isLockScreenOptionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (allowOnLockScreen) {
                                    "Custom quick tile can be accessed from lock screen"
                                } else {
                                    "Tile cannot be accessed from lock screen without unlocking"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowOnLockScreen,
                            onCheckedChange = { isChecked ->
                                allowOnLockScreen = isChecked
                                if (isChecked) {
                                    isLockScreenOptionsExpanded = false
                                    hideFromLockScreen = false
                                } else {
                                    isLockScreenOptionsExpanded = true
                                }
                            }
                        )
                    }

                    // Dropdown menu option: "Hide from lock screen"
                    AnimatedVisibility(
                        visible = !allowOnLockScreen && isLockScreenOptionsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hideFromLockScreen = !hideFromLockScreen },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Hide from lock screen",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Custom quick tile will not show in lock screen and unlocking the screen it should show in quick settings panel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hideFromLockScreen,
                                    onCheckedChange = { hideFromLockScreen = it }
                                )
                            }
                        }
                    }
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
                tileName = app.label
                // Immediately draw outline of its icon element and set it as default tile icon!
                iconConfig = IconConfig(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = app.packageName,
                    outlineThickness = 2.5f,
                    outlineThreshold = 0.4f,
                    iconSize = 32f,
                    outlineStyle = "OUTLINE"
                )
            },
            onOpenSplit = {
                showAppPicker = false
                showSplitAppsDialog = true
            }
        )
    }

    if (showSplitAppsDialog) {
        val splitParts = targetData.split("|")
        val initP1 = if (selectedType == TileType.SPLIT_APPS && splitParts.isNotEmpty()) splitParts[0] else null
        val initP2 = if (selectedType == TileType.SPLIT_APPS && splitParts.size > 1) splitParts[1] else null

        SplitAppsDialog(
            initialPkg1 = initP1,
            initialPkg2 = initP2,
            onBack = {
                showSplitAppsDialog = false
                showAppPicker = true
            },
            onDismiss = {
                showSplitAppsDialog = false
            },
            onConfirm = { p1, p2, l1, l2, icon1, icon2 ->
                showSplitAppsDialog = false
                selectedType = TileType.SPLIT_APPS
                targetData = "$p1|$p2"
                tileName = "$l1 | $l2"
                iconConfig = IconConfig(
                    sourceType = IconSourceType.APP_ICON,
                    sourceData = p1,
                    outlineThickness = 2.5f,
                    outlineThreshold = 0.4f,
                    iconSize = 32f,
                    outlineStyle = "OUTLINE"
                )
            }
        )
    }

    if (showContactsDialog) {
        ContactsChoiceDialog(
            onDismiss = { showContactsDialog = false },
            onSelectPhoneDial = {
                contactPickerMode = "dial"
                try {
                    contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                } catch (e: Throwable) {
                    Toast.makeText(context, "Could not open contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectMessage = {
                contactPickerMode = "message"
                try {
                    contactPickerLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                } catch (e: Throwable) {
                    Toast.makeText(context, "Could not open contacts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        val coroutineScope = rememberCoroutineScope()
        ShortcutDialog(
            onDismiss = { showShortcutDialog = false },
            onSelect = { name, shortcutUri, pkg, iconBitmap ->
                showShortcutDialog = false
                selectedType = TileType.SHORTCUT
                targetData = shortcutUri
                if (tileName.isBlank()) tileName = name
                if (iconBitmap != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val savedPath = IconProcessor.saveBitmapToFile(context, iconBitmap, "shortcut_${pkg}")
                        if (savedPath != null) {
                            iconConfig = iconConfig.copy(
                                sourceType = IconSourceType.CUSTOM_FILE,
                                sourceData = savedPath
                            )
                        } else {
                            iconConfig = iconConfig.copy(
                                sourceType = IconSourceType.APP_ICON,
                                sourceData = pkg
                            )
                        }
                    }
                } else {
                    iconConfig = iconConfig.copy(
                        sourceType = IconSourceType.APP_ICON,
                        sourceData = pkg
                    )
                }
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
            initialShowPopups = showPopups,
            initialMultiShellSteps = multiShellSteps,
            onDismiss = { showShellDialog = false },
            onSwitchToMultipleShell = {
                showShellDialog = false
                showMultipleShellDialog = true
            },
            onConfirm = { name, cmd, onCmd, offCmd, vState, onName, offName, onIcon, offIcon, popups, steps ->
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
                showPopups = popups
                multiShellSteps = steps
                if (tileName.isBlank()) tileName = name
                when (vState) {
                    TileVisualState.DISABLED -> {
                        offIcon?.let { iconConfig = it }
                    }
                    TileVisualState.TOGGLE -> {
                        onIcon?.let { iconConfig = it }
                    }
                    TileVisualState.ENABLED -> {
                        onIcon?.let { iconConfig = it }
                    }
                }
            }
        )
    }

    if (showMultipleShellDialog) {
        MultipleShellDialog(
            initialSteps = multiShellSteps,
            initialShowPopups = showPopups,
            onDismiss = { showMultipleShellDialog = false },
            onBackToSingleShell = {
                showMultipleShellDialog = false
                showShellDialog = true
            },
            onConfirm = { steps, popups ->
                showMultipleShellDialog = false
                selectedType = TileType.SHELL
                multiShellSteps = steps
                showPopups = popups
                val first = steps.firstOrNull()
                if (first != null) {
                    if (tileName.isBlank() || tileName == "Shell") tileName = first.name
                    targetData = first.command
                    visualState = first.visualState
                    iconConfig = first.iconConfig
                }
            }
        )
    }

    if (showCollectionCreator) {
        CollectionCreatorScreen(
            onBack = { showCollectionCreator = false },
            onSaveCollection = { collectionTile, memberTiles ->
                showCollectionCreator = false
                // Save all collection member tiles first
                memberTiles.forEach { onSaveTile(it) }
                selectedType = TileType.COLLECTION
                onSaveTile(collectionTile)
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
            },
            onSelectAppIcon = { pkg ->
                showPresetsDialog = false
                iconConfig = IconConfig(
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

    if (showAppIconPicker) {
        AppPickerSheet(
            onDismiss = { showAppIconPicker = false },
            onAppSelected = { app ->
                showAppIconPicker = false
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
        }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTileSheet(
    item: TileItem,
    onDismiss: () -> Unit,
    onSave: (TileItem) -> Unit,
    onDelete: (TileItem) -> Unit
) {
    val context = LocalContext.current
    var itemName by remember(item.id) { mutableStateOf(item.name) }
    var itemIconConfig by remember(item.id) { mutableStateOf(item.iconConfig) }
    var itemVisualState by remember(item.id) { mutableStateOf(item.visualState) }
    var itemCollapseCollectionOnClick by remember(item.id) { mutableStateOf(item.collapseCollectionOnClick) }
    var itemTargetData by remember(item.id) { mutableStateOf(item.targetData) }
    var itemOnCommand by remember(item.id) { mutableStateOf(item.onCommand) }
    var itemOffCommand by remember(item.id) { mutableStateOf(item.offCommand) }
    var itemOnTileName by remember(item.id) { mutableStateOf(item.onTileName) }
    var itemOffTileName by remember(item.id) { mutableStateOf(item.offTileName) }
    var itemOnIconConfig by remember(item.id) { mutableStateOf(item.onIconConfig) }
    var itemOffIconConfig by remember(item.id) { mutableStateOf(item.offIconConfig) }
    var itemShowPopups by remember(item.id) { mutableStateOf(item.showPopups) }
    var itemMultiShellSteps by remember(item.id) { mutableStateOf(item.multiShellSteps) }
    var showShellDialog by remember { mutableStateOf(false) }
    var showMultipleShellDialog by remember { mutableStateOf(false) }
    var showPresetsDialog by remember { mutableStateOf(false) }


    val customImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = IconProcessor.saveCustomImage(context, it)
            if (savedPath != null) {
                itemIconConfig = itemIconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = savedPath
                )
            }
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val previewBitmap = remember(itemIconConfig, isDark) {
        if (itemIconConfig.sourceType == IconSourceType.APP_ICON) {
            IconProcessor.loadRawBitmap(context, itemIconConfig, targetPixelSize = 120, isDarkTheme = isDark)
        } else if (itemIconConfig.outlineThickness > 0.5f) {
            IconProcessor.processIconBitmap(context, itemIconConfig, targetPixelSize = 120, isDarkTheme = isDark)
        } else {
            IconProcessor.loadRawBitmap(context, itemIconConfig, targetPixelSize = 120, isDarkTheme = isDark)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Tile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Tile",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // a. Icon category: With existing Icon preview, Pick File, Presets and Customise sliders
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { customImagePicker.launch("*/*") },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Pick File", fontSize = 11.sp)
                                }

                                FilledTonalButton(
                                    onClick = { showPresetsDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Presets", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // b. Tile Name text field: to change current tile name
            OutlinedTextField(
                value = itemName,
                onValueChange = { itemName = it },
                label = { Text("Tile Name") },
                placeholder = { Text("Enter tile name") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            // c. Apps behavior category: with existing Enabled, Disabled and Both buttons
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Apps Behavior Category",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = itemVisualState == TileVisualState.ENABLED,
                            onClick = { itemVisualState = TileVisualState.ENABLED },
                            label = { Text("Enabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = itemVisualState == TileVisualState.DISABLED,
                            onClick = { itemVisualState = TileVisualState.DISABLED },
                            label = { Text("Disabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = itemVisualState == TileVisualState.TOGGLE,
                            onClick = { itemVisualState = TileVisualState.TOGGLE },
                            label = { Text("Both", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // d. Collaspe collection on click options: this new option for individual tiles, if option is enabled upon clicking the tile, collections will collaspe
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { itemCollapseCollectionOnClick = !itemCollapseCollectionOnClick },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Collapse collection on click",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Dismiss collection popup when this tile is clicked",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = itemCollapseCollectionOnClick,
                        onCheckedChange = { itemCollapseCollectionOnClick = it }
                    )
                }
            }

            // Configure Shell Tile panel (only for shell tiles in collections)
            if (item.type == TileType.SHELL) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showShellDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Configure Shell Tile",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val summary = if (itemVisualState == TileVisualState.TOGGLE) {
                                "Toggle commands configured"
                            } else {
                                val c = if (itemVisualState == TileVisualState.ENABLED) itemOnCommand else itemOffCommand
                                c.ifBlank { itemTargetData }.take(35).ifBlank { "No command configured" }
                            }
                            Text(
                                text = summary,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (itemMultiShellSteps.isNotEmpty()) {
                                    showMultipleShellDialog = true
                                } else {
                                    showShellDialog = true
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Configure", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Save Tile Button
            Button(
                onClick = {
                    onSave(
                        item.copy(
                            name = itemName.ifBlank { item.name },
                            targetData = itemTargetData,
                            iconConfig = itemIconConfig,
                            visualState = itemVisualState,
                            onCommand = itemOnCommand,
                            offCommand = itemOffCommand,
                            onTileName = itemOnTileName,
                            offTileName = itemOffTileName,
                            onIconConfig = itemOnIconConfig,
                            offIconConfig = itemOffIconConfig,
                            collapseCollectionOnClick = itemCollapseCollectionOnClick,
                            showPopups = itemShowPopups,
                            multiShellSteps = itemMultiShellSteps
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }

        if (showShellDialog) {
                ShellDialog(
                    initialName = itemName,
                    initialCommand = itemTargetData,
                    initialOnCommand = itemOnCommand,
                    initialOffCommand = itemOffCommand,
                    initialVisualState = itemVisualState,
                    initialOnTileName = itemOnTileName,
                    initialOffTileName = itemOffTileName,
                    initialOnIconConfig = itemOnIconConfig,
                    initialOffIconConfig = itemOffIconConfig,
                    initialShowPopups = itemShowPopups,
                    initialMultiShellSteps = itemMultiShellSteps,
                    onDismiss = { showShellDialog = false },
                    onSwitchToMultipleShell = {
                        showShellDialog = false
                        showMultipleShellDialog = true
                    },
                    onConfirm = { name, cmd, onCmd, offCmd, vState, onName, offName, onIcon, offIcon, popups, steps ->
                        showShellDialog = false
                        itemName = name
                        itemTargetData = cmd
                        itemOnCommand = onCmd
                        itemOffCommand = offCmd
                        itemVisualState = vState
                        itemOnTileName = onName
                        itemOffTileName = offName
                        itemOnIconConfig = onIcon
                        itemOffIconConfig = offIcon
                        itemShowPopups = popups
                        itemMultiShellSteps = steps
                        if (onIcon != null) {
                            itemIconConfig = onIcon
                        }
                    }
                )
            }

            if (showMultipleShellDialog) {
                MultipleShellDialog(
                    initialSteps = itemMultiShellSteps,
                    initialShowPopups = itemShowPopups,
                    onDismiss = { showMultipleShellDialog = false },
                    onBackToSingleShell = {
                        showMultipleShellDialog = false
                        showShellDialog = true
                    },
                    onConfirm = { steps, popups ->
                        showMultipleShellDialog = false
                        val first = steps.firstOrNull()
                        if (first != null) {
                            itemName = first.name.ifBlank { itemName }
                            itemTargetData = first.command
                            itemOnCommand = first.command
                            itemVisualState = first.visualState
                            itemIconConfig = first.iconConfig
                        }
                        itemShowPopups = popups
                        itemMultiShellSteps = steps
                    }
                )
            }
        }
    }

    if (showPresetsDialog) {
        IconPresetsDialog(
            currentKey = itemIconConfig.sourceData,
            onDismiss = { showPresetsDialog = false },
            onSelectPreset = { key ->
                showPresetsDialog = false
                itemIconConfig = itemIconConfig.copy(
                    sourceType = IconSourceType.PRESET,
                    sourceData = key
                )
            },
            onSelectCustomFile = { path ->
                showPresetsDialog = false
                itemIconConfig = itemIconConfig.copy(
                    sourceType = IconSourceType.CUSTOM_FILE,
                    sourceData = path
                )
            },
            onSelectAppIcon = { pkg ->
                showPresetsDialog = false
                itemIconConfig = IconConfig(
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
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CollectionCreatorScreen(
    initialTile: TileItem? = null,
    onBack: () -> Unit,
    onSaveCollection: (collectionTile: TileItem, memberTiles: List<TileItem>) -> Unit
) {
    val context = LocalContext.current
    var collectionName by rememberSaveable { mutableStateOf(initialTile?.name ?: "My Collection") }
    var collapseOnTap by rememberSaveable { mutableStateOf(initialTile?.collapseOnTap ?: true) }
    var allowOnLockScreen by rememberSaveable { mutableStateOf(initialTile?.allowOnLockScreen ?: true) }
    var hideFromLockScreen by rememberSaveable { mutableStateOf(initialTile?.hideFromLockScreen ?: false) }
    var isLockScreenOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var tilesPerRow by rememberSaveable { mutableIntStateOf(initialTile?.tilesPerRow ?: 3) }
    var tilesPerRowInput by rememberSaveable { mutableStateOf((initialTile?.tilesPerRow ?: 3).toString()) }
    var isCollectionCustomizeExpanded by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var collectionIconConfig by rememberSaveable(stateSaver = IconConfigSaver) {
        mutableStateOf(initialTile?.iconConfig ?: IconConfig(sourceType = IconSourceType.PRESET, sourceData = "collections"))
    }
    var collectionVisualState by rememberSaveable { mutableStateOf(initialTile?.visualState ?: TileVisualState.ENABLED) }

    val collectionItems = remember {
        val list = mutableStateListOf<TileItem>()
        if (initialTile != null && initialTile.type == TileType.COLLECTION) {
            val repo = TileRepository.getInstance(context)
            val all = repo.tilesFlow.value
            initialTile.collectionItemIds.forEach { id ->
                all.find { it.id == id }?.let { list.add(it) }
            }
        }
        list
    }

    var selectedItemForEdit by remember { mutableStateOf<TileItem?>(null) }
    var selectedMultiShellItemForEdit by remember { mutableStateOf<TileItem?>(null) }

    // Dialog states
    var showAppPicker by remember { mutableStateOf(false) }
    var showWebsiteDialog by remember { mutableStateOf(false) }
    var showShellDialog by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }
    var showIntentDialog by remember { mutableStateOf(false) }
    var showActivityPicker by remember { mutableStateOf(false) }
    var showCollectionPresetsDialog by remember { mutableStateOf(false) }

    // File pickers
    val customImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = IconProcessor.saveCustomImage(context, it)
            if (savedPath != null) {
                collectionIconConfig = collectionIconConfig.copy(
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
            } catch (e: Throwable) { /* Ignore */ }
            val item = TileItem(
                id = UUID.randomUUID().toString(),
                name = it.lastPathSegment?.substringAfterLast('/') ?: "File Tile",
                type = TileType.FILES,
                targetData = it.toString(),
                iconConfig = IconConfig(sourceType = IconSourceType.CUSTOM_FILE, sourceData = it.toString()),
                isActive = false,
                slotNumber = -1,
                collapseCollectionOnClick = collapseOnTap
            )
            collectionItems.add(item)
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
            } catch (e: Throwable) { /* Ignore */ }
            val item = TileItem(
                id = UUID.randomUUID().toString(),
                name = it.lastPathSegment?.substringAfterLast(':') ?: "Folder Tile",
                type = TileType.FOLDERS,
                targetData = it.toString(),
                iconConfig = IconConfig(sourceType = IconSourceType.PRESET, sourceData = "folder"),
                isActive = false,
                slotNumber = -1,
                collapseCollectionOnClick = collapseOnTap
            )
            collectionItems.add(item)
        }
    }

    if (showActivityPicker) {
        ActivityPickerScreen(
            onBack = { showActivityPicker = false },
            onActivitySelected = { app, activity ->
                showActivityPicker = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = activity.simpleName,
                    type = TileType.ACTIVITY,
                    targetData = "${app.packageName}/${activity.fullName}",
                    iconConfig = IconConfig(
                        sourceType = IconSourceType.APP_ICON,
                        sourceData = app.packageName,
                        outlineThickness = 0f,
                        outlineThreshold = 0.5f,
                        iconSize = 32f,
                        outlineStyle = "ORIGINAL"
                    ),
                    isActive = false,
                    slotNumber = -1,
                    collapseCollectionOnClick = collapseOnTap
                )
                collectionItems.add(item)
            }
        )
        return
    }

    val saveCollectionAction = {
        if (collectionItems.isEmpty()) {
            Toast.makeText(context, "Please add at least one tile to the collection", Toast.LENGTH_SHORT).show()
        } else {
            val collectionTile = (initialTile ?: TileItem(
                name = collectionName.ifBlank { "My Collection" },
                type = TileType.COLLECTION,
                targetData = "",
                iconConfig = collectionIconConfig,
                isActive = true,
                isAddedToQs = false,
                collectionItemIds = collectionItems.map { it.id },
                collapseOnTap = collapseOnTap,
                tilesPerRow = tilesPerRow,
                visualState = collectionVisualState,
                allowOnLockScreen = allowOnLockScreen,
                hideFromLockScreen = hideFromLockScreen
            )).copy(
                name = collectionName.ifBlank { "My Collection" },
                type = TileType.COLLECTION,
                targetData = "",
                iconConfig = collectionIconConfig,
                collectionItemIds = collectionItems.map { it.id },
                collapseOnTap = collapseOnTap,
                tilesPerRow = tilesPerRow,
                visualState = collectionVisualState,
                allowOnLockScreen = allowOnLockScreen,
                hideFromLockScreen = hideFromLockScreen
            )
            onSaveCollection(collectionTile, collectionItems.toList())
        }
    }

    if (showDeleteConfirmation && initialTile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Collection") },
            text = { Text("Are you sure you want to delete '${initialTile.name}' and all its tiles?") },
            confirmButton = {
                Button(
                    onClick = {
                        val repo = TileRepository.getInstance(context)
                        repo.deleteTile(initialTile.id)
                        showDeleteConfirmation = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialTile == null) "Create Collection" else "Edit Collection") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (initialTile != null) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Collection",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = saveCollectionAction) {
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
            // Collection Name field
            OutlinedTextField(
                value = collectionName,
                onValueChange = { collectionName = it },
                label = { Text("Collection Name") },
                placeholder = { Text("Enter collection name") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )

            // Collection Tile Icon
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val colBitmap = remember(collectionIconConfig, isDark) {
                            IconProcessor.processIconBitmap(context, collectionIconConfig, targetPixelSize = 64, isDarkTheme = isDark)
                        }
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = colBitmap.asImageBitmap(),
                                contentDescription = "Collection Icon",
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Collection Icon", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Icon shown in Quick Settings panel", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { showCollectionPresetsDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Presets", fontSize = 11.sp)
                        }
                        FilledTonalButton(
                            onClick = { customImagePicker.launch("*/*") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Pick File", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { isCollectionCustomizeExpanded = !isCollectionCustomizeExpanded },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isCollectionCustomizeExpanded) "Hide Icon Sliders" else "Customize Icon Sliders", fontSize = 12.sp)
                    }

                    AnimatedVisibility(visible = isCollectionCustomizeExpanded) {
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
                                            selected = collectionIconConfig.outlineStyle.equals(styleKey, ignoreCase = true),
                                            onClick = { collectionIconConfig = collectionIconConfig.copy(outlineStyle = styleKey) },
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
                                    Text("${String.format("%.1f", collectionIconConfig.outlineThickness)} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = collectionIconConfig.outlineThickness,
                                    onValueChange = { collectionIconConfig = collectionIconConfig.copy(outlineThickness = it) },
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
                                    Text("${(collectionIconConfig.outlineThreshold * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = collectionIconConfig.outlineThreshold,
                                    onValueChange = { collectionIconConfig = collectionIconConfig.copy(outlineThreshold = it) },
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
                                    Text("${collectionIconConfig.iconSize.toInt()} dp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = collectionIconConfig.iconSize,
                                    onValueChange = { collectionIconConfig = collectionIconConfig.copy(iconSize = it) },
                                    valueRange = 16f..160f
                                )
                            }
                        }
                    }
                }
            }

            // Tiles Per Row Option Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tiles Per Row",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Number of tiles per row in collection tray",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = tilesPerRowInput,
                        onValueChange = { newVal ->
                            val digitsOnly = newVal.filter { it.isDigit() }
                            tilesPerRowInput = digitsOnly
                            val parsed = digitsOnly.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                tilesPerRow = parsed
                            }
                        },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(68.dp)
                    )
                }
            }


            // TOP SECTION: Horizontally scrollable tiles preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tiles Preview (${collectionItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap a tile to edit",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (collectionItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tiles added yet. Tap a button below to add tiles.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(collectionItems, key = { it.id }) { item ->
                                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                                val previewBitmap = remember(item.id, item.iconConfig, isDark) {
                                    if (item.iconConfig.sourceType == IconSourceType.APP_ICON) {
                                        IconProcessor.loadRawBitmap(context, item.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                                    } else if (item.iconConfig.outlineThickness > 0.5f) {
                                        IconProcessor.processIconBitmap(context, item.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                                    } else {
                                        IconProcessor.loadRawBitmap(context, item.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                                    }
                                }
                                Card(
                                    modifier = Modifier
                                        .width(84.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            if (item.type == TileType.SHELL && item.multiShellSteps.isNotEmpty()) {
                                                selectedMultiShellItemForEdit = item
                                            } else {
                                                selectedItemForEdit = item
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                bitmap = previewBitmap.asImageBitmap(),
                                                contentDescription = item.name,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Text(
                                            text = item.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM SECTION: All existing buttons except Collections
            Text(
                text = "Add Tile to Collection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TileTypeButton(
                    modifier = Modifier.weight(1f),
                    title = "Apps",
                    subtitle = "Launch any app",
                    icon = Icons.Default.Apps,
                    isSelected = false,
                    onClick = { showAppPicker = true }
                )
                TileTypeButton(
                    modifier = Modifier.weight(1f),
                    title = "Website",
                    subtitle = "Open a URL",
                    icon = Icons.Default.Language,
                    isSelected = false,
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
                    subtitle = "App shortcut",
                    icon = Icons.Default.OpenInNew,
                    isSelected = false,
                    onClick = { showShortcutDialog = true }
                )
                TileTypeButton(
                    modifier = Modifier.weight(1f),
                    title = "Activity",
                    subtitle = "Pick specific activity",
                    icon = Icons.Default.Extension,
                    isSelected = false,
                    onClick = { showActivityPicker = true }
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
                    isSelected = false,
                    onClick = { showIntentDialog = true }
                )
                TileTypeButton(
                    modifier = Modifier.weight(1f),
                    title = "Shell",
                    subtitle = "Shizuku & root script",
                    icon = Icons.Default.Terminal,
                    isSelected = false,
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
                    isSelected = false,
                    onClick = { filePicker.launch(arrayOf("*/*")) }
                )
                TileTypeButton(
                    modifier = Modifier.weight(1f),
                    title = "Folders",
                    subtitle = "Open directory",
                    icon = Icons.Default.Folder,
                    isSelected = false,
                    onClick = { folderPicker.launch(null) }
                )
            }

            // --- TILE BEHAVIOR & APPEARANCE ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column {
                        Text(
                            text = "Tile Behavior & Appearance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Choose whether this collection tile appears enabled or disabled in the quick settings panel",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = collectionVisualState == TileVisualState.ENABLED,
                                onClick = { collectionVisualState = TileVisualState.ENABLED },
                                label = { Text("Enabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = collectionVisualState == TileVisualState.DISABLED,
                                onClick = { collectionVisualState = TileVisualState.DISABLED },
                                label = { Text("Disabled", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Bottom-most choice in bottom section: "Collapse panel on click"
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { collapseOnTap = !collapseOnTap },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Collapse panel on click",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Collapse quick settings notification shade when collection tile is tapped",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = collapseOnTap,
                        onCheckedChange = { collapseOnTap = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- ALLOW ON LOCK SCREEN (BELOW COLLAPSE PANEL ON CLICK) ---
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!allowOnLockScreen) {
                                    isLockScreenOptionsExpanded = !isLockScreenOptionsExpanded
                                } else {
                                    allowOnLockScreen = false
                                    isLockScreenOptionsExpanded = true
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Allow on lock screen",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!allowOnLockScreen) {
                                    Icon(
                                        imageVector = if (isLockScreenOptionsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (allowOnLockScreen) {
                                    "Custom collection tile can be accessed from lock screen"
                                } else {
                                    "Collection tile cannot be accessed from lock screen without unlocking"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = allowOnLockScreen,
                            onCheckedChange = { isChecked ->
                                allowOnLockScreen = isChecked
                                if (isChecked) {
                                    isLockScreenOptionsExpanded = false
                                    hideFromLockScreen = false
                                } else {
                                    isLockScreenOptionsExpanded = true
                                }
                            }
                        )
                    }

                    // Dropdown menu option: "Hide from lock screen"
                    AnimatedVisibility(
                        visible = !allowOnLockScreen && isLockScreenOptionsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { hideFromLockScreen = !hideFromLockScreen },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Hide from lock screen",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Custom collection tile will not show in lock screen and unlocking the screen it should show in quick settings panel",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = hideFromLockScreen,
                                    onCheckedChange = { hideFromLockScreen = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit sheet for selected tile
    if (selectedItemForEdit != null) {
        EditTileSheet(
            item = selectedItemForEdit!!,
            onDismiss = { selectedItemForEdit = null },
            onSave = { updated ->
                val idx = collectionItems.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    collectionItems[idx] = updated
                }
                selectedItemForEdit = null
            },
            onDelete = { deleted ->
                collectionItems.removeAll { it.id == deleted.id }
                selectedItemForEdit = null
            }
        )
    }

    if (selectedMultiShellItemForEdit != null) {
        val editingItem = selectedMultiShellItemForEdit!!
        MultipleShellDialog(
            initialSteps = editingItem.multiShellSteps,
            initialShowPopups = editingItem.showPopups,
            onDismiss = { selectedMultiShellItemForEdit = null },
            onBackToSingleShell = {
                val target = editingItem.copy(multiShellSteps = emptyList())
                selectedMultiShellItemForEdit = null
                selectedItemForEdit = target
            },
            onConfirm = { steps, popups ->
                val first = steps.firstOrNull()
                val updated = editingItem.copy(
                    name = first?.name?.ifBlank { editingItem.name } ?: editingItem.name,
                    targetData = first?.command ?: editingItem.targetData,
                    onCommand = first?.command ?: editingItem.onCommand,
                    visualState = first?.visualState ?: editingItem.visualState,
                    iconConfig = first?.iconConfig ?: editingItem.iconConfig,
                    showPopups = popups,
                    multiShellSteps = steps
                )
                val idx = collectionItems.indexOfFirst { it.id == updated.id }
                if (idx >= 0) {
                    collectionItems[idx] = updated
                }
                selectedMultiShellItemForEdit = null
            }
        )
    }

    // App Picker
    if (showAppPicker) {
        AppPickerSheet(
            onDismiss = { showAppPicker = false },
            onAppSelected = { app ->
                showAppPicker = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = app.label,
                    type = TileType.APPS,
                    targetData = app.packageName,
                    iconConfig = IconConfig(
                        sourceType = IconSourceType.APP_ICON,
                        sourceData = app.packageName,
                        outlineThickness = 0f,
                        outlineThreshold = 0.5f,
                        iconSize = 32f,
                        outlineStyle = "ORIGINAL"
                    ),
                    isActive = false,
                    slotNumber = -1,
                    collapseCollectionOnClick = collapseOnTap
                )
                collectionItems.add(item)
            }
        )
    }

    // Website Dialog
    if (showWebsiteDialog) {
        WebsiteDialog(
            initialName = "",
            initialUrl = "",
            onDismiss = { showWebsiteDialog = false },
            onConfirm = { name, url ->
                showWebsiteDialog = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { url },
                    type = TileType.WEBSITE,
                    targetData = url,
                    iconConfig = IconConfig(sourceType = IconSourceType.PRESET, sourceData = "language"),
                    isActive = false,
                    slotNumber = -1,
                    collapseCollectionOnClick = collapseOnTap
                )
                collectionItems.add(item)
            }
        )
    }

    // Shortcut Dialog
    if (showShortcutDialog) {
        ShortcutDialog(
            onDismiss = { showShortcutDialog = false },
            onSelect = { name, intentUri, pkg, iconBitmap ->
                showShortcutDialog = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    type = TileType.SHORTCUT,
                    targetData = intentUri,
                    iconConfig = IconConfig(sourceType = IconSourceType.PRESET, sourceData = pkg, outlineThickness = 0f, outlineStyle = "ORIGINAL"),
                    isActive = false,
                    slotNumber = -1,
                    collapseCollectionOnClick = collapseOnTap
                )
                collectionItems.add(item)
            }
        )
    }

    // Intent Dialog
    if (showIntentDialog) {
        IntentDialog(
            initialName = "",
            initialAction = "",
            initialPackage = "",
            onDismiss = { showIntentDialog = false },
            onConfirm = { name, action, pkg ->
                showIntentDialog = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Intent" },
                    type = TileType.INTENT,
                    targetData = if (pkg.isNotBlank()) "intent:#Intent;action=$action;package=$pkg;end" else action,
                    iconConfig = IconConfig(sourceType = IconSourceType.PRESET, sourceData = "send"),
                    isActive = false,
                    slotNumber = -1,
                    collapseCollectionOnClick = collapseOnTap
                )
                collectionItems.add(item)
            }
        )
    }

    // Shell Dialog
    if (showShellDialog) {
        ShellDialog(
            initialName = "",
            initialCommand = "",
            initialOnCommand = "",
            initialOffCommand = "",
            initialVisualState = TileVisualState.ENABLED,
            initialOnTileName = "",
            initialOffTileName = "",
            initialOnIconConfig = null,
            initialOffIconConfig = null,
            initialShowPopups = false,
            initialMultiShellSteps = emptyList(),
            onDismiss = { showShellDialog = false },
            onConfirm = { name, cmd, onCmd, offCmd, vState, onName, offName, onIcon, offIcon, popups, steps ->
                showShellDialog = false
                val item = TileItem(
                    id = UUID.randomUUID().toString(),
                    name = name.ifBlank { "Shell" },
                    type = TileType.SHELL,
                    targetData = cmd,
                    iconConfig = onIcon ?: IconConfig(sourceType = IconSourceType.PRESET, sourceData = "terminal"),
                    isActive = false,
                    slotNumber = -1,
                    visualState = vState,
                    onCommand = onCmd,
                    offCommand = offCmd,
                    onTileName = onName,
                    offTileName = offName,
                    onIconConfig = onIcon,
                    offIconConfig = offIcon,
                    collapseCollectionOnClick = collapseOnTap,
                    showPopups = popups,
                    multiShellSteps = steps
                )
                collectionItems.add(item)
            }
        )
    }

    // Presets Dialog for Collection Tile Icon
    if (showCollectionPresetsDialog) {
        IconPresetsDialog(
            currentKey = collectionIconConfig.sourceData,
            onDismiss = { showCollectionPresetsDialog = false },
            onSelectPreset = { key ->
                showCollectionPresetsDialog = false
                collectionIconConfig = collectionIconConfig.copy(sourceType = IconSourceType.PRESET, sourceData = key)
            },
            onSelectCustomFile = { path ->
                showCollectionPresetsDialog = false
                collectionIconConfig = collectionIconConfig.copy(sourceType = IconSourceType.CUSTOM_FILE, sourceData = path)
            },
            onSelectAppIcon = { pkg ->
                showCollectionPresetsDialog = false
                collectionIconConfig = IconConfig(sourceType = IconSourceType.APP_ICON, sourceData = pkg)
            }
        )
    }
    }
}

