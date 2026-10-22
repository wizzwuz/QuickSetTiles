package com.wizzwuz.quicktiles.util

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val executionMode: String
)

object ShizukuHelper {

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

    fun requestShizukuPermission(requestCode: Int = 1001) {
        if (!isShizukuRunning()) return
        try {
            if (!hasShizukuPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        // 1. If Shizuku is running and authorized, try rish (Shizuku privileged shell)
        if (isShizukuRunning() && hasShizukuPermission()) {
            try {
                val rishProcess = Runtime.getRuntime().exec(arrayOf("rish", "-c", command))
                val reader = BufferedReader(InputStreamReader(rishProcess.inputStream))
                val errReader = BufferedReader(InputStreamReader(rishProcess.errorStream))
                val output = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                while (errReader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                val exitCode = rishProcess.waitFor()
                if (exitCode == 0 || output.isNotEmpty()) {
                    return@withContext CommandResult(exitCode, output.toString().trim(), "Shizuku (rish)")
                }
            } catch (e: Throwable) {
                // fallback to root or sh
            }
        }

        // 2. Try Superuser Root (su)
        try {
            val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(suProcess.inputStream))
            val errReader = BufferedReader(InputStreamReader(suProcess.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = suProcess.waitFor()
            return@withContext CommandResult(exitCode, output.toString().trim(), "Root (su)")
        } catch (e: Throwable) {
            // fallback to standard shell
        }

        // 3. Fallback to standard app shell (sh)
        try {
            val shProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(shProcess.inputStream))
            val errReader = BufferedReader(InputStreamReader(shProcess.errorStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            while (errReader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            val exitCode = shProcess.waitFor()
            return@withContext CommandResult(exitCode, output.toString().trim(), "Standard Shell")
        } catch (e: Throwable) {
            return@withContext CommandResult(-1, "Execution failed: ${e.message}", "Error")
        }
    }
}
