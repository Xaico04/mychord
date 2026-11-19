package com.hichord.app
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.view.*
import android.widget.Toast
import kotlin.math.*

class HiChordView(ctx: Context, private val synth: Synth) : View(ctx) {
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
    private val BTN = arrayOf("Tom −", "Tom +", "Escala", "Onda")
    private val NB = 8
    private var key = 0; private var minor = false; private var cur = -1; private var mod = 0
    private var padSeen = false
    private val touchChord = BooleanArray(7); private val padChord = BooleanArray(7)
    private val stamp = IntArray(7); private val prevH = BooleanArray(7); private var ctr = 0
    private var jx = 0f; private var jy = 0f; private var gx = 0f; private var gy = 0f
    private var l2Key = false; private var l2Axis = false; private var r2Key = false; private var r2Axis = false
    private var hx = 0; private var hy = 0
    private val padR = Array(7) { RectF() }; private val btnR = Array(NB) { RectF() }
    private val padArea = RectF(); private val helpR = RectF()
    private val visor = RectF(); private var jcx = 0f; private var jcy = 0f; private var jr = 1f
    private val ptrPad = HashMap<Int, Int>(); private var joyPtr = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val laneR = Array(8) { RectF() }
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
        0 -> when (i) {
            2 -> if (minor) "Menor" else "Maior"
            3 -> WAVES[synth.wave]
            4 -> if (synth.playing) "■ Parar" else "▶ Tocar"
            5 -> BPMS[bpmI].toString() + " BPM"
            6 -> fxText(0)
            7 -> fxText(1)
            else -> BTN[i]
        }
        1 -> fxText(i)
        2 -> loopText(i)
        3 -> drumText(i)
        else -> midiText(i)
    }
    private fun btnHot(i: Int): Boolean = when (page) {
        0 -> (i == 4 && synth.playing) || (i == 6 && fxOn(0)) || (i == 7 && fxOn(1))
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
            1 -> midi.changeChannel((midi.channel + 1) % 16)
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
    private val LSH = arrayOf("Kick", "Caixa", "Hat", "Hat ab.", "Palma", "Tom", "Perc", "Melod.")
    private fun laneHas(l: Int): Boolean { for (s in 0 until 16) if (synth.pat[l * 16 + s].toInt() != 0) return true; return false }
    private fun drawSteps() {
        rr(visor, dp(18f), visorC)
        // seletor de pista: toque direto no nome do instrumento (pista com som fica com tom mais claro)
        for (i in 0 until 8) {
            val on = i == lane
            val bgc = if (on) accent2 else if (laneHas(i)) laneSoundC else panelC
            rr(laneR[i], dp(10f), bgc)
            txt(LSH[i], laneR[i].centerX(), laneR[i].centerY() + dp(4f), 10f, if (on) onAccent else creamC, Paint.Align.CENTER, false, on, laneR[i].width() - dp(4f))
        }
        val cur16 = if (synth.playing) synth.uiStep % 16 else -1
        for (i in 0 until 16) {
            val r = stepR[i]; val v = synth.pat[lane * 16 + i].toInt()
            val c = if (v > 0) stepOnC else if (i % 4 == 0) stepBeatC else stepOffC
            rr(r, dp(6f), c)
            if (v > 0 && lane == 7) txt(v.toString(), r.centerX(), r.centerY() + dp(5f), 13f, onAccent, Paint.Align.CENTER, false, true)
            if (i == cur16) {
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(2.5f); p.color = inkC
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
        tmpR.set(x0, by, x1, by + dp(3f)); rr(tmpR, dp(2f), Color.argb(60, 90, 50, 20))
        tmpR.set(x0, by, x0 + (x1 - x0) * prog, by + dp(3f)); rr(tmpR, dp(2f), stepOnC)
        val cw = (x1 - x0) / 6f
        for (k in 0 until 6) {
            val st = synth.tState.get(k)
            val c = when (st) { 0 -> Color.argb(50, 90, 50, 20); 1 -> mix(stepOnC, visorC, 0.55f); 2 -> Color.rgb(229, 57, 53); 3 -> stepOnC; else -> Color.argb(120, 90, 50, 20) }
            tmpR.set(x0 + k * cw + dp(2f), by + dp(8f), x0 + (k + 1) * cw - dp(2f), by + dp(26f)); rr(tmpR, dp(6f), c)
            txt((k + 1).toString(), tmpR.centerX(), tmpR.centerY() + dp(4f), 11f, if (st == 0) infoC else onAccent, Paint.Align.CENTER, false, true)
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
    private fun tapBtn(i: Int) { when (page) { 0 -> actMenu(i); 1 -> actFx(i); 2 -> actLoop(i); 3 -> actDrum(i); else -> actMidi(i) } }
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

    // ---------- cores: paleta "Synthos" (roxo-uva, creme, madeira e cores de cabo de patch) ----------
    private val accent = Color.parseColor("#ffc83d")      // mostarda
    private val accent2 = Color.parseColor("#ff6fa5")     // rosa
    private val panelC = Color.parseColor("#4a3068")      // botões
    private val visorC = Color.parseColor("#fff0cf")      // tela de LCD creme
    private val gridC = Color.parseColor("#efd9a8")
    private val nameC = Color.parseColor("#3b1f5c")
    private val infoC = Color.parseColor("#8a5a2b")
    private val onAccent = Color.parseColor("#2a1a3d")
    private val grayC = Color.parseColor("#c3b0e0")
    private val creamC = Color.parseColor("#fff3d9")
    private val inkC = Color.parseColor("#2a1a3d")
    private val woodC = Color.parseColor("#a8683c")
    private val scopeC = Color.parseColor("#c2255c")
    private val stepOnC = Color.parseColor("#ff6f61")
    private val stepBeatC = Color.parseColor("#e6c98e")
    private val stepOffC = Color.parseColor("#f2dfb2")
    private val laneSoundC = Color.parseColor("#7a4fa3")
    // um cabo de cor para cada grau (I a VII)
    private val PADC = intArrayOf(
        Color.parseColor("#ff6f61"), Color.parseColor("#ff9f43"), Color.parseColor("#ffd23f"), Color.parseColor("#6fd3a0"),
        Color.parseColor("#4cc9f0"), Color.parseColor("#9b8cff"), Color.parseColor("#ff7eb6"))

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())

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
    private fun actMenu(i: Int) {
        when (i) {
            0 -> { if (key > -12) key-- }
            1 -> { if (key < 12) key++ }
            2 -> { minor = !minor }
            3 -> { synth.wave = (synth.wave + 1) and 3 }
            4 -> { synth.transport(!synth.playing) }
            5 -> { cycleBpm(); saveFx() }
            6 -> { fxLvl[0] = (fxLvl[0] + 1) % 4; pushFx(); saveFx() }
            7 -> { fxLvl[1] = (fxLvl[1] + 1) % 4; pushFx(); saveFx() }
        }
        applyChord(false); invalidate()
    }

    // ---------- navegação pelo controle (modo EDITAR) ----------
    private var navOn = false
    private var navId = 0   // 0-7 botões · 8-23 passos · 24 Atualizar · 25 Página · 26 ? · 27-34 pistas
    private fun navValid(id: Int) = id in 0..7 || id in 24..26 || ((id in 8..23 || id in 27..34) && page == 3)
    private fun navRect(id: Int): RectF = when {
        id < 8 -> btnR[id]
        id < 24 -> stepR[id - 8]
        id == 24 -> updR
        id == 25 -> fxR
        id == 26 -> helpR
        else -> laneR[id - 27]
    }
    private fun navMove(d: Int) {   // d: 0 esquerda, 1 direita, 2 cima, 3 baixo
        if (!navValid(navId)) navId = 0
        val a = navRect(navId); val cx = a.centerX(); val cy = a.centerY()
        var best = -1; var bs = Float.MAX_VALUE
        for (id in 0..34) {
            if (id == navId || !navValid(id)) continue
            val r = navRect(id); val dx = r.centerX() - cx; val dy = r.centerY() - cy
            val along = when (d) { 0 -> -dx; 1 -> dx; 2 -> -dy; else -> dy }
            val perp = if (d < 2) abs(dy) else abs(dx)
            if (along < dp(4f)) continue
            val s = along + perp * 2.5f
            if (s < bs) { bs = s; best = id }
        }
        if (best >= 0) navId = best
        invalidate()
    }
    private fun navActivate() {
        val id = navId
        when {
            id < 8 -> tapBtn(id)
            id < 24 -> toggleStep(id - 8)
            id == 24 -> onUpdate()
            id == 25 -> goPage(1)
            id == 26 -> openHelp()
            else -> { lane = id - 27; saveFx(); invalidate() }
        }
        if (!navValid(navId)) navId = 0
    }
    private fun toggleNav() { navOn = !navOn; if (navOn && !navValid(navId)) navId = 0; invalidate() }
    private fun goPage(d: Int) {
        page = (page + d + PAGES.size) % PAGES.size
        if (navOn && !navValid(navId)) navId = 0
        invalidate()
    }
    private fun dpad(d: Int) {
        if (helpOpen) {
            when (d) { 0 -> helpSection(-1); 1 -> helpSection(1); 2 -> helpScrollBy(-dp(70f)); else -> helpScrollBy(dp(70f)) }
            return
        }
        if (navOn) { navMove(d); return }
        when (d) { 0 -> actMenu(0); 1 -> actMenu(1); 2 -> actMenu(2); else -> actMenu(3) }
    }

    // ---------- controle ----------
    fun onPadKey(code: Int, down: Boolean, rep: Int): Boolean {
        if (splash) { if (down) { splash = false; invalidate() }; return true }
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
        if (helpOpen) {   // com a ajuda aberta o controle só navega nela
            if (ci >= 0) {
                if (down) {
                    when (ci) { 0 -> dpad(0); 1 -> dpad(1); 6 -> closeHelp() }
                } else {   // soltar um botão não deixa acorde preso
                    when (ci) {
                        2 -> { l2Key = false; padChord[2] = l2Axis }
                        3 -> { r2Key = false; padChord[3] = r2Axis }
                        else -> padChord[ci] = false
                    }
                    refresh()
                }
                return true
            }
            if (down) {
                when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> dpad(0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> dpad(1)
                    KeyEvent.KEYCODE_DPAD_UP -> dpad(2)
                    KeyEvent.KEYCODE_DPAD_DOWN -> dpad(3)
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START -> closeHelp()
                }
            }
            return true
        }
        if (ci >= 0) {
            padSeen = true
            if (navOn && (ci == 5 || ci == 6)) {   // no EDITAR: A confirma, B sai
                if (down) { if (ci == 5) navActivate() else navOn = false; invalidate() }
                else { padChord[ci] = false; refresh() }
                return true
            }
            when (ci) {
                2 -> { l2Key = down; padChord[2] = l2Key || l2Axis }
                3 -> { r2Key = down; padChord[3] = r2Key || r2Axis }
                else -> padChord[ci] = down
            }
            refresh(); return true
        }
        if (down && rep == 0) {
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> dpad(0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> dpad(1)
                KeyEvent.KEYCODE_DPAD_UP -> dpad(2)
                KeyEvent.KEYCODE_DPAD_DOWN -> dpad(3)
                KeyEvent.KEYCODE_BUTTON_SELECT -> toggleNav()
                KeyEvent.KEYCODE_BUTTON_THUMBL -> goPage(-1)
                KeyEvent.KEYCODE_BUTTON_THUMBR -> goPage(1)
                KeyEvent.KEYCODE_BUTTON_Y -> nextMode()
                KeyEvent.KEYCODE_BUTTON_START -> synth.transport(!synth.playing)
                else -> return false
            }
            padSeen = true; invalidate(); return true
        }
        return false
    }
    fun onPadMotion(e: MotionEvent) {
        if (splash) return
        padSeen = true
        fun dz(v: Float) = if (abs(v) < 0.2f) 0f else v
        val ax = dz(e.getAxisValue(MotionEvent.AXIS_X)); val ay = -dz(e.getAxisValue(MotionEvent.AXIS_Y))
        val nx = e.getAxisValue(MotionEvent.AXIS_HAT_X).roundToInt(); val ny = e.getAxisValue(MotionEvent.AXIS_HAT_Y).roundToInt()
        if (helpOpen) {
            gx = 0f; gy = 0f
            if (ay != 0f) helpScrollBy(-ay * dp(6f))
            if (nx != hx) { if (nx < 0) dpad(0) else if (nx > 0) dpad(1); hx = nx }
            if (ny != hy) { if (ny < 0) dpad(2) else if (ny > 0) dpad(3); hy = ny }
            return
        }
        gx = ax; gy = ay
        l2Axis = max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f
        r2Axis = max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f
        padChord[2] = l2Key || l2Axis
        padChord[3] = r2Key || r2Axis
        if (nx != hx) { if (nx < 0) dpad(0) else if (nx > 0) dpad(1); hx = nx }
        if (ny != hy) { if (ny < 0) dpad(2) else if (ny > 0) dpad(3); hy = ny }
        refresh()
    }

    // ---------- ajuda (botão ?) ----------
    private var helpOpen = false
    private var helpSec = 0
    private var helpScroll = 0f
    private var helpMax = 0f
    private var helpH = 0f
    private var helpKey = ""
    private var hDownY = 0f
    private var hLastY = 0f
    private var hMoved = false
    private val helpPanel = RectF(); private val helpClose = RectF(); private val helpView = RectF()
    private class HL(val s: String, val x: Float, val kind: Int, val y: Float)
    private var helpLines: List<HL> = emptyList()

    private val HT = arrayOf("Início", "Acordes", "Modos", "Efeitos", "Bateria", "Loop", "MIDI", "Controle")
    private val HELP = arrayOf(
        arrayOf(
            "# O que é o MyChord",
            "Um sintetizador de acordes de bolso: você toca acordes prontos da tonalidade escolhida, sem precisar saber teoria. Dá para tocar na tela ou com um controle Bluetooth.",
            "# A tela",
            "• Visor (canto superior esquerdo): mostra o acorde que está soando, suas notas, a forma de onda e um resumo com modo, tom e timbre.",
            "• Pads (grade da direita): os 7 acordes da tonalidade, graus I a VII. Toque e segure para soar. Arraste o dedo de um pad para outro, sem soltar, para trocar de acorde.",
            "• Analógico (círculo): arraste para variar o acorde. Veja a seção Acordes.",
            "• Botões ao lado do analógico: mudam conforme a página escolhida na barra de cima.",
            "• MODO (última célula da grade de pads): muda a forma como o acorde soa. Veja a seção Modos.",
            "# Barra de cima",
            "• Atualizar: procura uma versão nova do app e instala. Fica colorido, com um ●, quando existe versão nova.",
            "• Página (Menu, FX, Loop, Bateria, MIDI): cada toque vai para a página seguinte de botões.",
            "• ?: abre esta ajuda.",
            "• 🎮 ou 🎧: mostra se um controle foi detectado.",
            "# Volume e giro de tela",
            "• O app sempre toca em 100%. Use os botões de volume do próprio celular.",
            "• A tela fica deitada. Ela só gira para vertical se a rotação automática do celular estiver ligada."
        ),
        arrayOf(
            "# Escolher o tom e o timbre",
            "• Tom − e Tom +: sobem ou descem a tonalidade em meio tom por toque (de −12 a +12). Serve para ajustar ao alcance da sua voz ou de uma música.",
            "• Escala: alterna entre Maior (som alegre) e Menor (som triste). Os pads mudam para os acordes da nova escala.",
            "• Onda: troca o timbre: Seno (suave), Triângulo, Serra (brilhante) e Quadrada (oca, estilo videogame).",
            "# Variar o acorde com o analógico",
            "• Para cima: 7ª (som mais jazzístico).",
            "• Para baixo: sus4 (suspenso, parece pedir uma resolução).",
            "• Para a direita: add9 (aberto e moderno).",
            "• Para a esquerda: inversão (a nota de baixo muda, deixando a troca entre acordes mais suave).",
            "• Solte o analógico para voltar ao acorde normal. Os nomes das variações aparecem em volta do círculo.",
            "# Dica",
            "Os 7 pads sempre combinam entre si. Comece alternando os pads I, V, VI e IV: é a progressão de muita música pop."
        ),
        arrayOf(
            "# Modos de toque (botão MODO)",
            "Cada toque no MODO passa para o próximo. O nome do modo aparece no visor.",
            "• Play: o acorde soa enquanto você segura o pad. O básico, para acompanhar.",
            "• Strum: as notas entram em sequência, como uma palhetada de violão. Cada toque alterna palhetada para baixo e para cima.",
            "• Arpejo: toca as notas do acorde uma a uma enquanto você segura. Direção e velocidade ficam na página FX.",
            "• Drone: toca o acorde e deixa o grave (nota principal e quinta) soando mesmo depois de soltar, deslizando para o próximo acorde. Troque de modo para parar.",
            "• Lead: cada pad vira uma nota da escala, para tocar melodias de uma nota só. Analógico: cima = oitava acima, baixo = oitava abaixo, direita = vibrato, esquerda = duas oitavas acima.",
            "• Sequencer: ao tocar um acorde, a pista Melodia da Bateria passa a tocar as notas dele no andamento. Liga o ritmo sozinho.",
            "• Ouvido: treino musical. O app toca uma nota e você responde tocando o pad do grau certo (I a VII). Toque no visor para ouvir de novo. A dificuldade fica na página MIDI."
        ),
        arrayOf(
            "# Página FX",
            "• Reverb, Delay, Chorus e Flanger: cada toque muda a intensidade (desligado, 30%, 60%, 100% e volta). Reverb = som de sala. Delay = eco. Chorus = som mais largo. Flanger = efeito de jato.",
            "• Arp: direção do arpejo (sobe, desce, vai e volta, aleatório).",
            "• Tempo: andamento em BPM, de 70 a 180.",
            "• Eco: tempo entre os ecos do Delay (150, 250, 350 ou 450 ms).",
            "• Voltar: volta para a página Menu.",
            "# Atalhos",
            "• Reverb e Delay também estão na página Menu, para mexer sem trocar de página.",
            "• Os efeitos ficam salvos ao fechar o app.",
            "• Os efeitos entram na gravação do Loop, mas a bateria não usa efeitos."
        ),
        arrayOf(
            "# Criar um ritmo, passo a passo",
            "1. Toque no botão de página até aparecer Bateria.",
            "2. Escolha o Kit: só muda o timbre dos sons (808, Rock, Lo-Fi, Techno, Jazz, Eletro e Acústico).",
            "3. Escolha um Ritmo pronto (Rock, Hip-hop, Disco, Techno, Reggae, Funk) para começar, ou Vazio para começar do zero. Atenção: trocar o Ritmo substitui todo o padrão atual.",
            "4. Toque em Tocar. Uma moldura branca corre pelos 16 passos.",
            "5. No topo do visor, toque no nome do instrumento (Kick, Caixa, Hat...) para escolher a pista. Depois toque nos quadradinhos para ligar ou desligar cada passo. Dá para editar com o ritmo tocando.",
            "6. Ajuste o BPM e o volume da Bateria (mudo, 30%, 55%, 80% e 100%).",
            "# Como ler a grade",
            "• São 16 passos, cada um é uma semicolcheia. Os passos 1, 5, 9 e 13 são os tempos do compasso e aparecem mais claros.",
            "• Pistas que já têm som aparecem com tom mais claro no topo do visor.",
            "# Receita básica",
            "• Kick nos passos 1, 5, 9 e 13.",
            "• Caixa nos passos 5 e 13.",
            "• Hat nos passos ímpares (1, 3, 5, 7...).",
            "# Outros botões",
            "• Limpar pista: apaga só a pista escolhida.",
            "• Pista Melodia: cada passo aceita de 0 a 8. De 1 a 4 são as notas do acorde e de 5 a 8 são as mesmas uma oitava acima. Toque várias vezes no passo para trocar o número. Só soa no modo Sequencer.",
            "• A bateria sai seca (no centro, sem efeitos) e o padrão fica salvo ao fechar o app."
        ),
        arrayOf(
            "# Como o Loop funciona",
            "O Loop grava o que você toca (já com os efeitos) e repete em ciclo por cima da bateria. São 6 faixas, T1 a T6. A bateria não entra na gravação: ela toca pelo sequenciador.",
            "# Sua primeira música",
            "1. Página Bateria: monte o ritmo, escolha o BPM e o tamanho do loop (1, 2 ou 4 compassos). Faça isso antes de gravar: depois o andamento e o tamanho travam.",
            "2. Página Loop: escolha um modo (Play ou Strum para acordes) e deixe o primeiro acorde pronto.",
            "3. Toque em T1. Ele fica aguardando e começa a gravar no início do próximo compasso, por um loop inteiro. Toque seus acordes. Se o ritmo estava parado, ele liga sozinho.",
            "4. Quando o loop fecha, T1 passa a tocar sozinha.",
            "5. Troque de modo (Lead para melodia, Drone para grave) e grave T2 do mesmo jeito. Repita até T6.",
            "# Controlar as faixas",
            "• Faixa vazia: um toque prepara e grava.",
            "• Faixa tocando: um toque deixa muda. Toque de novo para voltar.",
            "• Faixa aguardando ou gravando: um toque cancela.",
            "• Limpar tudo: apaga todas as faixas e libera o BPM e o tamanho do loop.",
            "# Importante",
            "• As gravações não ficam salvas ao fechar o app.",
            "• Se aparecer aviso de memória, use menos faixas ou um loop menor."
        ),
        arrayOf(
            "# Página MIDI",
            "Serve para ligar o MyChord a teclados, computadores e outros equipamentos pela porta USB (cabo OTG).",
            "• MIDI: cada toque passa pelo próximo dispositivo encontrado. Depois do último, desliga.",
            "• Canal: canal MIDI usado nas notas enviadas (1 a 16). A bateria sai no canal 10.",
            "• Saída: envia para fora as notas que você toca e a bateria.",
            "• Entrada: um teclado externo passa a tocar o som do app.",
            "• Clock: envia o relógio MIDI e Start/Stop, para sincronizar com outros aparelhos.",
            "• Ouvido: dificuldade do treino de ouvido.",
            "• Notas OFF: corta qualquer nota que tenha ficado presa.",
            "• Zerar placar: zera acertos e sequência do treino de ouvido.",
            "# Dica",
            "Fone USB com DAC: conecte antes de abrir o app."
        ),
        arrayOf(
            "# Tocar",
            "• LB, RB, LT, RT, X, A e B tocam os acordes I a VII. As letras aparecem nos pads quando o controle é detectado.",
            "• Analógico esquerdo: variações do acorde (cima 7ª, baixo sus4, direita add9, esquerda inversão).",
            "• Y: troca o MODO. Start: liga e desliga o ritmo.",
            "• Direcional: ◀ e ▶ mudam o tom (− e +). ▲ alterna Maior e Menor. ▼ troca a onda.",
            "# Configurar sem tocar na tela (modo EDITAR)",
            "• R3 (apertar o analógico direito): próxima página (Menu, FX, Loop, Bateria, MIDI). L3 (apertar o analógico esquerdo): página anterior.",
            "• Select: liga ou desliga o modo EDITAR. Uma moldura branca aparece sobre um botão.",
            "• No EDITAR, o direcional move a moldura por todos os botões da página, pelos passos e pistas da Bateria e pela barra de cima (Atualizar, Página e ?).",
            "• A confirma, o mesmo que tocar no botão. B ou Select sai do EDITAR. Enquanto estiver no EDITAR, A e B não tocam acorde.",
            "• Tocar na tela também sai do EDITAR.",
            "# Exemplo: criar um ritmo só com o controle",
            "1. R3 até a página Bateria.",
            "2. Select para entrar no EDITAR. Com o direcional, vá até Ritmo e aperte A até escolher um.",
            "3. Vá até Tocar e aperte A.",
            "4. Suba até o nome de uma pista e aperte A. Desça aos passos e aperte A em cada um para ligar ou desligar.",
            "5. Select para sair e toque seus acordes por cima.",
            "# Nesta ajuda",
            "• ◀ ▶ ou LB RB trocam de seção. ▲ ▼ ou o analógico rolam o texto. B ou Select fecham."
        )
    )

    private fun openHelp() {
        ptrPad.clear(); syncTouch(); joyPtr = -1; jx = 0f; jy = 0f
        navOn = false; helpOpen = true; helpScroll = 0f; refresh()
    }
    fun closeHelp(): Boolean {
        if (!helpOpen) return false
        helpOpen = false; invalidate(); return true
    }
    private fun helpSection(d: Int) { helpSec = (helpSec + d + HT.size) % HT.size; helpScroll = 0f; invalidate() }
    private fun helpScrollBy(dy: Float) { helpScroll = (helpScroll + dy).coerceIn(0f, helpMax); invalidate() }

    private fun chipRect(i: Int, out: RectF) {
        val cols = if (width > height) HT.size else 4
        val gp = dp(6f); val pad = dp(12f)
        val cw = (helpPanel.width() - 2 * pad - (cols - 1) * gp) / cols
        val ch = dp(30f)
        val r = i / cols; val cc = i % cols
        val x = helpPanel.left + pad + cc * (cw + gp); val y = helpPanel.top + dp(48f) + r * (ch + gp)
        out.set(x, y, x + cw, y + ch)
    }
    private fun helpLayoutView() {
        val cols = if (width > height) HT.size else 4
        val rows = (HT.size + cols - 1) / cols
        val top = helpPanel.top + dp(48f) + rows * (dp(30f) + dp(6f)) + dp(4f)
        helpView.set(helpPanel.left + dp(16f), top, helpPanel.right - dp(16f), helpPanel.bottom - dp(10f))
    }
    private fun buildHelp() {
        val key = helpSec.toString() + "|" + helpView.width().toInt()
        if (key == helpKey) return
        helpKey = key
        val out = ArrayList<HL>()
        val w = helpView.width()
        var cur = 0f
        p.typeface = Typeface.DEFAULT; p.shader = null
        for (raw in HELP[helpSec]) {
            val head = raw.startsWith("# ")
            val item = raw.startsWith("• ") || (raw.length > 2 && raw[0].isDigit() && raw[1] == '.')
            val text = if (head) raw.substring(2) else raw
            p.textSize = dp(if (head) 15f else 13f); p.isFakeBoldText = head
            val lh = dp(if (head) 22f else 19f)
            val ind = if (item) dp(16f) else 0f
            val kind = if (head) 1 else 0
            if (head && cur > 0f) cur += dp(8f)
            val line = StringBuilder(); var first = true
            for (wd in text.split(" ")) {
                val t = if (line.isEmpty()) wd else line.toString() + " " + wd
                val lim = if (first) w else w - ind
                if (line.isEmpty() || p.measureText(t) <= lim) {
                    line.setLength(0); line.append(t)
                } else {
                    cur += lh; out.add(HL(line.toString(), if (first) 0f else ind, kind, cur - dp(5f)))
                    first = false; line.setLength(0); line.append(wd)
                }
            }
            if (line.isNotEmpty()) { cur += lh; out.add(HL(line.toString(), if (first) 0f else ind, kind, cur - dp(5f))) }
            cur += dp(if (head) 2f else 5f)
        }
        p.isFakeBoldText = false
        helpLines = out; helpH = cur + dp(10f)
    }
    private fun drawHelp(c: Canvas) {
        helpLayoutView(); buildHelp()
        helpMax = max(0f, helpH - helpView.height()); helpScroll = helpScroll.coerceIn(0f, helpMax)
        c.drawColor(Color.argb(190, 0, 0, 0))
        rr(helpPanel, dp(18f), Color.rgb(47, 29, 69))
        txt("Ajuda · " + HT[helpSec], helpPanel.left + dp(16f), helpPanel.top + dp(32f), 17f, Color.WHITE, Paint.Align.LEFT, false, true, helpClose.left - helpPanel.left - dp(24f))
        rr(helpClose, dp(14f), panelC)
        txt("×", helpClose.centerX(), helpClose.centerY() + dp(7f), 22f, Color.WHITE, Paint.Align.CENTER, false, true)
        for (i in HT.indices) {
            chipRect(i, tmpR)
            rr(tmpR, dp(12f), if (i == helpSec) accent else panelC)
            txt(HT[i], tmpR.centerX(), tmpR.centerY() + dp(4f), 12f, if (i == helpSec) onAccent else Color.WHITE, Paint.Align.CENTER, false, i == helpSec, tmpR.width() - dp(6f))
        }
        c.save(); c.clipRect(helpView)
        for (l in helpLines) {
            val y = helpView.top + l.y - helpScroll
            if (y < helpView.top - dp(24f) || y > helpView.bottom + dp(24f)) continue
            if (l.kind == 1) txt(l.s, helpView.left + l.x, y, 15f, accent, Paint.Align.LEFT, false, true)
            else txt(l.s, helpView.left + l.x, y, 13f, Color.rgb(241, 230, 255), Paint.Align.LEFT)
        }
        c.restore()
        if (helpMax > 0f) {
            val th = max(dp(24f), helpView.height() * helpView.height() / helpH)
            val ty = helpView.top + (helpView.height() - th) * (helpScroll / helpMax)
            tmpR.set(helpPanel.right - dp(8f), ty, helpPanel.right - dp(5f), ty + th)
            rr(tmpR, dp(2f), Color.argb(120, 255, 255, 255))
        }
    }
    private fun helpTap(x: Float, y: Float) {
        if (helpClose.contains(x, y) || !helpPanel.contains(x, y)) { closeHelp(); return }
        for (i in HT.indices) {
            chipRect(i, tmpR)
            if (tmpR.contains(x, y)) { helpSec = i; helpScroll = 0f; invalidate(); return }
        }
    }
    private fun helpTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { hDownY = e.y; hLastY = e.y; hMoved = false }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.y - hDownY) > dp(8f)) hMoved = true
                if (hMoved) helpScrollBy(hLastY - e.y)
                hLastY = e.y
            }
            MotionEvent.ACTION_UP -> { if (!hMoved) helpTap(e.x, e.y) }
        }
        return true
    }

    // ---------- layout (deitado e em pé, com área segura do furo da câmera) ----------
    private var iL = 0f; private var iT = 0f; private var iR = 0f; private var iB = 0f
    private var barL = 0f; private var barY = 0f; private var barH = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutAll(w, h) }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(wi: WindowInsets): WindowInsets {
        var l = 0; var t = 0; var r = 0; var b = 0
        if (Build.VERSION.SDK_INT >= 30) {
            val ins = wi.getInsets(WindowInsets.Type.displayCutout())
            l = ins.left; t = ins.top; r = ins.right; b = ins.bottom
        } else if (Build.VERSION.SDK_INT >= 28) {
            val dc = wi.displayCutout
            if (dc != null) { l = dc.safeInsetLeft; t = dc.safeInsetTop; r = dc.safeInsetRight; b = dc.safeInsetBottom }
        }
        if (l.toFloat() != iL || t.toFloat() != iT || r.toFloat() != iR || b.toFloat() != iB) {
            iL = l.toFloat(); iT = t.toFloat(); iR = r.toFloat(); iB = b.toFloat()
            if (width > 0 && height > 0) { layoutAll(width, height); invalidate() }
        }
        return wi
    }

    private fun layoutAll(w: Int, h: Int) {
        val m = dp(12f); val W = w.toFloat(); val H = h.toFloat()
        val L = max(m, iL + dp(8f)); val R = W - max(m, iR + dp(8f))
        val T = max(dp(6f), iT + dp(4f)); val B = H - max(m, iB + dp(6f))
        barL = L; barY = T; barH = dp(32f)
        updR.set(L + dp(98f), T, L + dp(206f), T + barH)
        fxR.set(L + dp(214f), T, L + dp(322f), T + barH)
        helpR.set(R - barH, T, R, T + barH)
        val vt = T + barH + dp(8f)
        val gap = m * 0.5f
        val bt: Float; val bx0: Float; val bw: Float; val bh: Float
        val pTop: Float; val pLeft: Float; val pw: Float; val ph: Float
        if (W > H) {
            val leftW = L + (R - L) * 0.40f
            visor.set(L, vt, leftW, vt + (B - vt) * 0.42f)
            val rh = B - visor.bottom - m
            jr = min(rh / 2.2f, (leftW - L) * 0.22f)
            jcx = L + jr * 1.1f; jcy = visor.bottom + m + rh / 2f
            bx0 = jcx + jr * 1.1f + m; bw = (leftW - bx0 - m) / 2f
            bt = visor.bottom + m; bh = (rh - 3 * gap) / 4f
            pLeft = leftW + m; pTop = vt
            pw = (R - pLeft - 3 * m) / 4f; ph = (B - pTop - m) / 2f
        } else {
            visor.set(L, vt, R, vt + H * 0.27f)
            jr = min((R - L) * 0.2f, H * 0.12f); jcx = L + jr * 1.1f; jcy = visor.bottom + m + jr * 1.1f
            bx0 = jcx + jr * 1.1f + m; bw = (R - bx0 - m) / 2f
            bt = jcy - jr * 1.1f; bh = (jr * 2.2f - 3 * gap) / 4f
            pLeft = L; pTop = jcy + jr * 1.1f + m
            pw = (R - L - 3 * m) / 4f; ph = (B - pTop - m) / 2f
        }
        run {   // pistas e grade de 16 passos dentro do visor (página Bateria)
            val gx0 = visor.left + dp(8f); val gx1 = visor.right - dp(8f)
            val gy0 = visor.top + dp(42f); val gy1 = visor.bottom - dp(8f)
            val cw = (gx1 - gx0) / 8f; val chh = (gy1 - gy0) / 2f
            for (i in 0 until 16) {
                val c = i % 8; val r = i / 8
                stepR[i].set(gx0 + c * cw + dp(2f), gy0 + r * chh + dp(2f), gx0 + (c + 1) * cw - dp(2f), gy0 + (r + 1) * chh - dp(2f))
            }
            for (i in 0 until 8) laneR[i].set(gx0 + i * cw + dp(2f), visor.top + dp(8f), gx0 + (i + 1) * cw - dp(2f), visor.top + dp(34f))
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
        helpPanel.set(L - dp(4f), T, R + dp(4f), B + dp(4f))
        helpClose.set(helpPanel.right - dp(44f), helpPanel.top + dp(8f), helpPanel.right - dp(10f), helpPanel.top + dp(40f))
        helpKey = ""
        titleR.set(barL, barY, barL + dp(92f), barY + barH)
        bgBmp = null; splashBg = null
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
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
        if (helpR.contains(x, y)) { openHelp(); return }
        if (titleR.contains(x, y)) { openSplash(); return }
        if (updR.contains(x, y)) { onUpdate(); return }
        if (fxR.contains(x, y)) { goPage(1); return }
        if (page == 3 && visor.contains(x, y)) {
            for (i in 0 until 8) if (laneR[i].contains(x, y)) { lane = i; saveFx(); invalidate(); return }
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
        if (splash) { if (e.actionMasked == MotionEvent.ACTION_UP) { splash = false; invalidate() }; return true }
        if (helpOpen) return helpTouch(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) navOn = false
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
    private val SANS_BLACK: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val mascot: Drawable? = ContextCompat.getDrawable(ctx, R.drawable.mascote)
    // opcional: coloque uma imagem em app/src/main/assets/mascote.png e ela substitui o desenho da capa
    private val mascotBmp: Bitmap? = try { ctx.assets.open("mascote.png").use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
    private val bmpP = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val titleR = RectF(); private val tmpR2 = RectF(); private val dstR = RectF()
    private val sPath = Path()
    private val scopePath = Path()
    private var bgBmp: Bitmap? = null
    private var splashBg: Shader? = null
    private var splash = true
    private var splashT0 = SystemClock.uptimeMillis()
    private var splashEnd = splashT0 + 4200L   // some sozinha; tocar na tela ou apertar um botão também fecha

    private fun openSplash() {
        ptrPad.clear(); syncTouch(); joyPtr = -1; jx = 0f; jy = 0f
        splash = true; splashT0 = SystemClock.uptimeMillis(); splashEnd = Long.MAX_VALUE
        refresh()
    }

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
    /** botão "de brinquedo": um degrauzinho mais escuro embaixo; apertado, ele afunda */
    private fun chunky(r: RectF, rad: Float, face: Int, down: Boolean, lip: Float) {
        tmpR2.set(r.left, r.top + lip, r.right, r.bottom + lip); rr(tmpR2, rad, mix(face, Color.BLACK, 0.38f))
        val o = if (down) lip * 0.7f else 0f
        tmpR2.set(r.left, r.top + o, r.right, r.bottom + o); rr(tmpR2, rad, face)
    }

    /** fundo roxo com estrelinhas e notas bem fracas, desenhado uma vez só e guardado */
    private fun buildBg() {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bm); val bp = Paint(Paint.ANTI_ALIAS_FLAG)
        bp.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.parseColor("#3d2560"), Color.parseColor("#22132f"), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bp); bp.shader = null
        var seed = 20261118
        fun rnd(): Float { seed = seed * 1664525 + 1013904223; return ((seed ushr 8) % 1000) / 1000f }
        bp.textAlign = Paint.Align.CENTER
        for (i in 0 until 48) {
            val x = rnd() * w; val y = rnd() * h
            if (i % 3 == 0) {
                bp.color = Color.argb(34, 255, 255, 255); bp.textSize = dp(14f + rnd() * 14f)
                cv.drawText(if (i % 2 == 0) "♪" else "♫", x, y, bp)
            } else {
                bp.color = Color.argb(60 + (rnd() * 50).toInt(), 255, 232, 180)
                cv.drawCircle(x, y, dp(1f + rnd() * 1.6f), bp)
            }
        }
        bgBmp = bm
    }

    private fun drawTitle() {
        val s = "MyChord"
        p.style = Paint.Style.FILL; p.shader = null; p.typeface = SANS_BLACK; p.textAlign = Paint.Align.LEFT
        p.textSize = dp(21f)
        var tw = 0f; for (ch in s) tw += p.measureText(ch.toString())
        p.textSize = dp(21f) * min(1f, dp(90f) / tw)
        var x = barL; val y = barY + dp(24f)
        for (i in s.indices) {
            val ch = s[i].toString()
            p.color = inkC; canvas0.drawText(ch, x + dp(1.2f), y + dp(1.6f), p)
            p.color = PADC[i % 7]; canvas0.drawText(ch, x, y, p)
            x += p.measureText(ch)
        }
    }

    /** capa: sol de raios, planeta Synthos, o rei do sintetizador e cabos de patch */
    private fun drawSplash(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat(); val u = min(W, H)
        val now = SystemClock.uptimeMillis()
        if (now > splashEnd) { splash = false; return }
        val t = (now - splashT0) / 1000f
        val deg = PI.toFloat() / 180f
        val land = W >= H
        p.style = Paint.Style.FILL
        if (splashBg == null) splashBg = LinearGradient(0f, 0f, 0f, H, Color.parseColor("#5a2f8e"), Color.parseColor("#1f1230"), Shader.TileMode.CLAMP)
        p.shader = splashBg; c.drawRect(0f, 0f, W, H, p); p.shader = null
        // raios girando devagar
        val cx = if (land) W * 0.27f else W * 0.5f; val cy = if (land) H * 0.52f else H * 0.30f
        val rad = hypot(W, H); val n = 18; val stp = 360f / n
        p.color = Color.argb(40, 255, 205, 110)
        for (i in 0 until n step 2) {
            val a0 = (i * stp + t * 5f) * deg; val a1 = a0 + stp * deg
            sPath.reset(); sPath.moveTo(cx, cy)
            sPath.lineTo(cx + rad * cos(a0), cy + rad * sin(a0)); sPath.lineTo(cx + rad * cos(a1), cy + rad * sin(a1)); sPath.close()
            c.drawPath(sPath, p)
        }
        // estrelas piscando
        for (i in 0 until 26) {
            val sx = ((i * 97 + 13) % 100) / 100f * W; val sy = ((i * 53 + 7) % 100) / 100f * H
            p.color = Color.argb((110 + 100 * sin(t * 2.2f + i)).toInt().coerceIn(0, 255), 255, 236, 190)
            c.drawCircle(sx, sy, dp(1.2f + (i % 3) * 0.7f), p)
        }
        // planeta Synthos, com anel
        val px = if (land) W * 0.88f else W * 0.82f; val py = if (land) H * 0.20f else H * 0.10f; val pr = u * 0.10f
        c.save(); c.rotate(-20f, px, py)
        tmpR.set(px - pr * 2.0f, py - pr * 0.55f, px + pr * 2.0f, py + pr * 0.55f)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(5f); p.color = accent
        c.drawArc(tmpR, 180f, 180f, false, p)
        p.style = Paint.Style.FILL; p.color = Color.parseColor("#ff8c5a"); c.drawCircle(px, py, pr, p)
        p.color = Color.parseColor("#e0683c"); c.drawCircle(px - pr * 0.35f, py - pr * 0.25f, pr * 0.2f, p); c.drawCircle(px + pr * 0.30f, py + pr * 0.35f, pr * 0.13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(5f); p.color = accent
        c.drawArc(tmpR, 0f, 180f, false, p)
        c.restore()
        p.style = Paint.Style.FILL
        // mascote
        val ms = if (land) min(H * 0.80f, W * 0.40f) else W * 0.74f
        val mx = if (land) W * 0.05f else (W - ms) / 2f
        val my0 = if (land) (H - ms) / 2f - H * 0.02f else H * 0.08f
        val my = my0 + sin(t * 2.2f) * dp(4f)
        p.color = Color.argb(60, 0, 0, 0)
        tmpR.set(mx + ms * 0.18f, my0 + ms * 0.95f, mx + ms * 0.82f, my0 + ms * 1.01f)
        c.drawOval(tmpR, p)
        val bmp = mascotBmp
        if (bmp != null) { dstR.set(mx, my, mx + ms, my + ms); c.drawBitmap(bmp, null, dstR, bmpP) }
        else {
            val d = mascot
            if (d != null) { d.setBounds(mx.toInt(), my.toInt(), (mx + ms).toInt(), (my + ms).toInt()); d.draw(c) }
        }
        // título: cada letra de uma cor, pulando
        val title = "MyChord"
        val tx0 = if (land) W * 0.44f else W * 0.05f; val avail = if (land) W * 0.52f else W * 0.90f
        val ty = if (land) H * 0.50f else H * 0.62f
        p.typeface = SANS_BLACK; p.textSize = u * 0.24f
        val gap = dp(2f)
        var tw = p.measureText(title) + (title.length - 1) * gap
        if (tw > avail) { p.textSize = p.textSize * avail / tw; tw = avail }
        var x = tx0 + (avail - tw) / 2f
        p.textAlign = Paint.Align.CENTER
        for (i in title.indices) {
            val ch = title[i].toString(); val w = p.measureText(ch)
            val bob = sin(t * 3.2f + i * 0.8f) * dp(5f); val rot = sin(t * 2.4f + i * 1.1f) * 4f
            c.save(); c.translate(x + w / 2f, ty + bob); c.rotate(rot)
            p.style = Paint.Style.FILL; p.color = Color.argb(90, 0, 0, 0); c.drawText(ch, dp(3f), dp(5f), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = dp(7f); p.strokeJoin = Paint.Join.ROUND; p.color = inkC; c.drawText(ch, 0f, 0f, p)
            p.style = Paint.Style.FILL; p.color = PADC[i % 7]; c.drawText(ch, 0f, 0f, p)
            c.restore()
            x += w + gap
        }
        p.typeface = Typeface.DEFAULT; p.style = Paint.Style.FILL
        val cxT = tx0 + avail / 2f
        val ud = u / resources.displayMetrics.density   // lado menor da tela em dp
        txt("sintetizador de acordes", cxT, ty + u * 0.11f, ud * 0.05f, creamC, Paint.Align.CENTER, false, true, avail)
        txt("♪ direto do planeta Synthos ♪", cxT, ty + u * 0.18f, ud * 0.04f, accent, Paint.Align.CENTER, false, false, avail)
        // cabos de patch pendurados
        val cab = intArrayOf(PADC[0], PADC[4], PADC[2])
        for (k in 0 until 3) {
            val x0 = W * (if (land) 0.43f + 0.05f * k else 0.08f + 0.05f * k); val y0 = H * (0.80f + 0.035f * k)
            val x1 = W * (0.98f - 0.04f * k); val y1 = H * (0.84f + 0.03f * k)
            val sag = u * (0.09f + 0.03f * k) + sin(t * 2f + k) * dp(4f)
            sPath.reset(); sPath.moveTo(x0, y0)
            sPath.cubicTo(x0 + (x1 - x0) * 0.25f, y0 + sag, x0 + (x1 - x0) * 0.75f, y1 + sag, x1, y1)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
            p.strokeWidth = dp(10f); p.color = inkC; c.drawPath(sPath, p)
            p.strokeWidth = dp(6f); p.color = cab[k]; c.drawPath(sPath, p)
            p.style = Paint.Style.FILL
            for (e in 0..1) {
                val ex = if (e == 0) x0 else x1; val ey = if (e == 0) y0 else y1
                p.color = inkC; c.drawCircle(ex, ey, dp(9f), p); p.color = creamC; c.drawCircle(ex, ey, dp(5f), p)
            }
        }
        p.strokeCap = Paint.Cap.BUTT
        // convite piscando
        val al = (120 + 135 * (0.5f + 0.5f * sin(t * 4f))).toInt().coerceIn(0, 255)
        txt("toque para começar", cxT, ty + u * 0.27f, ud * 0.045f, Color.argb(al, 255, 243, 217), Paint.Align.CENTER, false, true, avail)
    }

    override fun onDraw(c: Canvas) {
        canvas0 = c
        if (bgBmp == null) buildBg()
        val bm = bgBmp
        if (bm != null) c.drawBitmap(bm, 0f, 0f, null) else c.drawColor(Color.parseColor("#2a1a3d"))
        if (splash) { drawSplash(c); postInvalidateOnAnimation(); return }
        drawTitle()
        chunky(updR, dp(16f), if (updHot) accent else panelC, false, dp(2f))
        txt(updLabel, updR.centerX(), updR.centerY() + dp(4f), 12f, if (updHot) onAccent else creamC, Paint.Align.CENTER, false, updHot, updR.width() - dp(10f))
        chunky(fxR, dp(16f), if (page != 0) accent else panelC, false, dp(2f))
        txt((if (synth.playing) "▶ " else "") + PAGES[page], fxR.centerX(), fxR.centerY() + dp(4f), 12f, if (page != 0) onAccent else creamC, Paint.Align.CENTER, false, page != 0, fxR.width() - dp(8f))
        p.style = Paint.Style.FILL; p.shader = null; p.color = if (helpOpen) accent else panelC
        c.drawCircle(helpR.centerX(), helpR.centerY(), helpR.width() / 2f, p)
        txt("?", helpR.centerX(), helpR.centerY() + dp(6f), 17f, if (helpOpen) onAccent else creamC, Paint.Align.CENTER, false, true)
        val st = if (navOn) (if (width > height) "EDITAR: setas movem · A confirma · B sai" else "EDITAR") else if (padSeen) "🎮" else "🎧"
        txt(st, helpR.left - dp(10f), barY + dp(23f), if (navOn) 12f else 16f, accent, Paint.Align.RIGHT, false, false, max(dp(40f), helpR.left - fxR.right - dp(24f)))
        // moldura de madeira do visor, como nos sintetizadores antigos
        tmpR2.set(visor.left - dp(5f), visor.top - dp(4f), visor.right + dp(5f), visor.bottom + dp(6f)); rr(tmpR2, dp(22f), mix(woodC, Color.BLACK, 0.40f))
        tmpR2.set(visor.left - dp(5f), visor.top - dp(4f), visor.right + dp(5f), visor.bottom + dp(4f)); rr(tmpR2, dp(22f), woodC)
        // visor
        if (page == 3) drawSteps() else {
        rr(visor, dp(18f), visorC)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = gridC
        for (i in 1 until 12) { val x = visor.left + visor.width() * i / 12; c.drawLine(x, visor.top, x, visor.bottom, p) }
        for (i in 1 until 6) { val y = visor.top + visor.height() * i / 6; c.drawLine(visor.left, y, visor.right, y, p) }
        val path = scopePath; path.rewind(); val n = 256; val sp = synth.scopePos
        for (i in 0 until n) {
            val v = synth.scope[(sp + i * 2) % synth.scope.size]
            val x = visor.left + visor.width() * i / (n - 1); val y = visor.centerY() - v * visor.height() * 0.9f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        p.strokeJoin = Paint.Join.ROUND; p.strokeCap = Paint.Cap.ROUND
        p.color = Color.argb(46, 194, 37, 92); p.strokeWidth = dp(7f); c.drawPath(path, p)
        p.color = scopeC; p.strokeWidth = dp(2.6f); c.drawPath(path, p)
        p.strokeCap = Paint.Cap.BUTT
        val name = if (modo == 6) earMsg else if (modo == 5) cname(seqChord, seqMod) else if (cur >= 0) (if (modo == 4) nm(leadNote(cur)) else cname(cur, mod)) else "—"
        txt(name, visor.left + dp(16f), visor.top + dp(62f), 52f, nameC, Paint.Align.LEFT, true, true, visor.width() * 0.55f)
        if (cur >= 0 && modo != 4 && modo != 6) txt(notes(cur, mod).joinToString("  ") { nm(it) }, visor.right - dp(14f), visor.top + dp(24f), 12f, infoC, Paint.Align.RIGHT, true, false, visor.width() * 0.5f)
        val info = if (modo == 6) "OUVIDO · ${EARL[earLvl].uppercase()} · ACERTOS $earOk/$earTot · SÉRIE $earStreak" else "${MODES[modo].uppercase()} · TOM ${NN[((key % 12) + 12) % 12]} ${if (minor) "MENOR" else "MAIOR"} · ${WAVES[synth.wave].uppercase()}" + (if (mod > 0) " · ${(if (modo == 4) LMODS[mod] else MODS[mod]).uppercase()}" else "")
        txt(info, visor.left + dp(16f), visor.bottom - dp(12f), 11f, infoC, Paint.Align.LEFT, true, false, visor.width() - dp(32f))
        if (page == 2) drawLoopStrip()
        }
        // joystick
        p.style = Paint.Style.FILL; p.shader = null
        p.color = mix(panelC, Color.BLACK, 0.38f); c.drawCircle(jcx, jcy + dp(3f), jr * 1.1f, p)
        p.color = panelC; c.drawCircle(jcx, jcy, jr * 1.1f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = dp(2f); p.color = Color.argb(60, 255, 243, 217)
        c.drawCircle(jcx, jcy, jr * 0.62f, p)
        p.style = Paint.Style.FILL
        val jl = if (modo == 4) JLL else JLP
        txt(jl[0], jcx, jcy - jr * 0.85f, 10f, grayC, Paint.Align.CENTER); txt(jl[1], jcx, jcy + jr * 1.0f, 10f, grayC, Paint.Align.CENTER)
        txt(jl[2], jcx + jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER); txt(jl[3], jcx - jr * 0.78f, jcy + dp(4f), 10f, grayC, Paint.Align.CENTER)
        val kx = jcx + (if (abs(gx) > abs(jx)) gx else jx) * jr * 0.6f; val ky = jcy - (if (abs(gy) > abs(jy)) gy else jy) * jr * 0.6f
        p.style = Paint.Style.FILL
        p.color = Color.argb(70, 0, 0, 0); c.drawCircle(kx, ky + dp(3f), jr * 0.32f, p)
        p.shader = LinearGradient(kx - jr * 0.3f, ky - jr * 0.3f, kx + jr * 0.3f, ky + jr * 0.3f, accent, accent2, Shader.TileMode.CLAMP)
        c.drawCircle(kx, ky, jr * 0.32f, p); p.shader = null
        // botões
        for (i in 0 until NB) {
            val hot = btnHot(i)
            chunky(btnR[i], dp(14f), if (hot) accent else panelC, false, dp(3f))
            val label = btnLabel(i)
            txt(label, btnR[i].centerX(), btnR[i].centerY() + dp(5f), 13f, if (hot) onAccent else creamC, Paint.Align.CENTER, false, hot, btnR[i].width() - dp(8f))
        }
        // pads: um de cada cor, como os cabos de patch
        for (i in 0 until 7) {
            val on = i == (if (modo == 5) seqChord else cur)
            val face = if (on) mix(PADC[i], Color.WHITE, 0.45f) else PADC[i]
            chunky(padR[i], dp(20f), face, on, dp(4f))
            if (on) {
                val o = dp(4f) * 0.7f
                tmpR.set(padR[i].left, padR[i].top + o, padR[i].right, padR[i].bottom + o)
                p.style = Paint.Style.STROKE; p.strokeWidth = dp(3f); p.color = creamC
                c.drawRoundRect(tmpR, dp(20f), dp(20f), p); p.style = Paint.Style.FILL
            }
            val oy = if (on) dp(3f) else 0f
            txt(if (modo == 4) nm(leadNote(i)) else cname(i, mod), padR[i].centerX(), padR[i].centerY() + dp(2f) + oy, 19f, onAccent, Paint.Align.CENTER, false, true, padR[i].width() - dp(10f))
            txt(ROM[i] + (if (padSeen) " · " + PADL[i] else ""), padR[i].centerX(), padR[i].centerY() + dp(22f) + oy, 11f, Color.argb(190, 42, 26, 61), Paint.Align.CENTER, false, false, padR[i].width() - dp(10f))
        }
        // botão de modo (ocupa a 8ª célula da grade de pads)
        chunky(modeR, dp(20f), panelC, false, dp(4f))
        txt("MODO" + (if (padSeen) " · Y" else ""), modeR.centerX(), modeR.centerY() - dp(10f), 11f, grayC, Paint.Align.CENTER, false, false, modeR.width() - dp(10f))
        txt(MODES[modo], modeR.centerX(), modeR.centerY() + dp(14f), 19f, accent, Paint.Align.CENTER, false, true, modeR.width() - dp(10f))
        if (navOn && navValid(navId)) {
            val r = navRect(navId)
            tmpR.set(r.left - dp(2f), r.top - dp(2f), r.right + dp(2f), r.bottom + dp(2f))
            p.style = Paint.Style.STROKE; p.shader = null
            p.strokeWidth = dp(6f); p.color = inkC; c.drawRoundRect(tmpR, dp(14f), dp(14f), p)
            p.strokeWidth = dp(3f); p.color = Color.WHITE; c.drawRoundRect(tmpR, dp(14f), dp(14f), p)
            p.style = Paint.Style.FILL
        }
        if (helpOpen) drawHelp(c)
        postInvalidateOnAnimation()
    }
}
