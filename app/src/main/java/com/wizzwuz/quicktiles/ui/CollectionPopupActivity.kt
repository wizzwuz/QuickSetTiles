package com.wizzwuz.quicktiles.ui

import android.app.Activity
import android.os.Bundle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wizzwuz.quicktiles.data.TileRepository
import com.wizzwuz.quicktiles.service.BaseTileService
import com.wizzwuz.quicktiles.util.IconProcessor
import kotlinx.coroutines.delay

class CollectionPopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CollectionPopupScreen(
                    collectionId = intent.getStringExtra("collectionId") ?: "",
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
                            val previewBitmap = remember(item.iconConfig, isDark) {
                                IconProcessor.processIconBitmap(context, item.iconConfig, targetPixelSize = 64, isDarkTheme = isDark)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        try {
                                            val serviceClass = Class.forName("com.wizzwuz.quicktiles.service.QuickTileService${item.slotNumber}")
                                            // Try to trigger tile action
                                        } catch (e: Exception) {}
                                        // For now just dismiss
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
