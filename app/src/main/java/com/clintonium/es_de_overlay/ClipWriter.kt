package com.clintonium.es_de_overlay

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val SAMPLE_RATE = 48_000
private const val VIDEO = 0
private const val AUDIO = 1

/**
 * MP4 writer for a clip: H.264 from frames drawn on [surface] plus, when [withAudio], AAC of the
 * games' own sound via Android playback capture (MediaRecorder can't record another app's audio).
 * Apps can opt out of playback capture; their clips then get a silent audio track.
 */
class ClipWriter(projection: MediaProjection, file: File, width: Int, height: Int, withAudio: Boolean) {
    private val threads = listOf(HandlerThread("clip-video"), HandlerThread("clip-audio")).onEach { it.start() }
    private val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val record = if (withAudio) captureGameAudio(projection) else null
    private val audio = record?.let { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }
    private val done = CountDownLatch(if (audio != null) 2 else 1)
    private val tracks = intArrayOf(-1, -1)
    private val pending = mutableListOf<Pair<Int, Pair<ByteArray, MediaCodec.BufferInfo>>>()
    private var muxing = false
    private var videoSamples = 0
    private var running = false

    @Volatile private var recording = false

    val surface: Surface

    init {
        video.setCallback(Output(VIDEO), Handler(threads[VIDEO].looper))
        video.configure(
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
        surface = video.createInputSurface()
        audio?.setCallback(Output(AUDIO), Handler(threads[AUDIO].looper))
        audio?.configure(
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            },
            null, null, MediaCodec.CONFIGURE_FLAG_ENCODE,
        )
    }

    fun start() {
        running = true
        recording = true
        record?.startRecording()
        audio?.start()
        video.start()
    }

    /** Finishes the file; true when it holds video. */
    fun stop(): Boolean {
        recording = false
        if (running) {
            runCatching { record?.stop() }
            runCatching { video.signalEndOfInputStream() }
            if (!done.await(3, TimeUnit.SECONDS)) Log.w(TAG, "clip encoders did not finish")
        }
        runCatching { video.stop() }
        video.release()
        runCatching { audio?.stop() }
        audio?.release()
        record?.release()
        surface.release()
        val ok = synchronized(muxer) { muxing && videoSamples > 0 && runCatching { muxer.stop() }.isSuccess }
        runCatching { muxer.release() }
        threads.forEach { it.quitSafely() }
        Log.i(TAG, "clip: $videoSamples video samples, audio=${audio != null}")
        return ok
    }

    @SuppressLint("MissingPermission") // only called when RECORD_AUDIO is granted
    private fun captureGameAudio(projection: MediaProjection): AudioRecord? = runCatching {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
            .build()
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(format)
            .setBufferSizeInBytes(4 * AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT))
            .build()
            .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }.onFailure { Log.w(TAG, "game audio capture unavailable", it) }.getOrNull()

    /** Muxer starts once every expected track has its format; earlier samples wait in [pending]. */
    private fun addTrack(kind: Int, format: MediaFormat) = synchronized(muxer) {
        tracks[kind] = muxer.addTrack(format)
        if (tracks[VIDEO] >= 0 && (audio == null || tracks[AUDIO] >= 0)) {
            muxer.start()
            muxing = true
            for ((k, sample) in pending) muxer.writeSampleData(tracks[k], ByteBuffer.wrap(sample.first), sample.second)
            pending.clear()
        }
    }

    private fun write(kind: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) = synchronized(muxer) {
        if (kind == VIDEO) videoSamples++
        if (muxing) {
            muxer.writeSampleData(tracks[kind], buffer, info)
        } else {
            val bytes = ByteArray(info.size).also { buffer.position(info.offset); buffer.get(it) }
            pending += kind to (bytes to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) })
        }
    }

    private inner class Output(private val kind: Int) : MediaCodec.Callback() {
        /** Audio only (video input is the surface): fill with captured PCM, stamped on the same clock as video frames. */
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            val buffer = codec.getInputBuffer(index) ?: return
            if (!recording) {
                codec.queueInputBuffer(index, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return
            }
            val read = record!!.read(buffer, buffer.capacity()).coerceAtLeast(0)
            val startUs = System.nanoTime() / 1000 - read / 4 * 1_000_000L / SAMPLE_RATE
            codec.queueInputBuffer(index, 0, read, startUs, 0)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            val buffer = codec.getOutputBuffer(index)
            if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) write(kind, buffer, info)
            codec.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done.countDown()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = addTrack(kind, format)

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.w(TAG, "clip codec $kind failed", e)
            done.countDown()
        }
    }
}
