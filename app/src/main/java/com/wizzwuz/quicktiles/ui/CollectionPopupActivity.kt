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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.IconSourceType
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.model.TileVisualState
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.LogManager
import com.wizzwuz.quicktiles.util.ShizukuHelper
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
                    .wrapContentWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {},
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Collection Title
                    Text(
                        text = collection?.name ?: "Collection",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 1.dp
                    )

                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                    if (collectionItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp, horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tiles in this collection",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val perRow = (collection?.tilesPerRow ?: 3).coerceIn(2, 5)
                        val chunkedRows = collectionItems.chunked(perRow)
                        Column(
                            modifier = Modifier.wrapContentWidth(),
                            verticalArrangement = Arrangement.spacedBy(5.4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            chunkedRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.wrapContentWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.4.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowItems.forEach { item ->
                                        CollectionTileItemView(
                                            item = item,
                                            context = context,
                                            isDark = isDark,
                                            onClick = {
                                                executeCollectionItem(context, repo, item)
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
    val isTileEnabled = when (item.visualState) {
        TileVisualState.ENABLED -> true
        TileVisualState.DISABLED -> false
        TileVisualState.TOGGLE -> item.currentToggleOn
    }

    val displayName = when (item.visualState) {
        TileVisualState.TOGGLE -> if (item.currentToggleOn) item.onTileName.ifBlank { item.name } else item.offTileName.ifBlank { item.name }
        TileVisualState.ENABLED -> item.name
        TileVisualState.DISABLED -> item.offTileName.ifBlank { item.name }
    }

    val effectiveIcon = when (item.visualState) {
        TileVisualState.TOGGLE -> if (item.currentToggleOn) (item.onIconConfig ?: item.iconConfig) else (item.offIconConfig ?: item.iconConfig)
        TileVisualState.ENABLED -> item.iconConfig
        TileVisualState.DISABLED -> item.offIconConfig ?: item.iconConfig
    }

    val previewBitmap = remember(item.id, effectiveIcon, isDark) {
        if (effectiveIcon.sourceType == IconSourceType.APP_ICON) {
            IconProcessor.loadRawBitmap(context, effectiveIcon, targetPixelSize = 128, isDarkTheme = isDark)
        } else if (effectiveIcon.outlineThickness > 0.5f) {
            IconProcessor.processIconBitmap(context, effectiveIcon, targetPixelSize = 128, isDarkTheme = isDark)
        } else {
            IconProcessor.loadRawBitmap(context, effectiveIcon, targetPixelSize = 128, isDarkTheme = isDark)
        }
    }

    val animDuration = 220
    val targetContainerColor = if (isTileEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = animDuration),
        label = "containerColor"
    )

    val targetBorderColor = if (isTileEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val animatedBorderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = animDuration),
        label = "borderColor"
    )

    val targetIconAlpha = if (isTileEnabled) 1.0f else 0.42f
    val animatedIconAlpha by animateFloatAsState(
        targetValue = targetIconAlpha,
        animationSpec = tween(durationMillis = animDuration),
        label = "iconAlpha"
    )

    val targetTextColor = if (isTileEnabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    }
    val animatedTextColor by animateColorAsState(
        targetValue = targetTextColor,
        animationSpec = tween(durationMillis = animDuration),
        label = "textColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .width(62.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(animatedContainerColor)
                .border(1.5.dp, animatedBorderColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier
                    .size(36.dp)
                    .graphicsLayer(alpha = animatedIconAlpha)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayName,
            fontSize = 11.sp,
            fontWeight = if (isTileEnabled) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = animatedTextColor
        )
    }
}

private val collectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun executeCollectionItem(context: android.content.Context, repo: TileRepository, item: TileItem) {
    try {
        if (item.visualState == TileVisualState.TOGGLE && item.type != TileType.SHELL) {
            val updated = item.copy(currentToggleOn = !item.currentToggleOn)
            repo.saveTile(updated)
        }

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
                collectionScope.launch {
                    val cmdToRun: String
                    when (item.visualState) {
                        TileVisualState.TOGGLE -> {
                            val nextToggleState = !item.currentToggleOn
                            cmdToRun = if (item.currentToggleOn) {
                                if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                            } else {
                                if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                            }
                            val updatedItem = item.copy(currentToggleOn = nextToggleState)
                            repo.saveTile(updatedItem)
                        }
                        TileVisualState.ENABLED -> {
                            cmdToRun = if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                        }
                        TileVisualState.DISABLED -> {
                            cmdToRun = if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                        }
                    }

                    val result = ShizukuHelper.executeCommand(cmdToRun)
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
