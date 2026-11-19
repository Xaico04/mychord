package com.hichord.app
import android.media.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.*

/**
 * Sintetizador com 6 vozes, arpejador, strum, bateria, sequenciador, looper de 6 faixas,
 * cadeia de efeitos e saída MIDI. A interface escreve nos campos; a thread de áudio lê no começo de cada bloco.
 *
 * Vozes: 0..3 = acorde (ou nota do Lead na 0); 4..5 = graves do Drone. Arpejo/Sequencer usam as 6 em rodízio.
 * Pistas do sequenciador (pat): 0 Kick, 1 Caixa, 2 Hat fechado, 3 Hat aberto, 4 Palma, 5 Tom, 6 Perc, 7 Melodia (0..8).
 */
class Synth {
    companion object { const val NV = 6; const val NT = 6; const val LOOP_CAP = 672000 }
    private val sr = 48000

    // ---- sintetizador ----
    @Volatile var wave = 0
    @Volatile var vol = 1f   // fixo em 100%: o volume e controlado so pelo celular
    val freq = FloatArray(NV)
    val tf = FloatArray(NV)
    val glide = FloatArray(NV)
    @Volatile var atkT = 0.02f
    @Volatile var relT = 0.25f
    @Volatile var pluck = false
    @Volatile var vib = 0f
    @Volatile var arpOn = false
    val arpF = FloatArray(8)
    @Volatile var arpN = 0
    @Volatile var arpDir = 0
    @Volatile var bpm = 120
    // ---- efeitos ----
    @Volatile var rvMix = 0f
    @Volatile var dlMix = 0f
    @Volatile var chMix = 0f
    @Volatile var flMix = 0f
    @Volatile var dlTimeMs = 300f
    // ---- transporte, bateria, sequenciador, looper ----
    @Volatile var playing = false          // escrito pela thread de áudio
    @Volatile var bars = 2                 // compassos do loop (1, 2 ou 4)
    @Volatile var kit = 0
    @Volatile var drumVol = 0.8f
    @Volatile var uiStep = 0               // passo atual (para a tela)
    val pat = ByteArray(8 * 16)
    @Volatile var seqOn = false
    val seqF = FloatArray(4)
    @Volatile var seqN = 0
    val tState = AtomicIntegerArray(NT)    // 0 vazia, 1 aguardando, 2 gravando, 3 tocando, 4 muda
    val trackBuf = arrayOfNulls<FloatArray>(NT)
    // ---- MIDI (a interface define; o motor enfileira) ----
    @Volatile var midiOutOn = false
    @Volatile var midiClock = false
    internal val midiQ = ConcurrentLinkedQueue<Int>()   // (tipo shl 24) or (a shl 8) or b ; tipos: 1 on, 2 off, 3 bateria, 4 tempo real

    val scope = FloatArray(512)
    @Volatile var scopePos = 0

    internal val gateA = AtomicIntegerArray(NV)
    internal val trigA = AtomicIntegerArray(NV)
    internal val offAll = AtomicBoolean(false)
    internal val startReq = AtomicBoolean(false)
    internal val stopReq = AtomicBoolean(false)

    private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var runId = 0

    fun gate(k: Int, on: Boolean) { gateA.set(k, if (on) 1 else 0) }

    fun strum(n: Int, down: Boolean, spreadMs: Float = 28f) {
        val d = (spreadMs * sr / 1000f).toInt()
        for (k in 0 until n) {
            val order = if (down) k else n - 1 - k
            trigA.set(k, order * d + 1)
        }
    }

    fun allOff() {
        arpOn = false
        for (k in 0 until NV) { gateA.set(k, 0); trigA.set(k, 0) }
        offAll.set(true)
    }

    /** Liga/desliga o transporte (bateria, sequenciador e looper andam juntos). */
    fun transport(on: Boolean) { if (on) startReq.set(true) else stopReq.set(true) }

    /** Garante a memória da faixa do looper (chamar na interface, antes de gravar). */
    fun prepareTrack(k: Int): Boolean {
        if (trackBuf[k] == null) {
            try { trackBuf[k] = FloatArray(2 * LOOP_CAP) } catch (e: OutOfMemoryError) { return false }
        }
        return true
    }

