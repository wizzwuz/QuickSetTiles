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
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.model.TileItem
import com.wizzwuz.quicktiles.model.TileType
import com.wizzwuz.quicktiles.util.IconProcessor
import com.wizzwuz.quicktiles.util.AppHelper
import kotlinx.coroutines.delay

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionPopupScreen(
    collectionId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repo = remember { TileRepository.getInstance(context) }
    val tiles = repo.tilesFlow.collectAsState()
    val collection = tiles.value.find { it.id == collectionId }
    val collectionItems = collection?.collectionItemIds?.mapNotNull { id -> tiles.value.find { it.id == id } } ?: emptyList()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable { visible = false; onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(250), initialOffsetY = { it }),
            exit = slideOutVertically(animationSpec = tween(200), targetOffsetY = { it })
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        collection?.name ?: "Collection",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        collectionItems.forEach { item ->
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            val previewBitmap = remember(item.id, isDark) {
                                IconProcessor.loadRawBitmap(context, item.iconConfig, isDarkTheme = isDark)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        executeCollectionItem(context, item)
                                        visible = false
                                        onDismiss()
                                    }
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = previewBitmap.asImageBitmap(),
                                    contentDescription = item.name,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    item.name,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 80.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    AppHelper.launchActivityPrivileged(context, comp.packageName, comp.className)
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
                com.wizzwuz.quicktiles.util.ShizukuHelper.executeCommand(cmd)
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
