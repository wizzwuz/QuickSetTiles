package com.wizzwuz.quicksettiles.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

/** Root manager types detected on the device */
enum class RootManagerType(val displayName: String) {
    NONE("None"),
    MAGISK("Magisk"),
    KERNELSU("KernelSU"),
    APATCH("APatch"),
    OTHER("Root (Other)")
}

data class DiagnosticsInfo(
    val isShizukuRunning: Boolean,
    val hasShizukuPermission: Boolean,
    val shizukuVersion: Int,
    val shizukuUid: Int,
    val isSheveryInstalled: Boolean,
    val isOfficialShizukuInstalled: Boolean,
    val isRootBinaryPresent: Boolean,
    /** True if root was actually granted (su -c id returned uid=0) */
    val isRootGranted: Boolean = false,
    /** The detected root manager (Magisk, KernelSU, etc.) */
    val rootManagerType: RootManagerType = RootManagerType.NONE
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

    fun requestShizukuPermission(context: Context? = null, requestCode: Int = 1001) {
        var requestedViaApi = false
        try {
            if (isShizukuRunning() && !hasShizukuPermission()) {
                Shizuku.requestPermission(requestCode)
                requestedViaApi = true
            }
        } catch (e: Throwable) {
            LogManager.logError("ShizukuHelper", "Failed to request Shizuku permission via API", e)
        }

        // If context is provided, handle Shevery and Shizuku authorization prompts / fallback
        if (context != null) {
            try {
                // 1. Try explicit REQUEST_AUTHORIZATION intent supported by Shevery and Shizuku
                val authIntent = Intent("moe.shizuku.privileged.api.intent.action.REQUEST_AUTHORIZATION").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.queryIntentActivities(authIntent, 0).isNotEmpty()) {
                    context.startActivity(authIntent)
                    return
                }
            } catch (e: Throwable) {
                // Fallback to manager launch
            }

            // 2. Direct launch of Shevery or Shizuku manager app so user can enable permission
            try {
                val pm = context.packageManager
                val sheveryIntent = pm.getLaunchIntentForPackage("com.hamondev.shevery")
                val shizukuIntent = pm.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                val target = sheveryIntent ?: shizukuIntent
                if (target != null) {
                    target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(target)
                }
            } catch (e: Throwable) {
                // ignore
            }
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
            "/data/local/su",
            "/data/adb/magisk/busybox",
            "/data/adb/magisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su"
        )
        if (paths.any { File(it).exists() }) return true
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("which", "su"))
            proc.waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }


    /**
     * Actually tests if root is accessible by running `su -c id` and checking the output
     * for uid=0. This is more reliable than just checking for the su binary.
     */
    fun isRootGranted(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()
            exitCode == 0 && output.contains("uid=0")
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Detects the root manager type (Magisk, KernelSU, APatch, or other).
     * Checks known package names and binaries specific to each manager.
     */
    fun detectRootManager(context: android.content.Context): RootManagerType {
        val pm = context.packageManager

        // KernelSU — package name check
        val kernelsuPackages = listOf(
            "me.weishu.kernelsu",
            "com.kernelsu.ksu",
            "me.bmax.apatch" // APatch uses same style
        )
        for (pkg in kernelsuPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return if (pkg.contains("apatch")) RootManagerType.APATCH else RootManagerType.KERNELSU
            } catch (e: Throwable) { /* not installed */ }
        }

        // Magisk — package name check (original + various forks)
        val magiskPackages = listOf(
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",  // Delta/Kitsune
            "com.fox2code.mmm",           // Module Manager
            "io.github.vvb2060.magisk"
        )
        for (pkg in magiskPackages) {
            try {
                pm.getPackageInfo(pkg, 0)
                return RootManagerType.MAGISK
            } catch (e: Throwable) { /* not installed */ }
        }

        // KernelSU — binary check
        if (File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists()) {
            return RootManagerType.KERNELSU
        }

        // APatch — binary check
        if (File("/data/adb/ap").exists() || File("/data/adb/apd").exists()) {
            return RootManagerType.APATCH
        }

        // Magisk — binary check
        if (File("/data/adb/magisk").exists() || File("/sbin/.magisk").exists() ||
            File("/data/adb/magisk/magiskd").exists()) {
            return RootManagerType.MAGISK
        }

        // Generic su binary present but no known manager
        if (isRootBinaryPresent()) {
            return RootManagerType.OTHER
        }

        return RootManagerType.NONE
    }

    fun getDiagnostics(context: android.content.Context): DiagnosticsInfo {
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

        val rootBinaryPresent = isRootBinaryPresent()
        val rootGranted = if (rootBinaryPresent) isRootGranted() else false
        val rootManagerType = detectRootManager(context)

        return DiagnosticsInfo(
            isShizukuRunning = isShizukuRunning(),
            hasShizukuPermission = hasShizukuPermission(),
            shizukuVersion = getShizukuVersion(),
            shizukuUid = getShizukuUid(),
            isSheveryInstalled = isShevery,
            isOfficialShizukuInstalled = isOfficial,
            isRootBinaryPresent = rootBinaryPresent,
            isRootGranted = rootGranted,
            rootManagerType = rootManagerType
        )
    }

    suspend fun executeCommand(
        command: String,
        mode: ShellMode = ShellMode.AUTO,
        timeoutMs: Long = 15000L
    ): CommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // Normalize --windowingmode (case-insensitive) to canonical --windowingMode so Android am recognizes it
        val normalizedCommand = command.replace(Regex("""--windowingmode\b""", RegexOption.IGNORE_CASE), "--windowingMode")

        // 1. Check for explicit private launch alias
        val trimmed = normalizedCommand.trim()
        if (trimmed.startsWith("launch-private ") || trimmed.startsWith("am-private ")) {
            val targetComp = trimmed.substringAfter(" ").trim()
            val wm = extractWindowingMode(normalizedCommand)
            if (isShizukuRunning() && hasShizukuPermission()) {
                val bypassRes = tryAssistantMethodBypass(targetComp, wm)
                val totalDuration = System.currentTimeMillis() - startTime
                val finalResult = bypassRes.copy(durationMs = totalDuration)
                LogManager.logCommand(finalResult.executionMode, command, finalResult.exitCode, finalResult.output, totalDuration)
                return@withContext finalResult
            }
        }

        // 2. For multi-line scripts containing 'am start', handle each line intelligently
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val hasAmStart = lines.any { it.startsWith("am start") }
        if (lines.size > 1 && hasAmStart && (isShizukuRunning() && hasShizukuPermission())) {
            val multiResult = executeMultiLineWithAmStartBypass(lines, mode, timeoutMs)
            val totalDuration = System.currentTimeMillis() - startTime
            val finalResult = multiResult.copy(durationMs = totalDuration)
            LogManager.logCommand(finalResult.executionMode, command, finalResult.exitCode, finalResult.output, totalDuration)
            return@withContext finalResult
        }

        val result: CommandResult = when (mode) {
            ShellMode.SHIZUKU -> runShizukuProcess(normalizedCommand, timeoutMs)
            ShellMode.ROOT -> runRootProcess(normalizedCommand, timeoutMs)
            ShellMode.STANDARD -> runStandardProcess(normalizedCommand, timeoutMs)
            ShellMode.AUTO -> {
                // Prefer Root if granted, then Shizuku, then standard
                if (isRootBinaryPresent()) {
                    val rootRes = runRootProcess(normalizedCommand, timeoutMs)
                    if (rootRes.exitCode == 0 && !isRootDenied(rootRes.output, rootRes.exitCode)) {
                        rootRes
                    } else if (isShizukuRunning() && hasShizukuPermission()) {
                        val shizukuRes = runShizukuProcess(normalizedCommand, timeoutMs)
                        if (shizukuRes.exitCode == 0 || !isFatalShellError(shizukuRes.output)) {
                            shizukuRes
                        } else {
                            runStandardProcess(normalizedCommand, timeoutMs)
                        }
                    } else {
                        runStandardProcess(normalizedCommand, timeoutMs)
                    }
                } else if (isShizukuRunning() && hasShizukuPermission()) {
                    val shizukuRes = runShizukuProcess(normalizedCommand, timeoutMs)
                    if (shizukuRes.exitCode == 0 || !isFatalShellError(shizukuRes.output)) {
                        shizukuRes
                    } else {
                        tryFallbackAfterShizuku(normalizedCommand, timeoutMs, shizukuRes)
                    }
                } else {
                    runStandardProcess(normalizedCommand, timeoutMs)
                }
            }
        }

        // 3. Automatic Private Activity Permission Denial detection & Assistant Method bypass
        val resolvedResult: CommandResult = if (result.exitCode != 0 && isPermissionDenialForPrivateActivity(result.output)) {
            val comp = extractComponentFromCommand(normalizedCommand) ?: extractComponentFromOutput(result.output)
            val wm = extractWindowingMode(normalizedCommand)
            if (isRootBinaryPresent()) {
                val rootRes = runRootProcess(normalizedCommand, timeoutMs)
                if (rootRes.exitCode == 0 && !isRootDenied(rootRes.output, rootRes.exitCode)) {
                    rootRes
                } else if (comp != null && isShizukuRunning() && hasShizukuPermission()) {
                    val bypassRes = tryAssistantMethodBypass(comp, wm)
                    if (bypassRes.exitCode == 0) bypassRes else result
                } else {
                    result
                }
            } else if (comp != null && isShizukuRunning() && hasShizukuPermission()) {
                val bypassRes = tryAssistantMethodBypass(comp, wm)
                if (bypassRes.exitCode == 0) {
                    bypassRes
                } else {
                    result
                }
            } else {
                result
            }
        } else {
            result
        }

        val wm = extractWindowingMode(normalizedCommand)
        if (resolvedResult.exitCode == 0 && wm != null) {
            val comp = extractComponentFromCommand(normalizedCommand)
            if (comp != null) {
                val cleanComp = comp.trim('\'', '"').replace("\\$", "$")
                val pkg = cleanComp.substringBefore("/")
                applyWindowingModeForPackage(pkg, cleanComp, wm)
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val finalResult = resolvedResult.copy(durationMs = totalDuration)

        LogManager.logCommand(
            mode = finalResult.executionMode,
            command = command,
            exitCode = finalResult.exitCode,
            output = finalResult.output,
            durationMs = totalDuration
        )

        finalResult
    }

    /**
     * Executes a multi-line script where some lines are `am start -n <component>`.
     * `am start` lines for private/non-exported activities are handled via assistant bypass;
     * all other lines are batched and run as a normal shell script via Shizuku.
     */
    private suspend fun executeMultiLineWithAmStartBypass(
        lines: List<String>,
        mode: ShellMode,
        timeoutMs: Long
    ): CommandResult = withContext(Dispatchers.IO) {
        val outputParts = mutableListOf<String>()
        var overallExitCode = 0
        val executionMode = "Shizuku+Bypass"

        // Collect consecutive non-am-start lines into batches, run am start lines via bypass
        val batch = mutableListOf<String>()

        suspend fun flushBatch() {
            if (batch.isEmpty()) return
            val script = batch.joinToString("\n")
            batch.clear()
            val res = if (isShizukuRunning() && hasShizukuPermission()) {
                runShizukuProcess(script, timeoutMs)
            } else {
                runRootProcess(script, timeoutMs)
            }
            if (res.output.isNotBlank()) outputParts.add(res.output)
            if (res.exitCode != 0) overallExitCode = res.exitCode
        }

        for (line in lines) {
            if (line.startsWith("am start")) {
                // Run batched non-am-start commands first
                flushBatch()
                // Try am start normally first
                val amRes = if (isShizukuRunning() && hasShizukuPermission()) {
                    runShizukuProcess(line, timeoutMs)
                } else {
                    runRootProcess(line, timeoutMs)
                }
                if (amRes.exitCode != 0 && isPermissionDenialForPrivateActivity(amRes.output)) {
                    // Private activity — use assistant bypass
                    val comp = extractComponentFromCommand(line) ?: extractComponentFromOutput(amRes.output)
                    val wm = extractWindowingMode(line)
                    if (comp != null) {
                        val bypassRes = tryAssistantMethodBypass(comp, wm)
                        if (bypassRes.output.isNotBlank()) outputParts.add(bypassRes.output)
                        if (bypassRes.exitCode != 0) overallExitCode = bypassRes.exitCode

                    } else {
                        if (amRes.output.isNotBlank()) outputParts.add(amRes.output)
                        overallExitCode = amRes.exitCode
                    }
                } else {
                    if (amRes.output.isNotBlank()) outputParts.add(amRes.output)
                    if (amRes.exitCode != 0) overallExitCode = amRes.exitCode
                }
            } else {
                batch.add(line)
            }
        }
        // Flush any remaining batch
        flushBatch()

        CommandResult(overallExitCode, outputParts.joinToString("\n").trim(), executionMode)
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

    private fun sanitizeCommandForShell(cmd: String): String {
        return cmd.replace(Regex("""(?<!\\)\$(?!([0-9\?*@#]|PATH|USER|HOME|SHELL|TMPDIR)\b)([a-zA-Z0-9_]+)""")) {
            "\\$" + it.groupValues[2]
        }
    }

    private suspend fun runShizukuProcess(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        if (!isShizukuRunning()) {
            return@withContext CommandResult(-1, "Shizuku is not running. Start Shizuku or Shevery first.", "Shizuku")
        }
        if (!hasShizukuPermission()) {
            return@withContext CommandResult(-1, "Shizuku permission not granted to QuickSetTiles.", "Shizuku")
        }

        try {
            val safeCmd = sanitizeCommandForShell(command)
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val proc = method.invoke(null, arrayOf("sh", "-c", safeCmd), null, null) as Process
            readProcessWithTimeout(proc, "Shizuku", timeoutMs)
        } catch (e: Throwable) {
            val errMsg = "Shizuku execution failed: ${e.message ?: e.javaClass.simpleName}"
            LogManager.logError("ShizukuHelper", errMsg, e)
            CommandResult(-1, errMsg, "Shizuku")
        }
    }

    private suspend fun runRootProcess(command: String, timeoutMs: Long): CommandResult = withContext(Dispatchers.IO) {
        try {
            val envCmd = "export PATH=\$PATH:/system/bin:/system/xbin:/data/adb/ksu/bin:/data/adb/ap/bin; $command"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", envCmd))

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

    private fun isPermissionDenialForPrivateActivity(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("securityexception") ||
                lower.contains("permission denial") ||
                lower.contains("not exported") ||
                lower.contains("exported=false") ||
                lower.contains("requires permission") ||
                (lower.contains("error: activity not started") && !lower.contains("does not exist"))
    }

    private fun extractComponentFromCommand(command: String): String? {
        val nMatch = Regex("""-n\s+([^\s]+)""").find(command)
        if (nMatch != null) return nMatch.groupValues[1].trim('\'', '"').replace("\\$", "$")
        val cMatch = Regex("""\b([a-zA-Z0-9._]+/[a-zA-Z0-9._$]+)\b""").find(command)
        return cMatch?.groupValues?.get(1)?.trim('\'', '"')?.replace("\\$", "$")
    }

    private fun extractComponentFromOutput(output: String): String? {
        val cmpMatch = Regex("""cmp=([^\s}]+)""").find(output)
        return cmpMatch?.groupValues?.get(1)?.trim('\'', '"')?.replace("\\$", "$")
    }

    private fun extractWindowingMode(command: String): String? {
        val match = Regex("""--windowingmode[\s=]+(\d+)""", RegexOption.IGNORE_CASE).find(command)
        return match?.groupValues?.get(1)
    }

    private suspend fun findTaskIdForPackage(pkg: String, cleanComp: String): String? {
        // 1. Dumpsys activity top (shows current top activity/task on screen)
        val topRes = runShizukuProcess("dumpsys activity top", 2000L)
        if (topRes.exitCode == 0 && topRes.output.isNotBlank()) {
            val lines = topRes.output.lines()
            for (line in lines.take(35)) {
                if (line.contains("TASK", ignoreCase = true) || line.contains("Task{") || line.contains("Hist #")) {
                    val match = Regex("""#(\d+)""").find(line)
                    if (match != null) {
                        return match.groupValues[1]
                    }
                }
            }
        }

        // 2. Dumpsys activity activities looking for the component or package
        val escapedComp = cleanComp.replace("$", "\\$")
        val actRes = runShizukuProcess("dumpsys activity activities | grep -E 'Task.*($pkg|$escapedComp)' | head -n 3", 2000L)
        if (actRes.exitCode == 0 && actRes.output.isNotBlank()) {
            val match = Regex("""#(\d+)""").find(actRes.output)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // 3. cmd activity task list
        val listRes = runShizukuProcess("cmd activity task list 2>/dev/null | grep -F '$pkg' | head -n 1", 2000L)
        if (listRes.exitCode == 0 && listRes.output.isNotBlank()) {
            val match = Regex("""#(\d+)""").find(listRes.output) ?: Regex("""taskId=(\d+)""").find(listRes.output)
            if (match != null) {
                return match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
            }
        }

        return null
    }

    private suspend fun applyWindowingModeForPackage(pkg: String, cleanComp: String, windowingMode: String) {
        for (attempt in 1..4) {
            delay(250)
            val tid = findTaskIdForPackage(pkg, cleanComp)
            if (tid != null) {
                runShizukuProcess("am task set-windowing-mode $tid $windowingMode 2>/dev/null", 2000L)
                runShizukuProcess("cmd activity task set-windowing-mode $tid $windowingMode 2>/dev/null", 2000L)
                break
            }
        }
    }

    suspend fun tryAssistantMethodBypass(
        componentStr: String,
        windowingMode: String? = null
    ): CommandResult {
        val cleanComp = componentStr.trim('\'', '"').replace("\\$", "$")
        val escapedComp = cleanComp.replace("$", "\\$")
        val origRes = runShizukuProcess("settings get secure assistant", 4000L)
        val originalAssistant = origRes.output.trim()

        val origVisRes = runShizukuProcess("settings get secure voice_interaction_service", 4000L)
        val originalVis = origVisRes.output.trim()

        return try {
            // Temporarily clear voice interaction service so voice services don't conflict or trigger chooser
            if (originalVis.isNotBlank() && originalVis != "null") {
                runShizukuProcess("settings put secure voice_interaction_service \"\"", 4000L)
            }

            val setRes = runShizukuProcess("settings put secure assistant $escapedComp", 4000L)
            if (setRes.exitCode != 0) {
                CommandResult(
                    setRes.exitCode,
                    "Failed to set assistant for private launch: ${setRes.output}",
                    "Shizuku (Assistant Bypass)"
                )
            } else {
                val wmFlag = if (windowingMode != null) " --windowingMode $windowingMode" else ""
                val miuiFlags = if (windowingMode == "5") " --ez miui.intent.extra.FLOATING_WINDOW true --ez miui.intent.extra.IS_FLOATING_WINDOW true" else ""

                // 1. Direct explicit am start with ASSIST intent and windowing flags
                runShizukuProcess("am start -a android.intent.action.ASSIST -n $escapedComp$wmFlag$miuiFlags", 4000L)

                // 2. Direct cmd activity start as fallback
                runShizukuProcess("cmd activity start -a android.intent.action.ASSIST -n $escapedComp$wmFlag$miuiFlags 2>/dev/null", 4000L)

                // 3. Assist hardware key event (system UID triggers assistant launch)
                runShizukuProcess("input keyevent 219", 4000L)

                // 4. If windowing mode is requested (e.g. 5 for freeform / floating window), set task windowing mode using integer task ID
                if (windowingMode != null) {
                    val pkg = cleanComp.substringBefore("/")
                    applyWindowingModeForPackage(pkg, cleanComp, windowingMode)
                } else {
                    delay(800)
                }

                CommandResult(
                    0,
                    "Starting: Intent { cmp=$cleanComp }$wmFlag\n[Success] Private activity started via Assistant Method",
                    "Shizuku (Assistant Bypass)"
                )
            }
        } catch (e: Throwable) {
            CommandResult(
                -1,
                "Assistant bypass failed: ${e.message}",
                "Shizuku (Assistant Bypass)"
            )
        } finally {
            if (originalAssistant.isNotBlank() && originalAssistant != "null") {
                runShizukuProcess("settings put secure assistant $originalAssistant", 4000L)
            } else {
                runShizukuProcess("settings delete secure assistant", 4000L)
            }
            if (originalVis.isNotBlank() && originalVis != "null") {
                runShizukuProcess("settings put secure voice_interaction_service $originalVis", 4000L)
            } else {
                runShizukuProcess("settings delete secure voice_interaction_service", 4000L)
            }
        }
    }
}

