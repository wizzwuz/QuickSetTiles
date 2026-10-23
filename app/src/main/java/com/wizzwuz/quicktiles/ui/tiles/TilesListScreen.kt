package com.wizzwuz.quicktiles.ui.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.util.IconProcessor

@Composable
fun TilesListScreen(
    tiles: List<TileItem>,
    isActiveTab: Boolean,
    onTileClick: (TileItem) -> Unit,
    onInactivateTile: (TileItem) -> Unit,
    onActivateTile: (TileItem) -> Unit,
    onDeleteTile: (TileItem) -> Unit,
    onAddTileClick: () -> Unit
) {
    val context = LocalContext.current
    var tileToDelete by remember { mutableStateOf<TileItem?>(null) }

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
            Column {
                Text(
                    text = if (isActiveTab) "Active Quick Tiles (${tiles.size})" else "Inactive Tiles (${tiles.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isActiveTab)
                        "Tap a tile to edit. Tap Remove to deactivate."
                    else
                        "Tap a tile to edit. Tap Add to activate.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tiles, key = { it.id }) { tile ->
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val previewBitmap = remember(tile.iconConfig, isDark) {
                        IconProcessor.processIconBitmap(context, tile.iconConfig, targetPixelSize = 96, isDarkTheme = isDark)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onTileClick(tile) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (tile.isActive)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                            // Details: Tile Name and "Tile X" badge
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = tile.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1
                                    )
                                    if (tile.isActive && tile.slotNumber in 1..TileRepository.MAX_SLOTS) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("Tile ${tile.slotNumber}", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // Action buttons: Add / Remove & Delete
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isActiveTab) {
                                    // Android 14+ Quick Add to QS shade shortcut
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val sbm = context.getSystemService(StatusBarManager::class.java)
                                                    val comp = ComponentName(
                                                        context,
                                                        "com.wizzwuz.quicktiles.service.QuickTileService${tile.slotNumber}"
                                                    )
                                                    sbm.requestAddTileService(
                                                        comp,
                                                        tile.name,
                                                        Icon.createWithBitmap(previewBitmap),
                                                        context.mainExecutor
                                                    ) { res ->
                                                        val msg = when (res) {
                                                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Tile already added"
                                                            StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tile added to Quick Settings"
                                                            else -> "Tile not added"
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    }
                                                } catch (e: Throwable) {
                                                    Toast.makeText(context, "Add to QS: Drag tile from system edit panel", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddCircleOutline,
                                                contentDescription = "Add to QS Panel",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Remove button to deactivate and move to inactive tab
                                    FilledTonalButton(
                                        onClick = { onInactivateTile(tile) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Remove", fontSize = 12.sp)
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
