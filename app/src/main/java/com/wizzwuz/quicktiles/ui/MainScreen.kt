package com.wizzwuz.quicktiles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.ui.create.ActivityPickerScreen
import com.wizzwuz.quicktiles.ui.create.CreateTileScreen
import com.wizzwuz.quicktiles.ui.settings.SettingsScreen
import com.wizzwuz.quicktiles.ui.tiles.TilesListScreen
import com.wizzwuz.quicktiles.util.TileEditChannel

enum class MainDestination {
    LIST,
    CREATE_OR_EDIT,
    ACTIVITY_PICKER,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { TileRepository.getInstance(context) }
    val allTiles by repository.tilesFlow.collectAsState()

    var currentDestination by remember { mutableStateOf(MainDestination.LIST) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active Tiles, 1 = Inactive Tiles
    var editingTile by remember { mutableStateOf<TileItem?>(null) }
    var selectedTileIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    val activeTiles = remember(allTiles) { allTiles.filter { it.isActive } }
    val inactiveTiles = remember(allTiles) { allTiles.filter { !it.isActive } }

    BackHandler(enabled = selectedTileIds.isNotEmpty() || currentDestination != MainDestination.LIST || selectedTab != 0) {
        if (selectedTileIds.isNotEmpty()) {
            selectedTileIds = emptySet()
        } else if (currentDestination != MainDestination.LIST) {
            currentDestination = MainDestination.LIST
        } else if (selectedTab != 0) {
            selectedTab = 0
        }
    }

    LaunchedEffect(Unit) {
        TileEditChannel.editSlotFlow.collect { slot ->
            val tile = repository.getTileBySlot(slot)
            if (tile != null) {
                editingTile = tile
                currentDestination = MainDestination.CREATE_OR_EDIT
                TileEditChannel.clear()
            }
        }
    }

    Scaffold(
        topBar = {
            if (currentDestination == MainDestination.LIST) {
                if (selectedTileIds.isNotEmpty()) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedTileIds.size} selected",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedTileIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close selection")
                            }
                        },
                        actions = {
                            // Select All icon
                            IconButton(onClick = {
                                val currentList = if (selectedTab == 0) activeTiles else inactiveTiles
                                if (selectedTileIds.size == currentList.size) {
                                    selectedTileIds = emptySet()
                                } else {
                                    selectedTileIds = currentList.map { it.id }.toSet()
                                }
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                            }
                            // Delete icon
                            IconButton(onClick = { showBatchDeleteDialog = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.DashboardCustomize,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "QuickSetTiles",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { currentDestination = MainDestination.SETTINGS }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            }
        },
        bottomBar = {
            if (currentDestination == MainDestination.LIST) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    // Left Tab: Active Tiles
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = {
                            if (selectedTab != 0) {
                                selectedTab = 0
                                selectedTileIds = emptySet()
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeTiles.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${activeTiles.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                                    contentDescription = "Active Tiles"
                                )
                            }
                        },
                        label = {
                            Text(
                                "Active Tiles",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )

                    // Center Floating Plus (+) Button
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        FloatingActionButton(
                            onClick = {
                                editingTile = null
                                selectedTileIds = emptySet()
                                currentDestination = MainDestination.CREATE_OR_EDIT
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Tile",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Right Tab: Inactive Tiles
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            if (selectedTab != 1) {
                                selectedTab = 1
                                selectedTileIds = emptySet()
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (inactiveTiles.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                                            Text("${inactiveTiles.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == 1) Icons.Default.PauseCircle else Icons.Default.PauseCircleOutline,
                                    contentDescription = "Inactive Tiles"
                                )
                            }
                        },
                        label = {
                            Text(
                                "Inactive Tiles",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                MainDestination.LIST -> {
                    Crossfade(targetState = selectedTab, label = "TabCrossfade") { tab ->
                        if (tab == 0) {
                            TilesListScreen(
                                tiles = activeTiles,
                                isActiveTab = true,
                                selectedTileIds = selectedTileIds,
                                onTileClick = { tile ->
                                    editingTile = tile
                                    selectedTileIds = emptySet()
                                    currentDestination = MainDestination.CREATE_OR_EDIT
                                },
                                onTileLongClick = { tile ->
                                    selectedTileIds = selectedTileIds + tile.id
                                },
                                onTileSelectToggle = { tile ->
                                    selectedTileIds = if (tile.id in selectedTileIds) {
                                        selectedTileIds - tile.id
                                    } else {
                                        selectedTileIds + tile.id
                                    }
                                },
                                onInactivateTile = { tile ->
                                    repository.inactivateTile(tile.id)
                                },
                                onActivateTile = { tile ->
                                    repository.activateTile(tile.id)
                                },
                                onDeleteTile = { tile ->
                                    repository.deleteTile(tile.id)
                                },
                                onAddTileClick = {
                                    editingTile = null
                                    selectedTileIds = emptySet()
                                    currentDestination = MainDestination.CREATE_OR_EDIT
                                }
                            )
                        } else {
                            TilesListScreen(
                                tiles = inactiveTiles,
                                isActiveTab = false,
                                selectedTileIds = selectedTileIds,
                                onTileClick = { tile ->
                                    editingTile = tile
                                    selectedTileIds = emptySet()
                                    currentDestination = MainDestination.CREATE_OR_EDIT
                                },
                                onTileLongClick = { tile ->
                                    selectedTileIds = selectedTileIds + tile.id
                                },
                                onTileSelectToggle = { tile ->
                                    selectedTileIds = if (tile.id in selectedTileIds) {
                                        selectedTileIds - tile.id
                                    } else {
                                        selectedTileIds + tile.id
                                    }
                                },
                                onInactivateTile = { tile ->
                                    repository.inactivateTile(tile.id)
                                },
                                onActivateTile = { tile ->
                                    repository.activateTile(tile.id)
                                },
                                onDeleteTile = { tile ->
                                    repository.deleteTile(tile.id)
                                },
                                onAddTileClick = {
                                    editingTile = null
                                    selectedTileIds = emptySet()
                                    currentDestination = MainDestination.CREATE_OR_EDIT
                                }
                            )
                        }
                    }
                }

                MainDestination.CREATE_OR_EDIT -> {
                    CreateTileScreen(
                        initialTile = editingTile,
                        onBack = { currentDestination = MainDestination.LIST },
                        onOpenActivityPicker = {
                            currentDestination = MainDestination.ACTIVITY_PICKER
                        },
                        onSaveTile = { tileToSave ->
                            val isNew = (editingTile == null)
                            repository.saveTile(tileToSave)
                            if (isNew) {
                                selectedTab = 0
                            }
                            currentDestination = MainDestination.LIST
                        }
                    )
                }

                MainDestination.ACTIVITY_PICKER -> {
                    ActivityPickerScreen(
                        onBack = { currentDestination = MainDestination.CREATE_OR_EDIT },
                        onActivitySelected = { app, act ->
                            // Update editingTile with selected activity and app icon
                            val current = editingTile ?: TileItem(
                                name = act.simpleName,
                                type = TileType.ACTIVITY,
                                targetData = "${app.packageName}/${act.fullName}",
                                iconConfig = IconConfig(
                                    sourceType = IconSourceType.APP_ICON,
                                    sourceData = app.packageName,
                                    outlineThickness = 2.5f,
                                    outlineThreshold = 0.4f,
                                    iconSize = 32f,
                                    outlineStyle = "OUTLINE"
                                )
                            )
                            editingTile = current.copy(
                                name = if (current.name.isBlank() || current.name == "Tile") act.simpleName else current.name,
                                type = TileType.ACTIVITY,
                                targetData = "${app.packageName}/${act.fullName}",
                                iconConfig = current.iconConfig.copy(
                                    sourceType = IconSourceType.APP_ICON,
                                    sourceData = app.packageName,
                                    outlineThickness = if (current.iconConfig.outlineThickness > 0.1f) current.iconConfig.outlineThickness else 2.5f,
                                    outlineThreshold = 0.4f,
                                    iconSize = 32f,
                                    outlineStyle = current.iconConfig.outlineStyle.ifBlank { "OUTLINE" }
                                )
                            )
                            currentDestination = MainDestination.CREATE_OR_EDIT
                        }
                    )
                }

                MainDestination.SETTINGS -> {
                    SettingsScreen(
                        onBack = { currentDestination = MainDestination.LIST }
                    )
                }
            }
        }
    }

    if (showBatchDeleteDialog && selectedTileIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete Tiles") },
            text = { Text("Are you sure you want to permanently delete ${selectedTileIds.size} selected tile(s)?") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteTiles(selectedTileIds)
                        selectedTileIds = emptySet()
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
