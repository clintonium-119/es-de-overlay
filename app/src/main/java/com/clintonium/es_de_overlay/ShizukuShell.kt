package com.clintonium.es_de_overlay

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.clintonium.es_de_overlay.core.PACKAGE_NAME
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

/**
 * Shizuku runs a server as adb's shell user, which may force-stop other apps and read other apps' folders under
 * Android/data; Android allows that only to system apps otherwise. The overlay uses it to end a game without opening
 * the game's App Info page, and to copy themes from ES-DE's theme downloader.
 */
object ShizukuShell {
    /** Shizuku's package; its forks such as Shizuku-Next keep it. */
    const val PACKAGE = "moe.shizuku.privileged.api"

    enum class State { NOT_INSTALLED, NOT_RUNNING, NOT_ALLOWED, READY }

    fun state(context: Context): State = when {
        context.packageManager.getLaunchIntentForPackage(PACKAGE) == null -> State.NOT_INSTALLED
        !running -> State.NOT_RUNNING
        !allowed -> State.NOT_ALLOWED
        else -> State.READY
    }

    /** Whether Shizuku is running and lets this app use it. */
    val ready get() = running && allowed

    private val running get() = Shizuku.pingBinder() && !Shizuku.isPreV11()
    // Throws if Shizuku stopped since the ping.
    private val allowed get() = runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    /** Shows Shizuku's dialog asking whether this app may use it. */
    fun requestPermission() = Shizuku.requestPermission(0)

    /** Whether [pkg] may be force-stopped: a package name, and not the overlay, ES-DE or Shizuku themselves. */
    fun mayEnd(pkg: String) = PACKAGE_NAME.matches(pkg) && pkg !in setOf(BuildConfig.APPLICATION_ID, ESDE, PACKAGE)

    /** Off the main thread: runs `am force-stop [pkg]` through Shizuku, and returns whether it worked; only if [mayEnd]. */
    fun forceStop(pkg: String): Boolean {
        if (!mayEnd(pkg)) {
            Log.w(TAG, "force stop refused for $pkg")
            return false
        }
        return try {
            val process = process("am", "force-stop", pkg)
            // Its own timed wait: Process.waitFor(timeout) polls exitValue(), whose "still running" doesn't cross the binder.
            val exited = process.waitForTimeout(5, TimeUnit.SECONDS)
            if (!exited) process.destroy()
            exited && process.exitValue() == 0
        } catch (e: Exception) {
            Log.w(TAG, "force stop through Shizuku failed", e)
            false
        }
    }

    /**
     * Off the main thread: what `sh -c [command]` writes to its output, run through Shizuku as the shell user; null when
     * it fails, exits non-zero or runs past [timeoutSeconds].
     */
    fun output(command: String, timeoutSeconds: Long = 30): ByteArray? = try {
        val process = process("sh", "-c", command)
        // Ending the process closes its output, so a command that hangs can't hold the reading thread.
        val watchdog = Thread {
            try {
                Thread.sleep(timeoutSeconds * 1000)
                process.destroy()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        val bytes = process.inputStream.use { it.readBytes() }
        watchdog.interrupt()
        if (process.waitForTimeout(5, TimeUnit.SECONDS) && process.exitValue() == 0) bytes else null
    } catch (e: Exception) {
        Log.w(TAG, "command through Shizuku failed", e)
        null
    }

    // ponytail: newProcess is private since library 13 and reached by reflection; if a later Shizuku drops it, run
    // the commands from a Shizuku UserService instead.
    private fun process(vararg argv: String): ShizukuRemoteProcess {
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
        return newProcess.invoke(null, argv, null, null) as ShizukuRemoteProcess
    }
}