    fun start() {
        if (running) return
        val bpf = 2 * 4                                   // bytes por frame (estéreo, float)
        val minFrames = max(0, AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)) / bpf
        // Capacidade grande + tamanho de trabalho adaptativo: começa pequeno (resposta rápida)
        // e cresce sozinho se o Android avisar que faltou áudio (underrun = "estalo").
        val capFrames = max(minFrames * 2, 4096)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(capFrames * bpf).build()
        var target = min(capFrames, max(minFrames, 768))   // ~16 ms para começar
        try { t.bufferSizeInFrames = target } catch (e: Exception) {}
        // pré-enche com silêncio para o início não faltar áudio
        try { val z = (target / 2) * 2; t.write(FloatArray(z), 0, z, AudioTrack.WRITE_BLOCKING) } catch (e: Exception) {}   // metade do buffer: nunca trava antes do play()
        track = t; running = true; val my = ++runId; t.play()
        val eng = Engine(this, sr)
        Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buf = FloatArray(256 * 2)
            var lastUnder = 0; var n = 0
            while (running && runId == my) {
                eng.render(buf, 256)
                val w = t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
                if (w < 0) break
                if (++n >= 16) {                           // a cada ~85 ms confere se faltou áudio
                    n = 0
                    val u = t.underrunCount
                    if (u > lastUnder) {
                        lastUnder = u
                        if (target < min(capFrames, 2048)) {
                            target = min(capFrames, min(2048, target + 256))
                            try { t.bufferSizeInFrames = target } catch (e: Exception) {}
                        }
                    }
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        playing = false
        try { track?.stop(); track?.release() } catch (e: Exception) {}
        track = null
    }
}

// =====================================================================
//  Kits de bateria: por pista (kick, caixa, hat fechado, hat aberto, palma, tom, perc)
//  colunas: freqInicial freqFinal tempoPitch(ms) decaimento(ms) mistura-tom mistura-ruído passa-alta
// =====================================================================
private val KIT_TXT = arrayOf(
    // 0 - 808
    "160 45 45 520 1 0.03 0", "230 190 25 190 0.55 0.8 0.12", "0 0 1 45 0 1 0.45", "0 0 1 320 0 1 0.45",
    "0 0 1 170 0 1 0.25", "190 85 70 380 1 0 0", "560 540 10 140 0.85 0.15 0.3",
    // 1 - Rock
    "130 55 28 240 1 0.12 0", "200 170 18 210 0.35 1 0.10", "0 0 1 38 0 1 0.35", "0 0 1 260 0 1 0.35",
    "0 0 1 150 0 1 0.2", "210 120 45 300 1 0.05 0", "800 760 8 110 0.8 0.2 0.3",
    // 2 - Lo-Fi
    "105 50 35 180 1 0.02 0", "190 160 20 150 0.5 0.6 0.04", "0 0 1 32 0 1 0.18", "0 0 1 150 0 1 0.18",
    "0 0 1 130 0 1 0.12", "150 80 50 220 1 0 0", "420 400 8 90 0.9 0.1 0.15",
    // 3 - Techno
    "190 50 32 330 1 0.10 0", "260 200 15 120 0.3 1 0.2", "0 0 1 26 0 1 0.6", "0 0 1 200 0 1 0.6",
    "0 0 1 125 0 1 0.3", "230 120 40 230 1 0 0", "1200 1000 6 70 0.9 0.1 0.4",
    // 4 - Jazz
    "95 62 22 290 1 0.04 0", "180 165 15 160 0.3 0.55 0.05", "0 0 1 60 0 1 0.25", "0 0 1 420 0 1 0.25",
    "0 0 1 140 0 1 0.15", "170 105 55 320 1 0.03 0", "650 620 10 160 0.8 0.15 0.25",
    // 5 - Eletro
    "210 70 16 360 1 0.08 0", "330 220 30 170 0.7 0.6 0.15", "0 0 1 24 0 1 0.5", "0 0 1 170 0 1 0.5",
    "0 0 1 230 0 1 0.3", "260 110 35 280 1 0 0", "900 700 12 130 0.9 0.1 0.35",
    // 6 - Acústico
    "115 62 20 190 1 0.16 0", "195 160 14 175 0.28 1 0.07", "0 0 1 42 0 1 0.3", "0 0 1 280 0 1 0.3",
    "0 0 1 135 0 1 0.18", "165 100 38 270 1 0.05 0", "720 690 9 120 0.8 0.25 0.3"
)
private val DK: FloatArray = FloatArray(KIT_TXT.size * 7).also { a ->
    for (i in KIT_TXT.indices) { val p = KIT_TXT[i].split(" "); for (j in 0 until 7) a[i * 7 + j] = p[j].toFloat() }
}
private val LG = floatArrayOf(1.0f, 0.8f, 0.32f, 0.32f, 0.65f, 0.8f, 0.5f)   // ganho por pista
private val GM = intArrayOf(36, 38, 42, 46, 39, 45, 56)                      // notas General MIDI (canal 10)

// =====================================================================
//  Motor de áudio (roda só na thread de áudio)
// =====================================================================
private class Engine(private val s: Synth, private val sr: Int) {
    private val NV = Synth.NV
    private val NT = Synth.NT
    private val phase = FloatArray(NV); private val env = FloatArray(NV); private val stage = IntArray(NV)
    private val fcur = FloatArray(NV); private val cnt = IntArray(NV); private val hold = IntArray(NV)
    private val lpv = FloatArray(NV); private val prevG = BooleanArray(NV)
    private val mNote = IntArray(NV) { -1 }
    private val tsL = IntArray(NT)
    private var curW = 0; private var wGain = 1f          // troca de onda sem estalo (fade curto)
    private val wStep = 1f / (0.004f * sr)
    private val dR0 = FloatArray(7)                       // nível de partida da bateria (evita degrau ao retocar)
    private val dAtk = max(1, (sr * 0.0015f).toInt())     // rampa de 1,5 ms no ataque da bateria
    private val loopFade = (sr * 0.003f).toInt()          // fade de 3 ms nas pontas do loop
    private val fx = Fx(sr)
    private var lp = 0f; private var lfo = 0f
    private var arpCnt = 0; private var arpStep = 0; private var arpWas = false; private var arpVoice = 0
    private var rng = 123456789
    private val pdec = exp(-1f / (0.6f * sr))
    private val twoPi = 2f * PI.toFloat()
    // bateria
    private val dF = FloatArray(7); private val dFe = FloatArray(7); private val dMulP = FloatArray(7); private val dMulA = FloatArray(7)
    private val dTone = FloatArray(7); private val dNz = FloatArray(7); private val dHp = FloatArray(7)
    private val dEnv = FloatArray(7); private val dPh = FloatArray(7); private val dLp = FloatArray(7)
    private val dAge = IntArray(7); private val dOn = BooleanArray(7)
    private val clapEnd = (sr * 0.03f).toInt(); private val clapPulse = max(1, (sr * 0.01f).toInt())
    // transporte
    private var playing = false; private var stepIdx = 0; private var sub = 0; private var clkAcc = 0f

