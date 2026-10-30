package com.clintonium.es_de_overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
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

/**
 * A touch browser over the game, for guide sites only a browser gets into: a WebView with its own User-Agent
 * and cookie settings, above a bar like the viewer's with Back, the page title and Close. Built on [open] and
 * destroyed on [close]; it runs in the viewer's process, which ends when it closes.
 */
class BrowserOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
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
