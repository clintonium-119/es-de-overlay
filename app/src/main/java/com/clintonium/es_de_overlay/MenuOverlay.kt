package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

// The minui-menu-es-de-custom theme's dark-bold colors (colors.xml) and small font size (theme.xml).
const val BACKGROUND = 0xFF000000.toInt()
const val PILL = 0xFFF7FBF7.toInt()
const val PILL_TEXT = 0xFF000000.toInt()
const val TEXT = 0xFFF7FBF7.toInt()
const val HELP_BG = 0xFF202420.toInt()
const val HELP_ICON = 0xFFA1A2A1.toInt()
const val HELP_TEXT = 0xFFF7FBF7.toInt()
/** List font size and line spacing as fractions of the screen height (`listFontSize`, `listLineSpacing`). */
const val LIST_FONT = 0.041666666666667f
const val LIST_SPACING = 1.75f
// ES-DE's own manual-viewer bar, which the theme doesn't style.
const val VIEWER_BAR = 0xFF222222.toInt()
const val VIEWER_TEXT = 0xFFAAAAAA.toInt()

private const val FONT = "themes/minui-menu-es-de-custom/_inc/color-schemes/_default/VAG Rounded Std Bold.otf"

/** The theme's VAG Rounded font from the ES-DE folder (it isn't ours to bundle), else sans-serif. */
fun themeFont(context: Context, prefs: Prefs): Typeface = runCatching {
    context.contentResolver.openFileDescriptor(treeChild(prefs.esdeTree!!, FONT), "r")!!.use {
        Typeface.Builder(it.fileDescriptor).setFallback("sans-serif").build()
    }
}.getOrDefault(Typeface.SANS_SERIF)

/** Adds [view] as a focusable full-screen window over the game, with the system bars hidden, and focuses it. */
fun WindowManager.addFullScreen(view: View, format: Int) {
    addView(
        view,
        WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            format,
        ).apply {
            // Cover the bars' area as well, instead of leaving the game showing there.
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        },
    )
    view.windowInsetsController?.run {
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsets.Type.systemBars())
    }
    view.requestFocus()
}

/** A rounded help pill: `A OPEN  B BACK`, button letters in the icon color. */
fun helpPill(context: Context, font: Typeface, textPx: Float, vararg entries: Pair<String, String>) = TextView(context).apply {
    text = SpannableStringBuilder().apply {
        entries.forEachIndexed { i, (button, label) ->
            if (i > 0) append("   ")
            append(button, ForegroundColorSpan(HELP_ICON), 0)
            append(" ${label.uppercase()}")
        }
    }
    typeface = font
    setTextColor(HELP_TEXT)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.65f)
    val pad = (textPx * 0.5f).toInt()
    setPadding(pad * 2, pad, pad * 2, pad)
    background = GradientDrawable().apply { setColor(HELP_BG); cornerRadius = textPx }
}

/**
 * The in-game menu: a focusable full-screen window over the game, a themed list moved with the D-pad,
 * A to pick and B to close. Built on [show] and dropped on [close], so nothing is held between opens.
 * Also owns the invisible window kept up during a capture.
 */
class MenuOverlay(private val context: Context, private val prefs: Prefs) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val screenW = wm.maximumWindowMetrics.bounds.width()
    private val screenH = wm.maximumWindowMetrics.bounds.height()
    private var root: View? = null
    private var captureView: View? = null
    private val captureParams = WindowManager.LayoutParams(
        1, 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { alpha = 0f }

    val isOpen get() = root != null

    fun show(items: List<String>, onPick: (Int) -> Unit, onClose: () -> Unit) {
        close()
        val font = themeFont(context, prefs)
        val textPx = screenH * LIST_FONT
        val rowPx = (textPx * LIST_SPACING).toInt()
        // Measured from ES-DE's gamelist with this theme: the pill fills 6/7 of a row, text inset 0.7 em.
        val pillInset = rowPx / 14
        val textInset = (textPx * 0.7f).toInt()
        val rows = items.map { label ->
            TextView(context).apply {
                text = label
                typeface = font
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, rowPx)
            }
        }
        var selected = 0
        fun select(i: Int) {
            selected = i
            rows.forEachIndexed { n, row ->
                row.setTextColor(if (n == i) PILL_TEXT else TEXT)
                row.background = if (n == i) {
                    InsetDrawable(GradientDrawable().apply { setColor(PILL); cornerRadius = rowPx / 2f }, 0, pillInset, 0, pillInset)
                } else {
                    null
                }
                // A background with padding replaces the view's own, so set it back.
                row.setPadding(textInset, 0, textInset, 0)
            }
            rows.getOrNull(i)?.let { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height)) }
        }
        select(0)
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            rows.forEach(::addView)
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(BACKGROUND)
            // ES-DE's gamelist margins: 5% of the width at the sides, 7% of the height above, 6% below.
            setPadding((screenW * 0.05f).toInt(), (screenH * 0.07f).toInt(), (screenW * 0.05f).toInt(), (screenH * 0.06f).toInt())
            addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(list) })
            addView(
                helpPill(context, font, textPx, "A" to "Open", "B" to "Back"),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END),
            )
            // Keep focus on the root so the ScrollView doesn't take the D-pad.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, code, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener code in KEYS
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> select((selected - 1).coerceAtLeast(0))
                    KeyEvent.KEYCODE_DPAD_DOWN -> select((selected + 1).coerceAtMost(rows.lastIndex))
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                        if (rows.isNotEmpty()) onPick(selected)
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { close(); onClose() }
                    else -> return@setOnKeyListener false
                }
                true
            }
        }
        wm.addFullScreen(view, PixelFormat.TRANSLUCENT)
        root = view
    }

    fun close() {
        root?.let(wm::removeView)
        root = null
    }

    /** Adds the invisible window that [nudge] relayouts, so a static screen still yields capture frames. */
    fun beginCapture() {
        if (captureView != null) return
        captureView = View(context).also { wm.addView(it, captureParams) }
    }

    /** Forces a window layout pass without any visible change (only the window title differs). */
    fun nudge() {
        val view = captureView ?: return
        captureParams.title = "capture ${SystemClock.uptimeMillis()}"
        wm.updateViewLayout(view, captureParams)
    }

    fun endCapture() {
        captureView?.let(wm::removeView)
        captureView = null
    }

    private companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK,
        )
    }
}
