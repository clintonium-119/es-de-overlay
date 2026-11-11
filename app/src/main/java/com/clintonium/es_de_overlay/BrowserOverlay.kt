package com.clintonium.es_de_overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.GuideImage
import com.clintonium.es_de_overlay.core.GuideSite
import com.clintonium.es_de_overlay.core.GuideSection
import com.clintonium.es_de_overlay.core.LINEAR
import com.clintonium.es_de_overlay.core.Look
import com.clintonium.es_de_overlay.core.escapeHtml
import com.clintonium.es_de_overlay.core.guideAuthor
import com.clintonium.es_de_overlay.core.guideFileName
import com.clintonium.es_de_overlay.core.guidePageList
import com.clintonium.es_de_overlay.core.guideText
import com.clintonium.es_de_overlay.core.isBotCheck
import com.clintonium.es_de_overlay.core.isGuidePage
import com.clintonium.es_de_overlay.core.onSameSite
import com.clintonium.es_de_overlay.core.siteOf
import com.clintonium.es_de_overlay.core.textSections
import com.clintonium.es_de_overlay.core.writeHtmlGuide
import java.io.IOException
import java.net.URI
import java.net.URLConnection
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/** Characters per piece when a long result comes back from the page; `guide-extract.js` uses the same. */
private const val CHUNK = 256 * 1024

/** The least time between two page loads of a save, so a whole guide never arrives faster than a reader's taps. */
private const val PAGE_GAP_MS = 2_000L

/** The most pages a save follows Next links to. */
private const val MAX_NEXT_PAGES = 50

/** The most characters read back from a page at once: a 50 MB image in base64. */
private const val MAX_READ = 70 shl 20

/**
 * The guide on the browser's page: [text] for a text guide, else [html], whose image sources are `img:0` to
 * `img:<imageCount - 1>`. [pages] are the guide's pages in order as URL and title; just this page if it has one.
 * A [generic] guide is a page off the guide sites, read by Reader View, whose [next] page may follow.
 */
class PageGuide(
    val kind: String,
    val title: String,
    val author: String,
    val pages: List<Pair<String, String>>,
    val text: String?,
    val html: String?,
    val imageCount: Int,
    val generic: Boolean,
    val next: String?,
)

/** The pages the user ticked before a save, and whether to follow Next links from the last of them. */
class Picked(val pages: List<Pair<String, String>>, val followNext: Boolean)

/**
 * A touch browser over the game, for guide sites only a browser gets into and the web search: a WebView with its
 * own User-Agent and cookie settings, above a bar like the viewer's with Back, the page title, Save guide on a
 * guide page, and Close. Off the guide sites, Save guide is offered where Firefox's Reader View would be, and
 * first lists the guide's other pages it found to untick. Save guide loads each of the guide's pages in turn in
 * this same visible WebView, waits for the user on a bot check, and writes the guide as one HTML file in the
 * game's guides folder. It also plays a guide's YouTube link over the viewer, with the gamepad: A pauses, the
 * D-pad's left and right skip ten seconds, B closes. Built on [open] and destroyed on [close]; it runs in the
 * viewer's process, which ends when it closes.
 */
