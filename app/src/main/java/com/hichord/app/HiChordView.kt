package com.hichord.app
import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.Toast
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

    // ---------- modos de toque e efeitos ----------
    private val MODES = arrayOf("Play", "Strum", "Arpejo", "Drone", "Lead", "Sequencer", "Ouvido")
    private val LV = floatArrayOf(0f, 0.3f, 0.6f, 1f)
    private val BPMS = intArrayOf(70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180)
    private val ARPD = arrayOf("Sobe", "Desce", "Vai-vem", "Aleatório")
    private val DLT = intArrayOf(150, 250, 350, 450)
    private val FXN = arrayOf("Reverb", "Delay", "Chorus", "Flanger")
    private val LMODS = arrayOf("", "8VA ↑", "8VA ↓", "VIBRATO", "15ª ↑")
    private val JLP = arrayOf("7ª", "sus4", "add9", "inv")
    private val JLL = arrayOf("8va ↑", "8va ↓", "vib", "15ª ↑")
    private var modo = 0
    private val fxLvl = IntArray(4)
    private var arpDirI = 0; private var bpmI = 5; private var dlI = 2
    private var page = 0; private var strumDown = false
    private val PAGES = arrayOf("Menu", "FX", "Loop", "Bateria", "MIDI")
    private val KITS = arrayOf("808", "Rock", "Lo-Fi", "Techno", "Jazz", "Eletro", "Acústico")
    private val LANES = arrayOf("Kick", "Caixa", "Hat fechado", "Hat aberto", "Palma", "Tom", "Perc", "Melodia")
    private val PRESETS = arrayOf("Vazio", "Rock", "Hip-hop", "Disco", "Techno", "Reggae", "Funk")
    private val PDATA = arrayOf(
        arrayOf("0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000"),
        arrayOf("1000001010000010", "0000100000001000", "1010101010101010", "0000000000000000", "0000000000000000", "0000000000000000", "0000000000000000", "1030204010302040"),
        arrayOf("1000000110100000", "0000100000001000", "1010101010101010", "0000000000000010", "0000100000001000", "0000000000000000", "0000000000000000", "1000300020004000"),
        arrayOf("1000100010001000", "0000000000000000", "1010101010101010", "0010001000100010", "0000100000001000", "0000000000000000", "0000000000000000", "1030503010305030"),
        arrayOf("1000100010001000", "0000000000000000", "1011101110111011", "0010001000100010", "0000100000001000", "0000000000000000", "0000000000100000", "1010303010105050"),
        arrayOf("0000000010000000", "0000000010000000", "1010101010101010", "0000000000000000", "0000000000000000", "0000000000000000", "0010001000100010", "0000300000004000"),
        arrayOf("1000010010100100", "0000100000001001", "1011101110111011", "0000000000000010", "0000000000000000", "0000000000000000", "0000000000100000", "1020304010203040")
    )
    private val BARS = intArrayOf(1, 2, 4)
    private val DVOL = floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f)
    private val EARL = arrayOf("Fácil", "Normal", "Difícil")
    private var lane = 0; private var presetI = 1; private var barsI = 1; private var dvI = 3
    private var seqChord = 0; private var seqMod = 0
    private var earQ = -1; private var earState = 0; private var earMsg = "Toque para ouvir"
    private var earOk = 0; private var earTot = 0; private var earStreak = 0; private var earLvl = 1; private var earGen = 0
    private val stepR = Array(16) { RectF() }
    private val tmpR = RectF()
    private val midi = MidiIO(ctx, synth)
    private val fxR = RectF(); private val modeR = RectF()
    private val sp = ctx.getSharedPreferences("hichord", Context.MODE_PRIVATE)
    init {
        for (i in 0 until 4) fxLvl[i] = sp.getInt("fx$i", 0).coerceIn(0, 3)
        arpDirI = sp.getInt("arp_dir", 0).coerceIn(0, ARPD.size - 1)
        bpmI = sp.getInt("bpm2", 5).coerceIn(0, BPMS.size - 1)
        dlI = sp.getInt("dl_t", 2).coerceIn(0, DLT.size - 1)
        synth.kit = sp.getInt("kit", 0).coerceIn(0, KITS.size - 1)
        lane = sp.getInt("lane", 0).coerceIn(0, 7)
        presetI = sp.getInt("preset", 1).coerceIn(0, PRESETS.size - 1)
        barsI = sp.getInt("bars_i", 1).coerceIn(0, BARS.size - 1)
        dvI = sp.getInt("dvol_i", 3).coerceIn(0, DVOL.size - 1)
        earLvl = sp.getInt("ear", 1).coerceIn(0, 2)
        midi.sendOn = sp.getBoolean("m_send", true); midi.recvOn = sp.getBoolean("m_recv", true)
        midi.clockOn = sp.getBoolean("m_clock", false); midi.channel = sp.getInt("m_ch", 0).coerceIn(0, 15)
        midi.onChange = { postInvalidate() }
        val ps = sp.getString("pat", null)
        if (ps != null && ps.length == 128) {
            for (i in 0 until 128) synth.pat[i] = (ps[i] - '0').toByte()
        } else {
            loadPreset(1)
        }
        pushFx()
        setMode(sp.getInt("modo", 0).coerceIn(0, MODES.size - 1), false)
    }
    fun midiStart() { midi.start() }
    fun midiStop() { midi.stop() }
    private fun loadPreset(i: Int) {
        val d = PDATA[i]
        for (l in 0 until 8) for (s in 0 until 16) synth.pat[l * 16 + s] = (d[l][s] - '0').toByte()
    }
    private fun hz(n: Int) = 440f * 2f.pow((n - 69) / 12f)
    /** nota do Lead: grau da escala (0..6) uma oitava acima do acorde, com a oitava escolhida no analógico */
    private fun leadNote(deg: Int): Int {
        val sc = if (minor) MIN else MAJ
        val sh = when (mod) { 1 -> 12; 2 -> -12; 4 -> 24; else -> 0 }
        return 60 + key + sn(sc, deg) + sh
    }
    private fun pushFx() {
        synth.rvMix = LV[fxLvl[0]]; synth.dlMix = LV[fxLvl[1]]
        synth.chMix = LV[fxLvl[2]]; synth.flMix = LV[fxLvl[3]]
        synth.arpDir = arpDirI; synth.bpm = BPMS[bpmI]; synth.dlTimeMs = DLT[dlI].toFloat()
        synth.bars = BARS[barsI]; synth.drumVol = DVOL[dvI]
    }
    private fun saveFx() {
        val sb = StringBuilder()
        for (i in 0 until 128) sb.append(('0'.code + synth.pat[i]).toChar())
        sp.edit().putInt("fx0", fxLvl[0]).putInt("fx1", fxLvl[1]).putInt("fx2", fxLvl[2]).putInt("fx3", fxLvl[3])
            .putInt("arp_dir", arpDirI).putInt("bpm2", bpmI).putInt("dl_t", dlI)
            .putInt("kit", synth.kit).putInt("lane", lane).putInt("preset", presetI).putInt("bars_i", barsI).putInt("dvol_i", dvI)
            .putInt("ear", earLvl).putString("pat", sb.toString())
            .putBoolean("m_send", midi.sendOn).putBoolean("m_recv", midi.recvOn).putBoolean("m_clock", midi.clockOn).putInt("m_ch", midi.channel)
            .apply()
    }
    private fun setMode(m: Int, save: Boolean) {
        modo = m
        earGen++
        synth.allOff(); synth.vib = 0f
        synth.pluck = (m == 1)
        synth.seqOn = (m == 5)
        for (k in 0 until Synth.NV) synth.glide[k] = 0f
        when (m) {
            0 -> { synth.atkT = 0.02f; synth.relT = 0.25f }
            1 -> { synth.atkT = 0.004f; synth.relT = 0.5f }
            2 -> { synth.atkT = 0.004f; synth.relT = 0.07f }
            3 -> { synth.atkT = 0.03f; synth.relT = 0.35f; synth.glide[4] = 0.00015f; synth.glide[5] = 0.00015f }
            4 -> { synth.atkT = 0.012f; synth.relT = 0.12f; synth.glide[0] = 0.0006f }
            5 -> { synth.atkT = 0.004f; synth.relT = 0.09f; updateSeq() }
            else -> { synth.atkT = 0.01f; synth.relT = 0.15f; earEnter() }
        }
        if (save) sp.edit().putInt("modo", m).apply()
        if (cur >= 0) applyChord(true)
        invalidate()
    }
    private fun nextMode() = setMode((modo + 1) % MODES.size, true)
    private fun anyFx() = fxLvl.any { it > 0 }
    private fun toast(s: String) { Toast.makeText(context, s, Toast.LENGTH_SHORT).show() }
    private fun loopHasData(): Boolean { for (k in 0 until Synth.NT) { val t = synth.tState.get(k); if (t in 2..4) return true }; return false }
    private fun cycleBpm() {
        if (loopHasData()) { toast("Limpe o looper para mudar o andamento"); return }
        bpmI = (bpmI + 1) % BPMS.size; pushFx()
    }
    private fun updateSeq() {
        val ns = notes(seqChord, seqMod).sorted()
        for (i in ns.indices) synth.seqF[i] = hz(ns[i])
        synth.seqN = ns.size
    }
    private fun btnLabel(i: Int): String = when (page) {
        0 -> when (i) { 2 -> if (minor) "Menor" else "Maior"; 3 -> WAVES[synth.wave]; else -> BTN[i] }
        1 -> fxText(i)
        2 -> loopText(i)
        3 -> drumText(i)
        else -> midiText(i)
    }
    private fun btnHot(i: Int): Boolean = when (page) {
        1 -> fxOn(i)
        2 -> if (i < 6) (synth.tState.get(i) == 2 || synth.tState.get(i) == 3) else (i == 6 && synth.playing)
        3 -> i == 2 && synth.playing
        4 -> midiHot(i)
        else -> false
    }
    private fun loopText(i: Int): String {
        if (i < 6) {
            val st = when (synth.tState.get(i)) { 0 -> "vazio"; 1 -> "aguarda…"; 2 -> "gravando"; 3 -> "toca"; else -> "mudo" }
            return "T" + (i + 1) + " " + st
        }
        return if (i == 6) (if (synth.playing) "■ Parar" else "▶ Tocar") else "Limpar tudo"
    }
    private fun drumText(i: Int): String = when (i) {
        0 -> "Kit: " + KITS[synth.kit]
        1 -> "Pista: " + LANES[lane]
        2 -> if (synth.playing) "■ Parar" else "▶ Tocar"
        3 -> BPMS[bpmI].toString() + " BPM"
        4 -> "Ritmo: " + PRESETS[presetI]
        5 -> "Limpar pista"
        6 -> "Loop: " + BARS[barsI] + " comp."
        else -> if (DVOL[dvI] == 0f) "Bateria: mudo" else "Bateria " + (DVOL[dvI] * 100).roundToInt() + "%"
    }
    private fun midiText(i: Int): String = when (i) {
        0 -> "MIDI: " + midi.label()
        1 -> "Canal " + (midi.channel + 1)
        2 -> "Saída: " + (if (midi.sendOn) "on" else "off")
        3 -> "Entrada: " + (if (midi.recvOn) "on" else "off")
        4 -> "Clock: " + (if (midi.clockOn) "on" else "off")
        5 -> "Ouvido: " + EARL[earLvl]
        6 -> "Notas OFF"
        else -> "Zerar placar"
    }
    private fun midiHot(i: Int): Boolean = when (i) { 2 -> midi.sendOn; 3 -> midi.recvOn; 4 -> midi.clockOn; else -> false }
    private fun actLoop(i: Int) {
        if (i < 6) {
            when (synth.tState.get(i)) {
                0 -> {
                    if (synth.prepareTrack(i)) {
                        synth.tState.set(i, 1)
                        if (!synth.playing) synth.transport(true)
                    } else toast("Memória insuficiente para gravar")
                }
                1, 2 -> synth.tState.set(i, 0)
                3 -> synth.tState.set(i, 4)
                else -> synth.tState.set(i, 3)
            }
        } else if (i == 6) {
            synth.transport(!synth.playing)
        } else {
            for (k in 0 until Synth.NT) synth.tState.set(k, 0)
        }
        invalidate()
    }
    private fun actDrum(i: Int) {
        when (i) {
            0 -> synth.kit = (synth.kit + 1) % KITS.size
            1 -> lane = (lane + 1) % 8
            2 -> synth.transport(!synth.playing)
            3 -> cycleBpm()
            4 -> { presetI = (presetI + 1) % PRESETS.size; loadPreset(presetI) }
            5 -> { for (s in 0 until 16) synth.pat[lane * 16 + s] = 0 }
            6 -> {
                if (loopHasData()) toast("Limpe o looper para mudar o tamanho")
                else { barsI = (barsI + 1) % BARS.size; synth.bars = BARS[barsI] }
            }
            else -> { dvI = (dvI + 1) % DVOL.size; synth.drumVol = DVOL[dvI] }
        }
        saveFx(); invalidate()
    }
    private fun actMidi(i: Int) {
        when (i) {
            0 -> midi.cycle()
            1 -> midi.setChannel((midi.channel + 1) % 16)
            2 -> midi.setSend(!midi.sendOn)
            3 -> midi.setRecv(!midi.recvOn)
            4 -> midi.setClock(!midi.clockOn)
            5 -> earLvl = (earLvl + 1) % 3
            6 -> midi.panic()
            else -> { earOk = 0; earTot = 0; earStreak = 0 }
        }
        saveFx(); invalidate()
    }
    private fun toggleStep(i: Int) {
        val idx = lane * 16 + i
        val v = synth.pat[idx].toInt()
        synth.pat[idx] = (if (lane == 7) (v + 1) % 9 else if (v == 0) 1 else 0).toByte()
        saveFx(); invalidate()
    }
    // ---- treino de ouvido ----
    private fun earAt(gen: Int, ms: Long, f: () -> Unit) { postDelayed({ if (gen == earGen && modo == 6) f() }, ms) }
    private fun earTone(ns: IntArray, on: Boolean) {
        if (on) { for (k in ns.indices) { synth.freq[k] = hz(ns[k]); synth.gate(k, true) } }
        else { for (k in 0 until 4) synth.gate(k, false) }
    }
    private fun earEnter() {
        earState = 0; earQ = -1; earMsg = "Toque para ouvir"
        earAt(earGen, 500L) { earQuestion() }
    }
    private fun earQuestion() {
        val pool = if (earLvl == 0) intArrayOf(0, 2, 4) else intArrayOf(0, 1, 2, 3, 4, 5, 6)
        var q = pool[kotlin.random.Random.nextInt(pool.size)]
        if (q == earQ) q = pool[kotlin.random.Random.nextInt(pool.size)]
        earQ = q; earState = 1; earMsg = "Qual nota?"
        val gen = ++earGen
        earAt(gen, 300L) { earPlay() }
        invalidate()
    }
    private fun earPlay() {
        val gen = ++earGen
        val sc = if (minor) MIN else MAJ
        val tgt = intArrayOf(60 + key + sn(sc, earQ))
        val ref = if (earLvl == 2) notes(0, 0).toIntArray() else intArrayOf(60 + key + sn(sc, 0))
        earTone(ref, true)
        earAt(gen, 650L) { earTone(ref, false) }
        earAt(gen, 850L) { earTone(tgt, true) }
        earAt(gen, 1650L) { earTone(tgt, false) }
    }
    private fun earReplay() { if (earState == 0) earQuestion() else if (earState == 1) earPlay() }
    private fun earAnswer(d: Int) {
        if (earState != 1) { if (earState == 0) earQuestion(); return }
        val sc = if (minor) MIN else MAJ
        val tgt = 60 + key + sn(sc, earQ)
        earTot++
        if (d == earQ) { earOk++; earStreak++; earMsg = "✔ Certo!" }
        else { earStreak = 0; earMsg = "✘ Era " + ROM[earQ] + " · " + NN[((tgt % 12) + 12) % 12] }
        earState = 2
        val gen = ++earGen
        earTone(intArrayOf(tgt), true)
        earAt(gen, 900L) { earTone(intArrayOf(tgt), false) }
        earAt(gen, 1500L) { earQuestion() }
        invalidate()
    }
    private fun drawSteps() {
        rr(visor, dp(18f), visorC)
        val head = LANES[lane].uppercase() + " · KIT " + KITS[synth.kit].uppercase() + " · " + BPMS[bpmI] + " BPM" + (if (synth.playing) " ▶" else "")
        txt(head, visor.left + dp(14f), visor.top + dp(20f), 11f, infoC, Paint.Align.LEFT, true, false, visor.width() - dp(28f))
        val cur16 = if (synth.playing) synth.uiStep % 16 else -1
        for (i in 0 until 16) {
            val r = stepR[i]; val v = synth.pat[lane * 16 + i].toInt()
            val c = if (v > 0) accent else if (i % 4 == 0) mix(panelC, Color.WHITE, 0.18f) else mix(panelC, Color.WHITE, 0.07f)
            rr(r, dp(6f), c)
            if (v > 0 && lane == 7) txt(v.toString(), r.centerX(), r.centerY() + dp(5f), 13f, onAccent, Paint.Align.CENTER, false, true)
            if (i == cur16) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = Color.WHITE
                canvas0.drawRoundRect(r, dp(6f), dp(6f), p)
                p.style = Paint.Style.FILL
            }
        }
    }
    private fun drawLoopStrip() {
        val x0 = visor.left + dp(16f); val x1 = visor.right - dp(16f)
        val total = 16 * synth.bars
        val prog = if (synth.playing) (synth.uiStep + 0.5f) / total else 0f
        val by = visor.bottom - dp(46f)
        tmpR.set(x0, by, x1, by + dp(3f)); rr(tmpR, dp(2f), Color.argb(50, 255, 255, 255))
        tmpR.set(x0, by, x0 + (x1 - x0) * prog, by + dp(3f)); rr(tmpR, dp(2f), accent)
        val cw = (x1 - x0) / 6f
        for (k in 0 until 6) {
            val st = synth.tState.get(k)
            val c = when (st) { 0 -> Color.argb(40, 255, 255, 255); 1 -> mix(accent, Color.BLACK, 0.5f); 2 -> Color.rgb(239, 68, 68); 3 -> accent; else -> grayC }
            tmpR.set(x0 + k * cw + dp(2f), by + dp(8f), x0 + (k + 1) * cw - dp(2f), by + dp(26f)); rr(tmpR, dp(6f), c)
            txt((k + 1).toString(), tmpR.centerX(), tmpR.centerY() + dp(4f), 11f, if (st == 0) grayC else onAccent, Paint.Align.CENTER, false, true)
        }
    }
    private fun fxText(i: Int): String = when (i) {
        0, 1, 2, 3 -> FXN[i] + (if (fxLvl[i] == 0) " off" else " " + (LV[fxLvl[i]] * 100).roundToInt() + "%")
        4 -> "Arp: " + ARPD[arpDirI]
        5 -> "Tempo " + BPMS[bpmI] + " bpm"
        6 -> "Eco " + DLT[dlI] + " ms"
        else -> "◂ Voltar"
    }
    private fun fxOn(i: Int) = i in 0..3 && fxLvl[i] > 0
    private fun tapBtn(i: Int) { when (page) { 0 -> act(i); 1 -> actFx(i); 2 -> actLoop(i); 3 -> actDrum(i); else -> actMidi(i) } }
    private fun actFx(i: Int) {
        when (i) {
            0, 1, 2, 3 -> fxLvl[i] = (fxLvl[i] + 1) % 4
            4 -> arpDirI = (arpDirI + 1) % ARPD.size
            5 -> cycleBpm()
            6 -> dlI = (dlI + 1) % DLT.size
            else -> page = 0
        }
        pushFx(); saveFx(); invalidate()
    }

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
    /** newEvent = true: acorde/modificador mudou (dispara strum/arpejo); false: só reafina (tom, onda). */
    private fun applyChord(newEvent: Boolean) {
        when (modo) {
            0 -> {   // Play: acorde sustentado enquanto o pad está apertado
                if (cur < 0) { for (k in 0 until 4) synth.gate(k, false); return }
                val ns = notes(cur, mod)
                for (k in 0 until 4) {
                    if (k < ns.size) { synth.freq[k] = hz(ns[k]); synth.gate(k, true) } else synth.gate(k, false)
                }
            }
            1 -> {   // Strum: notas em sequência, alternando para baixo e para cima; soam até decair
                if (cur < 0 || !newEvent) return
                val ns = notes(cur, mod).sorted()
                for (k in ns.indices) synth.tf[k] = hz(ns[k])
                strumDown = !strumDown
                synth.strum(ns.size, strumDown)
            }
            2 -> {   // Arpejo: notas do acorde em duas oitavas, no andamento e direção escolhidos
                if (cur < 0) { synth.arpOn = false; return }
                val ns = notes(cur, mod).sorted()
                for (i in 0 until ns.size * 2) synth.arpF[i] = hz(ns[i % ns.size] + 12 * (i / ns.size))
                synth.arpN = ns.size * 2
                synth.arpOn = true
            }
            3 -> {   // Drone: acorde enquanto aperta; o grave (fundamental + quinta) continua soando
                if (cur < 0) { for (k in 0 until 4) synth.gate(k, false); return }
                val ns = notes(cur, mod)
                for (k in 0 until 4) {
                    if (k < ns.size) { synth.freq[k] = hz(ns[k]); synth.gate(k, true) } else synth.gate(k, false)
                }
                val r = notes(cur, 0)[0]
                synth.freq[4] = hz(r - 12); synth.freq[5] = hz(r - 5)
                synth.gate(4, true); synth.gate(5, true)
            }
            4 -> {   // Lead: cada pad é um grau da escala (uma nota só, com glissando entre elas)
                if (cur < 0) { synth.gate(0, false); synth.vib = 0f; return }
                synth.freq[0] = hz(leadNote(cur))
                synth.vib = if (mod == 3) 0.35f else 0f
                synth.gate(0, true)
            }
            5 -> {   // Sequencer: o último acorde tocado vira a harmonia da melodia da pista 8
                if (cur >= 0 && newEvent) { seqChord = cur; seqMod = mod; if (!synth.playing) synth.transport(true) }
                updateSeq()
            }
            else -> {   // Ouvido: os pads são as respostas (graus da escala)
                if (cur >= 0 && newEvent) earAnswer(cur)
            }
        }
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
        if (n != cur || m != mod) { cur = n; mod = m; applyChord(true) }
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
            8 -> { nextMode() }
            9 -> { synth.transport(!synth.playing) }
            10 -> { page = (page + 1) % PAGES.size }
        }
        applyChord(false); invalidate()
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
                KeyEvent.KEYCODE_BUTTON_Y -> 8
                KeyEvent.KEYCODE_BUTTON_START -> 9
                KeyEvent.KEYCODE_BUTTON_THUMBR -> 10
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
        rotR.set(dp(92f), dp(6f), dp(154f), dp(34f))
        updR.set(dp(160f), dp(6f), dp(254f), dp(34f))
        fxR.set(dp(260f), dp(6f), dp(322f), dp(34f))
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
        run {   // grade de 16 passos dentro do visor (página Bateria)
            val gx0 = visor.left + dp(8f); val gx1 = visor.right - dp(8f)
            val gy0 = visor.top + dp(30f); val gy1 = visor.bottom - dp(8f)
            val cw = (gx1 - gx0) / 8f; val chh = (gy1 - gy0) / 2f
            for (i in 0 until 16) {
                val c = i % 8; val r = i / 8
                stepR[i].set(gx0 + c * cw + dp(2f), gy0 + r * chh + dp(2f), gx0 + (c + 1) * cw - dp(2f), gy0 + (r + 1) * chh - dp(2f))
            }
        }
        for (i in 0 until NB) {
            val l = bx0 + (i % 2) * (bw + m); val t = bt + (i / 2) * (bh + gap)
            btnR[i].set(l, t, l + bw, t + bh)
        }
        for (i in 0 until 7) {
            val l = pLeft + (i % 4) * (pw + m); val t = pTop + (i / 4) * (ph + m)
            padR[i].set(l, t, l + pw, t + ph)
        }
        // a 8ª célula da grade de pads (vazia) vira o botão de MODO
        val l7 = pLeft + 3 * (pw + m); val t7 = pTop + (ph + m)
        modeR.set(l7, t7, l7 + pw, t7 + ph)
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
        if (fxR.contains(x, y)) { page = (page + 1) % PAGES.size; invalidate(); return }
        if (page == 3 && visor.contains(x, y)) {
            for (i in 0 until 16) if (stepR[i].contains(x, y)) { toggleStep(i); break }
            return
        }
        if (modo == 6 && visor.contains(x, y)) { earReplay(); return }
        if (hypot(x - jcx, y - jcy) < jr * 1.3f) { joyPtr = id; joy(x, y); return }
        for (i in 0 until NB) if (btnR[i].contains(x, y)) { tapBtn(i); return }
        if (modeR.contains(x, y)) { nextMode(); return }
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
        rr(fxR, dp(14f), if (page != 0) accent else panelC)
        txt((if (synth.playing) "▶ " else "") + PAGES[page], fxR.centerX(), fxR.centerY() + dp(4f), 12f, if (page != 0) onAccent else Color.WHITE, Paint.Align.CENTER, false, page != 0, fxR.width() - dp(8f))
        txt(if (padSeen) "🎮" else "🎧", width - dp(14f), dp(26f), 16f, accent, Paint.Align.RIGHT)
        // visor
        if (page == 3) drawSteps() else {
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
        val name = if (modo == 6) earMsg else if (modo == 5) cname(seqChord, seqMod) else if (cur >= 0) (if (modo == 4) nm(leadNote(cur)) else cname(cur, mod)) else "—"
        txt(name, visor.left + dp(16f), visor.top + dp(62f), 52f, nameC, Paint.Align.LEFT, true, true, visor.width() * 0.55f)
        if (cur >= 0 && modo != 4 && modo != 6) txt(notes(cur, mod).joinToString("  ") { nm(it) }, visor.right - dp(14f), visor.top + dp(24f), 12f, infoC, Paint.Align.RIGHT, true, false, visor.width() * 0.5f)
        val info = if (modo == 6) "OUVIDO · ${EARL[earLvl].uppercase()} · ACERTOS $earOk/$earTot · SÉRIE $earStreak" else "${MODES[modo].uppercase()} · TOM ${NN[((key % 12) + 12) % 12]} ${if (minor) "MENOR" else "MAIOR"} · ${WAVES[synth.wave].uppercase()} · VOL ${(synth.vol * 100).roundToInt()}%" + (if (mod > 0) " · ${(if (modo == 4) LMODS[mod] else MODS[mod]).uppercase()}" else "")
        txt(info, visor.left + dp(16f), visor.bottom - dp(12f), 11f, infoC, Paint.Align.LEFT, true, false, visor.width() - dp(32f))
        if (page == 2) drawLoopStrip()
        }
        // joystick
        p.style = Paint.Style.FILL; p.shader = null; p.color = panelC; c.drawCircle(jcx, jcy, jr * 1.1f, p)
        val jl = if (modo == 4) JLL else JLP
        txt(jl[0], jcx, jcy - jr * 0.85f, 10f, grayC, Paint.Align.CENTER); txt(jl[1], jcx, jcy + jr * 1.0f, 10f, grayC, Paint.Align.CENTER)
        txt(jl[2], jcx + jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER); txt(jl[3], jcx - jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER)
        val kx = jcx + (if (abs(gx) > abs(jx)) gx else jx) * jr * 0.6f; val ky = jcy - (if (abs(gy) > abs(jy)) gy else jy) * jr * 0.6f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(kx - jr * 0.3f, ky - jr * 0.3f, kx + jr * 0.3f, ky + jr * 0.3f, accent, accent2, Shader.TileMode.CLAMP)
        c.drawCircle(kx, ky, jr * 0.32f, p); p.shader = null
        // botões
        for (i in 0 until NB) {
            val hot = btnHot(i)
            rr(btnR[i], dp(12f), if (hot) accent else panelC)
            val label = btnLabel(i)
            txt(label, btnR[i].centerX(), btnR[i].centerY() + dp(5f), 13f, if (hot) onAccent else Color.WHITE, Paint.Align.CENTER, false, hot, btnR[i].width() - dp(8f))
        }
        // pads
        for (i in 0 until 7) {
            val on = i == (if (modo == 5) seqChord else cur)
            rr(padR[i], dp(18f), if (on) accent else panelC)
            txt(if (modo == 4) nm(leadNote(i)) else cname(i, mod), padR[i].centerX(), padR[i].centerY() + dp(2f), 19f, if (on) onAccent else Color.WHITE, Paint.Align.CENTER, false, true, padR[i].width() - dp(10f))
            txt(ROM[i] + (if (padSeen) " · " + PADL[i] else ""), padR[i].centerX(), padR[i].centerY() + dp(22f), 11f, if (on) onAccent else grayC, Paint.Align.CENTER, false, false, padR[i].width() - dp(10f))
        }
        // botão de modo (ocupa a 8ª célula da grade de pads)
        rr(modeR, dp(18f), panelC)
        txt("MODO" + (if (padSeen) " · Y" else ""), modeR.centerX(), modeR.centerY() - dp(10f), 11f, grayC, Paint.Align.CENTER, false, false, modeR.width() - dp(10f))
        txt(MODES[modo], modeR.centerX(), modeR.centerY() + dp(14f), 19f, accent, Paint.Align.CENTER, false, true, modeR.width() - dp(10f))
        postInvalidateOnAnimation()
    }
}
