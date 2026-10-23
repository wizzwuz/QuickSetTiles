package com.wizzwuz.quicktiles.ui.create

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
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
import com.wizzwuz.quicktiles.util.ActivityItem
import com.wizzwuz.quicktiles.util.AppHelper
import com.wizzwuz.quicktiles.util.AppItem
import com.wizzwuz.quicktiles.util.IconProcessor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ActivityPickerScreen(
    onBack: () -> Unit,
    onActivitySelected: (app: AppItem, activity: ActivityItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { com.wizzwuz.quicktiles.data.TileRepository.getInstance(context) }
    val isPrivateLaunchable by repository.privateActivityLaunchableFlow.collectAsState()

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Map of packageName to activities list
    val activitiesMap = remember { mutableStateMapOf<String, List<ActivityItem>>() }
    // Set of currently expanded packageNames
    var expandedApps by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        apps = AppHelper.getInstalledApps(context)
        isLoading = false
    }

    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            val q = searchQuery.trim().lowercase()
            apps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Activities Browser") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search apps & activities") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Text(
                text = "Tip: Tap app to expand activities. Tap an activity to select it, or long-press to test run it.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isExpanded = expandedApps.contains(app.packageName)
                        val activities = activitiesMap[app.packageName]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isExpanded)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // App Header Row (Tap to expand/collapse)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (isExpanded) {
                                                    expandedApps = expandedApps - app.packageName
                                                } else {
                                                    expandedApps = expandedApps + app.packageName
                                                    if (activities == null) {
                                                        coroutineScope.launch {
                                                            val acts = AppHelper.getAppActivities(context, app.packageName)
                                                            activitiesMap[app.packageName] = acts
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val bmp = remember(app.packageName) { IconProcessor.drawableToBitmap(app.icon) }
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = app.label,
                                        modifier = Modifier.size(40.dp)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                                    )
                                }

                                // Expanded Activities List
                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (activities == null) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        } else if (activities.isEmpty()) {
                                            Text(
                                                text = "No declared activities found",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        } else {
                                            activities.forEach { act ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!act.isExported && !isPrivateLaunchable) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Private activity launch is disabled in Settings. Enable 'Private Activity Launchable' in Settings to use private activities.",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                } else {
                                                                    onActivitySelected(app, act)
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (!act.isExported && !isPrivateLaunchable) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Launch test failed: Private activity launch is disabled in Settings. Enable 'Private Activity Launchable' in Settings.",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                    return@combinedClickable
                                                                }
                                                                // Execute activity immediately for testing!
                                                                coroutineScope.launch {
                                                                    val result = AppHelper.testLaunchActivity(
                                                                        context,
                                                                        app.packageName,
                                                                        act.fullName,
                                                                        act.isExported
                                                                    )
                                                                    result.onSuccess { msg ->
                                                                        Toast.makeText(
                                                                            context,
                                                                            "Testing: $msg",
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }.onFailure { err ->
                                                                        Toast.makeText(
                                                                            context,
                                                                            "Launch test failed: ${err.message}",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                    }
                                                                }
                                                            }
                                                        ),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    tonalElevation = 2.dp
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = act.simpleName,
                                                                fontWeight = FontWeight.SemiBold,
                                                                fontSize = 13.sp
                                                            )
                                                            Text(
                                                                text = act.fullName,
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                maxLines = 1
                                                            )
                                                        }

                                                        if (!act.isExported) {
                                                            SuggestionChip(
                                                                onClick = {},
                                                                label = {
                                                                    Text(
                                                                        if (isPrivateLaunchable) "Private (Shizuku)" else "Private (Disabled)",
                                                                        fontSize = 9.sp,
                                                                        color = if (isPrivateLaunchable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                                                    )
                                                                },
                                                                modifier = Modifier.height(20.dp)
                                                            )
                                                        } else {
                                                            SuggestionChip(
                                                                onClick = {},
                                                                label = { Text("Exported", fontSize = 9.sp) },
                                                                modifier = Modifier.height(20.dp)
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
                }
            }
        }
    }
}
