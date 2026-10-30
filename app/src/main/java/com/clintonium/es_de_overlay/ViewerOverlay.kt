package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** One page: its size in source units (PDF points or image pixels) and a renderer for part of it. Worker thread only. */
private interface Page {
    val width: Int
    val height: Int

    /** Renders [rect] for [scale] screen pixels per source unit; returns the bitmap and its pixels per source unit. */
    fun render(rect: Rect, scale: Float): Pair<Bitmap, Float>
    fun close()
}

private class RegionPage(private val decoder: BitmapRegionDecoder) : Page {
    override val width get() = decoder.width
    override val height get() = decoder.height

    override fun render(rect: Rect, scale: Float): Pair<Bitmap, Float> {
        // The largest power-of-two subsample that still has a pixel per screen pixel.
        val sample = Integer.highestOneBit((1 / scale).toInt().coerceAtLeast(1))
        return decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample }) to 1f / sample
    }

    override fun close() = decoder.recycle()
}

private class PdfPage(private val page: PdfRenderer.Page) : Page {
    override val width get() = page.width
    override val height get() = page.height

    override fun render(rect: Rect, scale: Float): Pair<Bitmap, Float> {
        val bitmap = Bitmap.createBitmap(
            (rect.width() * scale).toInt().coerceAtLeast(1), (rect.height() * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.WHITE)
        val transform = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-rect.left * scale, -rect.top * scale)
        }
        page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap to scale
    }

    override fun close() = page.close()
}

/** [count] pages; [open] gives page n on the worker thread; [close] releases the file. */
private class Doc(val count: Int, val open: (Int) -> Page, val close: () -> Unit)

private class Rendered(val page: Int, val width: Int, val height: Int, val rect: Rect, val bitmap: Bitmap, val density: Float)

/**
 * Full-screen viewer for a manual or guide that works like ES-DE's own manual viewer. PDF, CBZ and
 * PNG/JPG pages are fitted whole and centered; zoomed out, D-pad left/right turn pages and L2/R2 go to
 * the first or last page; zoomed in, the D-pad pans and L2/R2 reset; L1/R1 zoom. Text scrolls. Only the
 * current page's bitmap is held, and everything is released on [close].
 */
class ViewerOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val bounds = wm.maximumWindowMetrics.bounds
    private val screenW = bounds.width()
    private val screenH = bounds.height()
    /** ES-DE's bottom bar: 40 px of the Nova's 960. */
    private val barH = screenH / 24
    private val viewH = screenH - barH
    private val resolver = context.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var release: () -> Unit = {}

    val isOpen get() = root != null

    /** Opens [uri], picking the format from [name]'s extension; [onClose] runs when B closes it. */
    fun open(uri: Uri, name: String, onClose: () -> Unit) {
        close()
        try {
            build(uri, name, onClose)
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun build(uri: Uri, name: String, onClose: () -> Unit) {
        val label = bar(Gravity.START or Gravity.CENTER_VERTICAL)
        val help = bar(Gravity.CENTER)
        val content: View
        val keys: (Int) -> Boolean
        when (val ext = name.substringAfterLast('.').lowercase()) {
            "txt" -> {
                // Invalid UTF-8 shows as replacement characters rather than failing.
                val text = resolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                val scroll = ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    addView(TextView(context).apply {
                        this.text = text
                        typeface = Typeface.MONOSPACE
                        setTextColor(TEXT)
                        // 80 columns across the screen, so ASCII guides keep their layout.
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f * screenW * 0.95f / paint.measureText("M".repeat(80)))
                        setPadding(screenW / 40, barH / 2, screenW / 40, viewH / 2)
                    })
                }
                label.text = name
                help.text = "✚ SCROLL   L2 TOP   R2 BOTTOM"
                content = scroll
                keys = { code ->
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> scroll.smoothScrollBy(0, -viewH / 4)
                        KeyEvent.KEYCODE_DPAD_DOWN -> scroll.smoothScrollBy(0, viewH / 4)
                        KeyEvent.KEYCODE_DPAD_LEFT -> scroll.pageScroll(View.FOCUS_UP)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> scroll.pageScroll(View.FOCUS_DOWN)
                        KeyEvent.KEYCODE_BUTTON_L2 -> scroll.fullScroll(View.FOCUS_UP)
                        KeyEvent.KEYCODE_BUTTON_R2 -> scroll.fullScroll(View.FOCUS_DOWN)
                    }
                    code in TEXT_KEYS
                }
            }
            else -> {
                val doc = when (ext) {
                    "pdf" -> pdf(uri)
                    "cbz" -> cbz(uri)
                    "png", "jpg", "jpeg" -> Doc(1, { RegionPage(resolver.openInputStream(uri)!!.use { BitmapRegionDecoder.newInstance(it)!! }) }) {}
                    else -> throw IllegalArgumentException("unsupported guide format: $name")
                }
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.MATRIX
                    // Drawn in software: pages uploaded as GPU textures stayed in the renderer's cache
                    // (~70 MB after a big map) once the viewer closed, and nothing trims it in an overlay.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }
                content = image
                keys = paged(doc, name, image, label, help)
            }
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, viewH, Gravity.TOP))
            addView(
                FrameLayout(context).apply {
                    setBackgroundColor(VIEWER_BAR)
                    addView(label)
                    addView(help)
                },
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.BOTTOM),
            )
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_DOWN) { close(); onClose() }
                    return@setOnKeyListener true
                }
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                // A bad page shouldn't take the service down with it.
                runCatching { keys(code) }.onFailure { Log.w(TAG, "viewer: $name", it) }.getOrDefault(true)
            }
        }
        wm.addFullScreen(view, PixelFormat.OPAQUE)
        root = view
    }

    /**
     * Shows [doc] in [image] and returns its key handler. Pages open and render on a worker: a PNG region
     * decode inflates every row above it (about a second on a big map), so zoom and pan move the bitmap
     * already shown at once and the sharp one replaces it when ready.
     */
    private fun paged(doc: Doc, name: String, image: ImageView, label: TextView, help: TextView): (Int) -> Boolean {
        val worker = Executors.newSingleThreadExecutor()
        // Worker thread only.
        var opened: Page? = null
        var openedIndex = -1
        // Main thread. The page size is 0 until the page has opened.
        var page = 0
        var pw = 0
        var ph = 0
        var fit = 1f
        var scale = 1f
        var cx = 0f
        var cy = 0f
        var bitmap: Bitmap? = null
        var shown = Rect()
        var density = 1f
        var busy = false
        var again = false
        var closed = false

        fun zoomed() = pw > 0 && scale > fit * 1.01f
        fun chrome() {
            label.text = if (doc.count > 1) "PAGE ${page + 1} OF ${doc.count}" else name
            help.text = when {
                zoomed() -> "✚ PAN   L2/R2 RESET   L1/R1 ZOOM"
                doc.count > 1 -> "✚ BROWSE   L2 FIRST   R2 LAST   L1/R1 ZOOM"
                else -> "L1/R1 ZOOM"
            }
        }
        fun place() {
            if (pw == 0) return
            val k = scale / density
            image.imageMatrix = Matrix().apply {
                setScale(k, k)
                postTranslate((shown.left - cx) * scale + screenW / 2f, (shown.top - cy) * scale + viewH / 2f)
            }
        }
        fun render() {
            if (pw > 0) {
                val vw = screenW / scale
                val vh = viewH / scale
                cx = if (vw >= pw) pw / 2f else cx.coerceIn(vw / 2, pw - vw / 2)
                cy = if (vh >= ph) ph / 2f else cy.coerceIn(vh / 2, ph - vh / 2)
                place()
            }
            chrome()
            if (busy) {
                again = true
                return
            }
            busy = true
            val n = page
            val s = scale
            // The view plus a quarter screen on each side, so the next pan step is already sharp. Unknown
            // until the page opens; then the whole page renders at its fit.
            val rect = if (pw == 0) null else Rect(
                (cx - screenW * 0.75f / s).toInt().coerceAtLeast(0), (cy - viewH * 0.75f / s).toInt().coerceAtLeast(0),
                (cx + screenW * 0.75f / s).toInt().coerceAtMost(pw), (cy + viewH * 0.75f / s).toInt().coerceAtMost(ph),
            )
            worker.execute {
                val result = runCatching {
                    if (openedIndex != n) {
                        opened?.close()
                        opened = null
                        opened = doc.open(n)
                        openedIndex = n
                    }
                    val p = opened!!
                    val r = rect ?: Rect(0, 0, p.width, p.height)
                    val (b, d) = p.render(r, if (rect == null) minOf(screenW.toFloat() / p.width, viewH.toFloat() / p.height) else s)
                    Rendered(n, p.width, p.height, r, b, d)
                }.onFailure { Log.w(TAG, "viewer: $name page ${n + 1}", it) }.getOrNull()
                main.post {
                    busy = false
                    when {
                        result == null -> {}
                        closed || result.page != page -> result.bitmap.recycle()
                        else -> {
                            if (pw == 0) {
                                pw = result.width
                                ph = result.height
                                fit = minOf(screenW.toFloat() / pw, viewH.toFloat() / ph)
                                scale = fit
                                cx = pw / 2f
                                cy = ph / 2f
                            }
                            image.setImageBitmap(result.bitmap)
                            bitmap?.recycle()
                            bitmap = result.bitmap
                            shown = result.rect
                            density = result.density
                            place()
                            chrome()
                        }
                    }
                    if (again && !closed) {
                        again = false
                        render()
                    }
                }
            }
        }
        fun turnTo(n: Int) {
            if (n !in 0 until doc.count || n == page) return
            page = n
            pw = 0
            image.setImageDrawable(null)
            bitmap?.recycle()
            bitmap = null
            render()
        }
        fun zoom(to: Float) {
            if (pw == 0) return
            scale = to.coerceIn(fit, maxOf(fit * 4, 2f))
            render()
        }

        release = {
            closed = true
            image.setImageDrawable(null)
            bitmap?.recycle()
            bitmap = null
            // On the worker, after any render in flight: PdfRenderer isn't thread-safe.
            worker.execute {
                opened?.close()
                opened = null
                doc.close()
            }
            worker.shutdown()
        }
        render()

        return handler@{ code ->
            val step = screenW / scale / 4
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (zoomed()) { cx -= step; render() } else turnTo(page - 1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (zoomed()) { cx += step; render() } else turnTo(page + 1)
                KeyEvent.KEYCODE_DPAD_UP -> if (zoomed()) { cy -= step; render() } else return@handler false
                KeyEvent.KEYCODE_DPAD_DOWN -> if (zoomed()) { cy += step; render() } else return@handler false
                KeyEvent.KEYCODE_BUTTON_L1 -> zoom(scale / 1.5f)
                KeyEvent.KEYCODE_BUTTON_R1 -> zoom(scale * 1.5f)
                KeyEvent.KEYCODE_BUTTON_L2 -> if (zoomed()) zoom(fit) else turnTo(0)
                KeyEvent.KEYCODE_BUTTON_R2 -> if (zoomed()) zoom(fit) else turnTo(doc.count - 1)
                else -> return@handler false
            }
            true
        }
    }

    private fun pdf(uri: Uri): Doc {
        val fd = resolver.openFileDescriptor(uri, "r")!!
        val pdf = runCatching { PdfRenderer(fd) }.onFailure { fd.close() }.getOrThrow()
        return Doc(pdf.pageCount, { PdfPage(pdf.openPage(it)) }) {
            pdf.close()
            fd.close()
        }
    }

    private fun cbz(uri: Uri): Doc {
        val entries = zip(uri) { z ->
            generateSequence { z.nextEntry }
                .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg") }
                .map { it.name }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .toList()
        }
        return Doc(entries.size, { n ->
            val bytes = zip(uri) { z -> generateSequence { z.nextEntry }.first { it.name == entries[n] }; z.readBytes() }
            RegionPage(BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)!!)
        }) {}
    }

    fun close() {
        release()
        release = {}
        root?.let(wm::removeView)
        root = null
    }

    /** Text in ES-DE's viewer bar: its own condensed face, grey, upper case. */
    private fun bar(gravity: Int) = TextView(context).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        setTextColor(VIEWER_TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, barH * 0.7f)
        isAllCaps = true
        maxLines = 1
        this.gravity = gravity
        setPadding(barH / 4, 0, barH / 4, 0)
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun <T> zip(uri: Uri, read: (ZipInputStream) -> T): T = ZipInputStream(resolver.openInputStream(uri)!!).use(read)

    private companion object {
        val TEXT_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        )
    }
}
