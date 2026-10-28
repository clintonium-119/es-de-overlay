package com.clintonium.esderecorder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * One press: the first frame of a screen mirror saved as PNG, then the same mirror recorded as a
 * silent H.264 MP4 until [seconds] pass or [stop] is called. [onDone] gets whichever files are usable.
 */
class Capture(
    context: Context,
    private val projection: MediaProjection,
    seconds: Int,
    private val onDone: (png: File?, mp4: File?) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val encoder = Executors.newSingleThreadExecutor()
    private val png = File(context.cacheDir, "shot.png")
    private val mp4 = File(context.cacheDir, "clip.mp4")
    private val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
    private val width = bounds.width()
    private val height = bounds.height()
    private val dpi = context.resources.displayMetrics.densityDpi
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private var maxReached = false
    private val recorder = MediaRecorder(context).apply {
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setVideoSize(width, height)
        setVideoFrameRate(30)
        setVideoEncodingBitRate(8_000_000)
        setMaxDuration(seconds * 1000)
        setOutputFile(mp4)
        setOnInfoListener { _, what, _ ->
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                maxReached = true
                main.post(::stop)
            }
        }
        prepare()
    }
    private var display: VirtualDisplay? = null
    private var shot: Future<Boolean>? = null
    private var startedAt = 0L
    private var stopped = false

    fun start() {
        reader.setOnImageAvailableListener({ onFirstFrame() }, main)
        display = projection.createVirtualDisplay(
            "esde-recorder", width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, main,
        )
    }

    private fun onFirstFrame() {
        val image = reader.acquireLatestImage() ?: return
        reader.setOnImageAvailableListener(null, null)
        val plane = image.planes[0]
        val padded = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)
        image.close()
        val frame = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (stopped) return
        display?.surface = recorder.surface
        recorder.start()
        startedAt = SystemClock.elapsedRealtime()
        shot = encoder.submit<Boolean> { png.outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    /** Stops early (second press, game left) or at max duration; idempotent. */
    fun stop() {
        if (stopped) return
        stopped = true
        val elapsed = if (startedAt == 0L) 0 else SystemClock.elapsedRealtime() - startedAt
        val clean = startedAt != 0L && runCatching { recorder.stop() }.onFailure { Log.w(TAG, "recorder.stop", it) }.isSuccess
        recorder.release()
        display?.release()
        reader.close()
        val clipOk = (clean || maxReached) && elapsed >= 1_000 && mp4.length() > 0
        encoder.execute {
            val shotOk = runCatching { shot?.get() == true }.getOrDefault(false)
            encoder.shutdown()
            main.post { onDone(png.takeIf { shotOk }, mp4.takeIf { clipOk }) }
        }
        Log.i(TAG, "capture stopped after ${elapsed}ms (max=$maxReached clean=$clean)")
    }
}
