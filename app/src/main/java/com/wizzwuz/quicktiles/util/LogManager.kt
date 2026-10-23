package com.wizzwuz.quicktiles.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogStats(
    val exists: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val lineCount: Int,
    val lastModified: Long
)

object LogManager {

    private const val LOG_FILE_NAME = "log.txt"
    private var appContext: Context? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _updateFlow = MutableStateFlow(0L)
    val updateFlow: StateFlow<Long> = _updateFlow.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, LOG_FILE_NAME)
    }

    @Synchronized
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        writeEntry("ERROR", tag, message, throwable)
    }

    @Synchronized
    fun logInfo(tag: String, message: String) {
        writeEntry("INFO", tag, message, null)
    }

    @Synchronized
    fun logCommand(
        mode: String,
        command: String,
        exitCode: Int,
        output: String,
        durationMs: Long? = null
    ) {
        val level = if (exitCode == 0) "INFO" else "ERROR"
        val statusText = if (exitCode == 0) "SUCCESS" else "EXIT $exitCode"
        val timeText = if (durationMs != null) " (${durationMs}ms)" else ""
        val preview = output.trim().replace("\n", "\n    | ")
        val body = buildString {
            append("Command: $command\n")
            append("Mode: $mode | Status: $statusText$timeText\n")
            if (preview.isNotBlank()) {
                append("Output:\n    | $preview")
            } else {
                append("Output: <empty>")
            }
        }
        writeEntry(level, "ShellExecution", body, null)
    }

    private fun writeEntry(level: String, tag: String, message: String, throwable: Throwable?) {
        try {
            val file = getFile() ?: return
            val timestamp = dateFormat.format(Date())
            val writer = FileWriter(file, true)
            writer.use { fw ->
                fw.write("[$timestamp] [$level] [$tag] $message\n")
                if (throwable != null) {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    fw.write(sw.toString() + "\n")
                }
                fw.write("----------------------------------------------------------------\n")
            }
            _updateFlow.value = System.currentTimeMillis()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun readLogs(maxLines: Int = 300): String = withContext(Dispatchers.IO) {
        val file = getFile() ?: return@withContext "Log file not initialized."
        if (!file.exists() || file.length() == 0L) {
            return@withContext "No errors or logs recorded yet.\nLogs are automatically saved here when commands or actions run."
        }
        try {
            val lines = file.readLines()
            if (lines.size <= maxLines) {
                lines.joinToString("\n")
            } else {
                lines.takeLast(maxLines).joinToString("\n")
            }
        } catch (e: Throwable) {
            "Failed to read log.txt: ${e.message}"
        }
    }

    suspend fun getStats(): LogStats = withContext(Dispatchers.IO) {
        val file = getFile()
        if (file == null || !file.exists()) {
            return@withContext LogStats(
                exists = false,
                sizeBytes = 0,
                formattedSize = "0 B",
                lineCount = 0,
                lastModified = 0
            )
        }
        val size = file.length()
        val count = try { file.readLines().size } catch (e: Throwable) { 0 }
        LogStats(
            exists = true,
            sizeBytes = size,
            formattedSize = formatBytes(size),
            lineCount = count,
            lastModified = file.lastModified()
        )
    }

    suspend fun clearLogs(): Boolean = withContext(Dispatchers.IO) {
        val file = getFile() ?: return@withContext false
        try {
            if (file.exists()) {
                file.writeText("")
            }
            _updateFlow.value = System.currentTimeMillis()
            true
        } catch (e: Throwable) {
            false
        }
    }

    fun getLogFileForExport(): File? {
        val file = getFile() ?: return null
        if (!file.exists()) {
            try {
                file.createNewFile()
                file.writeText("QuickSetTiles Log File\nGenerated: ${dateFormat.format(Date())}\nNo logs recorded.\n")
            } catch (e: Throwable) {
                // Ignore
            }
        }
        return file
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }
}
