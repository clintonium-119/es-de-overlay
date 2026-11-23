package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.PathParser
import kotlin.math.ceil
import com.clintonium.es_de_overlay.core.HelpIcon
import com.clintonium.es_de_overlay.core.Look

/**
 * How far below the middle of its line [font]'s text goes, in ems, so a capital S sits centred the way ES-DE
 * draws list rows. Centring the line itself leaves a font with a deep descent, like minUI's VAG Rounded, with its
 * capitals high in the pill.
 */
fun capCentring(font: Typeface): Float {
    val paint = Paint().apply { typeface = font; textSize = 1000f }
    val s = Rect().also { paint.getTextBounds("S", 0, 1, it) }
    return paint.fontMetrics.run { ascent + descent - s.top } / 2000f
}

/**
 * Adds [view] as a focusable full-screen window over the game, with the system bars hidden, and focuses it.
 * The window shrinks to stay clear of [fitInsets]: `WindowInsets.Type.ime()` keeps a text field above the
 * keyboard, which is what a resize soft-input mode would do if setting the insets didn't turn that off.
 * With [hidden], the window is invisible and takes no focus, so the game under it carries on as if it weren't there.
 */
fun WindowManager.addFullScreen(view: View, format: Int, fitInsets: Int = 0, hidden: Boolean = false) {
    addView(
        view,
        WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or if (hidden) WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE else 0,
            format,
        ).apply {
            if (hidden) alpha = 0f
            // Cover the bars' area as well, instead of leaving the game showing there.
            fitInsetsTypes = fitInsets
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        },
    )
    view.windowInsetsController?.run {
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsets.Type.systemBars())
    }
    view.requestFocus()
}

/**
 * ES-DE's help sizes in [theme]'s look on a screen [screenH] tall: the icon and the text size in pixels. Below 1 the
 * theme's icon scale shrinks the text, above 1 the icons, as ES-DE's help does.
 */
private fun helpSizes(theme: ThemeLook.Current, screenH: Int): Pair<Float, Float> {
    val look = theme.look
    val base = look.helpFontSize * screenH
    return if (look.helpIconScale < 1f) base to base * look.helpIconScale else base / look.helpIconScale to base
}

// The help's background, padding and font on [view], as ES-DE draws them around its help prompts.
private fun helpStyle(view: View, theme: ThemeLook.Current, screenW: Int, screenH: Int) {
    val look = theme.look
    val (left, right, top, bottom) = look.helpPadding
    view.setPadding((left * screenW).toInt(), (top * screenH).toInt(), (right * screenW).toInt(), (bottom * screenH).toInt())
    view.background = GradientDrawable().apply { setColor(look.helpBackground); cornerRadius = look.helpCornerRadius * screenW }
}

private fun helpTextView(context: Context, theme: ThemeLook.Current, screenH: Int) = TextView(context).apply {
    typeface = theme.helpFont
    setTextColor(theme.look.helpText)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, helpSizes(theme, screenH).second)
    includeFontPadding = false
    isSingleLine = true
}

/** A pill of text in ES-DE's help style for [theme]: its help font, size, colours, padding and corners, in the text's own case. */
fun helpText(context: Context, theme: ThemeLook.Current, screenW: Int, screenH: Int) =
    helpTextView(context, theme, screenH).apply {
        gravity = Gravity.CENTER_VERTICAL
        minHeight = helpSizes(theme, screenH).first.toInt()
        helpStyle(this, theme, screenW, screenH)
    }

/**
 * ES-DE's help prompts for [entries], each an ES-DE prompt (`a`, `b`, `x`, `y`, `left/right` or `up/down`) and its
 * action, as ES-DE draws them in [theme]'s look on a [screenW] × [screenH] screen: ES-DE's own icon in the theme's icon
 * colour, then the action in its help font, size and letter case, on its help background with its spacing, padding and
 * corners. A prompt whose icon can't be read shows the button's name in the icon colour instead.
 */
