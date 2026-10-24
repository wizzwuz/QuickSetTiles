package com.wizzwuz.quicktiles.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.wizzwuz.quicktiles.R
import com.wizzwuz.quicktiles.model.IconConfig
import com.wizzwuz.quicktiles.model.IconSourceType
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object IconProcessor {

    private val elementCache = ConcurrentHashMap<String, Bitmap>()

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

    /**
     * Extracts the pure icon element (glyph/emblem) of an app,
     * stripping out solid background squircles/circles from AdaptiveIconDrawables.
     */
    fun extractAppIconElement(context: Context, packageName: String, targetSize: Int = 160): Bitmap {
        val cached = elementCache[packageName]
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val extracted = try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = pm.getApplicationIcon(appInfo)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
                // 1. Check Android 13+ monochrome icon first (already outlined/monochrome vector)
                var monoBmp: Bitmap? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        val monoDrawable = drawable.monochrome
                        if (monoDrawable != null) {
                            monoBmp = drawableToBitmap(monoDrawable, targetSize, targetSize)
                        }
                    } catch (e: Throwable) {
                        monoBmp = null
                    }
                }

                if (monoBmp != null) {
                    trimAndCenterBitmap(monoBmp, targetSize)
                } else {
                    // 2. Extract foreground layer of AdaptiveIconDrawable (contains actual emblem without solid background)
                    val fg = drawable.foreground
                    if (fg != null) {
                        val fgBmp = drawableToBitmap(fg, targetSize, targetSize)
                        trimAndCenterBitmap(fgBmp, targetSize)
                    } else {
                        val rawBmp = drawableToBitmap(drawable, targetSize, targetSize)
                        trimAndCenterBitmap(isolateForegroundFromBackground(rawBmp), targetSize)
                    }
                }
            } else {
                // Non-adaptive or legacy icon: remove background if solid
                val rawBmp = drawableToBitmap(drawable, targetSize, targetSize)
                trimAndCenterBitmap(isolateForegroundFromBackground(rawBmp), targetSize)
            }
        } catch (e: Throwable) {
            getDefaultPresetBitmap(context)
        }

        elementCache[packageName] = extracted
        return extracted
    }

    /**
     * Automatically trims transparent outer padding and centers the glyph within targetSize x targetSize
     * with comfortable padding to prevent clipping when stroked.
     */
    fun trimAndCenterBitmap(src: Bitmap, targetSize: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        for (y in 0 until h) {
            for (x in 0 until w) {
                val a = (pixels[y * w + x] ushr 24) and 0xFF
                if (a > 20) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (maxX <= minX || maxY <= minY) {
            return Bitmap.createScaledBitmap(src, targetSize, targetSize, true)
        }

        val cropW = (maxX - minX + 1).coerceIn(1, w - minX)
        val cropH = (maxY - minY + 1).coerceIn(1, h - minY)
        val cropped = Bitmap.createBitmap(src, minX, minY, cropW, cropH)

        val paddedSize = (targetSize * 0.82f).toInt().coerceAtLeast(16)
        val aspect = cropW.toFloat() / cropH.toFloat()
        val finalW: Int
        val finalH: Int
        if (aspect >= 1f) {
            finalW = paddedSize
            finalH = (paddedSize / aspect).toInt().coerceAtLeast(1)
        } else {
            finalH = paddedSize
            finalW = (paddedSize * aspect).toInt().coerceAtLeast(1)
        }

        val scaled = Bitmap.createScaledBitmap(cropped, finalW, finalH, true)
        val centered = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(centered)
        val left = (targetSize - finalW) / 2f
        val top = (targetSize - finalH) / 2f
        canvas.drawBitmap(scaled, left, top, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        return centered
    }

    /**
     * Checks if a legacy bitmap icon has a uniform solid background color,
     * and keys it out to transparent so the foreground icon element can be outlined.
     */
    fun isolateForegroundFromBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return src

        val cTL = src.getPixel(2, 2)
        val cTR = src.getPixel(w - 3, 2)
        val cBL = src.getPixel(2, h - 3)
        val cBR = src.getPixel(w - 3, h - 3)

        // Already transparent borders
        if (Color.alpha(cTL) < 32 && Color.alpha(cTR) < 32 &&
            Color.alpha(cBL) < 32 && Color.alpha(cBR) < 32) {
            return src
        }

        fun colorDist(c1: Int, c2: Int): Float {
            val dr = Color.red(c1) - Color.red(c2)
            val dg = Color.green(c1) - Color.green(c2)
            val db = Color.blue(c1) - Color.blue(c2)
            return Math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
        }

        val avgBgR = (Color.red(cTL) + Color.red(cTR) + Color.red(cBL) + Color.red(cBR)) / 4
        val avgBgG = (Color.green(cTL) + Color.green(cTR) + Color.green(cBL) + Color.green(cBR)) / 4
        val avgBgB = (Color.blue(cTL) + Color.blue(cTR) + Color.blue(cBL) + Color.blue(cBR)) / 4
        val avgBgColor = Color.rgb(avgBgR, avgBgG, avgBgB)

        val maxDist = Math.max(
            Math.max(colorDist(cTL, avgBgColor), colorDist(cTR, avgBgColor)),
            Math.max(colorDist(cBL, avgBgColor), colorDist(cBR, avgBgColor))
        )

        if (maxDist > 40f) {
            // Non-uniform background, keep as-is
            return src
        }

        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val resultPixels = IntArray(w * h)

        for (i in pixels.indices) {
            val c = pixels[i]
            if (colorDist(c, avgBgColor) < 35f) {
                resultPixels[i] = 0 // transparent
            } else {
                resultPixels[i] = c
            }
        }

        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return res
    }

    /**
     * Renders a crisp, anti-aliased outline of the icon element.
     * Traces the outer silhouette boundary and significant internal edges,
     * applying anti-aliased white stroke rendering according to the requested outline style.
     */
    fun renderElementOutline(
        sourceBitmap: Bitmap,
        thicknessPx: Float,
        threshold: Float = 0.4f,
        style: String = "OUTLINE",
        targetSize: Int = 128,
        isDarkTheme: Boolean = true
    ): Bitmap {
        val w = sourceBitmap.width
        val h = sourceBitmap.height
        if (w <= 0 || h <= 0) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }

        val srcPixels = IntArray(w * h)
        sourceBitmap.getPixels(srcPixels, 0, w, 0, 0, w, h)

        val threshAlpha = (threshold * 255).toInt().coerceIn(1, 254)
        val isSolid = BooleanArray(w * h)
        val lum = IntArray(w * h)
        val isEdge = BooleanArray(w * h)

        var hasAnySolid = false
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val c = srcPixels[idx]
                val a = (c ushr 24) and 0xFF
                if (a >= threshAlpha) {
                    isSolid[idx] = true
                    hasAnySolid = true
                    val r = (c ushr 16) and 0xFF
                    val g = (c ushr 8) and 0xFF
                    val b = c and 0xFF
                    lum[idx] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                }
            }
        }

        if (!hasAnySolid) {
            return Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        }

        // 1. Detect outer silhouette contour & internal contrast edges
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!isSolid[idx]) continue

                val onBoundary = (x == 0 || !isSolid[idx - 1] ||
                                  x == w - 1 || !isSolid[idx + 1] ||
                                  y == 0 || !isSolid[idx - w] ||
                                  y == h - 1 || !isSolid[idx + w])

                if (onBoundary) {
                    isEdge[idx] = true
                } else {
                    // Check internal contrast against adjacent neighbors
                    val currentLum = lum[idx]
                    val currC = srcPixels[idx]
                    val currR = (currC ushr 16) and 0xFF
                    val currG = (currC ushr 8) and 0xFF
                    val currB = currC and 0xFF

                    var diff = 0
                    if (x < w - 1 && isSolid[idx + 1]) {
                        val rC = srcPixels[idx + 1]
                        val dL = Math.abs(currentLum - lum[idx + 1])
                        val dC = Math.max(Math.abs(currR - ((rC ushr 16) and 0xFF)),
                                 Math.max(Math.abs(currG - ((rC ushr 8) and 0xFF)),
                                          Math.abs(currB - (rC and 0xFF))))
                        diff = Math.max(diff, Math.max(dL, dC))
                    }
                    if (y < h - 1 && isSolid[idx + w]) {
                        val bC = srcPixels[idx + w]
                        val dL = Math.abs(currentLum - lum[idx + w])
                        val dC = Math.max(Math.abs(currR - ((bC ushr 16) and 0xFF)),
                                 Math.max(Math.abs(currG - ((bC ushr 8) and 0xFF)),
                                          Math.abs(currB - (bC and 0xFF))))
                        diff = Math.max(diff, Math.max(dL, dC))
                    }

                    if (diff > 45) {
                        isEdge[idx] = true
                    }
                }
            }
        }

        // 2. Stroke dilation and anti-aliasing
        val radius = thicknessPx.coerceIn(1.0f, 10.0f)
        val intRadius = Math.ceil(radius.toDouble()).toInt() + 1
        val strokeAlpha = FloatArray(w * h)

        val isGlow = style.equals("GLOW", ignoreCase = true)
        val maxDist = if (isGlow) (radius * 2.2f) else (radius + 0.6f)
        val scanRadius = if (isGlow) Math.ceil(maxDist.toDouble()).toInt() + 1 else intRadius

        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (!isEdge[idx]) continue

                for (dy in -scanRadius..scanRadius) {
                    val py = y + dy
                    if (py !in 0 until h) continue
                    for (dx in -scanRadius..scanRadius) {
                        val px = x + dx
                        if (px !in 0 until w) continue
                        val distSq = dx * dx + dy * dy
                        val dist = Math.sqrt(distSq.toDouble()).toFloat()

                        val intensity = if (isGlow) {
                            if (dist <= radius) {
                                1.0f
                            } else if (dist <= maxDist) {
                                (1.0f - (dist - radius) / (maxDist - radius)) * 0.7f
                            } else 0f
                        } else {
                            (radius + 0.6f - dist).coerceIn(0f, 1f)
                        }

                        if (intensity > 0f) {
                            val targetIdx = py * w + px
                            if (intensity > strokeAlpha[targetIdx]) {
                                strokeAlpha[targetIdx] = intensity
                            }
                        }
                    }
                }
            }
        }

        // 3. Render according to style
        val resultPixels = IntArray(w * h)
        val strokeRgb = if (isDarkTheme) 0x00FFFFFF else 0x00111827
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                val edgeA = (strokeAlpha[idx] * 255f).toInt().coerceIn(0, 255)

                when (style.uppercase()) {
                    "SOLID" -> {
                        if (isSolid[idx]) {
                            resultPixels[idx] = (0xFF shl 24) or strokeRgb
                        }
                    }
                    "FROSTED" -> {
                        if (edgeA > 0) {
                            resultPixels[idx] = (edgeA shl 24) or strokeRgb
                        } else if (isSolid[idx]) {
                            val innerA = if (isDarkTheme) 55 else 90
                            resultPixels[idx] = (innerA shl 24) or strokeRgb
                        }
                    }
                    else -> {
                        // "OUTLINE" & "GLOW"
                        if (edgeA > 0) {
                            resultPixels[idx] = (edgeA shl 24) or strokeRgb
                        }
                    }
                }
            }
        }

        val outBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        outBmp.setPixels(resultPixels, 0, w, 0, 0, w, h)
        return outBmp
    }

    fun loadRawBitmap(context: Context, config: IconConfig, isDarkTheme: Boolean = true): Bitmap {
        return when (config.sourceType) {
            IconSourceType.APP_ICON -> {
                extractAppIconElement(context, config.sourceData)
            }
            IconSourceType.CUSTOM_FILE -> {
                try {
                    val file = File(config.sourceData)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath) ?: getDefaultPresetBitmap(context, isDarkTheme)
                    } else {
                        getDefaultPresetBitmap(context, isDarkTheme)
                    }
                } catch (e: Throwable) {
                    getDefaultPresetBitmap(context, isDarkTheme)
                }
            }
            IconSourceType.PRESET -> {
                getPresetBitmap(context, config.sourceData, isDarkTheme)
            }
        }
    }

    private fun tintWhitePixelsToDark(src: Bitmap, targetColor: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var modified = false
        val tr = (targetColor ushr 16) and 0xFF
        val tg = (targetColor ushr 8) and 0xFF
        val tb = targetColor and 0xFF

        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a > 10) {
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b
                if (lum > 180f) {
                    pixels[i] = (a shl 24) or (tr shl 16) or (tg shl 8) or tb
                    modified = true
                }
            }
        }
        if (!modified) return src
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    fun processIconBitmap(
        context: Context,
        config: IconConfig,
        targetPixelSize: Int = 128,
        isDarkTheme: Boolean = true
    ): Bitmap {
        val density = context.resources.displayMetrics.density

        val rawElement: Bitmap = when (config.sourceType) {
            IconSourceType.APP_ICON -> {
                extractAppIconElement(context, config.sourceData, targetSize = targetPixelSize)
            }
            IconSourceType.CUSTOM_FILE -> {
                try {
                    val file = File(config.sourceData)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath) ?: getDefaultPresetBitmap(context, isDarkTheme)
                    } else {
                        getDefaultPresetBitmap(context, isDarkTheme)
                    }
                } catch (e: Throwable) {
                    getDefaultPresetBitmap(context, isDarkTheme)
                }
            }
            IconSourceType.PRESET -> {
                getPresetBitmap(context, config.sourceData, isDarkTheme)
            }
        }

        // Scale factor from iconSize (16dp to 64dp, base 32dp)
        val scaleFactor = (config.iconSize / 32f).coerceIn(0.5f, 2.0f)
        val scaledDim = ((targetPixelSize * 0.75f) * scaleFactor).toInt().coerceIn(24, targetPixelSize)

        val scaledElement = Bitmap.createScaledBitmap(rawElement, scaledDim, scaledDim, true)

        val thicknessPx = config.outlineThickness * density

        val renderedBitmap = if (config.sourceType == IconSourceType.APP_ICON || thicknessPx > 0.5f) {
            val effectiveThickness = if (thicknessPx > 0.2f) thicknessPx else (2.5f * density)
            renderElementOutline(
                sourceBitmap = scaledElement,
                thicknessPx = effectiveThickness,
                threshold = config.outlineThreshold,
                style = config.outlineStyle,
                targetSize = scaledDim,
                isDarkTheme = isDarkTheme
            )
        } else {
            if (!isDarkTheme) {
                tintWhitePixelsToDark(scaledElement, 0xFF111827.toInt())
            } else {
                scaledElement
            }
        }

        val resultBitmap = Bitmap.createBitmap(targetPixelSize, targetPixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val left = (targetPixelSize - scaledDim) / 2f
        val top = (targetPixelSize - scaledDim) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
        canvas.drawBitmap(renderedBitmap, left, top, paint)

        return resultBitmap
    }

    fun createTileIcon(context: Context, config: IconConfig): Icon {
        // Quick Settings tiles on Android use monochrome white mask for SystemUI tinting
        val bmp = processIconBitmap(context, config, targetPixelSize = 96, isDarkTheme = true)
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

    fun saveAppOutlineIcon(context: Context, packageName: String, bitmap: Bitmap): String? {
        return try {
            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }
            val destFile = File(iconsDir, "outline_${packageName}_${System.currentTimeMillis()}.png")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            destFile.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Saves a Bitmap directly to the icons directory (for shortcut icons, etc.)
     * Returns the absolute file path on success, null on failure.
     */
    fun saveBitmapToFile(context: Context, bitmap: Bitmap, prefix: String = "shortcut"): String? {
        return try {
            val iconsDir = File(context.filesDir, "icons").apply { if (!exists()) mkdirs() }
            val destFile = File(iconsDir, "${prefix}_${System.currentTimeMillis()}.png")
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            destFile.absolutePath
        } catch (e: Throwable) {
            null
        }
    }

    fun drawableToBitmap(drawable: Drawable, widthOverride: Int = -1, heightOverride: Int = -1): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && widthOverride <= 0 && heightOverride <= 0) {
            return drawable.bitmap
        }
        val width = if (widthOverride > 0) widthOverride else if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (heightOverride > 0) heightOverride else if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun getDefaultPresetBitmap(context: Context, isDarkTheme: Boolean = true): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_tile_default)?.mutate()
            ?: return Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        d.setTint(if (isDarkTheme) Color.WHITE else 0xFF111827.toInt())
        return drawableToBitmap(d)
    }

    private fun getPresetBitmap(context: Context, key: String, isDarkTheme: Boolean = true): Bitmap {
        // Draw crisp vector icon dynamically
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val iconColor = if (isDarkTheme) Color.WHITE else 0xFF111827.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
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
                return getDefaultPresetBitmap(context, isDarkTheme)
            }
        }
        return bitmap
    }
}