    /** correção PolyBLEP: tira o "chiado" (aliasing) das ondas serra e quadrada */
    private fun blep(t: Float, dt: Float): Float {
        if (t < dt) { val x = t / dt; return x + x - x * x - 1f }
        if (t > 1f - dt) { val x = (t - 1f) / dt; return x * x + x + x + 1f }
        return 0f
    }
    /** limitador suave: linear (limpo) até 0,6 e curva só acima disso; tanh puro distorcia já em níveis baixos */
    private fun soft(x: Float): Float {
        val a = abs(x)
        if (a <= 0.6f) return x
        val y = 0.6f + 0.4f * tanh((a - 0.6f) / 0.4f)
        return if (x < 0f) -y else y
    }

    // ---- MIDI ----
    private fun emit(t: Int, a: Int, b: Int) { if (s.midiOutOn) s.midiQ.offer((t shl 24) or (a shl 8) or b) }
    private fun noteOf(f: Float): Int = if (f < 8f) -1 else (69f + 17.31234f * ln(f / 440f)).roundToInt().coerceIn(0, 127)
    private fun midiOn(k: Int) {
        if (!s.midiOutOn) return
        val n = noteOf(s.freq[k]); if (n < 0) return
        if (mNote[k] >= 0) emit(2, mNote[k], 0)
        mNote[k] = n; emit(1, n, 100)
    }
    private fun midiOff(k: Int) { if (mNote[k] >= 0) { emit(2, mNote[k], 0); mNote[k] = -1 } }

