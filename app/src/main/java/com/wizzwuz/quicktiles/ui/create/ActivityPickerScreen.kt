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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.wizzwuz.quicktiles.util.ShizukuHelper
import kotlinx.coroutines.launch

data class AppPickerItem(
    val app: AppItem,
    val matchingActivities: List<ActivityItem>,
    val appMatches: Boolean
)

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
    // Detect root access once for the lifetime of this screen
    var isRootGranted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isRootGranted = ShizukuHelper.isRootGranted()
    }

    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(false) }

    // Map of packageName to activities list
    val activitiesMap = remember { mutableStateMapOf<String, List<ActivityItem>>() }
    // Set of currently expanded packageNames
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var manuallyCollapsedApps by remember { mutableStateOf(setOf<String>()) }
    var manuallyExpandedApps by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        val loadedApps = AppHelper.getInstalledApps(context)
        apps = loadedApps
        isLoading = false

        // Background preload all activities for instant search across both short and full activity names
        AppHelper.preloadAllActivities(context, loadedApps) { pkg, acts ->
            activitiesMap[pkg] = acts
        }
    }

    val q = searchQuery.trim().lowercase()

    // Filter system apps based on toggle
    val visibleApps = remember(apps, showSystemApps) {
        if (showSystemApps) apps else apps.filter { !it.isSystemApp }
    }

    val filteredItems = remember(visibleApps, q, activitiesMap.toMap()) {
        if (q.isBlank()) {
            visibleApps.map { app ->
                AppPickerItem(
                    app = app,
                    matchingActivities = emptyList(),
                    appMatches = true
                )
            }
        } else {
            visibleApps.mapNotNull { app ->
                val appMatches = app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
                val activities = activitiesMap[app.packageName]
                val matchingActivities = activities?.filter { act ->
                    act.simpleName.lowercase().contains(q) || act.fullName.lowercase().contains(q)
                } ?: emptyList()

                if (appMatches || matchingActivities.isNotEmpty()) {
                    AppPickerItem(
                        app = app,
                        matchingActivities = matchingActivities,
                        appMatches = appMatches
                    )
                } else {
                    null
                }
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
                onValueChange = {
                    searchQuery = it
                    manuallyCollapsedApps = emptySet()
                    manuallyExpandedApps = emptySet()
                },
                label = { Text("Search apps & activities") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            manuallyCollapsedApps = emptySet()
                            manuallyExpandedApps = emptySet()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // System apps toggle + tip row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Tap to select, long-press to test run.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = {
                        Text(
                            text = if (showSystemApps) "System: On" else "System: Off",
                            fontSize = 11.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showSystemApps) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No apps or activities match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            searchQuery = ""
                            manuallyCollapsedApps = emptySet()
                            manuallyExpandedApps = emptySet()
                        }) {
                            Text("Clear search")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.app.packageName }) { item ->
                        val app = item.app
                        val isExpanded = if (q.isNotBlank()) {
                            if (app.packageName in manuallyCollapsedApps) {
                                false
                            } else {
                                item.matchingActivities.isNotEmpty() || app.packageName in manuallyExpandedApps
                            }
                        } else {
                            expandedApps.contains(app.packageName)
                        }
                        val allActivities = activitiesMap[app.packageName]
                        val displayedActivities = if (q.isNotBlank() && item.matchingActivities.isNotEmpty()) {
                            item.matchingActivities
                        } else {
                            allActivities ?: emptyList()
                        }

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
                                                    if (q.isNotBlank()) {
                                                        manuallyCollapsedApps = manuallyCollapsedApps + app.packageName
                                                        manuallyExpandedApps = manuallyExpandedApps - app.packageName
                                                    } else {
                                                        expandedApps = expandedApps - app.packageName
                                                    }
                                                } else {
                                                    if (q.isNotBlank()) {
                                                        manuallyExpandedApps = manuallyExpandedApps + app.packageName
                                                        manuallyCollapsedApps = manuallyCollapsedApps - app.packageName
                                                    } else {
                                                        expandedApps = expandedApps + app.packageName
                                                    }
                                                    if (allActivities == null) {
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

                                    if (q.isNotBlank() && item.matchingActivities.isNotEmpty()) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(
                                                text = "${item.matchingActivities.size} match",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
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
                                        if (allActivities == null && displayedActivities.isEmpty()) {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            }
                                        } else if (displayedActivities.isEmpty()) {
                                            Text(
                                                text = if (q.isNotBlank()) "No matching activities in this app" else "No declared activities found",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        } else {
                                            if (q.isNotBlank() && item.matchingActivities.isNotEmpty() && allActivities != null && allActivities.size > item.matchingActivities.size) {
                                                Text(
                                                    text = "Showing ${item.matchingActivities.size} matching of ${allActivities.size} activities",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                            displayedActivities.forEach { act ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (!act.isExported && !isPrivateLaunchable && !isRootGranted) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Private activity: enable root or 'Private Activity Launchable' in Settings.",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()
                                                                } else {
                                                                    onActivitySelected(app, act)
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (!act.isExported && !isPrivateLaunchable && !isRootGranted) {
                                                                    Toast.makeText(
                                                                        context,
                                                                        "Launch test failed: enable root or 'Private Activity Launchable' in Settings.",
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
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                softWrap = true
                                                            )
                                                        }

                                                        // Compact access-mode chip (fixed narrow width)
                                                        if (!act.isExported) {
                                                            val (chipLabel, chipColor) = when {
                                                                isRootGranted ->
                                                                    "Root" to MaterialTheme.colorScheme.tertiary
                                                                isPrivateLaunchable ->
                                                                    "Shizuku" to MaterialTheme.colorScheme.primary
                                                                else ->
                                                                    "Off" to MaterialTheme.colorScheme.error
                                                            }
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = chipColor.copy(alpha = 0.12f),
                                                                modifier = Modifier.padding(start = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = chipLabel,
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = chipColor,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        } else {
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                                                modifier = Modifier.padding(start = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Public",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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
}
