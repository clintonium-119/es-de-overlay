package com.clintonium.esderecorder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

private const val IDLE = "●"

/** Small draggable floating button; a tap (not a drag) calls [onTap]. */
@SuppressLint("ClickableViewAccessibility")
class TriggerOverlay(context: Context, private val prefs: Prefs, private val onTap: () -> Unit) {
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
    }
    private val params = WindowManager.LayoutParams(
        (44 * density).toInt(),
        (44 * density).toInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.overlayX
        y = prefs.overlayY
    }
    private var shown = false

    init {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX - params.x
                    downY = e.rawY - params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging || abs(e.rawX - downX - params.x) + abs(e.rawY - downY - params.y) > slop) {
                        dragging = true
                        params.x = (e.rawX - downX).toInt()
                        params.y = (e.rawY - downY).toInt()
                        wm.updateViewLayout(view, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (dragging) {
                    prefs.overlayX = params.x
                    prefs.overlayY = params.y
                } else {
                    onTap()
                }
            }
            true
        }
    }

    fun show(visible: Boolean) {
        if (visible == shown) return
        if (visible) wm.addView(view, params) else wm.removeView(view)
        shown = visible
    }

    /** Fully transparent but still tappable, so it never appears in a capture yet can stop one early. */
    fun invisible(on: Boolean) {
        params.alpha = if (on) 0f else 1f
        if (shown) wm.updateViewLayout(view, params)
    }

    /** Shows [label] (e.g. ✓, ✗, ?) for two seconds. */
    fun flash(label: String) {
        view.text = label
        main.removeCallbacksAndMessages(null)
        main.postDelayed({ view.text = IDLE }, 2_000)
    }
}
