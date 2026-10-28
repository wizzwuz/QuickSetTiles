package com.wizzwuz.quicktiles.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.ShizukuHelper
import com.wizzwuz.quicktiles.util.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CollectionPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CollectionPopupScreen(
                    collectionId = intent?.extras?.getString("collectionId") ?: "",
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
fun CollectionPopupScreen(
    collectionId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { TileRepository.getInstance(context) }
    val tiles by repo.tilesFlow.collectAsState()
    val collection = tiles.find { it.id == collectionId }
    val collectionItems = remember(tiles, collection) {
        collection?.collectionItemIds?.mapNotNull { id -> tiles.find { it.id == id } } ?: emptyList()
    }

    var visible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(40)
        visible = true
    }

    fun dismissAnimated() {
        visible = false
        coroutineScope.launch {
            delay(200)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { dismissAnimated() },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(280),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(240)),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(180))
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {},
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Collection Title
                    Text(
                        text = collection?.name ?: "Collection",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )

                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                    if (collectionItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tiles in this collection",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (collectionItems.size <= 3) {
                        // Horizontally arranged, not scrollable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            collectionItems.forEach { item ->
                                CollectionTileItemView(
                                    item = item,
                                    context = context,
                                    isDark = isDark,
                                    onClick = {
                                        executeCollectionItem(context, item)
                                        if (item.collapseCollectionOnClick) {
                                            dismissAnimated()
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        // Exceeds 3 items: Grid layout (non-scrollable)
                        val columns = if (collectionItems.size == 4) 2 else 3
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            collectionItems.chunked(columns).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowItems.forEach { item ->
                                        CollectionTileItemView(
                                            item = item,
                                            context = context,
                                            isDark = isDark,
                                            onClick = {
                                                executeCollectionItem(context, item)
                                                if (item.collapseCollectionOnClick) {
                                                    dismissAnimated()
                                                }
                                            }
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

@Composable
private fun CollectionTileItemView(
    item: TileItem,
    context: android.content.Context,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val previewBitmap = remember(item.id, item.iconConfig, isDark) {
        if (item.iconConfig.outlineThickness > 0.5f) {
            IconProcessor.processIconBitmap(context, item.iconConfig, targetPixelSize = 128, isDarkTheme = isDark)
        } else {
            IconProcessor.loadRawBitmap(context, item.iconConfig, isDarkTheme = isDark)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .widthIn(min = 68.dp, max = 88.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = item.name,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private val collectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun executeCollectionItem(context: android.content.Context, item: TileItem) {
    try {
        when (item.type) {
            TileType.APPS -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(item.targetData)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show()
                }
            }
            TileType.WEBSITE -> {
                var url = item.targetData.trim()
                if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(browserIntent)
            }
            TileType.SHORTCUT -> {
                val intent = Intent.parseUri(item.targetData, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            TileType.ACTIVITY -> {
                val comp = ComponentName.unflattenFromString(item.targetData)
                if (comp != null) {
                    collectionScope.launch {
                        AppHelper.launchActivityPrivileged(context, comp.packageName, comp.className)
                    }
                }
            }
            TileType.INTENT -> {
                val intent = if (item.targetData.startsWith("intent:")) {
                    Intent.parseUri(item.targetData, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(item.targetData)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            TileType.SHELL -> {
                val cmd = if (item.currentToggleOn) item.onCommand.ifBlank { item.targetData } else item.offCommand.ifBlank { item.targetData }
                collectionScope.launch {
                    val result = ShizukuHelper.executeCommand(cmd)
                    if (result.exitCode != 0) {
                        LogManager.logError(
                            "CollectionShell",
                            "Shell tile '${item.name}' failed: exit ${result.exitCode}: ${result.output}"
                        )
                    }
                }
            }
            TileType.FILES -> {
                val fileIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.targetData), "*/*")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(fileIntent)
            }
            TileType.FOLDERS -> {
                val folderIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.targetData), "vnd.android.document/root")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(folderIntent)
            }
            TileType.COLLECTION -> {
                val intent = Intent(context, CollectionPopupActivity::class.java).apply {
                    putExtra("collectionId", item.id)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
            }
        }
    } catch (e: Throwable) {
        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
