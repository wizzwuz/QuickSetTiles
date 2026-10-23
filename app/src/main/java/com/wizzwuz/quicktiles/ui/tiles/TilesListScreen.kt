package com.wizzwuz.quicktiles.ui.tiles

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.util.IconProcessor
import java.util.concurrent.Executor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilesListScreen(
    tiles: List<TileItem>,
    isActiveTab: Boolean,
    onTileClick: (TileItem) -> Unit,
    onTileLongClick: (TileItem) -> Unit,
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
                        "Tap a tile to deactivate. Long press to edit."
                    else
                        "Tap a tile to activate. Long press to edit.",
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
                    val previewBitmap = remember(tile.iconConfig) {
                        IconProcessor.processIconBitmap(context, tile.iconConfig, targetPixelSize = 96)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { onTileClick(tile) },
                                onLongClick = { onTileLongClick(tile) }
                            ),
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

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = tile.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1
                                    )
                                    if (tile.isActive && tile.slotNumber in 1..TileRepository.MAX_SLOTS) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("Slot ${tile.slotNumber}", fontSize = 10.sp)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tile.type.name, fontSize = 10.sp) },
                                        modifier = Modifier.height(22.dp)
                                    )
                                    Text(
                                        text = tile.targetData.take(35),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Actions
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Add to Quick Settings Prompt (Android 14+)
                                if (tile.isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
                                                    Toast.makeText(context, "Tile Add Response: $res", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Throwable) {
                                                Toast.makeText(context, "Add to QS: Drag tile from system edit panel", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddCircleOutline,
                                            contentDescription = "Add to QS Panel",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                IconButton(onClick = { tileToDelete = tile }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Tile",
                                        tint = MaterialTheme.colorScheme.error
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