    // stage: 0 parada, 1 ataque, 2 sustentação, 3 release
    private fun on(k: Int) { if (stage[k] == 0) fcur[k] = s.freq[k]; stage[k] = 1; midiOn(k) }
    private fun off(k: Int) { if (stage[k] != 0) stage[k] = 3; midiOff(k) }

    private fun fireVoice(f: Float, holdN: Int) {
        val k = arpVoice; arpVoice = (arpVoice + 1) % NV
        s.freq[k] = f; fcur[k] = f; hold[k] = holdN
        on(k)
    }

    private fun arpIdx(n: Int, st: Int, dir: Int): Int = when (dir) {
        0 -> st % n
        1 -> n - 1 - st % n
        2 -> { val per = max(1, 2 * n - 2); val q = st % per; if (q < n) q else per - q }
        else -> {
            rng = rng xor (rng shl 13); rng = rng xor (rng ushr 17); rng = rng xor (rng shl 5)
            (rng and 0x7fffffff) % n
        }
    }

    private fun hit(l: Int) {
        val b = (s.kit * 7 + l) * 7
        dF[l] = DK[b]; dFe[l] = DK[b + 1]
        dMulP[l] = exp(-1f / (max(0.5f, DK[b + 2]) * 0.001f * sr))
        dMulA[l] = exp(-1f / (max(5f, DK[b + 3]) * 0.001f * sr))
        dTone[l] = DK[b + 4]; dNz[l] = DK[b + 5]; dHp[l] = DK[b + 6]
        dR0[l] = if (dOn[l]) min(1f, dEnv[l]) else 0f     // retoque: parte do nível atual, sem degrau
        if (!dOn[l]) dPh[l] = 0f                           // começa a senoide no zero: sem estalo no bumbo/tom
        dEnv[l] = 1f; dAge[l] = 0; dOn[l] = true
        if (l == 2 && dOn[3]) dMulA[3] = exp(-1f / (0.006f * sr))   // hat fechado abafa o aberto em 6 ms (antes: corte seco)
        emit(3, GM[l], 100)
    }

    /** Chamado a cada passo de 1/16 enquanto o transporte anda. */
    private fun onStep(st: Int, stepLen: Int) {
        if (st == 0) {
            for (k in 0 until NT) {
                val t = s.tState.get(k)
                if (t == 2) s.tState.compareAndSet(k, 2, 3) else if (t == 1) s.tState.compareAndSet(k, 1, 2)
            }
        }
        for (k in 0 until NT) tsL[k] = s.tState.get(k)
        val s16 = st % 16
        for (l in 0 until 7) if (s.pat[l * 16 + s16].toInt() != 0) hit(l)
        val v = s.pat[7 * 16 + s16].toInt()
        val n = s.seqN
        if (v > 0 && s.seqOn && n > 0) {
            val f = s.seqF[(v - 1) % n] * (if (v > 4) 2f else 1f)
            fireVoice(f, (stepLen * 0.8f).toInt())
        }
    }

