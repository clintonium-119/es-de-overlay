package com.clintonium.es_de_overlay

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

/**
 * Shizuku runs a server as adb's shell user, which may force-stop other apps; Android allows that only to system apps
 * otherwise. The overlay uses it to end a game without opening the game's App Info page.
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

    /** Off the main thread: runs `am force-stop [pkg]` through Shizuku, and returns whether it worked. */
    fun forceStop(pkg: String): Boolean = try {
        // ponytail: newProcess is private since library 13 and reached by reflection; if a later Shizuku drops it,
        // run the command from a Shizuku UserService instead.
        val newProcess = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
        val process = newProcess.invoke(null, arrayOf("am", "force-stop", pkg), null, null) as ShizukuRemoteProcess
        // Its own timed wait: Process.waitFor(timeout) polls exitValue(), whose "still running" doesn't cross the binder.
        val exited = process.waitForTimeout(5, TimeUnit.SECONDS)
        if (!exited) process.destroy()
        exited && process.exitValue() == 0
    } catch (e: Exception) {
        Log.w(TAG, "force stop through Shizuku failed", e)
        false
    }
}
