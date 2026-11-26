package com.clintonium.es_de_overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils

/**
 * The game switcher over ES-DE: one recent game a screen, its whole picture as large as the screen allows, its title
 * pill (and a detail pill, if set) at the top left, its place in the list at the top right and what the buttons do
 * along the bottom, all in the theme's look; or, as the strip, the same with the neighbouring games' pictures along
 * the bottom and the buttons at the top right. Left and right move between games, A picks and B closes. Built on
 * [show] and dropped on [close].
 */
class SwitcherOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val screenW = wm.maximumWindowMetrics.bounds.width()
    private val screenH = wm.maximumWindowMetrics.bounds.height()
    private var root: View? = null
    /** The pills saying what the buttons do, which go once the switcher has given up the keys. */
    private var actions = emptyList<View>()
    /** Set by [yieldFocus]: the window on screen takes no more keys. */
    private var yielded = false

    val isOpen get() = root != null

    /** Uptime of the switcher's last press, or of its opening. */
    var pressedAt = 0L
        private set

    /**
     * Leaves the game on screen without its action pills and gives up the keys, so presses reach ES-DE underneath;
     * [then] runs once Android has taken the keys away, or after a quarter second if it never says so. From the call
     * on, the switcher ignores any key still sent to it: a press meant for ES-DE would otherwise move to another game.
     */
    fun yieldFocus(then: () -> Unit) {
        val view = root ?: return then()
        yielded = true
        actions.forEach { it.visibility = View.INVISIBLE }
        val params = view.layoutParams as WindowManager.LayoutParams
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(view, params)
        var waiting = true
        val done = Runnable {
            if (waiting) {
                waiting = false
                then()
            }
        }
        if (!view.hasWindowFocus()) return done.run()
        view.viewTreeObserver.addOnWindowFocusChangeListener(object : ViewTreeObserver.OnWindowFocusChangeListener {
            override fun onWindowFocusChanged(hasFocus: Boolean) {
                if (hasFocus) return
                view.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                done.run()
            }
        })
        view.postDelayed(done, 250)
    }

    /** Redraws the game a picture is for, if it's on screen. */
    private var onPicture: (Int) -> Unit = {}

    /** Game [i]'s picture has been read: the switcher takes it if the game is on screen. */
    fun pictureArrived(i: Int) = onPicture(i)

    /** How many games either side of the one shown the strip shows; 0 with one game a screen. */
    var neighbours = 0
        private set

    /**
     * Shows game [start] of [titles], each with its [details] line if given, in [theme]'s look; with [strip], the neighbouring
     * games' pictures run along the bottom. [picture] gives a game's picture if it's been read, else null, when the
     * game is black under its title until [pictureArrived] says it's in; while [updating] says a newer one is on its
     * way, the picture shown says so. Each arrival draws the game's picture and detail again. [onSelect] hears of each
     * game shown. A picks the game shown with [onPick]; B closes to [onClose].
     */
    fun show(
        theme: ThemeLook.Current,
        strip: Boolean,
        titles: List<String>,
        details: ((Int) -> String)?,
        start: Int,
        picture: (Int) -> Bitmap?,
        updating: (Int) -> Boolean,
        onSelect: (Int) -> Unit,
        onPick: (Int) -> Unit,
        onClose: () -> Unit,
    ) {
        close()
        val look = theme.look
        val font = theme.font
        val textPx = screenH * look.fontSize
        // The theme's selected game-list row, as the menu's list draws it: its text size and line height, the pill
        // 6/7 of the row, text inset 0.7 em, capitals centred. Where the row has no pill colour (Linear's bare bar),
        // the help's background, else the screen's, so the title reads over the picture.
        val rowPx = (textPx * look.lineSpacing).toInt()
        val rowPill = look.selectedBackground.takeIf { Color.alpha(it) > 0 }
        val helpFill = look.helpBackground.takeIf { Color.alpha(it) > 0 }
        val title = TextView(context).apply {
            typeface = font
            setTextColor(if (rowPill == null && helpFill != null) look.helpText else look.selectedText)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            background = InsetDrawable(
                GradientDrawable().apply {
                    setColor(rowPill ?: helpFill ?: look.background)
                    cornerRadius = minOf(look.selectedCornerRadius * screenW, rowPx / 2f).takeIf { rowPill != null } ?: (rowPx / 2f)
                },
                0, rowPx / 14, 0, rowPx / 14,
            )
            // After the background, whose insets would replace it. Padding on top moves the centred text down by half.
            val inset = (textPx * 0.7f).toInt()
            setPadding(inset, (2 * textPx * capCentring(font)).toInt(), inset, 0)
            height = rowPx
        }
        // A theme whose help has no background draws its prompts on its own screen, where they read. Over the picture
        // they get that screen's colour in a round pill, as the title does, padded where the theme gives no padding.
        fun <V : View> backed(pill: V) = pill.apply {
            if (helpFill != null) return@apply
            (background as GradientDrawable).apply { setColor(look.background); cornerRadius = screenH.toFloat() }
            if (look.helpPadding.all { it == 0f }) {
                val em = look.helpFontSize * screenH
                setPadding((em * 0.7f).toInt(), (em * 0.35f).toInt(), (em * 0.7f).toInt(), (em * 0.35f).toInt())
            }
        }
        val detail = backed(helpText(context, theme, screenW, screenH))
        val count = backed(helpText(context, theme, screenW, screenH))
        val update = backed(helpText(context, theme, screenW, screenH).apply { text = "Updating" })
        // The whole picture, in its own shape, on black where it isn't the screen's.
        val art = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        // The mockup's strip at 1280 × 960: 4:3 pictures 132 px tall, 20 px apart, the selected one 156 px tall,
        // as many either side as fit between the side margins, in a band 214 px tall.
        val thumbH = screenH * 0.1375f
        val selectedH = screenH * 0.1625f
        val gap = (screenH * 0.0208f).toInt()
        val fit = if (strip) ((screenW * 0.9f - selectedH * 4 / 3) / 2 / (thumbH * 4 / 3 + gap)).toInt() else 0
        // As many either side as the list has, so none shows twice.
        val left = minOf(fit, (titles.size - 1) / 2)
        val offsets = if (strip) (-left..minOf(fit, titles.size - 1 - left)).toList() else emptyList()
        neighbours = offsets.maxOfOrNull { maxOf(it, -it) } ?: 0
        val radius = screenW * (look.artCornerRadius ?: 0.0078125f)
        // Each strip picture fills its box; a game with none shows its title there.
        val thumbArts = offsets.map { ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } }
        val thumbTitles = offsets.map {
            TextView(context).apply {
                typeface = font
                setTextColor(look.helpText)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx * 0.5f)
                gravity = Gravity.CENTER
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                val pad = (textPx * 0.3f).toInt()
                setPadding(pad, pad, pad, pad)
            }
        }
        val thumbs = offsets.mapIndexed { n, offset ->
            FrameLayout(context).apply {
                background = GradientDrawable().apply { setColor(look.helpBackground); cornerRadius = radius }
                clipToOutline = true
                addView(thumbArts[n], FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(thumbTitles[n], FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                if (offset == 0) {
                    // The theme's selector colour, else its selected row's.
                    val outline = look.selector.takeIf { Color.alpha(it) > 0 } ?: look.selectedBackground
                    foreground = GradientDrawable().apply { setStroke((screenH * 0.0052f).toInt().coerceAtLeast(2), outline); cornerRadius = radius }
                } else {
                    alpha = 0.7f
                }
            }
        }
        var shown = -1
        // Unfiltered, so pixel art keeps hard edges, as ES-DE's default nearest interpolation scales it.
        fun drawable(i: Int) = picture(i)?.let { BitmapDrawable(context.resources, it).apply { isFilterBitmap = false } }
        fun drawArt() {
            art.setImageDrawable(drawable(shown))
            details?.let { detail.text = it(shown) }
            update.visibility = if (updating(shown)) View.VISIBLE else View.GONE
            offsets.forEachIndexed { n, offset ->
                val i = Math.floorMod(shown + offset, titles.size)
                val image = drawable(i)
                thumbArts[n].setImageDrawable(image)
                thumbTitles[n].text = if (image == null) titles[i] else null
            }
        }
        onPicture = { drawArt() }
        fun select(i: Int) {
            shown = i
            drawArt()
            onSelect(i)
            title.text = titles[i]
            count.text = "${i + 1} of ${titles.size}"
            // The theme's textHorizontalScrollDelay: a long title starts scrolling a second after it's shown.
            title.isSelected = false
            title.postDelayed({ title.isSelected = shown == i }, 1_000)
        }
        val view = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            addView(art, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(update, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            if (strip) {
                val band = LinearLayout(context).apply {
                    gravity = Gravity.CENTER
                    // The theme's background, a little see-through, as the mockup's black band.
                    setBackgroundColor(ColorUtils.setAlphaComponent(look.background, 0xDB))
                    thumbs.forEachIndexed { n, thumb ->
                        val h = if (offsets[n] == 0) selectedH else thumbH
                        addView(thumb, LinearLayout.LayoutParams((h * 4 / 3).toInt(), h.toInt()).apply { if (n > 0) leftMargin = gap })
                    }
                }
                addView(band, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (screenH * 0.223f).toInt(), Gravity.BOTTOM))
            }
            // ES-DE's gamelist margins, as the menu's: 5% of the width at the sides, 7% of the height above, 6% below.
            val pills = FrameLayout(context).apply {
                setPadding((screenW * 0.05f).toInt(), (screenH * 0.07f).toInt(), (screenW * 0.05f).toInt(), (screenH * 0.06f).toInt())
                val buttons = backed(helpPill(context, theme, screenW, screenH, listOf("a" to "Play", "b" to "Back")))
                // The strip says where the game is and that left and right move, so there the buttons take the corner.
                val corner = if (strip) buttons else count
                val gapX = (textPx * 0.35f).toInt()
                addView(
                    LinearLayout(context).apply {
                        isBaselineAligned = false
                        // The title and detail take what the corner pill leaves, so a long title scrolls short of it.
                        addView(
                            LinearLayout(context).apply {
                                orientation = LinearLayout.VERTICAL
                                // Each pill as wide as its own text: a vertical layout stretches its rows otherwise.
                                addView(title, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                                if (details != null) {
                                    addView(detail, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                        topMargin = gapX
                                    })
                                }
                            },
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(corner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            leftMargin = gapX
                        })
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP),
                )
                fun bottom(pill: View, gravity: Int) =
                    addView(pill, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or gravity))
                if (strip) {
                    actions = listOf(buttons)
                } else {
                    val switch = backed(helpPill(context, theme, screenW, screenH, listOf("left/right" to "Browse")))
                    bottom(switch, Gravity.START)
                    bottom(buttons, Gravity.END)
                    actions = listOf(switch, buttons)
                }
            }
            addView(pills, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, sent, event ->
                if (yielded) return@setOnKeyListener true
                val code = theme.button(sent, event.device?.name)
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener code in KEYS
                pressedAt = event.eventTime
                // A press past an end goes round to the other, as the menu's list does; a held D-pad stops at the end.
                fun move(by: Int) = select(
                    if (event.repeatCount > 0) (shown + by).coerceIn(0, titles.size - 1) else Math.floorMod(shown + by, titles.size),
                )
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> move(-1)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> move(1)
                    KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> onPick(shown)
                    // Swallowed, as in the menu: Android's gamepad key map turns an unused X into DPAD_CENTER, and with
                    // ES-DE's buttons swapped the X button arrives as Y.
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> {}
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> { close(); onClose() }
                    else -> return@setOnKeyListener false
                }
                true
            }
        }
        select(start.coerceIn(0, titles.size - 1))
        wm.addFullScreen(view, PixelFormat.OPAQUE)
        root = view
        yielded = false
        pressedAt = SystemClock.uptimeMillis()
    }

    fun close() {
        root?.let(wm::removeView)
        root = null
        onPicture = {}
    }

    private companion object {
        val KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BACK,
        )
    }
}