    fun render(out: FloatArray, frames: Int) {
        val w = s.wave; val vol = s.vol
        val atk = 1f / (max(0.001f, s.atkT) * sr)
        val rel = 1f / (max(0.005f, s.relT) * sr)
        val pl = s.pluck; val vb = s.vib
        val rvM = s.rvMix; val dlM = s.dlMix; val chM = s.chMix; val flM = s.flMix; val dlT = s.dlTimeMs
        val stepLen = max(1, (sr * 60f / max(40, s.bpm) / 4f).toInt())   // semicolcheia
        val total = 16 * max(1, s.bars)
        val loopLen = total * stepLen
        val dir = s.arpDir
        val dv = s.drumVol
        val mClk = s.midiClock
        val clkInc = 6f / stepLen

        // comandos vindos da interface
        if (s.offAll.getAndSet(false)) {
            for (k in 0 until NV) { cnt[k] = 0; hold[k] = 0; prevG[k] = false; off(k) }
        }
        if (s.startReq.getAndSet(false)) {
            playing = true; s.playing = true
            stepIdx = total - 1; sub = stepLen; clkAcc = 0f
            if (mClk) emit(4, 0xFA, 0)
        }
        if (s.stopReq.getAndSet(false)) {
            playing = false; s.playing = false
            for (k in 0 until NT) { val t = s.tState.get(k); if (t == 1 || t == 2) s.tState.set(k, 0) }
            if (mClk) emit(4, 0xFC, 0)
        }
        if (stepIdx >= total) stepIdx = total - 1
        for (k in 0 until NV) {
            val g = s.gateA.get(k) != 0
            if (g != prevG[k]) { prevG[k] = g; if (g) on(k) else off(k) }
            val t = s.trigA.getAndSet(k, 0)
            if (t > 0) cnt[k] = t
        }
        if (s.midiOutOn) {   // nota mudou com a tecla segurada (legato do Lead, troca de acorde)
            for (k in 0 until NV) if (prevG[k] && (stage[k] == 1 || stage[k] == 2)) {
                val n = noteOf(s.freq[k]); if (n >= 0 && n != mNote[k]) midiOn(k)
            }
        }
        for (k in 0 until NT) tsL[k] = s.tState.get(k)
        val aOn = s.arpOn
        if (aOn && !arpWas) { arpStep = 0; arpCnt = 0 }
        arpWas = aOn

        for (i in 0 until frames) {
            // transporte: passo atual do sequenciador / loop
            var loopPos = 0
            if (playing) {
                var newStep = false
                if (sub >= stepLen) { sub = 0; stepIdx++; if (stepIdx >= total) stepIdx = 0; newStep = true }
                loopPos = stepIdx * stepLen + sub
                sub++
                if (newStep) onStep(stepIdx, stepLen)
                if (mClk) { clkAcc += clkInc; if (clkAcc >= 1f) { clkAcc -= 1f; emit(4, 0xF8, 0) } }
            }
            // arpejador
            if (aOn) {
                if (arpCnt <= 0) {
                    val n = s.arpN
                    if (n > 0) {
                        val idx = arpIdx(n, arpStep, dir)
                        arpStep++
                        fireVoice(s.arpF[idx], (stepLen * 0.6f).toInt())
                    }
                    arpCnt = stepLen
                }
                arpCnt--
            }
            var lfoS = 0f
            if (vb > 0f) { lfo += 5.5f / sr; if (lfo >= 1f) lfo -= 1f; lfoS = sin(twoPi * lfo) }

            if (curW != w) { wGain -= wStep; if (wGain <= 0f) { wGain = 0f; curW = w } }
            else if (wGain < 1f) { wGain = min(1f, wGain + wStep) }
            var sum = 0f
            for (k in 0 until NV) {
                if (cnt[k] > 0) { cnt[k]--; if (cnt[k] == 0) { s.freq[k] = s.tf[k]; hold[k] = 0; on(k) } }
                if (hold[k] > 0) { hold[k]--; if (hold[k] == 0) off(k) }
                val st = stage[k]
                if (st == 0) continue
                var e = env[k]
                when (st) {
                    1 -> { e += atk; if (e >= 1f) { e = 1f; stage[k] = 2 } }
                    2 -> { if (pl) { e *= pdec; if (e < 0.0008f) { e = 0f; stage[k] = 0; midiOff(k) } } }
                    else -> { e -= rel; if (e <= 0f) { e = 0f; stage[k] = 0 } }
                }
                env[k] = e
                val g = s.glide[k]; val tfq = s.freq[k]
                fcur[k] = if (g > 0f) fcur[k] + (tfq - fcur[k]) * g else tfq
                var f = fcur[k]
                if (vb > 0f) f *= 1f + vb * 0.0578f * lfoS
                phase[k] += f / sr; if (phase[k] >= 1f) phase[k] -= 1f
                val p = phase[k]
                val dt = min(0.45f, f / sr)
                var o = when (curW) {
                    0 -> sin(twoPi * p)
                    1 -> 4f * abs(p - 0.5f) - 1f
                    2 -> 2f * p - 1f - blep(p, dt)
                    else -> {
                        var q = p + 0.5f; if (q >= 1f) q -= 1f
                        (if (p < 0.5f) 1f else -1f) + blep(p, dt) - blep(q, dt)
                    }
                }
                if (pl) { lpv[k] += (0.05f + 0.5f * e * e) * (o - lpv[k]); o = lpv[k] }
                sum += o * e * wGain
            }
            lp += 0.25f * (sum * 0.25f - lp)
            fx.process(lp, rvM, dlM, chM, flM, dlT)

            // looper: grava o som ao vivo (com efeitos) e toca as faixas gravadas
            var lL = 0f; var lR = 0f
            if (playing) {
                val ix = 2 * loopPos
                for (k in 0 until NT) {
                    val ts = tsL[k]
                    if (ts == 2 || ts == 3) {
                        val b = s.trackBuf[k]
                        if (b != null && ix + 1 < b.size) {
                            if (ts == 2) { b[ix] = fx.outL; b[ix + 1] = fx.outR }
                            else {
                                val edge = min(loopPos, loopLen - 1 - loopPos)
                                val lg = if (edge < loopFade) max(0, edge).toFloat() / loopFade else 1f
                                lL += b[ix] * lg; lR += b[ix + 1] * lg
                            }
                        }
                    }
                }
            }

            // bateria (seca, no centro)
            var ds = 0f
            for (l in 0 until 7) {
                if (!dOn[l]) continue
                var e = dEnv[l]
                dF[l] = dFe[l] + (dF[l] - dFe[l]) * dMulP[l]
                dPh[l] += dF[l] / sr; if (dPh[l] >= 1f) dPh[l] -= 1f
                val tone = sin(twoPi * dPh[l])
                rng = rng xor (rng shl 13); rng = rng xor (rng ushr 17); rng = rng xor (rng shl 5)
                val nz = (rng and 0xFFFF) / 32768f - 1f
                var nn = nz
                val hp = dHp[l]
                if (hp > 0f) { dLp[l] += hp * (nz - dLp[l]); nn = nz - dLp[l] }
                var amp = e
                if (dAge[l] < dAtk) amp *= dR0[l] + (1f - dR0[l]) * (dAge[l].toFloat() / dAtk)
                if (l == 4 && dAge[l] < clapEnd) amp *= 1f - 0.6f * ((dAge[l] % clapPulse).toFloat() / clapPulse)
                ds += amp * (dTone[l] * tone + dNz[l] * nn) * LG[l]
                e *= dMulA[l]; dEnv[l] = e; dAge[l]++
                if (e < 0.001f) dOn[l] = false
            }
            val dOut = ds * dv

            val oL = soft((fx.outL + lL * 0.9f + dOut) * vol)
            val oR = soft((fx.outR + lR * 0.9f + dOut) * vol)
            out[2 * i] = oL; out[2 * i + 1] = oR
            if (i % 4 == 0) { s.scope[s.scopePos] = oL; s.scopePos = (s.scopePos + 1) % s.scope.size }
        }
        s.uiStep = stepIdx
    }
}

// =====================================================================
//  Efeitos
// =====================================================================
private class DLine(private val len: Int) {
    private val buf = FloatArray(len); private var w = 0
    fun push(v: Float) { buf[w] = if (v < 1e-18f && v > -1e-18f) 0f else v; w++; if (w >= len) w = 0 }
    /** d = atraso em amostras (>= 1 e <= len - 2), contado a partir da última amostra gravada */
    fun read(d: Float): Float {
        var r = (w - 1) - d
        while (r < 0f) r += len
        var i0 = r.toInt(); if (i0 >= len) i0 = 0
        val fr = r - i0
        var i1 = i0 + 1; if (i1 >= len) i1 = 0
        return buf[i0] * (1f - fr) + buf[i1] * fr
    }
}
private class Comb(private val len: Int) {
    private val buf = FloatArray(len); private var i = 0; private var f = 0f
    fun go(x: Float, fb: Float, damp: Float): Float {
        val o = buf[i]
        f = o * (1f - damp) + f * damp
        if (f < 1e-15f && f > -1e-15f) f = 0f
        buf[i] = x + f * fb
        i++; if (i >= len) i = 0
        return o
    }
}
private class Allp(private val len: Int) {
    private val buf = FloatArray(len); private var i = 0
    fun go(x: Float): Float {
        val b = buf[i]
        buf[i] = x + b * 0.5f
        i++; if (i >= len) i = 0
        return b - x
    }
}
private val RV_COMB = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
private val RV_AP = intArrayOf(556, 441, 341, 225)
private const val RV_SPREAD = 23
private const val RV_FB = 0.84f
private const val RV_DAMP = 0.25f
private const val RV_WET = 2.0f

/** Cadeia: chorus -> flanger -> delay (ping-pong) -> reverb. Entrada mono, saída estéreo. */
private class Fx(private val sr: Int) {
    private val sc = sr / 44100f
    private val chL = DLine(4096); private val chR = DLine(4096)
    private val flL = DLine(1024); private val flR = DLine(1024)
    private val dlL = DLine(sr + 8); private val dlR = DLine(sr + 8)
    private val combL = Array(8) { Comb((RV_COMB[it] * sc).toInt()) }
    private val combR = Array(8) { Comb(((RV_COMB[it] + RV_SPREAD) * sc).toInt()) }
    private val apL = Array(4) { Allp((RV_AP[it] * sc).toInt()) }
    private val apR = Array(4) { Allp(((RV_AP[it] + RV_SPREAD) * sc).toInt()) }
    private val twoPi = 2f * PI.toFloat()
    private var chPh = 0f; private var flPh = 0f
    private var flFbL = 0f; private var flFbR = 0f
    private var dlCur = 0.3f * sr
    private var mRv = 0f; private var mDl = 0f; private var mCh = 0f; private var mFl = 0f
    var outL = 0f; var outR = 0f

