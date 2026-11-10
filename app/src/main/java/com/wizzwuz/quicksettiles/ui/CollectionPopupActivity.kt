package com.wizzwuz.quicksettiles.ui

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
import androidx.compose.foundation.BorderStroke
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
import com.wizzwuz.quicksettiles.data.TileRepository
import com.wizzwuz.quicksettiles.model.IconSourceType
import com.wizzwuz.quicksettiles.model.TileItem
import com.wizzwuz.quicksettiles.model.TileType
import com.wizzwuz.quicksettiles.model.TileVisualState
import com.wizzwuz.quicksettiles.ui.theme.QuickTilesTheme
import com.wizzwuz.quicksettiles.ui.theme.ThemeManager
import com.wizzwuz.quicksettiles.QuickTilesApp
import com.wizzwuz.quicksettiles.util.AppHelper
import com.wizzwuz.quicksettiles.util.IconProcessor
import com.wizzwuz.quicksettiles.util.LogManager
import com.wizzwuz.quicksettiles.util.PopupToastHelper
import com.wizzwuz.quicksettiles.util.ProcessManager
import com.wizzwuz.quicksettiles.util.ShizukuHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CollectionPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        setContent {
            val themeMode by ThemeManager.themeFlow.collectAsState()
            QuickTilesTheme(themeMode = themeMode) {
                CollectionPopupScreen(
                    collectionId = intent?.extras?.getString("collectionId") ?: "",
                    onDismiss = {
                        finishAndRemoveTask()
                        overridePendingTransition(0, android.R.anim.fade_out)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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
        if (!visible) return
        visible = false
        coroutineScope.launch {
            delay(160)
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
                animationSpec = tween(260),
                initialOffsetY = { it }
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                animationSpec = tween(180),
                targetOffsetY = { it }
            ) + fadeOut(animationSpec = tween(160))
        ) {
            Card(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 12.dp, vertical = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = false
                    ) {},
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(5.dp)
                ) {
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

                    if (collectionItems.isEmpty()) {
                        Text(
                            "No tiles in collection",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val perRow = (collection?.tilesPerRow ?: 3).coerceAtLeast(1)
                        val chunkedRows = collectionItems.chunked(perRow)
                        Column(
                            modifier = Modifier.wrapContentSize(),
                            verticalArrangement = Arrangement.spacedBy(5.4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            chunkedRows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.wrapContentSize(),
                                    horizontalArrangement = Arrangement.spacedBy(5.4.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowItems.forEach { item ->
                                        CollectionTileItemView(
                                            item = item,
                                            context = context,
                                            isDark = isDark,
                                            onClick = {
                                                val shouldCollapse = if (item.type == TileType.SHELL && item.multiShellSteps.isNotEmpty()) {
                                                    val stepIndex = item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size
                                                    item.multiShellSteps[stepIndex].collapsePanel
                                                } else {
                                                    item.collapseCollectionOnClick
                                                }

                                                executeCollectionItem(context, repo, item)
                                                val isLaunchable = when (item.type) {
                                                    TileType.APPS, TileType.WEBSITE, TileType.SHORTCUT,
                                                    TileType.ACTIVITY, TileType.INTENT, TileType.FILES, TileType.FOLDERS -> true
                                                    TileType.SHELL -> {
                                                        val cmd = if (item.multiShellSteps.isNotEmpty()) {
                                                            val stepIndex = item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size
                                                            item.multiShellSteps[stepIndex].command
                                                        } else {
                                                            when (item.visualState) {
                                                                TileVisualState.TOGGLE -> if (item.currentToggleOn) {
                                                                    if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                                                                } else {
                                                                    if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                                                                }
                                                                TileVisualState.ENABLED -> if (item.onCommand.isNotBlank()) item.onCommand else item.targetData
                                                                TileVisualState.DISABLED -> if (item.offCommand.isNotBlank()) item.offCommand else item.targetData
                                                            }
                                                        }
                                                        isShellCommandLaunchable(cmd)
                                                    }
                                                    TileType.COLLECTION -> false
                                                }

                                                if (shouldCollapse) {
                                                    dismissAnimated()
                                                } else if (isLaunchable) {
                                                    collectionScope.launch {
                                                        delay(350)
                                                        val bringToFront = Intent(context, CollectionPopupActivity::class.java).apply {
                                                            putExtra("collectionId", collectionId)
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                                        }
                                                        try {
                                                            context.startActivity(bringToFront)
                                                            delay(450)
                                                            context.startActivity(bringToFront)
                                                        } catch (e: Throwable) {
                                                            // ignore
                                                        }
                                                    }
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
    val isTileEnabled = if (item.multiShellSteps.isNotEmpty()) {
        val step = item.multiShellSteps[item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size]
        step.visualState == TileVisualState.ENABLED
    } else {
        when (item.visualState) {
            TileVisualState.ENABLED -> true
            TileVisualState.DISABLED -> false
            TileVisualState.TOGGLE -> item.currentToggleOn
        }
    }

    val displayName = if (item.multiShellSteps.isNotEmpty()) {
        val step = item.multiShellSteps[item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size]
        step.name.ifBlank { item.name }
    } else {
        when (item.visualState) {
            TileVisualState.TOGGLE -> if (item.currentToggleOn) item.onTileName.ifBlank { item.name } else item.offTileName.ifBlank { item.name }
            TileVisualState.ENABLED -> item.name
            TileVisualState.DISABLED -> item.offTileName.ifBlank { item.name }
        }
    }

    val effectiveIcon = if (item.multiShellSteps.isNotEmpty()) {
        val step = item.multiShellSteps[item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size]
        step.iconConfig
    } else {
        when (item.visualState) {
            TileVisualState.TOGGLE -> if (item.currentToggleOn) (item.onIconConfig ?: item.iconConfig) else (item.offIconConfig ?: item.iconConfig)
            TileVisualState.ENABLED -> item.iconConfig
            TileVisualState.DISABLED -> item.offIconConfig ?: item.iconConfig
        }
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
            .width(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(animatedContainerColor)
                .border(1.5.dp, animatedBorderColor, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = displayName,
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer(alpha = animatedIconAlpha)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = displayName,
            fontSize = 10.5.sp,
            fontWeight = if (isTileEnabled) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = animatedTextColor
        )
    }
}

private val collectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

private fun isShellCommandLaunchable(cmd: String): Boolean {
    val pattern = Regex("""\b(am\s+start|cmd\s+activity\s+start|monkey\b|launch-private|am-private)""", RegexOption.IGNORE_CASE)
    return pattern.containsMatchIn(cmd)
}

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
                ProcessManager.onTileTaskStarted()
                QuickTilesApp.applicationScope.launch(Dispatchers.Main) {
                    try {
                        val cmdToRun: String

                        if (item.multiShellSteps.isNotEmpty()) {
                            val stepIndex = item.currentMultiShellIndex.coerceAtLeast(0) % item.multiShellSteps.size
                            val step = item.multiShellSteps[stepIndex]
                            cmdToRun = step.command
                            val nextIndex = (stepIndex + 1) % item.multiShellSteps.size
                            val updatedItem = item.copy(currentMultiShellIndex = nextIndex)
                            repo.saveTile(updatedItem)
                        } else {
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
                        }

                        val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                            ShizukuHelper.executeCommand(cmdToRun)
                        }

                        if (item.showPopups && result.output.isNotBlank()) {
                            val lines = result.output.lines().map { it.trim() }.filter { it.isNotEmpty() }
                            for (line in lines) {
                                PopupToastHelper.showToast(context.applicationContext, line)
                                if (lines.size > 1) {
                                    delay(1200)
                                }
                            }
                        }

                        if (result.exitCode != 0) {
                            LogManager.logError(
                                "CollectionShell",
                                "Shell tile '${item.name}' failed: exit ${result.exitCode}: ${result.output}"
                            )
                        }
                    } finally {
                        ProcessManager.onTileTaskFinished()
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
