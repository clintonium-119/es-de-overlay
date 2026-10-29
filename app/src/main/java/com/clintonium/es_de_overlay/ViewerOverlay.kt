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
import android.graphics.drawable.GradientDrawable
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

/**
 * Full-screen viewer for a manual or guide: PDF and CBZ one page at a time, fit to width; text in a
 * monospaced scroll; PNG/JPG decoding only the visible region, so huge maps work. Everything it opens
 * is released on [close].
 */
class ViewerOverlay(private val context: Context, private val prefs: Prefs) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val bounds = wm.maximumWindowMetrics.bounds
    private val screenW = bounds.width()
    private val screenH = bounds.height()
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
        val font = themeFont(context, prefs)
        val textPx = screenH * LIST_FONT
        val title = chrome(font, textPx * 0.65f).apply { text = name }
        val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false }
        val keys: (Int) -> Boolean
        val help: Array<Pair<String, String>>
        val content: View
        when (name.substringAfterLast('.').lowercase()) {
            "txt" -> {
                // Invalid UTF-8 shows as replacement characters rather than failing.
                val text = resolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
                scroll.addView(TextView(context).apply {
                    this.text = text
                    typeface = Typeface.MONOSPACE
                    setTextColor(TEXT)
                    // 80 columns across the screen, so ASCII guides keep their layout.
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 100f * screenW * 0.95f / paint.measureText("M".repeat(80)))
                    setPadding(screenW / 40, (textPx * 2.5f).toInt(), screenW / 40, screenH / 2)
                })
                content = scroll
                help = arrayOf("L1/R1" to "Page", "B" to "Back")
                keys = { code -> scrollKeys(scroll, code) }
            }
            "png", "jpg", "jpeg" -> {
                val image = ImageView(context).apply { scaleType = ImageView.ScaleType.MATRIX }
                val decoder = resolver.openInputStream(uri)!!.use { BitmapRegionDecoder.newInstance(it)!! }
                val iw = decoder.width
                val ih = decoder.height
                val fit = minOf(screenW.toFloat() / iw, screenH.toFloat() / ih)
                var scale = fit
                var cx = iw / 2f
                var cy = ih / 2f
                // The decoded bitmap, the image area it covers and its subsample.
                var bitmap: Bitmap? = null
                var shown = Rect()
                var shownSample = 1
                // A PNG region decode inflates every row above it (about a second on a big map), so moves
                // shift the bitmap already shown at once while a sharper one decodes on the worker.
                val worker = Executors.newSingleThreadExecutor()
                var decoding = false
                var again = false
                var closed = false
                fun place() {
                    val k = scale * shownSample
                    image.imageMatrix = Matrix().apply {
                        setScale(k, k)
                        postTranslate((shown.left - cx) * scale + screenW / 2f, (shown.top - cy) * scale + screenH / 2f)
                    }
                }
                fun render() {
                    val vw = screenW / scale
                    val vh = screenH / scale
                    cx = if (vw >= iw) iw / 2f else cx.coerceIn(vw / 2, iw - vw / 2)
                    cy = if (vh >= ih) ih / 2f else cy.coerceIn(vh / 2, ih - vh / 2)
                    place()
                    if (decoding) {
                        again = true
                        return
                    }
                    decoding = true
                    // The view plus a quarter screen on each side, so the next pan step is already sharp.
                    val rect = Rect(
                        (cx - vw * 0.75f).toInt().coerceAtLeast(0), (cy - vh * 0.75f).toInt().coerceAtLeast(0),
                        (cx + vw * 0.75f).toInt().coerceAtMost(iw), (cy + vh * 0.75f).toInt().coerceAtMost(ih),
                    )
                    // The largest power-of-two subsample that still has a pixel per screen pixel.
                    val sample = Integer.highestOneBit((1 / scale).toInt().coerceAtLeast(1))
                    worker.execute {
                        val next = runCatching { decoder.decodeRegion(rect, BitmapFactory.Options().apply { inSampleSize = sample }) }
                            .onFailure { Log.w(TAG, "viewer: $name", it) }.getOrNull()
                        main.post {
                            if (closed) {
                                next?.recycle()
                                return@post
                            }
                            if (next != null) {
                                image.setImageBitmap(next)
                                bitmap?.recycle()
                                bitmap = next
                                shown = rect
                                shownSample = sample
                                place()
                            }
                            decoding = false
                            if (again) {
                                again = false
                                render()
                            }
                        }
                    }
                }
                release = {
                    closed = true
                    worker.shutdown()
                    image.setImageDrawable(null)
                    bitmap?.recycle()
                    bitmap = null
                    // Waits for a decode in flight; BitmapRegionDecoder serializes the two.
                    decoder.recycle()
                }
                render()
                content = image
                help = arrayOf("X/Y" to "Zoom", "B" to "Back")
                keys = handler@{ code ->
                    val step = screenW / scale / 4
                    when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> cy -= step
                        KeyEvent.KEYCODE_DPAD_DOWN -> cy += step
                        KeyEvent.KEYCODE_DPAD_LEFT -> cx -= step
                        KeyEvent.KEYCODE_DPAD_RIGHT -> cx += step
                        KeyEvent.KEYCODE_BUTTON_X -> scale = (scale * 1.5f).coerceAtMost(maxOf(fit, 2f))
                        KeyEvent.KEYCODE_BUTTON_Y -> scale = (scale / 1.5f).coerceAtLeast(fit)
                        else -> return@handler false
                    }
                    render()
                    true
                }
            }
            "pdf", "cbz" -> {
                val image = ImageView(context).apply { adjustViewBounds = true }
                scroll.addView(image, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                val pages: Int
                val renderPage: (Int) -> Bitmap
                if (name.endsWith(".pdf", ignoreCase = true)) {
                    val fd = resolver.openFileDescriptor(uri, "r")!!
                    val pdf = runCatching { PdfRenderer(fd) }.onFailure { fd.close() }.getOrThrow()
                    pages = pdf.pageCount
                    renderPage = { n ->
                        pdf.openPage(n).use { page ->
                            // Fit to width, but no taller than four screens so a long page can't exhaust memory.
                            val h = (screenW.toLong() * page.height / page.width).toInt().coerceAtMost(screenH * 4)
                            val w = h * page.width / page.height
                            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(Color.WHITE)
                                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                    }
                    release = { pdf.close(); fd.close() }
                } else {
                    val entries = zip(uri) { z ->
                        generateSequence { z.nextEntry }
                            .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("png", "jpg", "jpeg") }
                            .map { it.name }
                            .sortedWith(String.CASE_INSENSITIVE_ORDER)
                            .toList()
                    }
                    pages = entries.size
                    renderPage = { n ->
                        val bytes = zip(uri) { z -> generateSequence { z.nextEntry }.first { it.name == entries[n] }; z.readBytes() }
                        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, size)
                        val sample = Integer.highestOneBit((size.outWidth / screenW).coerceAtLeast(1))
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }
                val counter = chrome(font, textPx * 0.65f)
                var page = 0
                var bitmap: Bitmap? = null
                fun show(n: Int) {
                    if (n !in 0 until pages) return
                    val next = renderPage(n)
                    image.setImageBitmap(next)
                    bitmap?.recycle()
                    bitmap = next
                    page = n
                    counter.text = "${n + 1} / $pages"
                    scroll.scrollTo(0, 0)
                }
                show(0)
                val closeDoc = release
                release = {
                    image.setImageDrawable(null)
                    bitmap?.recycle()
                    bitmap = null
                    closeDoc()
                }
                content = FrameLayout(context).apply {
                    addView(scroll)
                    addView(counter, corner(Gravity.TOP or Gravity.END))
                }
                help = arrayOf("L1/R1" to "Page", "B" to "Back")
                keys = { code ->
                    when (code) {
                        KeyEvent.KEYCODE_BUTTON_L1 -> { show(page - 1); true }
                        KeyEvent.KEYCODE_BUTTON_R1 -> { show(page + 1); true }
                        else -> scrollKeys(scroll, code)
                    }
                }
            }
            else -> throw IllegalArgumentException("unsupported guide format: $name")
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(content)
            addView(title, corner(Gravity.TOP or Gravity.START))
            addView(helpPill(context, font, textPx, *help), corner(Gravity.BOTTOM or Gravity.END))
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, event ->
                if (code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_DOWN) { close(); onClose() }
                    return@setOnKeyListener true
                }
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                // A bad page or region shouldn't take the service down with it.
                runCatching { keys(code) }.onFailure { Log.w(TAG, "viewer: $name", it) }.getOrDefault(true)
            }
        }
        wm.addFullScreen(view, PixelFormat.OPAQUE)
        root = view
    }

    fun close() {
        release()
        release = {}
        root?.let(wm::removeView)
        root = null
    }

    private fun corner(gravity: Int) = FrameLayout.LayoutParams(-2, -2, gravity).apply {
        val m = screenH / 60
        setMargins(m, m, m, m)
    }

    /** Title and page counter, in a help-pill box so they stay readable over the page. */
    private fun chrome(font: Typeface, px: Float) = TextView(context).apply {
        typeface = font
        setTextColor(HELP_TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
        maxLines = 1
        val pad = (px * 0.6f).toInt()
        setPadding(pad * 2, pad, pad * 2, pad)
        background = GradientDrawable().apply { setColor(HELP_BG); cornerRadius = px }
    }

    private fun scrollKeys(scroll: ScrollView, code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> scroll.smoothScrollBy(0, -screenH / 4)
            KeyEvent.KEYCODE_DPAD_DOWN -> scroll.smoothScrollBy(0, screenH / 4)
            KeyEvent.KEYCODE_BUTTON_L1 -> scroll.pageScroll(View.FOCUS_UP)
            KeyEvent.KEYCODE_BUTTON_R1 -> scroll.pageScroll(View.FOCUS_DOWN)
            else -> return false
        }
        return true
    }

    private fun <T> zip(uri: Uri, read: (ZipInputStream) -> T): T = ZipInputStream(resolver.openInputStream(uri)!!).use(read)
}