    fun process(x: Float, tRv: Float, tDl: Float, tCh: Float, tFl: Float, dlMs: Float) {
        mRv += (tRv - mRv) * 0.002f; mDl += (tDl - mDl) * 0.002f
        mCh += (tCh - mCh) * 0.002f; mFl += (tFl - mFl) * 0.002f

        chPh += 0.7f / sr; if (chPh >= 1f) chPh -= 1f
        chL.push(x); chR.push(x)
        val cdL = (0.016f + 0.004f * sin(twoPi * chPh)) * sr
        val cdR = (0.016f + 0.004f * sin(twoPi * (chPh + 0.25f))) * sr
        val dryC = 1f - 0.3f * mCh
        var l = x * dryC + chL.read(cdL) * 0.7f * mCh
        var r = x * dryC + chR.read(cdR) * 0.7f * mCh

        flPh += 0.22f / sr; if (flPh >= 1f) flPh -= 1f
        val fdL = (0.0035f + 0.003f * sin(twoPi * flPh)) * sr
        val fdR = (0.0035f + 0.003f * sin(twoPi * (flPh + 0.25f))) * sr
        flL.push(l + flFbL * 0.5f); flR.push(r + flFbR * 0.5f)
        val fL = flL.read(fdL); val fR = flR.read(fdR)
        flFbL = fL; flFbR = fR
        val gF = 1f / (1f + 0.7f * mFl)
        l = (l + fL * mFl) * gF; r = (r + fR * mFl) * gF

        val dTarget = (dlMs * 0.001f * sr).coerceIn(64f, (sr - 4).toFloat())
        dlCur += (dTarget - dlCur) * 0.0004f
        val tL = dlL.read(dlCur); val tR = dlR.read(dlCur)
        dlL.push(0.5f * (l + r) + tR * 0.42f)
        dlR.push(tL * 0.42f)
        l += tL * 0.75f * mDl; r += tR * 0.75f * mDl

        val inp = (l + r) * 0.5f * 0.015f
        var sL = 0f; var sR = 0f
        for (i in 0 until 8) { sL += combL[i].go(inp, RV_FB, RV_DAMP); sR += combR[i].go(inp, RV_FB, RV_DAMP) }
        for (i in 0 until 4) { sL = apL[i].go(sL); sR = apR[i].go(sR) }
        l += sL * RV_WET * mRv; r += sR * RV_WET * mRv

        outL = l; outR = r
    }
}
