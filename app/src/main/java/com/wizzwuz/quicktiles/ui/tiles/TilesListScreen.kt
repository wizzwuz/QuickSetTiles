package com.wizzwuz.quicktiles.ui.tiles

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.QsTileManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilesListScreen(
    tiles: List<TileItem>,
    isActiveTab: Boolean,
    isGridView: Boolean = true,
    showCategories: Boolean = true,
    searchQuery: String = "",
    selectedTileIds: Set<String> = emptySet(),
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
    onTileClick: (TileItem) -> Unit,
    onTileLongClick: (TileItem) -> Unit = {},
    onTileSelectToggle: (TileItem) -> Unit = {},
    onToggleAddToQs: (TileItem, Boolean) -> Unit = { _, _ -> },
    onInactivateTile: (TileItem) -> Unit,
    onActivateTile: (TileItem) -> Unit,
    onDeleteTile: (TileItem) -> Unit,
    onAddTileClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tileToDelete by remember { mutableStateOf<TileItem?>(null) }
    var activeContextMenuTile by remember { mutableStateOf<TileItem?>(null) }
    var selectedCategory by remember(isActiveTab) { mutableStateOf<TileType?>(null) }

    val filteredTiles = remember(tiles, selectedCategory, showCategories) {
        if (!showCategories || selectedCategory == null) {
            tiles
        } else {
            tiles.filter { it.type == selectedCategory }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Status header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isActiveTab) "Active tiles" else "Inactive tiles",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (tiles.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "${tiles.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Categories filter panel (All + each TileType)
        if (showCategories) {
            CategoriesPanel(
                tiles = tiles,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (tiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (searchQuery.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = "No Matching Tiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No tiles found matching \"$searchQuery\".",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isActiveTab) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(
                                text = if (isActiveTab) "No Active Tiles" else "No Inactive Tiles",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isActiveTab)
                                    "Tap the center Plus (+) button below to create your first custom Quick Settings tile!"
                                else
                                    "Deactivated tiles will appear here so you can re-enable them anytime.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            if (isActiveTab) {
                                Button(onClick = onAddTileClick) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Tile")
                                }
                            }
                        }
                    }
                }
            }
        } else if (filteredTiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterAltOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text = "No ${selectedCategory?.displayName ?: ""} Tiles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "There are no ${selectedCategory?.displayName?.lowercase() ?: ""} tiles in ${if (isActiveTab) "active" else "inactive"} tiles.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        FilledTonalButton(onClick = { selectedCategory = null }) {
                            Text("Show All Tiles (${tiles.size})")
                        }
                    }
                }
            }
        } else {
            if (isGridView) {
                // Quick Settings Grid Layout
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTiles, key = { it.id }) { tile ->
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val previewBitmap = remember(tile.iconConfig, isDark) {
                            IconProcessor.processIconBitmap(context, tile.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                        }
                        val isSelectionMode = selectedTileIds.isNotEmpty()
                        val isSelected = tile.id in selectedTileIds

                        TileGridItem(
                            tile = tile,
                            previewBitmap = previewBitmap,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    onTileSelectToggle(tile)
                                } else {
                                    onTileClick(tile)
                                }
                            },
                            onLongClick = {
                                if (isSelectionMode) {
                                    onTileSelectToggle(tile)
                                } else {
                                    activeContextMenuTile = tile
                                }
                            }
                        )
                    }
                }
            } else {
                // Classic Card List Layout (Original)
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredTiles, key = { it.id }) { tile ->
                        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                        val previewBitmap = remember(tile.iconConfig, isDark) {
                            IconProcessor.processIconBitmap(context, tile.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                        }
                        val isSelectionMode = selectedTileIds.isNotEmpty()
                        val isSelected = tile.id in selectedTileIds

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onTileSelectToggle(tile)
                                        } else {
                                            onTileClick(tile)
                                        }
                                    },
                                    onLongClick = {
                                        if (isSelectionMode) {
                                            onTileSelectToggle(tile)
                                        } else {
                                            activeContextMenuTile = tile
                                        }
                                    }
                                ),
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    tile.isActive -> MaterialTheme.colorScheme.surfaceVariant
                                    else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Icon
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (tile.isActive) MaterialTheme.colorScheme.primaryContainer
                                             else MaterialTheme.colorScheme.surface
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = previewBitmap.asImageBitmap(),
                                        contentDescription = tile.name,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Details: Tile Name and "Tile X" pill below
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = tile.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (tile.isActive && tile.slotNumber in 1..TileRepository.MAX_SLOTS) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(
                                                text = "Tile ${tile.slotNumber}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onTileSelectToggle(tile) }
                                    )
                                } else {
                                    // Action buttons: Disable / Add & Delete
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isActiveTab) {
                                            // Button to manually add / remove tile to system Quick Settings shade
                                            IconButton(
                                                onClick = {
                                                    if (tile.isAddedToQs) {
                                                        coroutineScope.launch {
                                                            QsTileManager.removeTile(context, tile.slotNumber)
                                                            onToggleAddToQs(tile, false)
                                                            Toast.makeText(context, "Tile removed from Quick Settings", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        coroutineScope.launch {
                                                            onToggleAddToQs(tile, true)
                                                            val addedAtEnd = QsTileManager.addTileToLast(
                                                                context = context,
                                                                slot = tile.slotNumber,
                                                                tileName = tile.name,
                                                                iconBitmap = previewBitmap
                                                            )
                                                            if (addedAtEnd) {
                                                                Toast.makeText(context, "Tile added to the end of Quick Settings", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Tile enabled in Quick Settings edit panel", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (tile.isAddedToQs) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                                    contentDescription = if (tile.isAddedToQs) "Tile added to Quick Settings (tap to remove)" else "Add to Quick Settings",
                                                    tint = if (tile.isAddedToQs) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Disable button to deactivate and move to inactive tab
                                            FilledTonalButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        QsTileManager.removeTile(context, tile.slotNumber)
                                                        onInactivateTile(tile)
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Disable", fontSize = 12.sp)
                                            }
                                        } else {
                                            // Add button to activate and move to active tab
                                            Button(
                                                onClick = { onActivateTile(tile) },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        IconButton(
                                            onClick = { tileToDelete = tile },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Tile",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
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

    // Long-Press Context Menu Popup with Gradient Blurred Background
    if (activeContextMenuTile != null) {
        val targetTile = activeContextMenuTile!!
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val previewBitmap = remember(targetTile.iconConfig, isDark) {
            IconProcessor.processIconBitmap(context, targetTile.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
        }

        TileContextMenuDialog(
            tile = targetTile,
            previewBitmap = previewBitmap,
            isActiveTab = isActiveTab,
            onDismiss = { activeContextMenuTile = null },
            onToggleAddToQs = {
                val willBeAdded = !targetTile.isAddedToQs
                activeContextMenuTile = null
                coroutineScope.launch {
                    if (willBeAdded) {
                        if (!targetTile.isActive) {
                            onActivateTile(targetTile)
                            Toast.makeText(context, "'${targetTile.name}' activated and ready in active tiles", Toast.LENGTH_SHORT).show()
                        } else {
                            onToggleAddToQs(targetTile, true)
                            val added = QsTileManager.addTileToLast(
                                context = context,
                                slot = targetTile.slotNumber,
                                tileName = targetTile.name,
                                iconBitmap = previewBitmap
                            )
                            if (added) {
                                Toast.makeText(context, "Tile added to Quick Settings", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Tile enabled in Quick Settings edit panel", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        QsTileManager.removeTile(context, targetTile.slotNumber)
                        onToggleAddToQs(targetTile, false)
                        Toast.makeText(context, "Tile removed from Quick Settings", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSelect = {
                activeContextMenuTile = null
                onTileSelectToggle(targetTile)
            },
            onDisableOrEnable = {
                activeContextMenuTile = null
                coroutineScope.launch {
                    if (isActiveTab) {
                        QsTileManager.removeTile(context, targetTile.slotNumber)
                        onInactivateTile(targetTile)
                        Toast.makeText(context, "'${targetTile.name}' disabled and moved to inactive tiles", Toast.LENGTH_SHORT).show()
                    } else {
                        onActivateTile(targetTile)
                        Toast.makeText(context, "'${targetTile.name}' enabled and moved to active tiles", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                activeContextMenuTile = null
                tileToDelete = targetTile
            }
        )
    }

    // Delete Confirmation Dialog
    if (tileToDelete != null) {
        val target = tileToDelete!!
        AlertDialog(
            onDismissRequest = { tileToDelete = null },
            title = { Text("Delete Tile") },
            text = { Text("Are you sure you want to permanently delete '${target.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTile(target)
                        tileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tileToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Circular Quick Settings Tile grid item matching Android QS layout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileGridItem(
    tile: TileItem,
    previewBitmap: android.graphics.Bitmap,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular Tile Icon
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            tile.isAddedToQs -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .then(
                        if (isSelected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = tile.name,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Selection Checkmark Badge
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = tile.name,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Subtitle: (Tile X)
        Text(
            text = if (tile.isActive && tile.slotNumber in 1..TileRepository.MAX_SLOTS) {
                "(Tile ${tile.slotNumber})"
            } else if (tile.isActive) {
                "(Active)"
            } else {
                "(Inactive)"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}

/**
 * Beautiful context menu popup with rounded corners and gradient blurred backdrop.
 */
@Composable
fun TileContextMenuDialog(
    tile: TileItem,
    previewBitmap: android.graphics.Bitmap,
    isActiveTab: Boolean,
    onDismiss: () -> Unit,
    onToggleAddToQs: () -> Unit,
    onSelect: () -> Unit,
    onDisableOrEnable: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Background Scrim with Gradient & Blur
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.60f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
                    .blur(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            // Foreground: Held Tile Preview + Popup Menu Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks to prevent dismissing dialog */ }
            ) {
                // Held Tile Floating Preview
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            if (tile.isAddedToQs) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = tile.name,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = tile.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (tile.isActive && tile.slotNumber in 1..TileRepository.MAX_SLOTS) {
                        "(Tile ${tile.slotNumber})"
                    } else if (tile.isActive) {
                        "(Active)"
                    } else {
                        "(Inactive)"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Popup Menu Card with Rounded Corners
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                    modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // (a) "Add" or "Remove"
                        val isAdded = tile.isAddedToQs
                        TileContextMenuItem(
                            icon = if (isAdded) Icons.Default.RemoveCircleOutline else Icons.Default.AddCircleOutline,
                            title = if (isAdded) "Remove" else "Add",
                            subtitle = if (isAdded) "Remove from QS panel" else "Add to QS panel",
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = onToggleAddToQs
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // (b) "Select"
                        TileContextMenuItem(
                            icon = Icons.Default.CheckCircleOutline,
                            title = "Select",
                            subtitle = "Select tile",
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = onSelect
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // (c) "Disable" or "Enable"
                        TileContextMenuItem(
                            icon = if (isActiveTab) Icons.Default.PauseCircleOutline else Icons.Default.PlayCircleOutline,
                            title = if (isActiveTab) "Disable" else "Enable",
                            subtitle = if (isActiveTab) "Move to inactive tiles" else "Move to active tiles",
                            iconTint = MaterialTheme.colorScheme.secondary,
                            onClick = onDisableOrEnable
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )

                        // (d) "Delete"
                        TileContextMenuItem(
                            icon = Icons.Default.DeleteOutline,
                            title = "Delete",
                            subtitle = "Delete tile permanently",
                            iconTint = MaterialTheme.colorScheme.error,
                            textColor = MaterialTheme.colorScheme.error,
                            onClick = onDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TileContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (textColor == MaterialTheme.colorScheme.error)
                        textColor.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CategoriesPanel(
    tiles: List<TileItem>,
    selectedCategory: TileType?,
    onSelectCategory: (TileType?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        // "All" button
        item {
            val isSelected = selectedCategory == null
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(null) },
                label = {
                    Text(
                        text = if (tiles.isNotEmpty()) "All (${tiles.size})" else "All",
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AllInclusive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = CircleShape
            )
        }

        // Each TileType category button
        items(TileType.values()) { type ->
            val count = remember(tiles, type) { tiles.count { it.type == type } }
            val isSelected = selectedCategory == type
            val icon = when (type) {
                TileType.APPS -> Icons.Default.Apps
                TileType.WEBSITE -> Icons.Default.Language
                TileType.SHORTCUT -> Icons.Default.OpenInNew
                TileType.ACTIVITY -> Icons.Default.Extension
                TileType.INTENT -> Icons.Default.Send
                TileType.SHELL -> Icons.Default.Terminal
                TileType.FILES -> Icons.Default.InsertDriveFile
                TileType.FOLDERS -> Icons.Default.Folder
                TileType.COLLECTION -> Icons.Default.Collections
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectCategory(if (isSelected) null else type)
                },
                label = {
                    Text(
                        text = if (count > 0) "${type.displayName} ($count)" else type.displayName,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = CircleShape
            )
        }
    }
}
