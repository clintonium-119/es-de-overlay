package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.HardwareBuffer
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * One press: a screen mirror whose first frames become the PNG screenshot and whose following frames
 * are recorded at half size as an MP4 (with the sound of [gameUid]'s app, if given) until [seconds] pass or
 * [stop] is called.
 * [onRecording] fires once the screenshot frame is chosen; [onDone] gets whichever files are usable.
 * [nudge] must force a window layout pass: Android only starts mirroring into a new capture display
 * during one, and a game that never changes its windows (Unity menus, RetroArch mid-game) never
 * triggers it, so the display would stay empty.
 *
 * RetroArch's 120 Hz shader sub-frames alternate every real frame with a near-black one, which
 * flickers in a recording. So the screenshot is the brighter of the first two frames, and the clip
 * drops any frame much darker than the one before it (a fade still passes: each step is small).
 */
class Capture(
    context: Context,
    private val projection: MediaProjection,
    private val seconds: Int,
    gameUid: Int?,
    private val nudge: () -> Unit,
    private val onRecording: () -> Unit,
    private val onDone: (png: File?, mp4: File?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("capture").apply { start() }
    private val worker = Handler(thread.looper)
    private val encoder = Executors.newSingleThreadExecutor()
    private val png = File(context.cacheDir, "shot.png")
    private val mp4 = File(context.cacheDir, "clip.mp4")
    private val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    private val width = bounds.width()
    private val height = bounds.height()
    private val dpi = context.resources.displayMetrics.densityDpi
    private val reader = ImageReader.newInstance(
        width, height, PixelFormat.RGBA_8888, 4,
        HardwareBuffer.USAGE_CPU_READ_OFTEN or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
    )
    // ponytail: half resolution is the size knob; the Nova's encoder ignored bitrate requests.
    private val clip = ClipWriter(projection, mp4, width / 2, height / 2, gameUid)
    private val input = clip.surface
    private val clipRect = Rect(0, 0, width / 2, height / 2)

    // Worker thread only.
    private var display: VirtualDisplay? = null
    private var first: Image? = null
    private var firstLevel = 0L
    private var lastLevel = 0L
    private var shot: Future<Boolean>? = null
    private var startedAt = 0L
    private var stopped = false
    private var frames = 0
    private var kept = 0
    private var lastFrameAt = 0L
    private val watchdog = object : Runnable {
        override fun run() {
            if (stopped) return
            if (SystemClock.elapsedRealtime() - lastFrameAt > 300) main.post(nudge)
            worker.postDelayed(this, 300)
        }
    }

    fun start() {
        worker.post {
            reader.setOnImageAvailableListener({ onFrame() }, worker)
            display = projection.createVirtualDisplay(
                "esde-recorder", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, worker,
            )
            // Timed from the press, not the first frame: a stalled mirror must not hang the capture.
            worker.postDelayed(::finish, seconds * 1_000L)
            lastFrameAt = SystemClock.elapsedRealtime()
            main.post(nudge)
            worker.postDelayed(watchdog, 300)
        }
    }

    /** Stops early (second press, game left) or at the clip length; idempotent. */
    fun stop() {
        worker.post(::finish)
    }

    private fun onFrame() {
        if (stopped) return
        val image = reader.acquireNextImage() ?: return
        frames++
        lastFrameAt = SystemClock.elapsedRealtime()
        val level = brightness(image)
        val dark = level < lastLevel * 6 / 10
        lastLevel = level
        if (startedAt != 0L) {
            if (!dark) {
                draw(image)
                kept++
            }
            image.close()
            return
        }
        val prev = first ?: run {
            first = image
            firstLevel = level
            // A static screen (or one RetroArch is still setting up) may not send a second frame soon.
            worker.postDelayed(::recordFirstAlone, 200)
            return
        }
        first = null
        if (level > firstLevel) {
            prev.close()
            record(image)
        } else {
            image.close()
            record(prev)
        }
    }

    private fun recordFirstAlone() {
        val image = first ?: return
        first = null
        record(image)
    }

    private fun record(shotFrame: Image) {
        screenshot(shotFrame)
        clip.start()
        startedAt = SystemClock.elapsedRealtime()
        main.post(onRecording)
    }

    /** Sum of RGB over a sparse grid; enough to tell a sub-frame from a real one. */
    private fun brightness(image: Image): Long {
        val plane = image.planes[0]
        val buf = plane.buffer
        var sum = 0L
        for (y in 0 until height step 16) for (x in 0 until width step 16) {
            val i = y * plane.rowStride + x * plane.pixelStride
            sum += (buf.get(i).toInt() and 0xFF) + (buf.get(i + 1).toInt() and 0xFF) + (buf.get(i + 2).toInt() and 0xFF)
        }
        return sum
    }

    /** Copies [image] (then closes it) and encodes it as PNG off the worker thread. */
    private fun screenshot(image: Image) {
        val plane = image.planes[0]
        val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        image.close()
        val frame = Bitmap.createBitmap(padded, 0, 0, width, height)
        shot = encoder.submit<Boolean> { png.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun draw(image: Image) {
        image.hardwareBuffer?.use { buffer ->
            val bitmap = Bitmap.wrapHardwareBuffer(buffer, null) ?: return
            val canvas = input.lockHardwareCanvas()
            canvas.drawBitmap(bitmap, null, clipRect, null)
            input.unlockCanvasAndPost(canvas)
            bitmap.recycle()
        }
    }

    private fun finish() {
        if (stopped) return
        stopped = true
        reader.setOnImageAvailableListener(null, null)
        first?.let(::screenshot) // a static screen produced only one frame
        first = null
        val elapsed = if (startedAt == 0L) 0 else SystemClock.elapsedRealtime() - startedAt
        val clean = clip.stop() && startedAt != 0L
        display?.release()
        reader.close()
        thread.quitSafely()
        val clipOk = clean && elapsed >= 1_000 && mp4.length() > 0
        encoder.execute {
            val shotOk = runCatching { shot?.get() == true }.getOrDefault(false)
            encoder.shutdown()
            main.post { onDone(png.takeIf { shotOk }, mp4.takeIf { clipOk }) }
        }
        Log.i(TAG, "capture stopped after ${elapsed}ms (clean=$clean, frames=$frames, kept=$kept)")
    }
}
