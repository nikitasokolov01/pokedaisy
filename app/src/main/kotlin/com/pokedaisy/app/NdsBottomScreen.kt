package com.pokedaisy.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.view.Gravity
import com.pokedaisy.app.companion.ui.DsScreenSwitch
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A DS game's bottom (touch) screen on the bottom display, where the companion goes for a GBA game: the DS's own
 * picture and its stylus from this screen's touches. NdsBottomHost switches it with the companion. The frame is copied between frames on the emu thread
 * ([NdsBottomFrame.publish]), like the top screen's ([EmulatorView.publishFrame]).
 */
object NdsBottomFrame {
    const val W = 256
    const val H = 192

    private val lock = Any()
    private val pixels: ByteBuffer = ByteBuffer.allocateDirect(W * H * 4)
    /** The core's bottom-screen buffer while a DS core runs ([bind] / [unbind]). */
    private var source: ByteBuffer? = null
    private val views = CopyOnWriteArrayList<NdsTouchScreenView>()

    /** Emu thread, once the DS core is up. */
    fun bind(buffer: ByteBuffer?) = synchronized(lock) { source = buffer }

    /** Emu thread, before the core frees its buffer. */
    fun unbind() = synchronized(lock) { source = null }

    /** Emu thread, after each frame the screen shows. */
    fun publish() {
        synchronized(lock) {
            val src = source ?: return
            src.rewind()
            pixels.clear()
            pixels.put(src)
        }
        for (v in views) v.postInvalidateOnAnimation()
    }

    internal fun copyInto(bitmap: Bitmap) = synchronized(lock) {
        pixels.rewind()
        bitmap.copyPixelsFromBuffer(pixels)
    }

    internal fun attach(v: NdsTouchScreenView) = views.add(v)
    internal fun detach(v: NdsTouchScreenView) = views.remove(v)

    /** The bottom display's content for a DS game: its touch screen, on black. */
    fun content(context: Context): View = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        addView(NdsTouchScreenView(context), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
}

/**
 * The bottom display for a DS game on two screens: the DS touch screen, or the companion in its place (the player's
 * pick, Prefs.dsBottomCompanion). One button switches them, in the same place for both: centred in a reserved bar under the touch image (COMPANION, or GAME SCREEN back), the companion taking the touch screen's area above it.
 * BACK with nothing left to go back from also returns to the touch screen (PokeDaisyActivity.companionBackTap).
 */
@SuppressLint("ViewConstructor")
class NdsBottomHost(
    context: Context,
    companion: () -> View,
    showCompanion: Boolean,
    /** Every ownership change; remember is false for a temporary return after a game action. */
    private val onSwitch: (companion: Boolean, remember: Boolean) -> Unit,
    touchSink: (Boolean, Int, Int) -> Unit = { down, x, y -> MgbaCore.pkNdsTouch(down, x, y) },
) : FrameLayout(context) {
    private val touch = NdsTouchScreenView(context, touchSink)
    private val companionView = companion()
    private var label by mutableStateOf(showCompanion)
    private val button = CompanionColors.track(ComposeView(context)).apply {
        setContent {
            DsScreenSwitch(label) { show(!showingCompanion) }
        }
    }
    var showingCompanion = showCompanion
        private set

    init {
        setBackgroundColor(Color.BLACK)
        addView(touch, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(companionView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.TOP))
        addView(button, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        apply(showCompanion)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        // Both surfaces stop above the bar, including a wide/swapped display with no natural bottom letterbox.
        val screenH = NdsTouchRect.contentHeight(w, h, (resources.displayMetrics.density * 72).toInt())
        val bar = h - screenH
        if (touch.layoutParams.height != screenH) touch.cancelTouch()
        touch.layoutParams.height = screenH
        companionView.layoutParams.height = screenH
        button.layoutParams.height = bar
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Shows the companion ([on]) or the touch screen, and tells [onSwitch] when that's a change. */
    fun show(on: Boolean, remember: Boolean = true) {
        if (on == showingCompanion) return
        apply(on)
        onSwitch(on, remember)
    }

    private fun apply(on: Boolean) {
        touch.cancelTouch()
        companionView.findFocus()?.clearFocus()
        showingCompanion = on
        label = on
        companionView.visibility = if (on) VISIBLE else GONE
        touch.visibility = if (on) GONE else VISIBLE
        // No stylus left down on a screen that's gone.
    }

    fun cancelTouch() = touch.cancelTouch()
}

/** The DS's bottom screen, as large as fits at its own 4:3, top-centred; touches on it are the stylus. */
class NdsTouchScreenView(context: Context, touchSink: (Boolean, Int, Int) -> Unit = { down, x, y -> MgbaCore.pkNdsTouch(down, x, y) }) : View(context) {
    private val bitmap = Bitmap.createBitmap(NdsBottomFrame.W, NdsBottomFrame.H, Bitmap.Config.ARGB_8888)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()
    private val stylus = NdsTouchInput(touchSink)

    fun cancelTouch() = stylus.cancel()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NdsBottomFrame.attach(this)
    }

    override fun onDetachedFromWindow() {
        NdsBottomFrame.detach(this)
        cancelTouch()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val r = NdsTouchRect.fit(w, h)
        stylus.rect = r
        dst.set(r.left, r.top, r.left + r.width, r.top + r.height)
    }

    override fun onDraw(canvas: Canvas) {
        NdsBottomFrame.copyInto(bitmap)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isShown || dst.isEmpty) { cancelTouch(); return false }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelTouch()
                return stylus.start(event.getPointerId(0), event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount)
                stylus.move(event.getPointerId(i), event.getX(i), event.getY(i))
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> stylus.end(event.getPointerId(event.actionIndex))
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelTouch()
    }
}
