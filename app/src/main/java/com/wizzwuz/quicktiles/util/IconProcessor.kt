package com.wizzwuz.quicktiles.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import androidx.core.content.ContextCompat
import com.wizzwuz.quicktiles.R
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import java.io.File
import java.io.FileOutputStream

object IconProcessor {

    val PRESET_ICONS = listOf(
        "tile_default" to "Default Tile",
        "apps" to "App",
        "web" to "Website",
        "terminal" to "Terminal/Shell",
        "folder" to "Folder",
        "file" to "File",
        "settings" to "Settings",
        "star" to "Star",
        "music" to "Music",
        "camera" to "Camera",
        "flash" to "Flashlight"
    )

    fun loadRawBitmap(context: Context, config: IconConfig): Bitmap {
        return when (config.sourceType) {
            IconSourceType.APP_ICON -> {
                try {
                    val pm = context.packageManager
                    val drawable = pm.getApplicationIcon(config.sourceData)
                    drawableToBitmap(drawable)
                } catch (e: Throwable) {
                    getDefaultPresetBitmap(context)
                }
            }
            IconSourceType.CUSTOM_FILE -> {
                try {
                    val file = File(config.sourceData)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath) ?: getDefaultPresetBitmap(context)
                    } else {
                        getDefaultPresetBitmap(context)
                    }
                } catch (e: Throwable) {
                    getDefaultPresetBitmap(context)
                }
            }
            IconSourceType.PRESET -> {
                getPresetBitmap(context, config.sourceData)
            }
        }
    }

    fun processIconBitmap(context: Context, config: IconConfig, targetPixelSize: Int = 128): Bitmap {
        val raw = loadRawBitmap(context, config)
        val density = context.resources.displayMetrics.density

        // Desired size scaled by iconSize (16dp to 64dp, base 32dp)
        val scaleFactor = (config.iconSize / 32f).coerceIn(0.5f, 2.0f)
        val scaledDim = ((targetPixelSize * 0.75f) * scaleFactor).toInt().coerceIn(24, targetPixelSize)

        val scaledBitmap = Bitmap.createScaledBitmap(raw, scaledDim, scaledDim, true)

        val resultBitmap = Bitmap.createBitmap(targetPixelSize, targetPixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        val left = (targetPixelSize - scaledDim) / 2f
        val top = (targetPixelSize - scaledDim) / 2f

        val thicknessPx = config.outlineThickness * density
        val thresholdAlpha = (config.outlineThreshold * 255).toInt().coerceIn(1, 254)

        if (thicknessPx > 0.5f) {
            // Generate thresholded alpha mask
            val alphaMask = scaledBitmap.extractAlpha()
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = thicknessPx * 2f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                isFilterBitmap = true
                maskFilter = BlurMaskFilter(thicknessPx.coerceAtLeast(1f), BlurMaskFilter.Blur.SOLID)
            }

            // Draw outline
            canvas.drawBitmap(alphaMask, left, top, outlinePaint)
        }

        // Draw the core icon
        val contentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmap(scaledBitmap, left, top, contentPaint)

        return resultBitmap
    }

    fun createTileIcon(context: Context, config: IconConfig): Icon {
        val bmp = processIconBitmap(context, config, targetPixelSize = 96)
        return Icon.createWithBitmap(bmp)
    }

    fun saveCustomImage(context: Context, uri: Uri): String? {
        return try {
            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }
            val destFile = File(iconsDir, "icon_${System.currentTimeMillis()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getDefaultPresetBitmap(context: Context): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_tile_default)
            ?: return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        return drawableToBitmap(d)
    }

    private fun getPresetBitmap(context: Context, key: String): Bitmap {
        // Draw crisp vector icon dynamically
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        when (key) {
            "web" -> {
                // Draw globe
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                canvas.drawCircle(48f, 48f, 36f, paint)
                canvas.drawLine(12f, 48f, 84f, 48f, paint)
                val oval = RectF(28f, 12f, 68f, 84f)
                canvas.drawOval(oval, paint)
            }
            "terminal" -> {
                // Draw terminal prompt > _
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 7f
                paint.strokeCap = Paint.Cap.ROUND
                val path = Path().apply {
                    moveTo(24f, 28f)
                    lineTo(46f, 48f)
                    lineTo(24f, 68f)
                }
                canvas.drawPath(path, paint)
                canvas.drawLine(52f, 68f, 74f, 68f, paint)
            }
            "folder" -> {
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(16f, 28f)
                    lineTo(40f, 28f)
                    lineTo(48f, 36f)
                    lineTo(80f, 36f)
                    lineTo(80f, 76f)
                    lineTo(16f, 76f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            "file" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                val path = Path().apply {
                    moveTo(24f, 18f)
                    lineTo(56f, 18f)
                    lineTo(74f, 36f)
                    lineTo(74f, 80f)
                    lineTo(24f, 80f)
                    close()
                }
                canvas.drawPath(path, paint)
                canvas.drawLine(34f, 48f, 64f, 48f, paint)
                canvas.drawLine(34f, 62f, 64f, 62f, paint)
            }
            "settings" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                canvas.drawCircle(48f, 48f, 24f, paint)
                paint.strokeWidth = 6f
                for (i in 0 until 8) {
                    val angle = (i * 45).toDouble() * Math.PI / 180.0
                    val x1 = 48f + (28f * Math.cos(angle)).toFloat()
                    val y1 = 48f + (28f * Math.sin(angle)).toFloat()
                    val x2 = 48f + (40f * Math.cos(angle)).toFloat()
                    val y2 = 48f + (40f * Math.sin(angle)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, paint)
                }
            }
            "star" -> {
                paint.style = Paint.Style.FILL
                val path = Path()
                val cx = 48f; val cy = 48f; val outer = 38f; val inner = 18f
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) outer else inner
                    val angle = i * Math.PI / 5 - Math.PI / 2
                    val x = cx + (r * Math.cos(angle)).toFloat()
                    val y = cy + (r * Math.sin(angle)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            "flash" -> {
                paint.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(52f, 12f)
                    lineTo(26f, 50f)
                    lineTo(46f, 50f)
                    lineTo(42f, 86f)
                    lineTo(70f, 42f)
                    lineTo(50f, 42f)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            else -> {
                return getDefaultPresetBitmap(context)
            }
        }
        return bitmap
    }
}
