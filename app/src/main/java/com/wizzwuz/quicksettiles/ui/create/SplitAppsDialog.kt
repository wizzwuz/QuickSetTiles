package com.wizzwuz.quicksettiles.ui.create

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.wizzwuz.quicksettiles.util.AppHelper
import com.wizzwuz.quicksettiles.util.AppItem

@Composable
fun SplitAppsDialog(
    initialPkg1: String? = null,
    initialPkg2: String? = null,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (pkg1: String, pkg2: String, label1: String, label2: String, icon1: Drawable?, icon2: Drawable?) -> Unit
) {
    val context = LocalContext.current
    var app1 by remember { mutableStateOf<AppItem?>(null) }
    var app2 by remember { mutableStateOf<AppItem?>(null) }
    var pickingSide by remember { mutableStateOf<Int?>(null) } // 1 for left, 2 for right

    // Preload initial apps if editing existing split tile
    LaunchedEffect(initialPkg1, initialPkg2) {
        val apps = AppHelper.getInstalledApps(context)
        if (!initialPkg1.isNullOrBlank()) {
            app1 = apps.find { it.packageName == initialPkg1 }
        }
        if (!initialPkg2.isNullOrBlank()) {
            app2 = apps.find { it.packageName == initialPkg2 }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Back button on left, title "Split Apps", Close X on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Select App",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Split Apps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Start
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "Select two applications to launch side-by-side in split screen mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Split Selection Row: [Select App 1] [ ⇋ ] [Select App 2]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Filled Button: App 1
                    Button(
                        onClick = { pickingSide = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (app1 != null) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = app1!!.icon),
                                    contentDescription = app1!!.label,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app1!!.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text("Select App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Swap Button "⇋"
                    FilledTonalIconButton(
                        onClick = {
                            val temp = app1
                            app1 = app2
                            app2 = temp
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Text("⇋", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }

                    // Right Filled Button: App 2
                    Button(
                        onClick = { pickingSide = 2 },
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (app2 != null) {
                                Image(
                                    painter = rememberDrawablePainter(drawable = app2!!.icon),
                                    contentDescription = app2!!.label,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = app2!!.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text("Select App", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Confirm / Apply Button
                Button(
                    onClick = {
                        val a1 = app1
                        val a2 = app2
                        if (a1 != null && a2 != null) {
                            onConfirm(a1.packageName, a2.packageName, a1.label, a2.label, a1.icon, a2.icon)
                        }
                    },
                    enabled = app1 != null && app2 != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply Split Apps", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // Floating AppPickerSheet when user taps either Select App button
    if (pickingSide != null) {
        AppPickerSheet(
            onDismiss = { pickingSide = null },
            onAppSelected = { app ->
                if (pickingSide == 1) {
                    app1 = app
                } else if (pickingSide == 2) {
                    app2 = app
                }
                pickingSide = null
            },
            onOpenSplit = null
        )
    }
}