class BrowserOverlay(private val context: Context, private val prefs: Prefs) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    /** Mozilla's Readability, made global once per page, then `guide-extract.js`. */
    private val script by lazy {
        fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
        "if(!window.Readability)(function(){\n${asset("readability/Readability.js")}\n${asset("readability/Readability-readerable.js")}\n" +
            "window.Readability=Readability;window.isProbablyReaderable=isProbablyReaderable})();\n${asset("guide-extract.js")}"
    }
    /** The viewer's bar text size; the bar itself is 48 dp, tall enough to tap. */
    private val barH = wm.maximumWindowMetrics.bounds.height() / 24
    private val tapH = (48 * context.resources.displayMetrics.density).toInt()
    private var root: View? = null
    private var web: WebView? = null
    /** The colours of the open page's window, from [open]. */
    private var look = LINEAR
    /** Main thread: a save is running. */
    private var saving = false
    /** Main thread: while the page list is up, removes it and returns the ticked pages. */
    private var picking: (() -> Picked)? = null
    @Volatile private var cancelled = false
    /** URLs of finished page loads, which a save waits on. */
    private val finished = LinkedBlockingQueue<String>()
    /** Save thread only: when the last page load started. */
    private var lastLoad = 0L

    /**
     * Opens [url] in [look]'s colours. A saved guide of [game]'s goes to [onSaved] as its path below the ES-DE folder;
     * [onClose] runs when Back runs out of history, Close is tapped or the page's renderer dies. With no [game] the
     * page is a video from a guide: nothing to save, it plays without a tap, and the gamepad works it, its buttons
     * read and named as [gamepad] has ES-DE's.
     */
    // The guide sites and their bot checks need JavaScript; pages get no bridge into the app and no files.
    @SuppressLint("SetJavaScriptEnabled")
    fun open(
        url: String, game: GameRef?, look: Look, onSaved: (String) -> Unit, gamepad: ThemeLook.Current? = null, onClose: () -> Unit,
    ) {
        close()
        this.look = look
        val done = {
            close()
            onClose()
        }
        // For inspecting the sites' markup from a desktop Chrome.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        val web = WebView(context)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The web only, also for the URLs a page hands a save: no files, no content providers.
            allowFileAccess = false
            allowContentAccess = false
            // Search finds any site, so Android's phishing and malware checks stay on; they look pages up with Google.
            safeBrowsingEnabled = true
            // A video starts by itself, and A's pause and play count without a tap on the page.
            if (game == null) mediaPlaybackRequiresUserGesture = false
        }
        val title = barText(context, barH, Gravity.CENTER)
        fun button(label: String, onTap: () -> Unit) = barText(context, barH, Gravity.CENTER).apply {
            text = label
            setPadding(barH, 0, barH, 0)
            setOnClickListener { onTap() }
        }
        lateinit var save: TextView
        lateinit var view: LinearLayout
        save = button("Save guide") {
            val picked = picking
            when {
                saving -> cancelled = true
                picked != null -> startSave(game ?: return@button, title, save, picked(), onSaved)
                else -> web.evaluateJavascript("$script;esdeGuide.describe()") { json ->
                    val here = web.url.orEmpty()
                    val d = runCatching { JSONObject(json) }.getOrNull()
                    val list = d?.optJSONArray("pages")
                    val pages = list?.let { pageList(here, it) }.orEmpty()
                    val skipped = list?.let { l -> (0 until l.length()).mapNotNull(l::optJSONObject).filter { it.optBoolean("skip") } }
                        .orEmpty().map { it.optString("url") }.toSet()
                    val unticked = pages.indices.filter { pages[it].first in skipped }.toSet()
                    val next = d?.optString("next")?.takeIf { d.optBoolean("generic") && !d.isNull("next") && onSameSite(here, it) }
                    if (saving || picking != null || this.web == null) return@evaluateJavascript
                    if (d?.optBoolean("generic") == true && (pages.size > 1 || next != null)) {
                        pick(view, web, title, pages, unticked, next != null)
                    } else {
                        startSave(game ?: return@evaluateJavascript, title, save, null, onSaved)
                    }
                }
            }
        }.apply { visibility = View.GONE }
        // GameFAQs shows a visitor without its gf_dvi cookie, as on a first visit, its blank search form and sets the
        // cookie; the same search then lists its results. So a first visit loads its page again once the cookie is set.
        val cookies = CookieManager.getInstance()
        fun gameFaqsCookie(at: String) = cookies.getCookie(at).orEmpty().contains("gf_dvi=")
        var firstVisit = siteOf(url) == GuideSite.GAMEFAQS && !gameFaqsCookie(url)
        web.webViewClient = object : WebViewClient() {
            // Web pages only: intent:, file:, content: and javascript: links do nothing.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                request.url.scheme !in setOf("https", "about")

            override fun onPageFinished(view: WebView, url: String) {
                finished.offer(url)
                if (firstVisit && siteOf(url) == GuideSite.GAMEFAQS && gameFaqsCookie(url)) {
                    Log.i(TAG, "browser: first GameFAQs visit, loading $url again")
                    firstVisit = false
                    view.reload()
                    return
                }
                if (saving || picking != null) return
                save.visibility = View.GONE
                if (game != null && isGuidePage(url)) {
                    view.evaluateJavascript("$script;esdeGuide.describe().kind") { kind ->
                        if (!saving && kind.trim('"') in setOf("text", "html")) save.visibility = View.VISIBLE
                    }
                }
            }

            // Without this a crashed renderer takes the process down with it.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "browser: renderer gone")
                done()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, text: String?) {
                // While saving, the title shows progress instead.
                if (!saving) title.text = text
            }
        }
        val bar = LinearLayout(context).apply {
            setBackgroundColor(VIEWER_BAR)
            val fill = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(button("Back") {
                when {
                    saving -> Unit
                    picking != null -> picking?.invoke()?.also { title.text = web.title }
                    web.canGoBack() -> web.goBack()
                    else -> done()
                }
            }, fill)
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(save, fill)
            if (gamepad != null) {
                addView(barText(context, barH, Gravity.CENTER).apply {
                    text = "${gamepad.label("A")} PAUSE   ✚ SKIP   ${gamepad.label("B")} CLOSE"
                    setPadding(barH, 0, barH, 0)
                }, fill)
            }
            addView(button("Close", done), fill)
        }
        view = object : LinearLayout(context) {
            // B's press seen here, so the release of a press that began in the window below doesn't close this one.
            var closing = false

            // A video's keys, before the page gets them.
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                val js = if (gamepad == null) null else when (gamepad.button(event.keyCode, event.device?.name)) {
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> ""
                    // YouTube starts a video muted when the browser won't let it start with sound, under a button of
                    // its own to unmute it (`.ytp-unmute`, seen 2026-11-11); the first A is that button.
                    KeyEvent.KEYCODE_BUTTON_A -> "if(v.muted){var u=document.querySelector('.ytp-unmute');u?u.click():v.muted=false}" +
                        "else v.paused?v.play():v.pause()"
                    KeyEvent.KEYCODE_DPAD_LEFT -> "v.currentTime-=10"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "v.currentTime+=10"
                    else -> null
                }
                if (js == null) return super.dispatchKeyEvent(event)
                val down = event.action == KeyEvent.ACTION_DOWN
                when {
                    js.isEmpty() -> if (down) closing = true else if (closing) done()
                    // Skipping repeats while held; a held A doesn't flicker.
                    down && (event.repeatCount == 0 || event.keyCode != KeyEvent.KEYCODE_BUTTON_A) ->
                        web.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){$js}})()", null)
                }
                return true
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(look.background)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tapH))
        }
        web.loadUrl(url)
        wm.addFullScreen(view, PixelFormat.OPAQUE, WindowInsets.Type.ime())
        root = view
        this.web = web
    }

    /**
     * Main thread: lists [pages] in place of [web], each with a box, ticked but for the [unticked] ones (the site's
     * own pages, by their look), and a box for the pages after them by Next links when [next]. Save guide takes the
     * ticked ones, Back the page again.
     */
    private fun pick(
        view: LinearLayout, web: WebView, title: TextView, pages: List<Pair<String, String>>, unticked: Set<Int>, next: Boolean,
    ) {
        fun box(label: String) = CheckBox(context).apply {
            text = label
            isChecked = true
            setTextColor(look.text)
            buttonTintList = ColorStateList.valueOf(look.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, barH * 0.7f)
            isSingleLine = true
            // The view's own minimum: single-line text sets the text's minimum to one line.
            minimumHeight = tapH
        }
        val boxes = pages.mapIndexed { i, (url, name) -> box(name.ifEmpty { url }).apply { isChecked = i !in unticked } }
        val follow = if (next) box("The pages after these, by their Next links") else null
        val list = ScrollView(context).apply {
            setBackgroundColor(look.background)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(barH, barH / 2, barH, barH / 2)
                (boxes + listOfNotNull(follow)).forEach(::addView)
            })
        }
        web.visibility = View.GONE
        view.addView(list, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        title.text = "Untick the pages to leave out"
        picking = {
            picking = null
            view.removeView(list)
            web.visibility = View.VISIBLE
            Picked(pages.filterIndexed { i, _ -> boxes[i].isChecked }, follow?.isChecked == true)
        }
    }

    /**
     * Main thread: saves the guide on this page, or the [picked] pages of it, on its own thread, with progress in
     * [status]; [button] cancels it.
     */
    private fun startSave(game: GameRef, status: TextView, button: TextView, picked: Picked?, onSaved: (String) -> Unit) {
        saving = true
        cancelled = false
        button.text = "Cancel"
        thread(name = "guide-save") {
            val result = runCatching { saveGuide(game, picked) { text -> main.post { status.text = text } } }
                .onFailure { if (it !is CancellationException) Log.w(TAG, "browser: saving the guide failed", it) }
            main.post {
                saving = false
                if (web == null) return@post
                button.text = "Save guide"
                result.onSuccess(onSaved).onFailure { status.text = if (it is CancellationException) "Save cancelled" else "Couldn't save the guide" }
            }
        }
    }

    /**
     * Save thread: [game]'s guide from the loaded page, written into its guides folder only once all of it has
     * arrived. Each page of an HTML guide, or each [picked] one and those its Next links lead to, loads in turn in
     * this browser. Returns the path written.
     */
    private fun saveGuide(game: GameRef, picked: Picked?, progress: (String) -> Unit): String {
        val here = string(eval("location.href")).substringBefore('#')
        val guide = extract(picked?.pages) ?: throw IOException("no guide on $here")
        // A page off the guide sites is credited to its host.
        val site = siteOf(here)?.label ?: URI(here).host.removePrefix("www.")
        val sections = mutableListOf<GuideSection>()
        // ponytail: holds every image in memory until the file is written; spool to files if guides outgrow the heap.
        val images = mutableMapOf<String, GuideImage>()
        if (guide.text != null) {
            sections += textSections(guide.title, guide.text)
        } else {
            val pages = (picked?.pages ?: guide.pages).toMutableList()
            // Pages after the picked ones, found by Next links as each loads; their titles come from the pages.
            val following = picked?.followNext == true
            val listed = pages.size
            var i = 0
            while (i < pages.size) {
                val (url, title) = pages[i]
                val of = if (following) "Saving page ${i + 1}" else "Saving page ${i + 1} of ${pages.size}"
                progress(of)
                val page = if (i == 0 && url == here) {
                    guide
                } else {
                    load(url, progress)
                    progress(of)
                    // A picked page is read even where Reader View wouldn't offer it; a Next link that leads off the guide
                    // ends the guide there.
                    extract(picked?.pages, force = i < listed) ?: if (i >= listed) break else throw IOException("no guide on $url")
                }
                // Each page numbers its own images from 0.
                sections += GuideSection(title.ifEmpty { page.title }, page.html!!.replace("src=\"img:", "src=\"img:$i-"))
                for (n in 0 until page.imageCount) {
                    if (cancelled) throw CancellationException()
                    progress("$of, image ${n + 1} of ${page.imageCount}")
                    image(n)?.let { images["img:$i-$n"] = it }
                }
                val next = page.next
                if (following && i == pages.lastIndex && next != null && pages.none { it.first == next } && pages.size < listed + MAX_NEXT_PAGES) {
                    pages += next to ""
                }
                i++
            }
        }
        if (cancelled) throw CancellationException()
        progress("Writing the guide")
        val source = guide.pages.first().first
        val by = if (guide.author.isEmpty()) "" else " by ${escapeHtml(guide.author)}"
        val credit = "From <a href=\"${escapeHtml(source)}\">${escapeHtml(site)}</a> (${escapeHtml(source)})$by. " +
            "© the author; saved for personal use."
        val name = guideFileName(site, if (guide.author.isEmpty()) guide.title else "${guide.title} by ${guide.author}")
        val dir = isDirectory(context, prefs.romsTree!!, "${game.system}/${game.romRelativePath}")
        return saveVia(MediaWriter(context, prefs.esdeTree!!), context.cacheDir, game, dir, name, "text/html", { cancelled }) { file ->
            file.bufferedWriter().use { writeHtmlGuide(it, guide.title, sections, images, credit) }
        }.also { Log.i(TAG, "browser: saved $it: ${sections.size} sections, ${images.size} images") }
    }

    /**
     * Save thread: loads [url] in this browser, no sooner than [PAGE_GAP_MS] after the last load, and waits until
     * it has finished. On a bot check it says so and waits, for as long as it takes, for the user to clear it.
     */
    private fun load(url: String, progress: (String) -> Unit) {
        val wait = lastLoad + PAGE_GAP_MS - SystemClock.uptimeMillis()
        if (wait > 0) Thread.sleep(wait)
        finished.clear()
        lastLoad = SystemClock.uptimeMillis()
        main.post {
            val w = web ?: return@post
            Log.i(TAG, "browser: loading $url (shown: ${w.isShown})")
            w.loadUrl(url)
        }
        var checking = false
        while (true) {
            if (cancelled) throw CancellationException()
            if (finished.poll(1, TimeUnit.SECONDS) == null) {
                if (!checking && SystemClock.uptimeMillis() - lastLoad > 60_000) throw IOException("$url didn't load")
                continue
            }
            if (!isBotCheck(string(eval("document.title")))) return
            if (!checking) Log.i(TAG, "browser: bot check on $url")
            checking = true
            progress("Clear the check to continue")
        }
    }

    /**
     * Worker thread: the guide on the loaded page, or null when it isn't a guide page. Its links to the guide's pages,
     * or to the [picked] ones, point at their sections. With [force], a page off the guide sites is read even where
     * Reader View wouldn't offer it.
     */
    fun extract(picked: List<Pair<String, String>>? = null, force: Boolean = false): PageGuide? {
        val here = string(eval("location.href"))
        if (!isGuidePage(here)) return null
        eval(script)
        // What the page reports is data from the page, which may not be the script's: bounded before use.
        val d = JSONObject(eval("esdeGuide.describe($force)"))
        val kind = d.getString("kind")
        if (kind == "none") return null
        val pages = pageList(here, d.getJSONArray("pages"))
        val generic = d.optBoolean("generic")
        val next = if (d.isNull("next")) null else d.optString("next").takeIf { onSameSite(here, it) }
        val title = guideText(d.optString("title"))
        val author = guideAuthor(d.optString("author"))
        if (kind == "text") return PageGuide(kind, title, author, pages, read(eval("esdeGuide.text()").toInt()), null, 0, generic, next)
        val links = JSONArray((picked ?: pages).map { (url, name) -> JSONObject().put("url", url).put("title", name) })
        val html = JSONObject(eval("esdeGuide.html($links)"))
        return PageGuide(kind, title, author, pages, null, read(html.getInt("length")), html.getInt("images"), generic, next)
    }

    /** The pages a page at [here] listed in [list], bounded by [guidePageList]. */
    private fun pageList(here: String, list: JSONArray) =
        guidePageList(here, (0 until list.length()).mapNotNull(list::optJSONObject).map { it.optString("url") to it.optString("title") })

    /**
     * Worker thread: image [n] of the HTML [extract] read last, from inside the page, else from one plain request
     * with the app's User-Agent and no cookies. Null when both fail, which leaves the image its alt text.
     */
    fun image(n: Int): GuideImage? {
        val url = string(eval("esdeGuide.image($n)"))
        val until = SystemClock.uptimeMillis() + 30_000
        var result: JSONObject? = null
        while (result == null && SystemClock.uptimeMillis() < until) {
            val r = eval("esdeGuide.imageResult($n)")
            if (r == "null") Thread.sleep(100) else result = JSONObject(r)
        }
        val type = URLConnection.guessContentTypeFromName(url.substringBefore('?')) ?: "image/png"
        if (result != null && !result.has("error")) {
            Log.i(TAG, "browser: image ${n + 1} from the page: $url")
            return GuideImage(result.getString("mime").takeIf { it.startsWith("image/") } ?: type, Base64.getDecoder().decode(read(result.getInt("length"))))
        }
        Log.i(TAG, "browser: image ${n + 1} unreadable in the page (${result?.optString("error") ?: "timed out"}): $url")
        return runCatching { GuideImage(type, get(url)) }
            .onSuccess { Log.i(TAG, "browser: image ${n + 1} from a plain request: $url") }
            .onFailure { Log.i(TAG, "browser: image ${n + 1} left as alt text: ${it.message}") }
            .getOrNull()
    }

    /** Worker thread: runs [js] in the page and returns its result as JSON; "null" once the browser has closed. */
    private fun eval(js: String): String {
        val result = ArrayBlockingQueue<String>(1)
        main.post { web?.evaluateJavascript(js) { result.offer(it) } ?: result.offer("null") }
        return result.poll(30, TimeUnit.SECONDS) ?: throw IOException("the page didn't answer")
    }

    private fun string(json: String) = JSONArray("[$json]").getString(0)

    /** Worker thread: the [length] characters the page keeps for `part()`; the page says how many. */
    private fun read(length: Int): String {
        if (length !in 0..MAX_READ) throw IOException("the page offered $length characters")
        return buildString(length) {
            for (i in 0 until (length + CHUNK - 1) / CHUNK) append(string(eval("esdeGuide.part($i)")))
        }
    }

    fun close() {
        cancelled = true
        picking = null
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
            // The process ends when the browser closes, and WebView writes cookies to disk only now and then: without
            // this, the sites' cookies from the last seconds (sign-ins, bot checks passed, GameFAQs' visitor cookie)
            // would be lost.
            CookieManager.getInstance().flush()
        }
        web = null
        root?.let(wm::removeView)
        root = null
    }
}
