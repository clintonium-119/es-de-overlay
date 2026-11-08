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
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.clintonium.es_de_overlay.core.GameRef
import com.clintonium.es_de_overlay.core.GuideImage
import com.clintonium.es_de_overlay.core.GuideSection
import com.clintonium.es_de_overlay.core.LINEAR
import com.clintonium.es_de_overlay.core.Look
import com.clintonium.es_de_overlay.core.escapeHtml
import com.clintonium.es_de_overlay.core.guideFileName
import com.clintonium.es_de_overlay.core.isBotCheck
import com.clintonium.es_de_overlay.core.isGuidePage
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
 * game's guides folder. Built on [open] and destroyed on [close]; it runs in the viewer's process, which ends
 * when it closes.
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
     * [onClose] runs when Back runs out of history, Close is tapped or the page's renderer dies.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun open(url: String, game: GameRef, look: Look, onSaved: (String) -> Unit, onClose: () -> Unit) {
        close()
        this.look = look
        val done = {
            close()
            onClose()
        }
        // For inspecting the sites' markup from a desktop Chrome.
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        val web = WebView(context)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
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
                picked != null -> startSave(game, title, save, picked(), onSaved)
                else -> web.evaluateJavascript("$script;esdeGuide.describe()") { json ->
                    val d = runCatching { JSONObject(json) }.getOrNull()
                    val list = d?.optJSONArray("pages")
                    val pages = list?.let(::pageList).orEmpty()
                    val unticked = list?.let { l -> (0 until l.length()).filter { l.getJSONObject(it).optBoolean("skip") }.toSet() }.orEmpty()
                    val next = d?.optString("next")?.takeIf { d.optBoolean("generic") && !d.isNull("next") }
                    if (saving || picking != null || this.web == null) return@evaluateJavascript
                    if (d?.optBoolean("generic") == true && (pages.size > 1 || next != null)) {
                        pick(view, web, title, pages, unticked, next != null)
                    } else {
                        startSave(game, title, save, null, onSaved)
                    }
                }
            }
        }.apply { visibility = View.GONE }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                finished.offer(url)
                if (saving || picking != null) return
                save.visibility = View.GONE
                if (isGuidePage(url)) {
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
            addView(button("Close", done), fill)
        }
        view = LinearLayout(context).apply {
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
        if (!isGuidePage(string(eval("location.href")))) return null
        eval(script)
        val d = JSONObject(eval("esdeGuide.describe($force)"))
        val kind = d.getString("kind")
        if (kind == "none") return null
        val list = d.getJSONArray("pages")
        val pages = pageList(list)
        val generic = d.optBoolean("generic")
        val next = if (d.isNull("next")) null else d.optString("next")
        val title = d.getString("title")
        val author = d.getString("author")
        if (kind == "text") return PageGuide(kind, title, author, pages, read(eval("esdeGuide.text()").toInt()), null, 0, generic, next)
        val links = picked?.let { p -> JSONArray(p.map { (url, name) -> JSONObject().put("url", url).put("title", name) }) } ?: list
        val html = JSONObject(eval("esdeGuide.html($links)"))
        return PageGuide(kind, title, author, pages, null, read(html.getInt("length")), html.getInt("images"), generic, next)
    }

    private fun pageList(list: JSONArray) = (0 until list.length()).map { list.getJSONObject(it).run { getString("url") to getString("title") } }

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

    /** Worker thread: the [length] characters the page keeps for `part()`. */
    private fun read(length: Int) = buildString(length) {
        for (i in 0 until (length + CHUNK - 1) / CHUNK) append(string(eval("esdeGuide.part($i)")))
    }

    fun close() {
        cancelled = true
        picking = null
        web?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        web = null
        root?.let(wm::removeView)
        root = null
    }
}
