package com.pokedaisy.app

/** Geometry shared by drawing and hit testing. The switch bar never covers a DS pixel. */
data class NdsTouchRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    fun point(x: Float, y: Float): Pair<Int, Int>? {
        if (!x.isFinite() || !y.isFinite() || width <= 0 || height <= 0 ||
            x < left || y < top || x >= left + width || y >= top + height) return null
        return ((x - left) * 256 / width).toInt().coerceIn(0, 255) to
            ((y - top) * 192 / height).toInt().coerceIn(0, 191)
    }

    companion object {
        fun fit(w: Int, h: Int): NdsTouchRect {
            val scale = minOf(w.coerceAtLeast(0) / 256f, h.coerceAtLeast(0) / 192f)
            val width = (256 * scale).toInt()
            return NdsTouchRect((w - width) / 2, 0, width, (192 * scale).toInt())
        }
        fun contentHeight(w: Int, h: Int, bar: Int): Int =
            minOf(fit(w, h).height, (h - bar).coerceAtLeast(0))
    }
}

/** One finger owns the stylus until lifted. A hidden/resized/suspended surface requires
 * a fresh down, so an old move or a second finger cannot resurrect a touch. */
class NdsTouchInput(private val send: (Boolean, Int, Int) -> Unit) {
    private var pointer: Int? = null
    private var down = false
    var rect = NdsTouchRect(0, 0, 0, 0)
        set(value) { cancel(); field = value }

    fun start(id: Int, x: Float, y: Float): Boolean {
        if (pointer != null || rect.point(x, y) == null) return false
        pointer = id
        move(id, x, y)
        return true
    }
    fun move(id: Int, x: Float, y: Float) {
        if (pointer != id) return
        val point = rect.point(x, y)
        if (point != null) { down = true; send(true, point.first, point.second) }
        else release()
    }
    fun end(id: Int) { if (pointer == id) cancel() }
    fun cancel() { pointer = null; release() }
    private fun release() { if (down) send(false, 0, 0); down = false }
}
