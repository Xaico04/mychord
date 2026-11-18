package com.hichord.app
import android.content.Context
import android.graphics.*
import android.view.*
import kotlin.math.*

class HiChordView(ctx: Context, private val synth: Synth) : View(ctx) {
    var onTheme: () -> Unit = {}
    var onResetTheme: () -> Unit = {}
    var onRotate: () -> Unit = {}
    var onUpdate: () -> Unit = {}
    private val updR = RectF(); private var updLabel = "Atualizar"; private var updHot = false
    fun setUpdate(label: String, hot: Boolean) { updLabel = label; updHot = hot; invalidate() }

    private val NN = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val MAJ = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MIN = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private val WAVES = arrayOf("Seno", "Triângulo", "Serra", "Quadrada")
    private val ROM = arrayOf("I", "II", "III", "IV", "V", "VI", "VII")
    // botão do controle de cada acorde: LB=C, RB=D, LT=E, RT=F, X=G, A=A, B=B
    private val PADL = arrayOf("LB", "RB", "LT", "RT", "X", "A", "B")
    private val MODS = arrayOf("", "7ª", "sus4", "add9", "inversão")
    private val BTN = arrayOf("Tom −", "Tom +", "Escala", "Onda", "Vol −", "Vol +", "Tema", "Padrão")
    private val NB = 8
    private var key = 0; private var minor = false; private var cur = -1; private var mod = 0
    private var padSeen = false
    private val touchChord = BooleanArray(7); private val padChord = BooleanArray(7)
    private val stamp = IntArray(7); private val prevH = BooleanArray(7); private var ctr = 0
    private var jx = 0f; private var jy = 0f; private var gx = 0f; private var gy = 0f
    private var l2Key = false; private var l2Axis = false; private var r2Key = false; private var r2Axis = false
    private var hx = 0; private var hy = 0
    private val padR = Array(7) { RectF() }; private val btnR = Array(NB) { RectF() }
    private val padArea = RectF(); private val rotR = RectF()
    private val visor = RectF(); private var jcx = 0f; private var jcy = 0f; private var jr = 1f
    private val ptrPad = HashMap<Int, Int>(); private var joyPtr = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teal = Color.parseColor("#5eead4"); private val pink = Color.parseColor("#f472b6")
    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ---------- tema ----------
    private var bg: Bitmap? = null
    private val bgSrc = Rect(); private val bgDst = Rect()
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var accent = teal; private var accent2 = pink
    private var panelC = Color.parseColor("#1b2232"); private var visorC = Color.parseColor("#05080d")
    private var gridC = Color.parseColor("#12323a")
    private var nameC = Color.parseColor("#c9fff6"); private var infoC = Color.parseColor("#9fe9dc")
    private var onAccent = Color.parseColor("#06201c")
    private val grayC = Color.parseColor("#9aa4b5")

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())

    /** Troca a imagem de fundo e adapta cores de todo o app a ela. null = tema padrão. */
    fun applyTheme(b: Bitmap?) {
        bg = b
        if (b == null) {
            accent = teal; accent2 = pink
            panelC = Color.parseColor("#1b2232"); visorC = Color.parseColor("#05080d")
            gridC = Color.parseColor("#12323a")
            nameC = Color.parseColor("#c9fff6"); infoC = Color.parseColor("#9fe9dc")
            onAccent = Color.parseColor("#06201c")
        } else {
            extractAccents(b)
        }
        updateBgRects(); invalidate()
    }
    private fun extractAccents(b: Bitmap) {
        val s = Bitmap.createScaledBitmap(b, 48, 48, true)
        val wt = FloatArray(12); val hs = FloatArray(12); val ss = FloatArray(12); val vs = FloatArray(12)
        val hsv = FloatArray(3)
        for (y in 0 until 48) for (x in 0 until 48) {
            Color.colorToHSV(s.getPixel(x, y), hsv)
            if (hsv[1] < 0.3f || hsv[2] < 0.4f) continue
            val w = hsv[1] * hsv[2]; val k = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            wt[k] += w; hs[k] += hsv[0] * w; ss[k] += hsv[1] * w; vs[k] += hsv[2] * w
        }
        var k1 = 0
        for (k in 1 until 12) if (wt[k] > wt[k1]) k1 = k
        var h1 = 220f
        if (wt[k1] < 1f) {
            accent = Color.parseColor("#e5e7eb"); accent2 = Color.parseColor("#94a3b8")
        } else {
            h1 = hs[k1] / wt[k1]
            accent = Color.HSVToColor(floatArrayOf(h1, (ss[k1] / wt[k1]).coerceIn(0.55f, 0.95f), (vs[k1] / wt[k1]).coerceIn(0.85f, 1f)))
            var k2 = -1
            for (k in 0 until 12) {
                val d = min((k - k1 + 12) % 12, (k1 - k + 12) % 12)
                if (d >= 2 && wt[k] >= wt[k1] * 0.25f && (k2 < 0 || wt[k] > wt[k2])) k2 = k
            }
            accent2 = if (k2 >= 0) Color.HSVToColor(floatArrayOf(hs[k2] / wt[k2], (ss[k2] / wt[k2]).coerceIn(0.55f, 0.95f), (vs[k2] / wt[k2]).coerceIn(0.85f, 1f)))
            else Color.HSVToColor(floatArrayOf((h1 + 40f) % 360f, 0.6f, 1f))
        }
        panelC = Color.HSVToColor(200, floatArrayOf(h1, 0.45f, 0.14f))
        visorC = Color.argb(205, 4, 7, 12)
        gridC = Color.argb(50, Color.red(accent), Color.green(accent), Color.blue(accent))
        nameC = mix(accent, Color.WHITE, 0.75f); infoC = mix(accent, Color.WHITE, 0.5f)
        val lum = 0.299f * Color.red(accent) + 0.587f * Color.green(accent) + 0.114f * Color.blue(accent)
        onAccent = if (lum > 150f) Color.parseColor("#0b0e14") else Color.WHITE
    }
    private fun updateBgRects() {
        val b = bg ?: return
        if (width <= 0 || height <= 0) return
        val bw = b.width; val bh = b.height
        val vr = width.toFloat() / height; val br = bw.toFloat() / bh
        if (br > vr) { val nw = (bh * vr).toInt(); val x0 = (bw - nw) / 2; bgSrc.set(x0, 0, x0 + nw, bh) }
        else { val nh = (bw / vr).toInt(); val y0 = (bh - nh) / 2; bgSrc.set(0, y0, bw, y0 + nh) }
        bgDst.set(0, 0, width, height)
    }

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
            6 -> { onTheme() }
            7 -> { onResetTheme() }
        }
        applyChord(); invalidate()
    }

    // ---------- controle ----------
    fun onPadKey(code: Int, down: Boolean, rep: Int): Boolean {
        val ci = when (code) {
            KeyEvent.KEYCODE_BUTTON_L1 -> 0
            KeyEvent.KEYCODE_BUTTON_R1 -> 1
            KeyEvent.KEYCODE_BUTTON_L2 -> 2
            KeyEvent.KEYCODE_BUTTON_R2 -> 3
            KeyEvent.KEYCODE_BUTTON_X -> 4
            KeyEvent.KEYCODE_BUTTON_A -> 5
            KeyEvent.KEYCODE_BUTTON_B -> 6
            else -> -1
        }
        if (ci >= 0) {
            padSeen = true
            when (ci) {
                2 -> { l2Key = down; padChord[2] = l2Key || l2Axis }
                3 -> { r2Key = down; padChord[3] = r2Key || r2Axis }
                else -> padChord[ci] = down
            }
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
        l2Axis = max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f
        r2Axis = max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f
        padChord[2] = l2Key || l2Axis
        padChord[3] = r2Key || r2Axis
        val nx = e.getAxisValue(MotionEvent.AXIS_HAT_X).roundToInt(); val ny = e.getAxisValue(MotionEvent.AXIS_HAT_Y).roundToInt()
        if (nx != hx) { if (nx < 0) act(0) else if (nx > 0) act(1); hx = nx }
        if (ny != hy) { if (ny < 0) act(5) else if (ny > 0) act(4); hy = ny }
        refresh()
    }

    // ---------- layout (deitado e em pé) ----------
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val m = dp(12f); val W = w.toFloat(); val H = h.toFloat()
        val vt = m + dp(24f)
        rotR.set(dp(96f), dp(6f), dp(168f), dp(34f))
        updR.set(dp(176f), dp(6f), dp(284f), dp(34f))
        val gap = m * 0.5f
        val bt: Float; val bx0: Float; val bw: Float; val bh: Float
        val pTop: Float; val pLeft: Float; val pw: Float; val ph: Float
        if (W > H) {
            val leftW = W * 0.40f
            visor.set(m, vt, leftW, vt + (H - vt - m) * 0.42f)
            val rh = H - m - visor.bottom - m
            jr = min(rh / 2.2f, (leftW - m) * 0.22f)
            jcx = m + jr * 1.1f; jcy = visor.bottom + m + rh / 2f
            bx0 = jcx + jr * 1.1f + m; bw = (leftW - bx0 - m) / 2f
            bt = visor.bottom + m; bh = (rh - 3 * gap) / 4f
            pLeft = leftW + m; pTop = vt
            pw = (W - m - pLeft - 3 * m) / 4f; ph = (H - m - pTop - m) / 2f
        } else {
            visor.set(m, vt, W - m, vt + H * 0.27f)
            jr = min(W * 0.2f, H * 0.12f); jcx = m + jr * 1.1f; jcy = visor.bottom + m + jr * 1.1f
            bx0 = jcx + jr * 1.1f + m; bw = (W - m - bx0 - m) / 2f
            bt = jcy - jr * 1.1f; bh = (jr * 2.2f - 3 * gap) / 4f
            pLeft = m; pTop = jcy + jr * 1.1f + m
            pw = (W - m * 5) / 4f; ph = (H - pTop - m * 2) / 2f
        }
        for (i in 0 until NB) {
            val l = bx0 + (i % 2) * (bw + m); val t = bt + (i / 2) * (bh + gap)
            btnR[i].set(l, t, l + bw, t + bh)
        }
        for (i in 0 until 7) {
            val l = pLeft + (i % 4) * (pw + m); val t = pTop + (i / 4) * (ph + m)
            padR[i].set(l, t, l + pw, t + ph)
        }
        var l0 = padR[0].left; var t0 = padR[0].top; var r0 = padR[0].right; var b0 = padR[0].bottom
        for (i in 1 until 7) {
            l0 = min(l0, padR[i].left); t0 = min(t0, padR[i].top); r0 = max(r0, padR[i].right); b0 = max(b0, padR[i].bottom)
        }
        padArea.set(l0 - m * 0.5f, t0 - m * 0.5f, r0 + m * 0.5f, b0 + m * 0.5f)
        updateBgRects()
    }

    // ---------- toque (arrastar o dedo troca de acorde sem soltar) ----------
    private fun hitPad(x: Float, y: Float): Int { for (i in 0 until 7) if (padR[i].contains(x, y)) return i; return -1 }
    private fun syncTouch() {
        for (i in 0 until 7) touchChord[i] = false
        for (v in ptrPad.values) if (v >= 0) touchChord[v] = true
    }
    private fun down(id: Int, x: Float, y: Float) {
        val pi = hitPad(x, y)
        if (pi >= 0) { ptrPad[id] = pi; syncTouch(); return }
        if (rotR.contains(x, y)) { onRotate(); return }
        if (updR.contains(x, y)) { onUpdate(); return }
        if (hypot(x - jcx, y - jcy) < jr * 1.3f) { joyPtr = id; joy(x, y); return }
        for (i in 0 until NB) if (btnR[i].contains(x, y)) { act(i); return }
        if (padArea.contains(x, y)) ptrPad[id] = -1
    }
    private fun slide(id: Int, x: Float, y: Float) {
        val old = ptrPad[id] ?: return
        val nw = hitPad(x, y)
        if (nw >= 0) {
            if (nw != old) { ptrPad[id] = nw; syncTouch() }
        } else if (old >= 0 && !padArea.contains(x, y)) {
            ptrPad[id] = -1; syncTouch()
        }
    }
    private fun up(id: Int) {
        ptrPad.remove(id); syncTouch()
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
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) {
                val id = e.getPointerId(i)
                if (id == joyPtr) joy(e.getX(i), e.getY(i)) else slide(id, e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> { ptrPad.clear(); syncTouch(); joyPtr = -1; jx = 0f; jy = 0f }
        }
        refresh(); return true
    }

    // ---------- desenho ----------
    private fun rr(r: RectF, rad: Float, c: Int) { p.style = Paint.Style.FILL; p.color = c; p.shader = null; canvas0.drawRoundRect(r, rad, rad, p) }
    private lateinit var canvas0: Canvas
    private fun txt(s: String, x: Float, y: Float, size: Float, c: Int, a: Paint.Align, mono: Boolean = false, bold: Boolean = false, maxW: Float = 0f) {
        p.style = Paint.Style.FILL; p.shader = null; p.color = c; p.textSize = dp(size); p.textAlign = a
        p.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
        p.isFakeBoldText = bold
        if (maxW > 0f) { val tw = p.measureText(s); if (tw > maxW) p.textSize = p.textSize * maxW / tw }
        canvas0.drawText(s, x, y, p)
        p.isFakeBoldText = false
    }
    override fun onDraw(c: Canvas) {
        canvas0 = c
        val b = bg
        if (b != null) { c.drawBitmap(b, bgSrc, bgDst, bgPaint); c.drawColor(Color.argb(115, 0, 0, 0)) }
        else c.drawColor(Color.parseColor("#0b0e14"))
        txt("HiChord", dp(14f), dp(26f), 18f, Color.WHITE, Paint.Align.LEFT, false, true)
        rr(rotR, dp(14f), panelC)
        txt("Girar", rotR.centerX(), rotR.centerY() + dp(4f), 12f, Color.WHITE, Paint.Align.CENTER)
        rr(updR, dp(14f), if (updHot) accent else panelC)
        txt(updLabel, updR.centerX(), updR.centerY() + dp(4f), 12f, if (updHot) onAccent else Color.WHITE, Paint.Align.CENTER, false, updHot, updR.width() - dp(10f))
        txt(if (padSeen) "🎮" else "🎧", width - dp(14f), dp(26f), 16f, accent, Paint.Align.RIGHT)
        // visor
        rr(visor, dp(18f), visorC)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = gridC
        for (i in 1 until 12) { val x = visor.left + visor.width() * i / 12; c.drawLine(x, visor.top, x, visor.bottom, p) }
        for (i in 1 until 6) { val y = visor.top + visor.height() * i / 6; c.drawLine(visor.left, y, visor.right, y, p) }
        val path = Path(); val n = 256; val sp = synth.scopePos
        for (i in 0 until n) {
            val v = synth.scope[(sp + i * 2) % synth.scope.size]
            val x = visor.left + visor.width() * i / (n - 1); val y = visor.centerY() - v * visor.height() * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        p.color = accent; p.strokeWidth = dp(2f); p.setShadowLayer(dp(8f), 0f, 0f, accent); c.drawPath(path, p); p.clearShadowLayer()
        val name = if (cur >= 0) cname(cur, mod) else "—"
        txt(name, visor.left + dp(16f), visor.top + dp(62f), 52f, nameC, Paint.Align.LEFT, true, true, visor.width() * 0.55f)
        if (cur >= 0) txt(notes(cur, mod).joinToString("  ") { nm(it) }, visor.right - dp(14f), visor.top + dp(24f), 12f, infoC, Paint.Align.RIGHT, true, false, visor.width() * 0.5f)
        val info = "TOM ${NN[((key % 12) + 12) % 12]} ${if (minor) "MENOR" else "MAIOR"} · ${WAVES[synth.wave].uppercase()} · VOL ${(synth.vol * 100).roundToInt()}%" + (if (mod > 0) " · ${MODS[mod].uppercase()}" else "")
        txt(info, visor.left + dp(16f), visor.bottom - dp(12f), 11f, infoC, Paint.Align.LEFT, true, false, visor.width() - dp(32f))
        // joystick
        p.style = Paint.Style.FILL; p.shader = null; p.color = panelC; c.drawCircle(jcx, jcy, jr * 1.1f, p)
        txt("7ª", jcx, jcy - jr * 0.85f, 10f, grayC, Paint.Align.CENTER); txt("sus4", jcx, jcy + jr * 1.0f, 10f, grayC, Paint.Align.CENTER)
        txt("add9", jcx + jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER); txt("inv", jcx - jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER)
        val kx = jcx + (if (abs(gx) > abs(jx)) gx else jx) * jr * 0.6f; val ky = jcy - (if (abs(gy) > abs(jy)) gy else jy) * jr * 0.6f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(kx - jr * 0.3f, ky - jr * 0.3f, kx + jr * 0.3f, ky + jr * 0.3f, accent, accent2, Shader.TileMode.CLAMP)
        c.drawCircle(kx, ky, jr * 0.32f, p); p.shader = null
        // botões
        for (i in 0 until NB) {
            rr(btnR[i], dp(12f), panelC)
            val label = when (i) { 2 -> if (minor) "Menor" else "Maior"; 3 -> WAVES[synth.wave]; else -> BTN[i] }
            txt(label, btnR[i].centerX(), btnR[i].centerY() + dp(5f), 13f, Color.WHITE, Paint.Align.CENTER, false, false, btnR[i].width() - dp(8f))
        }
        // pads
        for (i in 0 until 7) {
            val on = i == cur
            rr(padR[i], dp(18f), if (on) accent else panelC)
            txt(cname(i, mod), padR[i].centerX(), padR[i].centerY() + dp(2f), 19f, if (on) onAccent else Color.WHITE, Paint.Align.CENTER, false, true, padR[i].width() - dp(10f))
            txt(ROM[i] + (if (padSeen) " · " + PADL[i] else ""), padR[i].centerX(), padR[i].centerY() + dp(22f), 11f, if (on) onAccent else grayC, Paint.Align.CENTER, false, false, padR[i].width() - dp(10f))
        }
        postInvalidateOnAnimation()
    }
}
