package com.clintonium.es_de_overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import com.clintonium.es_de_overlay.core.GuideImage
import com.clintonium.es_de_overlay.core.isGuidePage
import java.io.IOException
import java.net.URLConnection
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/** Characters per piece when a long result comes back from the page; `guide-extract.js` uses the same. */
private const val CHUNK = 256 * 1024

/**
 * The guide on the browser's page: [text] for a text guide, else [html], whose image sources are `img:0` to
 * `img:<imageCount - 1>`. [pages] are the guide's pages in order as URL and title; just this page if it has one.
 */
class PageGuide(
    val kind: String,
    val title: String,
    val author: String,
    val pages: List<Pair<String, String>>,
    val text: String?,
    val html: String?,
    val imageCount: Int,
)

/**
 * A touch browser over the game, for guide sites only a browser gets into: a WebView with its own User-Agent
 * and cookie settings, above a bar like the viewer's with Back, the page title and Close. Built on [open] and
 * destroyed on [close]; it runs in the viewer's process, which ends when it closes.
 */
class BrowserOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val script by lazy { context.assets.open("guide-extract.js").bufferedReader().use { it.readText() } }
    /** The viewer's bar text size; the bar itself is 48 dp, tall enough to tap. */
    private val barH = wm.maximumWindowMetrics.bounds.height() / 24
    private val tapH = (48 * context.resources.displayMetrics.density).toInt()
    private var root: View? = null
    private var web: WebView? = null

    /** Opens [url]; [onClose] runs when Back runs out of history, Close is tapped or the page's renderer dies. */
    @SuppressLint("SetJavaScriptEnabled")
    fun open(url: String, onClose: () -> Unit) {
        close()
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
        // Temporary, until Save guide: tapping the title logs what a save would read from this page.
        if (BuildConfig.DEBUG) {
            title.setOnClickListener { thread(name = "guide-extract") { runCatching(::logGuide).onFailure { Log.w(TAG, "browser: reading the guide failed", it) } } }
        }
        web.webViewClient = object : WebViewClient() {
            // Without this a crashed renderer takes the process down with it.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                Log.w(TAG, "browser: renderer gone")
                done()
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, text: String?) {
                title.text = text
            }
        }
        fun button(label: String, onTap: () -> Unit) = barText(context, barH, Gravity.CENTER).apply {
            text = label
            setPadding(barH, 0, barH, 0)
            setOnClickListener { onTap() }
        }
        val bar = LinearLayout(context).apply {
            setBackgroundColor(VIEWER_BAR)
            val fill = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(button("Back") { if (web.canGoBack()) web.goBack() else done() }, fill)
            addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(button("Close", done), fill)
        }
        val view = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tapH))
        }
        web.loadUrl(url)
        wm.addFullScreen(view, PixelFormat.OPAQUE, WindowInsets.Type.ime())
        root = view
        this.web = web
    }

    /** Worker thread: the guide on the loaded page, or null when it isn't a guide page. */
    fun extract(): PageGuide? {
        if (!isGuidePage(string(eval("location.href")))) return null
        eval(script)
        val d = JSONObject(eval("esdeGuide.describe()"))
        val kind = d.getString("kind")
        if (kind == "none") return null
        val list = d.getJSONArray("pages")
        val pages = (0 until list.length()).map { list.getJSONObject(it).run { getString("url") to getString("title") } }
        if (kind == "text") return PageGuide(kind, d.getString("title"), d.getString("author"), pages, read(eval("esdeGuide.text()").toInt()), null, 0)
        val html = JSONObject(eval("esdeGuide.html($list)"))
        return PageGuide(kind, d.getString("title"), d.getString("author"), pages, null, read(html.getInt("length")), html.getInt("images"))
    }

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

    private fun logGuide() {
        val g = extract() ?: return Unit.also { Log.i(TAG, "browser: no guide on this page") }
        Log.i(TAG, "browser: ${g.kind} guide \"${g.title}\" by \"${g.author}\", ${g.pages.size} pages")
        g.pages.forEachIndexed { i, (url, title) -> Log.i(TAG, "browser: page ${i + 1}: $title $url") }
        g.text?.let { Log.i(TAG, "browser: text ${it.length} chars, first lines:\n" + it.lineSequence().take(12).joinToString("\n")) }
        g.html?.let { html ->
            Log.i(TAG, "browser: html ${html.length} chars, ${g.imageCount} images; <script ${"<script" in html}, <iframe ${"<iframe" in html}, " +
                "on*= ${Regex("\\son\\w+=").containsMatchIn(html)}, style= ${"style=" in html}")
            Log.i(TAG, "browser: html starts: " + html.take(600))
        }
        for (n in 0 until g.imageCount) image(n)
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