fun helpPill(context: Context, theme: ThemeLook.Current, screenW: Int, screenH: Int, entries: List<Pair<String, String>>) =
    LinearLayout(context).apply {
        val look = theme.look
        val (iconPx, textPx) = helpSizes(theme, screenH)
        gravity = Gravity.CENTER_VERTICAL
        entries.forEachIndexed { i, (prompt, action) ->
            val icon = theme.helpIcons[prompt]?.let { runCatching { HelpIconDrawable(it, look.helpIcon) }.getOrNull() }
            val iconView = if (icon != null) {
                ImageView(context).apply { setImageDrawable(icon) }
            } else {
                helpTextView(context, theme, screenH).apply {
                    setTextColor(look.helpIcon)
                    text = when (prompt) {
                        "left/right" -> "◀▶"
                        "up/down" -> "▲▼"
                        else -> theme.label(prompt.uppercase())
                    }
                }
            }
            val size = if (icon != null) iconPx.toInt() else ViewGroup.LayoutParams.WRAP_CONTENT
            addView(iconView, LinearLayout.LayoutParams(size, size).apply { if (i > 0) leftMargin = (look.helpEntrySpacing * screenW).toInt() })
            addView(
                helpTextView(context, theme, screenH).apply {
                    text = when (look.helpLetterCase) {
                        "lowercase" -> action.lowercase()
                        "capitalize" -> action.split(' ').joinToString(" ") { word -> word.lowercase().replaceFirstChar(Char::uppercase) }
                        else -> action.uppercase()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = (look.helpIconTextSpacing * screenW).toInt()
                },
            )
        }
        minimumHeight = iconPx.toInt()
        helpStyle(this, theme, screenW, screenH)
    }

/** ES-DE's help [icon], drawn in [color] to fill its bounds, as ES-DE tints its white icons with the theme's colour. */
private class HelpIconDrawable(private val icon: HelpIcon, private val color: Int) : Drawable() {
    private val shapes = icon.shapes.map { PathParser.createPathFromPathData(it.pathData) to it }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val scale = bounds.width() / icon.size
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(scale, scale)
        for ((path, shape) in shapes) {
            paint.color = color
            paint.alpha = (Color.alpha(color) * shape.opacity).toInt()
            paint.style = if (shape.stroke != null) Paint.Style.STROKE else Paint.Style.FILL
            shape.stroke?.let { paint.strokeWidth = it }
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * The theme's list for a [screenW] × [screenH] screen: a row per item, the selected one on [look]'s selector bar and
 * in its pill, with its capitals centred, starting on [start]. A row too wide for the list scrolls sideways while
 * selected.
 */
class PillList(context: Context, private val look: Look, font: Typeface, private val screenW: Int, screenH: Int, items: List<String>, start: Int) {
    private val textPx = screenH * look.fontSize
    private val rowPx = (textPx * look.lineSpacing).toInt()
    // Measured from ES-DE's gamelist with minUI: the pill fills 6/7 of a row, text inset 0.7 em.
    private val pillInset = rowPx / 14
    private val textInset = (textPx * 0.7f).toInt()
    private val capShift = (2 * textPx * capCentring(font)).toInt()
    val rows = items.map { label ->
        TextView(context).apply {
            text = label
            typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            gravity = Gravity.CENTER_VERTICAL
            // Centre the line (ascent to descent) so capCentring's shift puts the capitals mid-pill.
            includeFontPadding = false
            // Like a long name in ES-DE's gamelist.
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            // The list's width, which a full-row selector covers; the pill keeps to the text.
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowPx)
        }
    }
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        rows.forEach(::addView)
    }
    var selected = 0
        private set

    init {
        select(start.coerceIn(0, (rows.size - 1).coerceAtLeast(0)))
    }

    /** Moves the pill [by] rows, stopping at the ends; with [wrap], a move past an end goes round to the other. */
    fun move(by: Int, wrap: Boolean = false) {
        if (rows.isEmpty()) return
        select(if (wrap) Math.floorMod(selected + by, rows.size) else (selected + by).coerceIn(0, rows.size - 1))
    }

    /** Changes row [i]'s label; the pill on it is sized again for the new label, which a progress count lengthens. */
    fun relabel(i: Int, label: String) {
        val row = rows.getOrNull(i) ?: return
        row.text = label
        if (i == selected) {
            row.background = selectedBackground(row)
            row.setPadding(textInset, capShift, textInset, 0)
        }
    }

    /** Puts the pill on row [i] and scrolls it into view. */
    fun select(i: Int) {
        selected = i
        rows.forEachIndexed { n, row ->
            row.isSelected = false
            row.setTextColor(if (n == i) look.selectedText else look.text)
            row.background = if (n == i) selectedBackground(row) else null
            // A background with padding replaces the view's own, so set it back. Padding on top only moves
            // the centred text down by half of it.
            row.setPadding(textInset, capShift, textInset, 0)
        }
        rows.getOrNull(i)?.let {
            it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height))
            // The theme's textHorizontalScrollDelay: a long row starts scrolling a second after it's selected.
            it.postDelayed({ it.isSelected = selected == i }, 1_000)
        }
    }

    // The selector bar, the row's height and width, or a thin one at its left, rounded as the theme's selector image
    // is, under the pill, as wide as [row]'s text and its insets, or the row when that's narrower (the text scrolls
    // inside); either may be transparent.
    private fun selectedBackground(row: TextView) = object : LayerDrawable(
        arrayOf(
            GradientDrawable().apply { setColor(look.selector); cornerRadius = look.selectorCornerRadius * rowPx },
            InsetDrawable(
                GradientDrawable().apply {
                    setColor(look.selectedBackground)
                    cornerRadius = minOf(look.selectedCornerRadius * screenW, rowPx / 2f)
                },
                0, pillInset, 0, pillInset,
            ),
        ),
    ) {
        private val pillWidth = ceil(Layout.getDesiredWidth(row.text, row.paint)).toInt() + 2 * textInset

        override fun onBoundsChange(bounds: Rect) {
            setLayerWidth(1, minOf(pillWidth, bounds.width()))
            super.onBoundsChange(bounds)
        }
    }.apply {
        look.thinSelector?.let {
            setLayerWidth(0, (it * screenW).toInt().coerceAtLeast(1))
            setLayerGravity(0, Gravity.START or Gravity.FILL_VERTICAL)
        }
        setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
    }
}

