package com.clintonium.es_de_overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
 * the first or last page; zoomed in, the D-pad pans and L2/R2 reset; L1/R1 zoom. Text scrolls. Saved
 * HTML guides scroll in an offline WebView styled like the theme, with their contents on Y. Only the
 * current page's bitmaps are held, and everything is released on [close].
 */
class ViewerOverlay(private val context: Context, private val prefs: Prefs) {
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
    /** Where the open document is: its page, or its scroll position from [fraction]. Saved under [key] on close. */
    private var position: () -> Int = { 0 }
    private var key: String? = null

    /**
     * Opens [uri], picking the format from [name]'s extension, at the position saved under [key] (its path
     * below the ES-DE folder); [onClose] runs when B closes it.
     */
    fun open(uri: Uri, name: String, key: String, onClose: () -> Unit) {
        close()
        try {
            build(uri, name, prefs.position(key), onClose)
            this.key = key
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun build(uri: Uri, name: String, saved: Int, onClose: () -> Unit) {
        val label = barText(context, barH, Gravity.START or Gravity.CENTER_VERTICAL)
        val help = barText(context, barH, Gravity.CENTER)
        val content: View
        val keys: (Int) -> Boolean
        // B steps back through a guide's links before it closes.
        var back = { false }
        when (val ext = name.substringAfterLast('.').lowercase()) {
            "html" -> {
                val web = web(uri, name, saved, label, onClose)
                // Y shows the guide's contents over it; while they're up, the D-pad, A, B and Y work the list.
                var contentsOpen = false
                fun showContents(open: Boolean) {
                    contentsOpen = open
                    help.text = if (open) "✚ MOVE   A GO   B CLOSE" else "✚ SCROLL   L1/R1 ZOOM   L2/R2 SECTION   Y CONTENTS"
                }
                fun closeContents() {
                    web.evaluateJavascript(CONTENTS_CLOSE, null)
                    showContents(false)
                }
                showContents(false)
                content = web
                back = {
                    if (contentsOpen) {
                        closeContents()
                        true
                    } else {
                        web.canGoBack().also { if (it) web.goBack() }
                    }
                }
                keys = { code ->
                    if (contentsOpen) when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> web.evaluateJavascript(CONTENTS_UP, null)
                        KeyEvent.KEYCODE_DPAD_DOWN -> web.evaluateJavascript(CONTENTS_DOWN, null)
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER -> {
                            web.evaluateJavascript(CONTENTS_PICK, null)
                            showContents(false)
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> closeContents()
                    } else when (code) {
                        KeyEvent.KEYCODE_BUTTON_Y -> web.evaluateJavascript(CONTENTS_OPEN) { if (it != "0") showContents(true) }
                        KeyEvent.KEYCODE_DPAD_UP -> web.scrollBy(0, -viewH / 4)
                        KeyEvent.KEYCODE_DPAD_DOWN -> web.scrollBy(0, viewH / 4)
                        KeyEvent.KEYCODE_DPAD_LEFT -> web.pageUp(false)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> web.pageDown(false)
                        KeyEvent.KEYCODE_BUTTON_L1 -> web.settings.textZoom = (web.settings.textZoom - 25).coerceAtLeast(50)
                        KeyEvent.KEYCODE_BUTTON_R1 -> web.settings.textZoom = (web.settings.textZoom + 25).coerceAtMost(200)
                        KeyEvent.KEYCODE_BUTTON_L2 -> web.evaluateJavascript(section(-1), null)
                        KeyEvent.KEYCODE_BUTTON_R2 -> web.evaluateJavascript(section(1), null)
                    }
                    code in HTML_KEYS
                }
            }
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
                fun range() = scroll.getChildAt(0).height - scroll.height
                var restored = false
                scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    if (!restored) {
                        restored = true
                        scroll.scrollTo(0, unfraction(saved, range()))
                    }
                }
                position = { fraction(scroll.scrollY, range()) }
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
                fun layer() = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.MATRIX
                    // Drawn in software: pages uploaded as GPU textures stayed in the renderer's cache
                    // (~70 MB after a big map) once the viewer closed, and nothing trims it in an overlay.
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                }
                val under = layer()
                val image = layer()
                content = FrameLayout(context).apply {
                    addView(under)
                    addView(image)
                }
                keys = paged(doc, name, saved, under, image, label, help)
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
                    if (event.action == KeyEvent.ACTION_DOWN && !back()) { close(); onClose() }
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
     * Shows [doc] and returns its key handler. Pages open and render on a worker: a PNG region decode
     * inflates every row down to the region's bottom (half a second on a big map), so zoom and pan move
     * the bitmaps already shown at once and the sharp one replaces its region when ready. The page's
     * first render, the whole page at its fit, stays in [under], so what the sharp region in [image]
     * doesn't cover yet is blurry rather than black. A big image's top quarter, which decodes in about a
     * quarter of the time, shows there first.
     */
    private fun paged(
        doc: Doc, name: String, start: Int, under: ImageView, image: ImageView, label: TextView, help: TextView,
    ): (Int) -> Boolean {
        val worker = Executors.newSingleThreadExecutor()
        // Worker thread only.
        var opened: Page? = null
        var openedIndex = -1
        // Main thread. The page size is 0 until the page has opened.
        var page = start.coerceIn(0, (doc.count - 1).coerceAtLeast(0))
        var pw = 0
        var ph = 0
        var fit = 1f
        var scale = 1f
        var cx = 0f
        var cy = 0f
        var bitmap: Bitmap? = null
        var shown = Rect()
        var density = 1f
        var base: Bitmap? = null
        var baseDensity = 1f
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
        /** Draws a render of the page from ([left], [top]) at [d] pixels per source unit into the view. */
        fun matrix(left: Int, top: Int, d: Float) = Matrix().apply {
            setScale(scale / d, scale / d)
            postTranslate((left - cx) * scale + screenW / 2f, (top - cy) * scale + viewH / 2f)
        }
        fun place() {
            if (pw == 0) return
            under.imageMatrix = matrix(0, 0, baseDensity)
            image.imageMatrix = matrix(shown.left, shown.top, density)
        }
        fun dropSharp() {
            image.setImageDrawable(null)
            bitmap?.recycle()
            bitmap = null
        }
        /** Shows [result], a render from the page's top-left corner at its fit, under the sharp region. */
        fun showBase(result: Rendered) {
            if (pw == 0) {
                pw = result.width
                ph = result.height
                fit = minOf(screenW.toFloat() / pw, viewH.toFloat() / ph)
                scale = fit
                cx = pw / 2f
                cy = ph / 2f
            }
            under.setImageBitmap(result.bitmap)
            base?.recycle()
            base = result.bitmap
            baseDensity = result.density
            place()
            chrome()
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
            // Zoomed out, the whole page underneath is the view, so there's nothing sharper to decode.
            if (base != null && !zoomed()) return dropSharp()
            if (busy) {
                again = true
                return
            }
            busy = true
            val n = page
            val s = scale
            val asked = SystemClock.uptimeMillis()
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
                    val fitScale = minOf(screenW.toFloat() / p.width, viewH.toFloat() / p.height)
                    // ponytail: the quarter, then the whole page, on one decoder, so the whole page lands a quarter
                    // later than alone; a second decoder in parallel avoids that at the cost of a second copy of the file.
                    if (rect == null && p is RegionPage && p.width.toLong() * p.height > PREVIEW_PIXELS) {
                        val band = Rect(0, 0, p.width, p.height / 4)
                        val top = p.render(band, fitScale).let { (b, d) -> Rendered(n, p.width, p.height, band, b, d) }
                        main.post {
                            if (BuildConfig.DEBUG) Log.d(TAG, "viewer: page ${n + 1} top quarter in ${SystemClock.uptimeMillis() - asked} ms")
                            if (closed || page != n || pw > 0) top.bitmap.recycle() else showBase(top)
                        }
                    }
                    val (b, d) = p.render(r, if (rect == null) fitScale else s)
                    Rendered(n, p.width, p.height, r, b, d)
                }.onFailure { Log.w(TAG, "viewer: $name page ${n + 1}", it) }.getOrNull()
                main.post {
                    busy = false
                    if (BuildConfig.DEBUG && result != null) {
                        Log.d(TAG, "viewer: page ${n + 1} ${result.rect.toShortString()} rendered in ${SystemClock.uptimeMillis() - asked} ms")
                    }
                    when {
                        result == null -> {}
                        // Stale: the viewer closed, or the page turned (or turned back before a zoomed render landed).
                        closed || result.page != page || (pw == 0 && rect != null) -> result.bitmap.recycle()
                        rect == null -> showBase(result)
                        // The view went back to the base meanwhile.
                        !zoomed() -> result.bitmap.recycle()
                        else -> {
                            image.setImageBitmap(result.bitmap)
                            bitmap?.recycle()
                            bitmap = result.bitmap
                            shown = result.rect
                            density = result.density
                            place()
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
            dropSharp()
            under.setImageDrawable(null)
            base?.recycle()
            base = null
            render()
        }
        fun zoom(to: Float) {
            if (pw == 0) return
            scale = to.coerceIn(fit, maxOf(fit * 4, 2f))
            render()
        }

        position = { page }
        release = {
            closed = true
            dropSharp()
            under.setImageDrawable(null)
            base?.recycle()
            base = null
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

    /**
     * A WebView for a saved guide that never reaches the network: the page, `guide.css` and the theme's
     * font are served from [uri] and the ES-DE folder, every other request gets a 404, and only `#` links
     * within the page are followed. JavaScript is on for the viewer's own section lookups and contents
     * list; there is no bridge.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun web(uri: Uri, name: String, saved: Int, label: TextView, onClose: () -> Unit): WebView {
        val css = css().toByteArray()
        val refused = ConcurrentHashMap.newKeySet<String>()
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
            // Safe Browsing would look pages up with Google.
            safeBrowsingEnabled = false
        }
        web.setBackgroundColor(BACKGROUND)
        web.isVerticalScrollBarEnabled = false
        val refresh = Runnable {
            web.evaluateJavascript("(function(){$SECTIONS return n+' '+s.length})()") { r ->
                val (n, of) = r.trim('"').split(' ').map { it.toIntOrNull() ?: 0 }
                label.text = if (of > 0) "SECTION ${n.coerceAtLeast(1)} OF $of" else name
            }
        }
        web.setOnScrollChangeListener { _, _, _, _, _ ->
            main.removeCallbacks(refresh)
            main.postDelayed(refresh, 100)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                if (url.host == HOST) {
                    when (url.path) {
                        "/guide.html" -> return WebResourceResponse("text/html", "utf-8", resolver.openInputStream(uri))
                        "/guide.css" -> return WebResourceResponse("text/css", "utf-8", ByteArrayInputStream(css))
                        "/font.otf" -> prefs.esdeTree?.let { runCatching { resolver.openInputStream(treeChild(it, FONT)) }.getOrNull() }
                            ?.let { return WebResourceResponse("font/otf", null, it) }
                    }
                }
                if (refused.add(url.toString())) Log.w(TAG, "viewer: refused $url")
                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                !(request.url.host == HOST && request.url.path == "/guide.html" && request.url.fragment != null)

            private var restored = false

            override fun onPageFinished(view: WebView, url: String) {
                // Also called after a # link; only the first load restores.
                if (!restored) {
                    restored = true
                    web.evaluateJavascript(
                        "scrollTo(0,(document.documentElement.scrollHeight-innerHeight)*${saved.coerceIn(0, 10_000)}/10000)", null,
                    )
                }
                refresh.run()
            }

            // Without this a crashed renderer takes the service down with it.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "viewer: $name renderer gone")
                close()
                onClose()
                return true
            }
        }
        web.loadUrl("https://$HOST/guide.html")
        val density = context.resources.displayMetrics.density
        position = { fraction(web.scrollY, (web.contentHeight * density).toInt() - web.height) }
        release = {
            main.removeCallbacks(refresh)
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        return web
    }

    /** The theme's colours and font for saved guides; `<pre>` fits 80 columns like the text viewer. */
    private fun css(): String {
        val density = context.resources.displayMetrics.density
        fun px(device: Float) = "%.2fpx".format(Locale.ROOT, device / density)
        fun color(c: Int) = "#%06X".format(c and 0xFFFFFF)
        val mono = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 100f }.measureText("M".repeat(80))
        // The pill's padding moves its text down by this, keeping the pill's size, to centre the capitals.
        val shift = capCentring(themeFont(context, prefs))
        fun em(v: Float) = "%.3fem".format(Locale.ROOT, v)
        return """
            @font-face { font-family: theme; src: url(font.otf); }
            body { background: ${color(BACKGROUND)}; color: ${color(TEXT)}; font-family: theme, sans-serif;
                font-size: ${px(screenH * LIST_FONT)}; margin: 0;
                padding: ${px(barH / 2f)} ${px(screenW / 40f)} ${px(viewH / 2f)}; }
            a { color: ${color(TEXT)}; }
            img { max-width: 100%; height: auto; }
            figure { margin-left: 0; margin-right: 0; }
            pre { font-family: monospace; font-size: ${px(100f * screenW * 0.95f / mono)}; overflow-x: auto; }
            #contents { display: none; }
            #contents.open { display: block; position: fixed; inset: 0; z-index: 9; overflow-y: auto;
                background: ${color(BACKGROUND)}; padding: ${px(barH / 2f)} ${px(screenW / 40f)}; }
            #contents.open::before { content: "Contents"; display: block; font-size: 1.4em; padding-left: 0.5em; }
            #contents ol { list-style: none; padding: 0; margin: 0; }
            #contents a { display: inline-block; color: ${color(TEXT)}; border-radius: 1em;
                padding: ${em(0.1f + shift)} 0.7em ${em(0.1f - shift)};
                margin: 0.15em 0; text-decoration: none; }
            #contents .on a { background: ${color(PILL)}; color: ${color(PILL_TEXT)}; }
            footer { color: ${color(VIEWER_TEXT)}; font-size: 0.7em; margin-top: 2em; }
        """.trimIndent()
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
        key?.let { prefs.setPosition(it, position()) }
        key = null
        position = { 0 }
        release()
        release = {}
        root?.let(wm::removeView)
        root = null
    }

    private fun <T> zip(uri: Uri, read: (ZipInputStream) -> T): T = ZipInputStream(resolver.openInputStream(uri)!!).use(read)

    private companion object {
        /** [offset] into a scroll [range] as parts per 10,000, so the position survives a zoom or font change. */
        fun fraction(offset: Int, range: Int) = if (range > 0) (offset * 10_000L / range).toInt().coerceIn(0, 10_000) else 0

        /** The offset into [range] for a saved [fraction], clamped to the range. */
        fun unfraction(fraction: Int, range: Int) = (fraction.coerceIn(0, 10_000) * range.coerceAtLeast(0).toLong() / 10_000).toInt()

        /** Images larger than this (about 0.2 s to decode whole on the Nova) show their top quarter first. */
        const val PREVIEW_PIXELS = 16_000_000L

        val TEXT_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        )
        val HTML_KEYS = TEXT_KEYS + setOf(
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
        )

        /** A made-up origin, so the guide's relative `guide.css` and `font.otf` resolve to the viewer. */
        const val HOST = "guide.local"

        /** JavaScript: `s` the guide's sections, `n` the one at the top of the view (1-based, 0 above the first). */
        const val SECTIONS = "var s=document.querySelectorAll('section'),n=0;" +
            "for(var i=0;i<s.length;i++)if(s[i].getBoundingClientRect().top<innerHeight/4)n=i+1;"

        /** JavaScript that scrolls [by] sections from the current one; before the first is the top. */
        fun section(by: Int) = "(function(){$SECTIONS n+=$by;if(n<1)scrollTo(0,0);else if(n<=s.length)s[n-1].scrollIntoView()})()"

        /** JavaScript that shows the contents list with the section in view picked; returns its rows, 0 for none. */
        const val CONTENTS_OPEN = "(function(){$SECTIONS var c=document.getElementById('contents'),l=c?c.querySelectorAll('li'):[];" +
            "if(!l.length)return 0;for(var i=0;i<l.length;i++)l[i].classList.toggle('on',i==Math.max(n,1)-1);" +
            "c.classList.add('open');c.querySelector('.on').scrollIntoView({block:'nearest'});return l.length})()"

        /** JavaScript that moves the contents list's pick to the row before or after it, stopping at the ends. */
        private fun contentsStep(sibling: String) = "(function(){var o=document.querySelector('#contents .on'),e=o&&o.$sibling;" +
            "if(e){o.classList.remove('on');e.classList.add('on');e.scrollIntoView({block:'nearest'})}})()"
        val CONTENTS_UP = contentsStep("previousElementSibling")
        val CONTENTS_DOWN = contentsStep("nextElementSibling")

        /** JavaScript that hides the contents list and scrolls to the picked section. */
        const val CONTENTS_PICK = "(function(){var c=document.getElementById('contents'),a=c.querySelector('.on a')," +
            "t=a&&document.querySelector(a.getAttribute('href'));c.classList.remove('open');if(t)t.scrollIntoView()})()"
        const val CONTENTS_CLOSE = "document.getElementById('contents').classList.remove('open')"
    }
}
