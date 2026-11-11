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
import android.text.TextUtils
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.doOnLayout
import com.clintonium.es_de_overlay.core.guidePicture
import com.clintonium.es_de_overlay.core.isOneTextSection
import com.clintonium.es_de_overlay.core.splitTextGuide
import com.clintonium.es_de_overlay.core.textSections
import com.clintonium.es_de_overlay.core.writeHtmlGuide
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringWriter
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

/** A region decoder for an image's [bytes]; a GIF or BMP, which it can't read, goes through PNG first. */
private fun regionDecoder(bytes: ByteArray): BitmapRegionDecoder = runCatching { BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)!! }
    .getOrElse { e ->
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw e
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        BitmapRegionDecoder.newInstance(png, 0, png.size)!!
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

/** [count] pages; [open] gives page n on the worker thread; [names], if any, name them; [close] releases the file. */
private class Doc(val count: Int, val open: (Int) -> Page, val names: List<String> = emptyList(), val close: () -> Unit)

private class Rendered(val page: Int, val width: Int, val height: Int, val rect: Rect, val bitmap: Bitmap, val density: Float)

/**
 * Full-screen viewer for a manual or guide that works like ES-DE's own manual viewer. PDF, CBZ and
 * PNG/JPG pages are fitted whole and centered; zoomed out, D-pad left/right turn pages and L2/R2 go to
 * the first or last page; zoomed in, the D-pad pans and L2/R2 reset; L1/R1 zoom; Y lists a CBZ's maps by
 * name. Saved HTML guides
 * scroll in an offline WebView styled like the theme, with their contents on Y; A or a tap opens their
 * pictures in the page viewer. Text guides show the same
 * way, split into sections at their headings. Only the
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
    /** The look and fonts of the open document's window, from [open]. */
    private var theme = ThemeLook.DEFAULT
    private var release: () -> Unit = {}
    /** Where the open document is: its page, or its scroll position from [fraction]. Saved under [key] on close. */
    private var position: () -> Int = { 0 }
    /** Its zoom, and a page's centre, or null at the fit. Saved with [position]. */
    private var view: () -> List<Float>? = { null }
    /** B steps back (through a guide's links, or out of a list) before it closes; false when there's nothing to step back from. */
    private var back: () -> Boolean = { false }
    private var key: String? = null

    /**
     * Opens [uri], picking the format from [name]'s extension, at the position and zoom saved under [key] (its
     * path below the ES-DE folder), drawn with [theme]; [onClose] runs when B closes it.
     */
    fun open(uri: Uri, name: String, key: String, theme: ThemeLook.Current, onClose: () -> Unit) {
        close()
        this.theme = theme
        try {
            build(uri, name, prefs.position(key), prefs.view(key), onClose)
            this.key = key
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    private fun build(uri: Uri, name: String, saved: Int, savedView: List<Float>?, onClose: () -> Unit) {
        val label = barText(context, barH, Gravity.START or Gravity.CENTER_VERTICAL)
        val help = barText(context, barH, Gravity.CENTER)
        val content: View
        val keys: (Int) -> Boolean
        when (val ext = name.substringAfterLast('.').lowercase()) {
            "html", "txt" -> {
                val page = guidePage(uri, ext, name)
                val box = FrameLayout(context)
                // Y shows the guide's contents over it; while they're up, the D-pad, A, B and Y work the list.
                var contentsOpen = false
                // Whether one of the guide's pictures is in view, for A.
                var pictureInView = false
                // The picture A or a tap opened, over the guide, and its key handler; B goes back to the guide.
                var picture: ((Int) -> Boolean)? = null
                lateinit var web: WebView
                // Zoomed past 100 %, a text guide's lines run off the right edge, and D-pad left/right pan to them.
                fun zoomed() = web.settings.textZoom > 100
                // The view's own scroll: WebView widens the page's layout viewport to fit a slightly wider page, so
                // script can't scroll it. Held between the page's edges from where the page says it is, since a
                // scrollBy past the right edge counted on and the next press back left did nothing.
                fun pan(by: Int) = web.evaluateJavascript("[visualViewport.pageLeft,document.documentElement.scrollWidth-visualViewport.width]") {
                    val density = context.resources.displayMetrics.density
                    val (left, range) = it.trim('[', ']').split(',').map { s -> ((s.toFloatOrNull() ?: 0f) * density).toInt() } + listOf(0, 0)
                    web.scrollTo((left + by).coerceIn(0, range.coerceAtLeast(0)), web.scrollY)
                }
                fun chrome() {
                    help.text = if (contentsOpen) "✚ MOVE   ${theme.label("A")} GO   ${theme.label("B")} CLOSE"
                    else (if (zoomed()) "✚ PAN" else "✚ SCROLL") + "   L1/R1 ZOOM   L2/R2 SECTION   ${theme.label("Y")} CONTENTS" +
                        if (pictureInView) "   ${theme.label("A")} IMAGE" else ""
                }
                /** Shows the guide's picture [n] of [of] in the page viewer, which turns through the others. */
                fun openPicture(n: Int, of: Int) {
                    if (picture != null || n !in 0 until of) return
                    val guidePosition = position
                    val guideView = view
                    val guideBack = back
                    val guideRelease = release
                    val guideLabel = label.text
                    val under = layer()
                    val image = layer()
                    val frame = FrameLayout(context).apply {
                        setBackgroundColor(theme.look.background)
                        // Taps stop here rather than reaching the guide underneath.
                        isClickable = true
                        addView(under)
                        addView(image)
                    }
                    box.addView(frame)
                    val doc = Doc(of, { RegionPage(regionDecoder(guidePicture(page(), it))) }) {}
                    picture = paged(doc, name, n, null, frame, under, image, label, help)
                    val shut = release
                    // Closing while a picture is up saves the guide's own place.
                    position = guidePosition
                    view = guideView
                    release = { shut(); guideRelease() }
                    back = {
                        shut()
                        box.removeView(frame)
                        picture = null
                        back = guideBack
                        release = guideRelease
                        label.text = guideLabel
                        chrome()
                        true
                    }
                }
                web = this.web(page, name, saved, savedView, { answer ->
                    if (picture == null) {
                        label.text = sectionText(answer, name)
                        pictureInView = (answer.trim('"').split(' ').getOrNull(2)?.toIntOrNull() ?: -1) >= 0
                        chrome()
                    }
                }, ::openPicture, onClose)
                box.addView(web)
                fun closeContents() {
                    web.evaluateJavascript(CONTENTS_CLOSE, null)
                    contentsOpen = false
                    chrome()
                }
                chrome()
                content = box
                back = {
                    if (contentsOpen) {
                        closeContents()
                        true
                    } else {
                        web.canGoBack().also { if (it) web.goBack() }
                    }
                }
                keys = keys@{ code ->
                    picture?.let { return@keys it(code) }
                    if (contentsOpen) when (code) {
                        KeyEvent.KEYCODE_DPAD_UP -> web.evaluateJavascript(CONTENTS_UP, null)
                        KeyEvent.KEYCODE_DPAD_DOWN -> web.evaluateJavascript(CONTENTS_DOWN, null)
                        // ES-DE's list keys: the bumpers page, the triggers go to the ends.
                        KeyEvent.KEYCODE_BUTTON_L1 -> web.evaluateJavascript(CONTENTS_PAGE_UP, null)
                        KeyEvent.KEYCODE_BUTTON_R1 -> web.evaluateJavascript(CONTENTS_PAGE_DOWN, null)
                        KeyEvent.KEYCODE_BUTTON_L2 -> web.evaluateJavascript(CONTENTS_FIRST, null)
                        KeyEvent.KEYCODE_BUTTON_R2 -> web.evaluateJavascript(CONTENTS_LAST, null)
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER -> {
                            web.evaluateJavascript(CONTENTS_PICK) { label.text = sectionText(it, name) }
                            contentsOpen = false
                            chrome()
                        }
                        KeyEvent.KEYCODE_BUTTON_Y -> closeContents()
                    } else when (code) {
                        KeyEvent.KEYCODE_BUTTON_Y -> web.evaluateJavascript(CONTENTS_OPEN) {
                            if (it != "0") {
                                contentsOpen = true
                                chrome()
                            }
                        }
                        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER -> web.evaluateJavascript("(function(){$PICTURES return p+' '+g.length})()") {
                            val (n, of) = it.trim('"').split(' ').map { s -> s.toIntOrNull() ?: -1 } + listOf(-1, -1)
                            openPicture(n, of)
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> web.scrollBy(0, -viewH / 4)
                        KeyEvent.KEYCODE_DPAD_DOWN -> web.scrollBy(0, viewH / 4)
                        KeyEvent.KEYCODE_DPAD_LEFT -> if (zoomed()) pan(-screenW / 4) else web.pageUp(false)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> if (zoomed()) pan(screenW / 4) else web.pageDown(false)
                        KeyEvent.KEYCODE_BUTTON_L1 -> {
                            web.settings.textZoom = (web.settings.textZoom - 25).coerceAtLeast(50)
                            chrome()
                        }
                        KeyEvent.KEYCODE_BUTTON_R1 -> {
                            web.settings.textZoom = (web.settings.textZoom + 25).coerceAtMost(200)
                            chrome()
                        }
                        // The bar is set from the jump's answer: a jump the page can't scroll for fires no scroll event.
                        KeyEvent.KEYCODE_BUTTON_L2 -> web.evaluateJavascript(section(-1)) { label.text = sectionText(it, name) }
                        KeyEvent.KEYCODE_BUTTON_R2 -> web.evaluateJavascript(section(1)) { label.text = sectionText(it, name) }
                    }
                    code in HTML_KEYS
                }
            }
            else -> {
                val doc = when (ext) {
                    "pdf" -> pdf(uri)
                    "cbz" -> cbz(uri)
                    "png", "jpg", "jpeg" -> Doc(1, { RegionPage(resolver.openInputStream(uri)!!.use { BitmapRegionDecoder.newInstance(it)!! }) }) {}
                    else -> throw IllegalArgumentException("unsupported guide format: $name")
                }
                val under = layer()
                val image = layer()
                val frame = FrameLayout(context).apply {
                    addView(under)
                    addView(image)
                }
                content = frame
                keys = paged(doc, name, saved, savedView, frame, under, image, label, help)
            }
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(theme.look.background)
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
            setOnKeyListener { _, sent, event ->
                val code = theme.button(sent, event.device?.name)
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

    private fun layer() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.MATRIX
        // Drawn in software: pages uploaded as GPU textures stayed in the renderer's cache
        // (~70 MB after a big map) once the viewer closed, and nothing trims it in an overlay.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    /**
     * Shows [doc] and returns its key handler. Pages open and render on a worker: a PNG region decode
     * inflates every row down to the region's bottom (half a second on a big map), so zoom and pan move
     * the bitmaps already shown at once and the sharp one replaces its region when ready. The page's
     * first render, the whole page at its fit, stays in [under], so what the sharp region in [image]
     * doesn't cover yet is blurry rather than black. A big image's top quarter, which decodes in about a
     * quarter of the time, shows there first. A [startView] saved zoomed in (zoom over the fit, centre over
     * the page size) is restored once the start page opens; turning the page goes back to the fit. Named
     * pages list in [frame] on Y.
     */
    private fun paged(
        doc: Doc, name: String, start: Int, startView: List<Float>?,
        frame: FrameLayout, under: ImageView, image: ImageView, label: TextView, help: TextView,
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
        var restore = startView?.takeIf { it.size == 3 }
        val named = doc.count > 1 && doc.names.size == doc.count
        var list: PillList? = null
        var listView: ScrollView? = null

        fun zoomed() = pw > 0 && scale > fit * 1.01f
        fun maxScale() = maxOf(fit * 4, 2f)
        fun chrome() {
            val y = if (named) "   ${theme.label("Y")} MAPS" else ""
            label.text = when {
                // The number first, so a long name is what gets cut.
                named -> "${page + 1} OF ${doc.count}   ${doc.names[page]}"
                doc.count > 1 -> "PAGE ${page + 1} OF ${doc.count}"
                else -> name
            }
            help.text = when {
                list != null -> "✚ MOVE   ${theme.label("A")} GO   ${theme.label("B")} CLOSE"
                zoomed() -> "✚ PAN   L2/R2 RESET   L1/R1 ZOOM$y"
                doc.count > 1 -> "✚ BROWSE   L2 FIRST   R2 LAST   L1/R1 ZOOM$y"
                else -> "L1/R1 ZOOM"
            }
            if (named) {
                // A name needs the room: the help moves right and the name stops short of it.
                help.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                label.ellipsize = TextUtils.TruncateAt.END
                label.setPadding(barH / 4, 0, help.paint.measureText(help.text.toString()).toInt() + barH, 0)
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
                restore?.let { (z, x, y) ->
                    scale = (fit * z).coerceIn(fit, maxScale())
                    cx = x * pw
                    cy = y * ph
                    // The sharp region, once the render in flight is in.
                    again = true
                }
                restore = null
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
            restore = null
            dropSharp()
            under.setImageDrawable(null)
            base?.recycle()
            base = null
            render()
        }
        fun zoom(to: Float) {
            if (pw == 0) return
            scale = to.coerceIn(fit, maxScale())
            render()
        }
        /** Lists the pages by name over the page, the current one picked, like a guide's contents. */
        fun openList() {
            val look = theme.look
            val textPx = screenH * look.fontSize
            val l = PillList(context, look, theme.font, screenW, screenH, doc.names, page)
            val v = ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                setBackgroundColor(look.background)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(screenW / 40, barH / 2, screenW / 40, barH / 2)
                    // A row of its own, as the menu's title: its size times the list's line spacing.
                    addView(
                        TextView(context).apply {
                            text = "Maps"
                            typeface = theme.font
                            setTextColor(look.bodyText)
                            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 1.4f)
                            gravity = Gravity.CENTER_VERTICAL
                            includeFontPadding = false
                            setPadding((textPx * 0.7f).toInt(), 0, 0, 0)
                        },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (textPx * 1.4f * look.lineSpacing).toInt()),
                    )
                    addView(l.view)
                })
            }
            frame.addView(v)
            // Scrolls the picked row into view once there's a layout to scroll.
            v.doOnLayout { l.select(l.selected) }
            list = l
            listView = v
            chrome()
        }
        // A screenful of the list's rows: those that fit below its title.
        fun screenful(l: PillList) = (((listView?.height ?: 0) - l.view.top) / (l.rows.firstOrNull()?.height ?: 1).coerceAtLeast(1)).coerceAtLeast(1)
        /**
         * Moves the list's pick [by] rows, stopping at the ends, with the pick in the middle of the screen as ES-DE's
         * list puts it on a bumper, or the list at its end (the title in view on the first row). The list scrolls
         * first, at once, so the pick's own scroll finds it in view; the smooth scroll of no distance stops one a
         * D-pad move may have just started, which would otherwise carry on from where it was going.
         */
        fun jump(l: PillList, by: Int) {
            val v = listView ?: return
            val row = l.rows.getOrNull((l.selected + by).coerceAtMost(l.rows.lastIndex).coerceAtLeast(0)) ?: return
            v.scrollTo(0, l.view.top + row.top + row.height / 2 - v.height / 2)
            v.smoothScrollTo(0, v.scrollY)
            l.move(by)
        }
        fun closeList() {
            frame.removeView(listView)
            list = null
            listView = null
            chrome()
        }

        position = { page }
        back = { (list != null).also { if (it) closeList() } }
        // Before the page opens, the view it was asked to open at.
        view = { if (zoomed()) listOf(scale / fit, cx / pw, cy / ph) else restore }
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
            list?.let {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> it.move(-1)
                    KeyEvent.KEYCODE_DPAD_DOWN -> it.move(1)
                    // ES-DE's list keys: the bumpers page, the triggers go to the ends.
                    KeyEvent.KEYCODE_BUTTON_L1 -> jump(it, -screenful(it))
                    KeyEvent.KEYCODE_BUTTON_R1 -> jump(it, screenful(it))
                    KeyEvent.KEYCODE_BUTTON_L2 -> jump(it, -it.rows.size)
                    KeyEvent.KEYCODE_BUTTON_R2 -> jump(it, it.rows.size)
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER -> {
                        closeList()
                        turnTo(it.selected)
                    }
                    KeyEvent.KEYCODE_BUTTON_Y -> closeList()
                }
                return@handler code in HTML_KEYS
            }
            val step = screenW / scale / 4
            when (code) {
                KeyEvent.KEYCODE_BUTTON_Y -> if (named) openList() else return@handler false
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
     * The page to show for the guide at [uri]: a text file as a guide split at its headings, a guide saved as
     * one text section (before text guides were split) split the same way, and anything else as it is.
     */
    private fun guidePage(uri: Uri, ext: String, name: String): () -> InputStream {
        // Invalid UTF-8 shows as replacement characters rather than failing.
        fun read(limit: Int = Int.MAX_VALUE) = resolver.openInputStream(uri)!!.use { String(it.readNBytes(limit), Charsets.UTF_8) }
        val html = when {
            ext == "txt" -> name.substringBeforeLast('.').let { title ->
                StringWriter().also { writeHtmlGuide(it, title, textSections(title, read()), emptyMap(), "") }.toString()
            }
            isOneTextSection(read(4096)) -> splitTextGuide(read())
            else -> null
        }
        val bytes = html?.toByteArray() ?: return { resolver.openInputStream(uri)!! }
        return { ByteArrayInputStream(bytes) }
    }

    /**
     * A WebView for a saved guide that never reaches the network: the [page], `guide.css` and the theme's
     * font are served from it and the ES-DE folder, every other request gets a 404, and only `#` links
     * within the page are followed. JavaScript is on for the viewer's own section lookups, contents list
     * and picture taps; there is no bridge. After a scroll, [refreshed] gets `"n m p"`: section n of m at the
     * top, and the picture in view (-1 for none). A tap on picture n of m calls [onPicture].
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun web(
        page: () -> InputStream, name: String, saved: Int, savedView: List<Float>?,
        refreshed: (String) -> Unit, onPicture: (Int, Int) -> Unit, onClose: () -> Unit,
    ): WebView {
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
            // The text zoom L1/R1 left it at, set before the page lays out so the scroll position lands.
            savedView?.firstOrNull()?.let { textZoom = it.toInt().coerceIn(50, 200) }
        }
        web.setBackgroundColor(theme.look.background)
        web.isVerticalScrollBarEnabled = false
        val refresh = Runnable { web.evaluateJavascript("(function(){$SECTIONS $PICTURES return n+' '+s.length+' '+p})()", refreshed) }
        web.setOnScrollChangeListener { _, _, _, _, _ ->
            main.removeCallbacks(refresh)
            main.postDelayed(refresh, 100)
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                if (url.host == HOST) {
                    when (url.path) {
                        "/guide.html" -> return WebResourceResponse(
                            "text/html", "utf-8", 200, "OK", mapOf("Content-Security-Policy" to GUIDE_CSP), page(),
                        )
                        "/guide.css" -> return WebResourceResponse("text/css", "utf-8", ByteArrayInputStream(css))
                        // The theme's list and guide text fonts; none (a 404) leaves the CSS to fall through to sans-serif.
                        "/font.otf" -> font(theme.look.fontPath)?.let { return WebResourceResponse("font/otf", null, it) }
                        "/body-font.otf" -> font(theme.look.bodyFontPath)?.let { return WebResourceResponse("font/otf", null, it) }
                    }
                }
                if (refused.add(url.toString())) Log.w(TAG, "viewer: refused $url")
                return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == HOST && url.path == "/picture") {
                    onPicture(url.getQueryParameter("n")?.toIntOrNull() ?: -1, url.getQueryParameter("of")?.toIntOrNull() ?: 0)
                }
                return !(url.host == HOST && url.path == "/guide.html" && url.fragment != null)
            }

            private var restored = false

            override fun onPageFinished(view: WebView, url: String) {
                // Also called after a # link; only the first load restores.
                if (!restored) {
                    restored = true
                    web.evaluateJavascript(
                        "scrollTo(0,(document.documentElement.scrollHeight-innerHeight)*${saved.coerceIn(0, 10_000)}/10000)", null,
                    )
                    web.evaluateJavascript(PICTURE_TAPS, null)
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
        view = { web.settings.textZoom.takeIf { it != 100 }?.let { listOf(it.toFloat()) } }
        release = {
            main.removeCallbacks(refresh)
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        return web
    }

    // A font of the look's, from wherever its theme was read, or null.
    private fun font(path: String?) = path?.let { runCatching { theme.open(it) }.getOrNull() }

    /**
     * The theme's colours and fonts for saved guides: the guide text's for the text, the list's for the contents;
     * `<pre>` fits 80 columns like the text viewer at 100 %.
     */
    private fun css(): String {
        val look = theme.look
        val density = context.resources.displayMetrics.density
        fun px(device: Float) = "%.2fpx".format(Locale.ROOT, device / density)
        fun color(c: Int) = "#%06X%02X".format(c and 0xFFFFFF, c ushr 24)
        val mono = Paint().apply { typeface = Typeface.MONOSPACE; textSize = 100f }.measureText("M".repeat(80))
        // The pill's padding moves its text down by this, keeping the pill's size, to centre the capitals.
        val shift = capCentring(theme.font)
        fun em(v: Float) = "%.3fem".format(Locale.ROOT, v)
        // The contents' rows as the menu's: the list's font size times its line spacing, the pill or bar 6/7 of a row.
        val rowPx = screenH * look.fontSize * look.lineSpacing
        val pillPx = rowPx * 6 / 7
        // The contents' current entry: the theme's pill, else its selector, a bar at the left when it has a width.
        val selected = when {
            look.selectedBackground ushr 24 != 0 -> "background: ${color(look.selectedBackground)};"
            look.thinSelector != null -> "box-shadow: inset ${px(look.thinSelector!! * screenW)} 0 ${color(look.selector)};"
            // A row-wide selector spans the theme's selector width, else the menu's list width, and a longer entry.
            else -> "background: ${color(look.selector)}; min-width: ${px((look.selectorWidth ?: 0.4f) * screenW)};"
        } + when {
            look.selectedBackground ushr 24 == 0 && look.selectorCornerRadius > 0f -> " border-radius: ${px(look.selectorCornerRadius * pillPx)};"
            else -> ""
        }
        return """
            @font-face { font-family: theme; src: url(font.otf); }
            @font-face { font-family: body; src: url(body-font.otf); }
            body { background: ${color(look.background)}; color: ${color(look.bodyText)}; font-family: body, theme, sans-serif;
                font-size: ${px(screenH * look.bodyFontSize)}; line-height: ${"%.3f".format(Locale.ROOT, look.bodyLineSpacing)}; margin: 0;
                padding: ${px(barH / 2f)} ${px(screenW / 40f)} ${px(viewH / 2f)}; }
            a { color: ${color(look.bodyText)}; }
            img { max-width: 100%; height: auto; }
            figure { margin-left: 0; margin-right: 0; }
            /* Long lines run off the right edge rather than wrap or scroll alone, so the whole page pans to them.
               The font's own spacing, so text drawings line up across lines. */
            pre { font-family: monospace; font-size: ${px(100f * screenW * 0.95f / mono)}; line-height: normal; }
            /* A text guide's text is its fixed-width blocks: its title and headings are sized from them, not from the
               theme's prose size, which they'd otherwise dwarf. */
            body:has(section > pre):not(:has(section > :not(h2, pre))) { font-size: ${px(100f * screenW * 0.95f / mono)}; }
            body:has(section > pre):not(:has(section > :not(h2, pre))) h1 { font-size: 1.5em; }
            body:has(section > pre):not(:has(section > :not(h2, pre))) h2 { font-size: 1.25em; }
            /* A text guide's sections, which carry their own headings, read on as one text. */
            section > pre:only-child { margin: 0; }
            #contents { display: none; }
            #contents.open { display: block; position: fixed; inset: 0; z-index: 9; overflow-y: auto;
                background: ${color(look.background)}; padding: ${px(barH / 2f)} ${px(screenW / 40f)};
                font-family: theme, sans-serif; font-size: ${px(screenH * look.fontSize)}; color: ${color(look.text)};
                line-height: normal; }
            /* A row of its own, as the menu's title: its size times the list's line spacing. */
            #contents.open::before { content: "Contents"; display: block; font-size: 1.4em; padding-left: 0.5em;
                line-height: ${"%.3f".format(Locale.ROOT, look.lineSpacing)}; color: ${color(look.bodyText)}; }
            #contents ol { list-style: none; padding: 0; margin: 0; }
            /* The top padding moves the line down by half of it, centring the capitals in the pill as the menu does. */
            #contents a { display: block; width: fit-content; box-sizing: border-box; height: ${px(pillPx)};
                margin: ${px(rowPx / 14)} 0; padding: ${em(2 * shift)} 0.7em 0; line-height: calc(${px(pillPx)} - ${em(2 * shift)});
                white-space: nowrap; color: ${color(look.text)}; text-decoration: none;
                border-radius: min(${px(look.selectedCornerRadius * screenW)}, ${px(pillPx / 2)}); }
            #contents .on a { $selected color: ${color(look.selectedText)}; }
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
        // `003 Tail Cave.png` → Tail Cave. Maps saved before they had names show their file name, `Shantae-DribbleFountain`.
        val names = entries.map { it.substringAfterLast('/').substringBeforeLast('.').replace(Regex("^\\d+ "), "") }
        return Doc(entries.size, { n ->
            val bytes = zip(uri) { z -> generateSequence { z.nextEntry }.first { it.name == entries[n] }; z.readBytes() }
            RegionPage(BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)!!)
        }, names) {}
    }

    fun close() {
        key?.let { prefs.setPosition(it, position(), view()) }
        key = null
        position = { 0 }
        view = { null }
        back = { false }
        release()
        release = {}
        root?.let(wm::removeView)
        root = null
    }

    private fun <T> zip(uri: Uri, read: (ZipInputStream) -> T): T = ZipInputStream(resolver.openInputStream(uri)!!).use(read)

    private companion object {
        /** [offset] into a scroll [range] as parts per 10,000, so the position survives a zoom or font change. */
        fun fraction(offset: Int, range: Int) = if (range > 0) (offset * 10_000L / range).toInt().coerceIn(0, 10_000) else 0

        /** Images larger than this (about 0.2 s to decode whole on the Nova) show their top quarter first. */
        const val PREVIEW_PIXELS = 16_000_000L

        val HTML_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_ENTER,
        )

        /** A made-up origin, so the guide's relative `guide.css` and `font.otf` resolve to the viewer. */
        const val HOST = "guide.local"

        /**
         * The saved guide's Content Security Policy: none of the page's own scripts run, and nothing loads but its
         * embedded pictures and the viewer's CSS and fonts. The viewer's JavaScript isn't the page's and still runs.
         */
        private const val GUIDE_CSP =
            "default-src 'none'; img-src data:; style-src 'self' 'unsafe-inline'; font-src 'self'; base-uri 'none'; form-action 'none'"

        /** The bar's text for a `"n m"` answer from the page: section n of m, or the guide's [name] without sections. */
        fun sectionText(answer: String, name: String): String {
            val (n, of) = answer.trim('"').split(' ').map { it.toIntOrNull() ?: 0 } + listOf(0, 0)
            return if (of > 0) "SECTION ${n.coerceAtLeast(1)} OF $of" else name
        }

        /**
         * JavaScript: `s` the guide's sections, `n` the one at the top of the view (1-based, 0 above the first): the
         * last to start in the top quarter, or the one a jump went to while the view is still where the jump left it.
         * A short section's next one also starts in the top quarter, and near the end the page can't scroll a
         * section up to the top, so the view alone would skip it.
         */
        const val SECTIONS = "var s=document.querySelectorAll('section'),n=0;" +
            "for(var i=0;i<s.length;i++)if(s[i].getBoundingClientRect().top<innerHeight/4)n=i+1;" +
            "if(window.esdeAt&&Math.abs(esdeAt.y-scrollY)<1)n=esdeAt.n;"

        /** A CSS selector for a saved guide's pictures: embedded as data, and not SVG, which the page viewer can't show. */
        private const val PICTURE = "img[src^=\"data:\"]:not([src^=\"data:image/svg\"])"

        /** JavaScript: `g` the guide's pictures, `p` the first one in view or -1. */
        const val PICTURES = "var g=document.querySelectorAll('$PICTURE'),p=-1;for(var i=0;i<g.length;i++){" +
            "var r=g[i].getBoundingClientRect();if(r.width>0&&r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth){p=i;break}}"

        /** JavaScript that turns a tap on picture p of m into a load of `picture?n=p&of=m`, which the viewer opens instead. */
        const val PICTURE_TAPS = "document.addEventListener('click',function(e){var g=document.querySelectorAll('$PICTURE')," +
            "p=[].indexOf.call(g,e.target);if(p>=0){e.preventDefault();location.href='https://$HOST/picture?n='+p+'&of='+g.length}})"

        /** JavaScript that records section `n` as where a jump went, at this scroll position. */
        private const val JUMPED = "window.esdeAt={n:n,y:scrollY};"

        /** JavaScript that scrolls [by] sections from the current one (before the first is the top); returns `"n m"`. */
        fun section(by: Int) = "(function(){$SECTIONS var to=n+$by;if(to<1){to=0;scrollTo(0,0)}" +
            "else if(to<=s.length)s[to-1].scrollIntoView();else to=n;n=to;$JUMPED return n+' '+s.length})()"

        /**
         * JavaScript that shows the contents list with the section in view picked; returns its rows, 0 for none. The
         * list covers what's on screen (the visual viewport): WebView widens the layout viewport to fit a slightly
         * wider page, and a list as tall as that ran below the screen, so its last rows could only show by moving
         * the visible area, which then hid its title.
         */
        const val CONTENTS_OPEN = "(function(){$SECTIONS var c=document.getElementById('contents'),l=c?c.querySelectorAll('li'):[];" +
            "if(!l.length)return 0;for(var i=0;i<l.length;i++)l[i].classList.toggle('on',i==Math.max(n,1)-1);" +
            "var v=window.visualViewport;if(v){c.style.top=v.offsetTop+'px';c.style.bottom='auto';c.style.boxSizing='border-box';" +
            "c.style.height=v.height+'px'}" +
            "c.classList.add('open');c.querySelector('.on').scrollIntoView({block:'nearest'});return l.length})()"

        /** JavaScript that moves the contents list's pick to the row before or after it, stopping at the ends. */
        private fun contentsStep(sibling: String) = "(function(){var o=document.querySelector('#contents .on'),e=o&&o.$sibling;" +
            "if(e){o.classList.remove('on');e.classList.add('on');e.scrollIntoView({block:'nearest'})}})()"
        val CONTENTS_UP = contentsStep("previousElementSibling")
        val CONTENTS_DOWN = contentsStep("nextElementSibling")

        /**
         * JavaScript that moves the contents list's pick [by] rows, stopping at the ends, and scrolls the list to put
         * the pick in the middle of the screen, as ES-DE's list does on a bumper, or as far as the list goes (the
         * title in view on the first row). In [by], `p` is a screenful: the rows that fit below the title.
         */
        private fun contentsJump(by: String) = "(function(){var c=document.getElementById('contents'),l=c.querySelectorAll('li')," +
            "o=c.querySelector('.on'),i=[].indexOf.call(l,o);if(i<0)return;var h=l.length>1?l[1].offsetTop-l[0].offsetTop:1," +
            "p=Math.max(1,Math.floor((c.clientHeight-l[0].offsetTop)/h)),to=Math.max(0,Math.min(l.length-1,i+($by)));" +
            "o.classList.remove('on');l[to].classList.add('on');c.scrollTop=l[to].offsetTop+(l[to].offsetHeight-c.clientHeight)/2})()"
        val CONTENTS_PAGE_UP = contentsJump("-p")
        val CONTENTS_PAGE_DOWN = contentsJump("p")
        val CONTENTS_FIRST = contentsJump("-l.length")
        val CONTENTS_LAST = contentsJump("l.length")

        /** JavaScript that hides the contents list and scrolls to the picked section; returns `"n m"`. */
        const val CONTENTS_PICK = "(function(){var c=document.getElementById('contents'),a=c.querySelector('.on a')," +
            "t=a&&document.querySelector(a.getAttribute('href'));c.classList.remove('open');if(t)t.scrollIntoView();" +
            "$SECTIONS var k=[].indexOf.call(s,t)+1;if(k){n=k;$JUMPED}return n+' '+s.length})()"
        const val CONTENTS_CLOSE = "document.getElementById('contents').classList.remove('open')"
    }
}
