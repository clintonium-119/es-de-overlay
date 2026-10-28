package com.clintonium.esderecorder

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

private const val IDLE = "●"

enum class Corner(val gravity: Int, val label: String) {
    TOP_LEFT(Gravity.TOP or Gravity.START, "Top left"),
    TOP_RIGHT(Gravity.TOP or Gravity.END, "Top right"),
    BOTTOM_LEFT(Gravity.BOTTOM or Gravity.START, "Bottom left"),
    BOTTOM_RIGHT(Gravity.BOTTOM or Gravity.END, "Bottom right"),
}

/** Small floating button in the corner chosen in settings, the same distance from both edges. */
class TriggerOverlay(context: Context, private val prefs: Prefs, onTap: () -> Unit) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density
    private val view = TextView(context).apply {
        text = IDLE
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        contentDescription = "Capture screenshot and clip"
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x88000000.toInt())
            setStroke((2 * density).toInt(), 0x88FFFFFF.toInt())
        }
        setOnClickListener { onTap() }
    }
    private val params = WindowManager.LayoutParams(
        (44 * density).toInt(),
        (44 * density).toInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        x = (12 * density).toInt()
        y = x
    }
    private var shown = false

    fun show(visible: Boolean) {
        if (visible == shown) return
        if (visible) {
            params.gravity = prefs.corner.gravity
            wm.addView(view, params)
        } else {
            wm.removeView(view)
        }
        shown = visible
    }

    /**
     * [alpha] 0 keeps the button out of the screenshot, but a fully transparent window gets no touches,
     * so while recording use a barely visible 0.01 to keep a second tap working.
     */
    fun setAlpha(alpha: Float) {
        params.alpha = alpha
        if (shown) wm.updateViewLayout(view, params)
    }

    /** Forces a window layout pass without any visible change (only the window title differs). */
    fun nudge() {
        if (!shown) return
        params.title = "capture ${SystemClock.uptimeMillis()}"
        wm.updateViewLayout(view, params)
    }

    /** Shows [label] (e.g. ✓, ✗, ?) for two seconds. */
    fun flash(label: String) {
        view.text = label
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ view.text = IDLE }, 2_000)
    }
}