// ES-DE's own manual-viewer bar, which a theme doesn't style.
const val VIEWER_BAR = 0xFF222222.toInt()
const val VIEWER_TEXT = 0xFFAAAAAA.toInt()

/** Text in ES-DE's viewer bar, sized for a bar [barH] pixels tall: its own condensed face, grey, upper case. */
fun barText(context: Context, barH: Int, gravity: Int) = TextView(context).apply {
    typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    setTextColor(VIEWER_TEXT)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, barH * 0.7f)
    isAllCaps = true
    maxLines = 1
    this.gravity = gravity
    setPadding(barH / 4, 0, barH / 4, 0)
    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

/**
 * The in-game menu: a focusable full-screen window over the game, a themed list moved with the D-pad,
 * A to pick and B to close, with the game's title and screenshot laid out like the theme's gamelist. Built on [show]
 * and dropped on [close]; [release] also drops the title and screenshot, so nothing is held between opens.
 * Also owns the invisible window kept up during a capture.
 */
class MenuOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val screenW = wm.maximumWindowMetrics.bounds.width()
    private val screenH = wm.maximumWindowMetrics.bounds.height()
    private var root: View? = null
    /** A window just closed, left up until the next one has drawn so the game doesn't show between two lists. */
    private var outgoing: View? = null
    private var dropOutgoing = Runnable {}
    private var list: PillList? = null
    /** Shows the list [show] last put up again, for [error] to go back to. */
    private var reshow: () -> Unit = {}
    private var captureView: View? = null
    private val captureParams = WindowManager.LayoutParams(
        1, 1,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { alpha = 0f }

    // The theme's 4:3 gamelist art box (aspect-ratio-4-3.xml): 0.3625 × 0.73333333 of the screen.
    val artW = (screenW * 0.3625f).toInt()
    val artH = (screenH * 0.73333333f).toInt()

    /** The game's name and image, set before the menu opens; every [show] keeps them until [release]. */
    var title = ""
    var art: Bitmap? = null
    /** Whether [art] fills the box, cropped, as themes draw a game's screen; otherwise it's scaled to fit. */
    var cropArt = true
    /** The look and fonts the menu draws with, set before it opens. */
    var theme = ThemeLook.DEFAULT

    val isOpen get() = root != null

    /**
     * While set, menu windows are added invisible and unfocusable, under a document that opens over them; [reveal]
     * shows them. Unfocusable because a game that loses focus may pause while it's still on screen: RetroArch then
     * stops drawing its shader, and the game looks brighter until the document covers it.
     */
    private var hidden = false

    /** Adds the menu invisible until [reveal], so a document opening over it shows straight over the game. */
    fun hideUntilRevealed() {
        hidden = true
    }

    fun reveal() {
        hidden = false
        val view = root ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        if (params.alpha < 1f) {
            params.alpha = 1f
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            wm.updateViewLayout(view, params)
        }
    }

    /** Shows [items] with the cursor on [start]; with [alt], X calls it with the selected row and the help pill names it. */
    fun show(
        items: List<String>,
        onPick: (Int) -> Unit,
        onClose: () -> Unit,
        alt: Pair<String, (Int) -> Unit>? = null,
        start: Int = 0,
    ) {
        close()
        reshow = { show(items, onPick, onClose, alt, start) }
        val list = PillList(context, theme.look, theme.font, screenW, screenH, items, start)
        val rows = list.rows
        val help = listOfNotNull("a" to "Select", alt?.let { "x" to it.first }, "b" to "Back")
        open(ScrollView(context).apply { addView(list.view) }, help) { code, held ->
            when (code) {
                // A press past an end goes round to the other, as in ES-DE's gamelist; a held D-pad stops at the end.
                KeyEvent.KEYCODE_DPAD_UP -> list.move(-1, wrap = !held)
                KeyEvent.KEYCODE_DPAD_DOWN -> list.move(1, wrap = !held)
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                    if (rows.isNotEmpty()) onPick(list.selected)
                // Always used: Android's gamepad key map turns an unused X into DPAD_CENTER, which would pick the row.
                // Y too: with ES-DE's buttons swapped, the X button arrives as Y.
                KeyEvent.KEYCODE_BUTTON_X -> if (alt != null && rows.isNotEmpty()) alt.second(list.selected)
                KeyEvent.KEYCODE_BUTTON_Y -> {}
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { close(); onClose() }
                else -> return@open false
            }
            true
        }
        this.list = list
    }

    /**
     * Shows [fields] as label–value lines, then [description], in the list column. Up and Down scroll a line at a
     * time when it's taller than the column; B closes to [onClose].
     */
    fun info(fields: List<Pair<String, String>>, description: String?, onClose: () -> Unit) {
        close()
        val look = theme.look
        // The guide text's colour and font, a size below the list's, as minUI's x-small is to its small.
        val textPx = screenH * look.fontSize * 0.8f
        // The theme's spacing for its game description.
        val linePx = (textPx * look.bodyLineSpacing).toInt()
        val textInset = (screenH * look.fontSize * 0.7f).toInt()
        fun line(text: CharSequence) = TextView(context).apply {
            this.text = text
            typeface = theme.bodyFont
            setTextColor(look.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            lineHeight = linePx
            // A one-line field gets no line spacing from lineHeight, which only spaces lines within a view.
            minimumHeight = linePx
            gravity = Gravity.CENTER_VERTICAL
        }
        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(textInset, 0, textInset, 0)
            fields.forEach { (label, value) ->
                addView(line(SpannableStringBuilder().append(label, ForegroundColorSpan(look.helpIcon), 0).append("  $value")))
            }
            description?.let {
                addView(line(it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (fields.isNotEmpty()) topMargin = linePx
                })
            }
        }
        val scroll = ScrollView(context).apply { addView(text) }
        open(scroll, listOf("up/down" to "Scroll", "b" to "Back")) { code, _ ->
            when (code) {
                KeyEvent.KEYCODE_DPAD_UP -> scroll.scrollBy(0, -linePx)
                KeyEvent.KEYCODE_DPAD_DOWN -> scroll.scrollBy(0, linePx)
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { close(); onClose() }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {}
                else -> return@open false
            }
            true
        }
    }

    /**
     * Adds the menu window: the [title] above [content] in the list column, the art box beside it and a help pill of
     * [help], ES-DE prompts and their actions. [onKey] gets each key press, and whether it's a repeat from a held key, and returns whether it used it.
     */
    private fun open(content: ScrollView, help: List<Pair<String, String>>, onKey: (Int, Boolean) -> Boolean) {
        val look = theme.look
        val font = theme.font
        val textPx = screenH * look.fontSize
        val textInset = (textPx * 0.7f).toInt()
        // A size up from the list's, as minUI's large is to its small, in a row of the same line spacing; in the
        // theme's text colour, which a list's own colour can dim (Linear's grey rows).
        val titlePx = textPx * 1.4f
        val titleRow = TextView(context).apply {
            text = title
            typeface = font
            setTextColor(look.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, titlePx)
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setPadding(textInset, 0, textInset, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (titlePx * look.lineSpacing).toInt())
            postDelayed({ isSelected = true }, 1_000)
        }
        content.isVerticalScrollBarEnabled = false
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        val image = art
        val artBox = FrameLayout(context).apply {
            // The theme's art corner radius, else minUI's; the screenshot is clipped to this background's outline.
            background = GradientDrawable().apply {
                cornerRadius = screenW * (look.artCornerRadius ?: 0.0078125f)
                if (image == null) setStroke(2, look.helpIcon)
            }
            clipToOutline = true
            if (image != null) {
                // Unfiltered, so pixel art keeps hard edges, as ES-DE's default nearest interpolation scales it.
                val drawable = BitmapDrawable(context.resources, image).apply { isFilterBitmap = false }
                addView(ImageView(context).apply {
                    scaleType = if (cropArt) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(drawable)
                })
            } else {
                addView(
                    TextView(context).apply {
                        text = "No screenshot"
                        typeface = font
                        setTextColor(look.helpIcon)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.65f)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
                )
            }
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(look.background)
            // ES-DE's gamelist margins: 5% of the width at the sides, 7% of the height above, 6% below.
            val padX = (screenW * 0.05f).toInt()
            val padTop = (screenH * 0.07f).toInt()
            setPadding(padX, padTop, padX, (screenH * 0.06f).toInt())
            // The theme's 4:3 list width (0.4 of the screen) plus the pill's margins either side.
            addView(column, FrameLayout.LayoutParams((screenW * 0.4f).toInt() + 2 * textInset, ViewGroup.LayoutParams.MATCH_PARENT))
            // The art box's origin (1, 0.5) sits at (0.928125, 0.44166667) of the screen.
            addView(artBox, FrameLayout.LayoutParams(artW, artH).apply {
                leftMargin = (screenW * 0.928125f).toInt() - artW - padX
                topMargin = (screenH * 0.44166667f).toInt() - artH / 2 - padTop
            })
            addView(
                helpPill(context, theme, screenW, screenH, help),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END),
            )
            // Keep focus on the root so the ScrollView doesn't take the D-pad.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, sent, event ->
                val code = theme.button(sent, event.device?.name)
                if (BuildConfig.DEBUG && event.action == KeyEvent.ACTION_DOWN) {
                    val swapped = if (code != sent) " as ${KeyEvent.keyCodeToString(code)}" else ""
                    Log.d(TAG, "key delivered ${SystemClock.uptimeMillis() - event.eventTime} ms: ${KeyEvent.keyCodeToString(sent)}$swapped")
                }
                if (event.action == KeyEvent.ACTION_DOWN) onKey(code, event.repeatCount > 0) else code in KEYS
            }
        }
        wm.addFullScreen(view, PixelFormat.TRANSLUCENT, hidden = hidden)
        root = view
        // close() left the last window up for this one. It goes once this one's frame is on screen, or after a
        // quarter second if no frame is reported.
        outgoing?.let { old ->
            old.removeCallbacks(dropOutgoing)
            view.post {
                view.viewTreeObserver.registerFrameCommitCallback { view.post { if (outgoing === old) dropOutgoing.run() } }
                view.invalidate()
            }
            old.postDelayed(dropOutgoing, 250)
        }
    }

    /** Changes row [i]'s label in place, for progress, without building a new window. */
    fun relabel(i: Int, label: String) {
        list?.relabel(i, label)
    }

    /** Replaces an open menu's list with [text] as its only row; B goes back to the list. */
    fun error(text: String) {
        if (!isOpen) return
        val back = reshow
        show(listOf(text), {}, back)
    }

    /**
     * Closes the menu. Its window stays until the end of this turn of the main thread, so that a list shown
     * straight after takes over from it without the game showing in between.
     */
    fun close() {
        // Nothing open: B closes the menu and then the list it goes back to closes it again before opening, and
        // the window already on its way out has to last until then.
        val old = root ?: return
        outgoing?.let { it.removeCallbacks(dropOutgoing); wm.removeView(it) }
        outgoing = old
        root = null
        list = null
        dropOutgoing = Runnable { if (outgoing === old) { wm.removeView(old); outgoing = null } }
        old.post(dropOutgoing)
    }

    /** Closes the menu for good, dropping its [title] and [art]. */
    fun release() {
        close()
        hidden = false
        title = ""
        art?.recycle()
        art = null
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
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
        )
    }
}
