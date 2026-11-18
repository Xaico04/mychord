package com.hichord.app
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class HiChordView(ctx: Context, private val synth: Synth) : View(ctx) {
    private val NN = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val MAJ = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MIN = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val WAVES = arrayOf("Seno", "Triângulo", "Serra", "Quadrada")
    private val ROM = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")
    private val MODS = arrayOf("", "7ª", "sus4", "add9", "inversão")
    private val BTN = arrayOf("Tom −", "Tom +", "Escala", "Onda", "Vol −", "Vol +")
    private var key = 0; private var minor = false; private var cur = -1; private var mod = 0
    private var padSeen = false
    private val touchChord = BooleanArray(7); private val padChord = BooleanArray(7)
    private val stamp = IntArray(7); private val prevH = BooleanArray(7); private var ctr = 0
    private var jx = 0f; private var jy = 0f; private var gx = 0f; private var gy = 0f
    private var r2Key = false; private var r2Axis = false; private var hx = 0; private var hy = 0
    private val padR = Array(7) { RectF() }; private val btnR = Array(6) { RectF() }
    private val visor = RectF(); private var jcx = 0f; private var jcy = 0f; private var jr = 1f
    private val ptrPad = HashMap<Int, Int>(); private var joyPtr = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teal = Color.parseColor("#5eead4"); private val pink = Color.parseColor("#f472b6")
    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ---------- teoria ----------
    private fun sn(sc: IntArray, s: Int) = sc[s % 7] + 12 * (s / 7)
    private fun notes(c: Int, m: Int): List<Int> {
        val sc = if (minor) MIN else MAJ
        val s = mutableListOf(c, c + 2, c + 4)
        if (m == 1) s.add(c + 6)
        if (m == 2) s[1] = c + 3
        if (m == 3) s.add(c + 8)
        if (m == 4) s[0] = c + 7
        return s.map { 48 + key + sn(sc, it) }
    }
    private fun nm(n: Int) = NN[((n % 12) + 12) % 12] + (Math.floorDiv(n, 12) - 1)
    private fun cname(c: Int, m: Int): String {
        val sc = if (minor) MIN else MAJ; val b = 48 + key; val r = b + sn(sc, c)
        val iv = notes(c, if (m == 4) 0 else m).map { it - r }
        var q = if (3 in iv) (if (6 in iv) "dim" else "m") else if (5 in iv) "sus4" else ""
        if (m == 1) {
            val s7 = if (11 in iv) 11 else 10
            q = if (6 in iv && 3 in iv) "m7♭5" else if (3 in iv) (if (s7 == 11) "m(maj7)" else "m7") else (if (s7 == 11) "maj7" else "7")
        }
        if (m == 3) q += "add9"
        return NN[r % 12] + q + (if (m == 4) "/" + NN[(b + sn(sc, c + 2)) % 12] else "")
    }
    private fun applyChord() {
        if (cur < 0) { synth.gate = false; return }
        val ns = notes(cur, mod)
        for (i in 0 until 4) {
            if (i < ns.size) { synth.freq[i] = 440f * 2f.pow((ns[i] - 69) / 12f); synth.active[i] = true } else synth.active[i] = false
        }
        synth.gate = true
    }
    private fun refresh() {
        var n = -1; var best = 0
        for (i in 0 until 7) {
            val h = touchChord[i] || padChord[i]
            if (h && !prevH[i]) stamp[i] = ++ctr
            prevH[i] = h
            if (h && stamp[i] >= best) { best = stamp[i]; n = i }
        }
        val x = if (abs(gx) > abs(jx)) gx else jx
        val y = if (abs(gy) > abs(jy)) gy else jy
        var m = 0
        if (abs(x) > 0.5f || abs(y) > 0.5f) m = if (abs(y) >= abs(x)) (if (y > 0) 1 else 2) else (if (x > 0) 3 else 4)
        if (n != cur || m != mod) { cur = n; mod = m; applyChord() }
        invalidate()
    }
    private fun act(i: Int) {
        when (i) {
            0 -> { if (key > -12) key-- }
            1 -> { if (key < 12) key++ }
            2 -> { minor = !minor }
            3 -> { synth.wave = (synth.wave + 1) and 3 }
            4 -> { synth.vol = max(0f, synth.vol - 0.05f) }
            5 -> { synth.vol = min(1f, synth.vol + 0.05f) }
        }
        applyChord(); invalidate()
    }

    // ---------- controle ----------
    fun onPadKey(code: Int, down: Boolean, rep: Int): Boolean {
        val ci = when (code) {
            KeyEvent.KEYCODE_BUTTON_A -> 0
            KeyEvent.KEYCODE_BUTTON_B -> 1
            KeyEvent.KEYCODE_BUTTON_X -> 2
            KeyEvent.KEYCODE_BUTTON_Y -> 3
            KeyEvent.KEYCODE_BUTTON_L1 -> 4
            KeyEvent.KEYCODE_BUTTON_R1 -> 5
            KeyEvent.KEYCODE_BUTTON_R2 -> 6
            else -> -1
        }
        if (ci >= 0) {
            padSeen = true
            if (ci == 6) { r2Key = down; padChord[6] = r2Key || r2Axis } else padChord[ci] = down
            refresh(); return true
        }
        if (down && rep == 0) {
            val a = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> 0
                KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                KeyEvent.KEYCODE_DPAD_UP -> 5
                KeyEvent.KEYCODE_DPAD_DOWN -> 4
                KeyEvent.KEYCODE_BUTTON_SELECT -> 3
                KeyEvent.KEYCODE_BUTTON_THUMBL -> 2
                else -> -1
            }
            if (a >= 0) { padSeen = true; act(a); return true }
        }
        return false
    }
    fun onPadMotion(e: MotionEvent) {
        padSeen = true
        fun dz(v: Float) = if (abs(v) < 0.2f) 0f else v
        gx = dz(e.getAxisValue(MotionEvent.AXIS_X)); gy = -dz(e.getAxisValue(MotionEvent.AXIS_Y))
        r2Axis = max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f
        padChord[6] = r2Key || r2Axis
        val nx = e.getAxisValue(MotionEvent.AXIS_HAT_X).roundToInt(); val ny = e.getAxisValue(MotionEvent.AXIS_HAT_Y).roundToInt()
        if (nx != hx) { if (nx < 0) act(0) else if (nx > 0) act(1); hx = nx }
        if (ny != hy) { if (ny < 0) act(5) else if (ny > 0) act(4); hy = ny }
        refresh()
    }

    // ---------- toque ----------
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val m = dp(12f); val W = w.toFloat(); val H = h.toFloat()
        val vt = m + dp(24f)
        visor.set(m, vt, W - m, vt + H * 0.27f)
        jr = min(W * 0.2f, H * 0.12f); jcx = m + jr * 1.1f; jcy = visor.bottom + m + jr * 1.1f
        val bx0 = jcx + jr * 1.1f + m; val bw = (W - m - bx0 - m) / 2f; val bh = jr * 0.62f
        for (i in 0 until 6) {
            val l = bx0 + (i % 2) * (bw + m); val t = jcy - jr * 1.1f + (i / 2) * (bh + m * 0.6f)
            btnR[i].set(l, t, l + bw, t + bh)
        }
        val top = jcy + jr * 1.1f + m; val pw = (W - m * 5) / 4f; val ph = (H - top - m * 2) / 2f
        for (i in 0 until 7) {
            val l = m + (i % 4) * (pw + m); val t = top + (i / 4) * (ph + m)
            padR[i].set(l, t, l + pw, t + ph)
        }
    }
    private fun down(id: Int, x: Float, y: Float) {
        for (i in 0 until 7) if (padR[i].contains(x, y)) { touchChord[i] = true; ptrPad[id] = i; return }
        if (hypot(x - jcx, y - jcy) < jr * 1.3f) { joyPtr = id; joy(x, y); return }
        for (i in 0 until 6) if (btnR[i].contains(x, y)) { act(i); return }
    }
    private fun up(id: Int) {
        ptrPad.remove(id)?.let { touchChord[it] = false }
        if (id == joyPtr) { joyPtr = -1; jx = 0f; jy = 0f }
    }
    private fun joy(x: Float, y: Float) {
        var dx = (x - jcx) / jr; var dy = (y - jcy) / jr
        val m = hypot(dx, dy); if (m > 1f) { dx /= m; dy /= m }
        jx = dx; jy = -dy
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { val i = e.actionIndex; down(e.getPointerId(i), e.getX(i), e.getY(i)) }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) if (e.getPointerId(i) == joyPtr) joy(e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> { ptrPad.keys.toList().forEach { up(it) }; up(joyPtr) }
        }
        refresh(); return true
    }

    // ---------- desenho ----------
    private fun rr(r: RectF, rad: Float, c: Int) { p.style = Paint.Style.FILL; p.color = c; p.shader = null; canvas0.drawRoundRect(r, rad, rad, p) }
    private lateinit var canvas0: Canvas
    private fun txt(s: String, x: Float, y: Float, size: Float, c: Int, a: Paint.Align, mono: Boolean = false, bold: Boolean = false) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = c; p.textSize = dp(size); p.textAlign = a
        p.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
        p.isFakeBoldText = bold
        canvas0.drawText(s, x, y, p)
        p.isFakeBoldText = false
    }
    override fun onDraw(c: Canvas) {
        canvas0 = c
        c.drawColor(Color.parseColor("#0b0e14"))
        val panel = Color.parseColor("#1b2232")
        txt("HiChord", dp(14f), dp(26f), 18f, Color.WHITE, Paint.Align.LEFT, false, true)
        txt(if (padSeen) "🎮 controle" else "🎧 toque ou use um controle", width - dp(14f), dp(26f), 12f, teal, Paint.Align.RIGHT)
        // visor
        rr(visor, dp(18f), Color.parseColor("#05080d"))
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Color.parseColor("#12323a")
        for (i in 1 until 12) { val x = visor.left + visor.width() * i / 12; c.drawLine(x, visor.top, x, visor.bottom, p) }
        for (i in 1 until 6) { val y = visor.top + visor.height() * i / 6; c.drawLine(visor.left, y, visor.right, y, p) }
        val path = Path(); val n = 256; val sp = synth.scopePos
        for (i in 0 until n) {
            val v = synth.scope[(sp + i * 2) % synth.scope.size]
            val x = visor.left + visor.width() * i / (n - 1); val y = visor.centerY() - v * visor.height() * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        p.color = teal; p.strokeWidth = dp(2f); p.setShadowLayer(dp(8f), 0f, 0f, teal); c.drawPath(path, p); p.clearShadowLayer()
        val name = if (cur >= 0) cname(cur, mod) else "—"
        txt(name, visor.left + dp(16f), visor.top + dp(62f), 52f, Color.parseColor("#c9fff6"), Paint.Align.LEFT, true, true)
        if (cur >= 0) txt(notes(cur, mod).joinToString("  ") { nm(it) }, visor.right - dp(14f), visor.top + dp(24f), 12f, Color.parseColor("#9fe9dc"), Paint.Align.RIGHT, true)
        val info = "TOM ${NN[((key % 12) + 12) % 12]} ${if (minor) "MENOR" else "MAIOR"} · ${WAVES[synth.wave].uppercase()} · VOL ${(synth.vol * 100).roundToInt()}%" + (if (mod > 0) " · ${MODS[mod].uppercase()}" else "")
        txt(info, visor.left + dp(16f), visor.bottom - dp(12f), 11f, Color.parseColor("#9fe9dc"), Paint.Align.LEFT, true)
        // joystick
        p.style = Paint.Style.FILL; p.color = panel; c.drawCircle(jcx, jcy, jr * 1.1f, p)
        txt("7ª", jcx, jcy - jr * 0.85f, 10f, Color.GRAY, Paint.Align.CENTER); txt("sus4", jcx, jcy + jr * 1.0f, 10f, Color.GRAY, Paint.Align.CENTER)
        txt("add9", jcx + jr * 0.78f, jcy + dp(4f), 10f, Color.GRAY, Paint.Align.CENTER); txt("inv", jcx - jr * 0.78f, jcy + dp(4f), 10f, Color.GRAY, Paint.Align.CENTER)
        val kx = jcx + (if (abs(gx) > abs(jx)) gx else jx) * jr * 0.6f; val ky = jcy - (if (abs(gy) > abs(jy)) gy else jy) * jr * 0.6f
        p.shader = LinearGradient(kx - jr * 0.3f, ky - jr * 0.3f, kx + jr * 0.3f, ky + jr * 0.3f, teal, pink, Shader.TileMode.CLAMP)
        c.drawCircle(kx, ky, jr * 0.32f, p); p.shader = null
        // botões
        for (i in 0 until 6) {
            rr(btnR[i], dp(12f), panel)
            val label = when (i) { 2 -> if (minor) "Menor" else "Maior"; 3 -> WAVES[synth.wave]; else -> BTN[i] }
            txt(label, btnR[i].centerX(), btnR[i].centerY() + dp(5f), 13f, Color.WHITE, Paint.Align.CENTER)
        }
        // pads
        for (i in 0 until 7) {
            val on = i == cur
            rr(padR[i], dp(18f), if (on) teal else panel)
            txt(cname(i, mod), padR[i].centerX(), padR[i].centerY() + dp(2f), 19f, if (on) Color.parseColor("#06201c") else Color.WHITE, Paint.Align.CENTER, false, true)
            txt(ROM[i], padR[i].centerX(), padR[i].centerY() + dp(22f), 11f, if (on) Color.parseColor("#06201c") else Color.GRAY, Paint.Align.CENTER)
        }
        postInvalidateOnAnimation()
    }
}
