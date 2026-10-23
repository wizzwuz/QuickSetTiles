package com.wizzwuz.quicktiles.util

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class ShizukuStatus {
    NOT_RUNNING,
    PERMISSION_REQUIRED,
    AUTHORIZED
}

enum class ShellMode(val label: String) {
    AUTO("Auto (Best Available)"),
    SHIZUKU("Shizuku (ADB / UID 2000)"),
    ROOT("Root (su / UID 0)"),
    STANDARD("Standard Shell (App UID)")
}

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val executionMode: String,
    val durationMs: Long = 0L
)

data class DiagnosticsInfo(
    val isShizukuRunning: Boolean,
    val hasShizukuPermission: Boolean,
    val shizukuVersion: Int,
    val shizukuUid: Int,
    val isSheveryInstalled: Boolean,
    val isOfficialShizukuInstalled: Boolean,
    val isRootBinaryPresent: Boolean
)

object ShizukuHelper {

    private val _statusFlow = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val statusFlow: StateFlow<ShizukuStatus> = _statusFlow.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _statusFlow.value = ShizukuStatus.NOT_RUNNING
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
        updateStatus()
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        updateStatus()
    }

    fun updateStatus() {
        if (!isShizukuRunning()) {
            _statusFlow.value = ShizukuStatus.NOT_RUNNING
            return
        }
        if (hasShizukuPermission()) {
            _statusFlow.value = ShizukuStatus.AUTHORIZED
        } else {
            _statusFlow.value = ShizukuStatus.PERMISSION_REQUIRED
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasShizukuPermission(): Boolean {
        if (!isShizukuRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    fun getShizukuVersion(): Int {
        return try {
            if (isShizukuRunning()) Shizuku.getVersion() else -1
        } catch (e: Throwable) {
            -1
        }
    }

    fun getShizukuUid(): Int {
        return try {
            if (isShizukuRunning()) Shizuku.getUid() else -1
        } catch (e: Throwable) {
            -1
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1001) {
        if (!isShizukuRunning()) return
        try {
            if (!hasShizukuPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            LogManager.logError("ShizukuHelper", "Failed to request Shizuku permission", e)
        }
    }

    fun isRootBinaryPresent(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        return paths.any { File(it).exists() }
    }

    fun getDiagnostics(context: Context): DiagnosticsInfo {
        val pm = context.packageManager
        val isShevery = try {
            pm.getPackageInfo("com.hamondev.shevery", 0) != null
        } catch (e: Throwable) {
            false
        }
        val isOfficial = try {
            pm.getPackageInfo("moe.shizuku.privileged.api", 0) != null
        } catch (e: Throwable) {
            false
        }

        return DiagnosticsInfo(
            isShizukuRunning = isShizukuRunning(),
            hasShizukuPermission = hasShizukuPermission(),
            shizukuVersion = getShizukuVersion(),
            shizukuUid = getShizukuUid(),
            isSheveryInstalled = isShevery,
            isOfficialShizukuInstalled = isOfficial,
            isRootBinaryPresent = isRootBinaryPresent()
        )
    }

    suspend fun executeCommand(
        command: String,
        mode: ShellMode = ShellMode.AUTO,
        timeoutMs: Long = 15000L
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val result: CommandResult = when (mode) {
            ShellMode.SHIZUKU -> runShizukuProcess(command, timeoutMs)
            ShellMode.ROOT -> runRootProcess(command, timeoutMs)
            ShellMode.STANDARD -> runStandardProcess(command, timeoutMs)
            ShellMode.AUTO -> {
                if (isShizukuRunning() && hasShizukuPermission()) {
                    val shizukuRes = runShizukuProcess(command, timeoutMs)
                    if (shizukuRes.exitCode == 0 || !isFatalShellError(shizukuRes.output)) {
                        shizukuRes
                    } else {
                        tryFallbackAfterShizuku(command, timeoutMs, shizukuRes)
                    }
                } else if (isRootBinaryPresent()) {
                    val rootRes = runRootProcess(command, timeoutMs)
                    if (rootRes.exitCode == 0 && !isRootDenied(rootRes.output, rootRes.exitCode)) {
                        rootRes
                    } else {
                        runStandardProcess(command, timeoutMs)
                    }
                } else {
                    runStandardProcess(command, timeoutMs)
                }
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val finalResult = result.copy(durationMs = totalDuration)

        LogManager.logCommand(
            mode = finalResult.executionMode,
            command = command,
            exitCode = finalResult.exitCode,
            output = finalResult.output,
            durationMs = totalDuration
        )

        finalResult
    }

    suspend fun executePrivilegedCommand(
        command: String,
        timeoutMs: Long = 15000L
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (isShizukuRunning() && hasShizukuPermission()) {
            val res = runShizukuProcess(command, timeoutMs)
            val duration = System.currentTimeMillis() - startTime
            val finalRes = res.copy(durationMs = duration)
            LogManager.logCommand(finalRes.executionMode, command, finalRes.exitCode, finalRes.output, duration)
            return@withContext finalRes
        }

        if (isRootBinaryPresent()) {
            val rootRes = runRootProcess(command, timeoutMs)
            val duration = System.currentTimeMillis() - startTime
            val finalRes = rootRes.copy(durationMs = duration)
            if (rootRes.exitCode == 0 && !isRootDenied(rootRes.output, rootRes.exitCode)) {
                LogManager.logCommand(finalRes.executionMode, command, finalRes.exitCode, finalRes.output, duration)
                return@withContext finalRes
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val reason = when {
            !isShizukuRunning() -> "Shizuku / Shevery is not running. Please start Shizuku or Shevery."
            !hasShizukuPermission() -> "Shizuku permission not granted. Please allow QuickSetTiles in Shizuku / Shevery."
            else -> "Privileged execution failed (Shizuku & Root unavailable)."
        }
        val errResult = CommandResult(-1, reason, "None", duration)
        LogManager.logCommand("None", command, -1, reason, duration)
        errResult
    }

    suspend fun executePrivilegedRaw(
        args: Array<String>,
        timeoutMs: Long = 15000L
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val proc = method.invoke(null, args, null, null) as Process
                val res = readProcessWithTimeout(proc, "Shizuku", timeoutMs)
                val duration = System.currentTimeMillis() - startTime
                val finalRes = res.copy(durationMs = duration)
                LogManager.logCommand("Shizuku", args.joinToString(" "), finalRes.exitCode, finalRes.output, duration)
                return@withContext finalRes
            } catch (e: Throwable) {
                LogManager.logError("ShizukuHelper", "Raw Shizuku execution failed", e)
            }
        }

        if (isRootBinaryPresent()) {
            try {
                val cmdList = mutableListOf("su", "-c")
                cmdList.add(args.joinToString(" "))
                val proc = Runtime.getRuntime().exec(cmdList.toTypedArray())
                val res = readProcessWithTimeout(proc, "Root (su)", timeoutMs)
                val duration = System.currentTimeMillis() - startTime
                val finalRes = res.copy(durationMs = duration)
                LogManager.logCommand("Root (su)", args.joinToString(" "), finalRes.exitCode, finalRes.output, duration)
                return@withContext finalRes
            } catch (e: Throwable) {
                LogManager.logError("ShizukuHelper", "Raw Root execution failed", e)
            }
        }

        val duration = System.currentTimeMillis() - startTime
        CommandResult(-1, "Privileged execution unavailable", "None", duration)
    }

    private suspend fun tryFallbackAfterShizuku(
        command: String,
        timeoutMs: Long,
        shizukuRes: CommandResult
    ): CommandResult {
        if (isRootBinaryPresent()) {
            val rootRes = runRootProcess(command, timeoutMs)
            if (rootRes.exitCode == 0 && !isRootDenied(rootRes.output, rootRes.exitCode)) {
                return rootRes
            }
        }
        return shizukuRes
    }

    private suspend fun runShizukuProcess(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        if (!isShizukuRunning()) {
            return@withContext CommandResult(-1, "Shizuku is not running. Start Shizuku or Shevery first.", "Shizuku")
        }
        if (!hasShizukuPermission()) {
            return@withContext CommandResult(-1, "Shizuku permission not granted to QuickSetTiles.", "Shizuku")
        }

        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            readProcessWithTimeout(proc, "Shizuku", timeoutMs)
        } catch (e: Throwable) {
            val errMsg = "Shizuku execution failed: ${e.message ?: e.javaClass.simpleName}"
            LogManager.logError("ShizukuHelper", errMsg, e)
            CommandResult(-1, errMsg, "Shizuku")
        }
    }

    private suspend fun runRootProcess(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val res = readProcessWithTimeout(proc, "Root (su)", timeoutMs)
            if (isRootDenied(res.output, res.exitCode)) {
                CommandResult(
                    exitCode = 255,
                    output = if (res.output.isNotBlank()) res.output else "Root access was denied or su failed.",
                    executionMode = "Root (su)"
                )
            } else {
                res
            }
        } catch (e: Throwable) {
            val errMsg = "Superuser (su) execution error: ${e.message ?: e.javaClass.simpleName}"
            LogManager.logError("ShizukuHelper", errMsg, e)
            CommandResult(-1, errMsg, "Root (su)")
        }
    }

    private suspend fun runStandardProcess(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            readProcessWithTimeout(proc, "Standard Shell", timeoutMs)
        } catch (e: Throwable) {
            val errMsg = "Standard shell (sh) execution error: ${e.message ?: e.javaClass.simpleName}"
            LogManager.logError("ShizukuHelper", errMsg, e)
            CommandResult(-1, errMsg, "Standard Shell")
        }
    }

    private suspend fun readProcessWithTimeout(
        proc: Process,
        mode: String,
        timeoutMs: Long
    ): CommandResult = withContext(Dispatchers.IO) {
        val stdoutLines = mutableListOf<String>()
        val stderrLines = mutableListOf<String>()

        val stdoutJob = async(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdoutLines.add(line!!)
                    }
                }
            } catch (e: Throwable) {
                // Ignore stream read errors
            }
        }

        val stderrJob = async(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderrLines.add(line!!)
                    }
                }
            } catch (e: Throwable) {
                // Ignore stream read errors
            }
        }

        val completed = withTimeoutOrNull(timeoutMs) {
            stdoutJob.await()
            stderrJob.await()
            proc.waitFor()
        }

        if (completed == null) {
            try {
                proc.destroy()
            } catch (e: Throwable) {
                // Ignore
            }
            return@withContext CommandResult(
                exitCode = -1,
                output = "Command timed out after ${timeoutMs / 1000}s.",
                executionMode = mode
            )
        }

        val exitCode = completed
        val stdoutText = stdoutLines.joinToString("\n").trim()
        val stderrText = stderrLines.joinToString("\n").trim()

        val fullOutput = buildString {
            if (stdoutText.isNotEmpty()) append(stdoutText)
            if (stderrText.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("[stderr]: ").append(stderrText)
            }
        }.trim()

        CommandResult(exitCode, fullOutput, mode)
    }

    private fun isRootDenied(output: String, exitCode: Int): Boolean {
        if (exitCode != 255 && exitCode != 127 && exitCode != 1) return false
        val lower = output.lowercase()
        return lower.contains("permission denied") ||
                lower.contains("not allowed") ||
                lower.contains("not found") ||
                lower.contains("inaccessible") ||
                lower.contains("unknown id") ||
                output.isBlank()
    }

    private fun isFatalShellError(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("securityexception") ||
                lower.contains("permission denial") ||
                lower.contains("requires permission")
    }
}
