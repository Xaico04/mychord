package com.hichord.app
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlin.math.pow

/**
 * MIDI pelo USB-C: envia notas, bateria (canal 10) e clock para DAWs/sintetizadores,
 * e recebe notas de um teclado externo para tocar o som do app.
 */
class MidiIO(ctx: Context, private val synth: Synth) {
    private val mm: MidiManager? = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val main = Handler(Looper.getMainLooper())
    private var infos: List<MidiDeviceInfo> = emptyList()
    private var selId = -1
    private var dev: MidiDevice? = null
    private var inPort: MidiInputPort? = null      // app -> dispositivo
    private var outPort: MidiOutputPort? = null    // dispositivo -> app
    @Volatile var sendOn = true
    @Volatile var recvOn = true
    @Volatile var clockOn = false
    @Volatile var channel = 0
    @Volatile private var running = false
    private var thread: Thread? = null
    var onChange: () -> Unit = {}
    private val vNote = IntArray(Synth.NV) { -1 }
    private val vTime = LongArray(Synth.NV)
    private var vClock = 0L
    private val buf3 = ByteArray(3)
    private val buf1 = ByteArray(1)

    private val receiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) { parse(msg, offset, count) }
    }
    private val cb = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { refresh(); onChange() }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (info.id == selId) { closeDev(); selId = -1 }
            refresh(); onChange()
        }
    }

    val available: Boolean get() = mm != null

    fun refresh() {
        val m = mm ?: return
        infos = m.devices.filter { it.inputPortCount > 0 || it.outputPortCount > 0 }
    }

    fun label(): String {
        if (mm == null) return "sem MIDI"
        val i = infos.indexOfFirst { it.id == selId }
        if (i < 0) return if (infos.isEmpty()) "nenhum" else "desligado"
        return infos[i].properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ("Dispositivo " + (i + 1))
    }

    /** Passa para o próximo dispositivo; depois do último, desliga. */
    fun cycle() {
        refresh()
        if (infos.isEmpty()) { selId = -1; closeDev(); return }
        val n = infos.indexOfFirst { it.id == selId } + 1
        if (n >= infos.size) { selId = -1; closeDev() } else { selId = infos[n].id; openSel(infos[n]) }
    }

    private fun openSel(info: MidiDeviceInfo) {
        closeDev()
        mm?.openDevice(info, MidiManager.OnDeviceOpenedListener { d ->
            if (d != null) {
                dev = d
                try { if (info.inputPortCount > 0) inPort = d.openInputPort(0) } catch (e: Exception) {}
                try {
                    if (info.outputPortCount > 0) { outPort = d.openOutputPort(0); outPort?.connect(receiver) }
                } catch (e: Exception) {}
            } else selId = -1
            apply(); onChange()
        }, main)
    }

    private fun closeDev() {
        panic()
        for (j in 0 until Synth.NV) if (vNote[j] >= 0) { vNote[j] = -1; synth.gate(j, false) }
        try { outPort?.disconnect(receiver) } catch (e: Exception) {}
        try { outPort?.close() } catch (e: Exception) {}
        try { inPort?.close() } catch (e: Exception) {}
        try { dev?.close() } catch (e: Exception) {}
        outPort = null; inPort = null; dev = null
        apply()
    }

    private fun apply() {
        synth.midiOutOn = sendOn && inPort != null
        synth.midiClock = clockOn && synth.midiOutOn
    }

    fun setSend(on: Boolean) { if (!on) panic(); sendOn = on; apply() }
    fun setRecv(on: Boolean) { recvOn = on }
    fun setClock(on: Boolean) { clockOn = on; apply() }
    fun setChannel(c: Int) { panic(); channel = c.coerceIn(0, 15) }

    fun start() {
        val m = mm ?: return
        refresh()
        try { m.registerDeviceCallback(cb, main) } catch (e: Exception) {}
        running = true
        thread = Thread { loop() }.also { it.start() }
        val info = infos.firstOrNull { it.id == selId }
        if (info != null) openSel(info) else selId = -1
    }

    fun stop() {
        running = false
        try { mm?.unregisterDeviceCallback(cb) } catch (e: Exception) {}
        closeDev()
        thread = null
    }

    @Synchronized private fun send3(a: Int, b: Int, c: Int) {
        buf3[0] = a.toByte(); buf3[1] = b.toByte(); buf3[2] = c.toByte()
        try { inPort?.send(buf3, 0, 3) } catch (e: Exception) {}
    }
    @Synchronized private fun send1(a: Int) {
        buf1[0] = a.toByte()
        try { inPort?.send(buf1, 0, 1) } catch (e: Exception) {}
    }

    /** Todas as notas desligadas no canal atual e no canal 10. */
    fun panic() {
        if (inPort == null) return
        send3(0xB0 or channel, 123, 0)
        send3(0xB9, 123, 0)
    }

    // ---- envio (thread própria; o áudio só enfileira) ----
    private fun loop() {
        val pend = ArrayList<LongArray>()
        while (running) {
            var e = synth.midiQ.poll()
            while (e != null) {
                val t = e shr 24; val a = (e shr 8) and 0xFF; val b = e and 0xFF
                when (t) {
                    1 -> send3(0x90 or channel, a, b)
                    2 -> send3(0x80 or channel, a, 0)
                    3 -> { send3(0x99, a, b); pend.add(longArrayOf(System.currentTimeMillis() + 60L, a.toLong())) }
                    4 -> send1(a)
                }
                e = synth.midiQ.poll()
            }
            if (pend.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val iter = pend.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    if (p[0] <= now) { send3(0x89, p[1].toInt(), 0); iter.remove() }
                }
            }
            try { Thread.sleep(2) } catch (e2: InterruptedException) {}
        }
    }

    // ---- recepção (teclado externo toca o som do app) ----
    private fun parse(b: ByteArray, off: Int, cnt: Int) {
        var i = off; val end = off + cnt
        while (i < end) {
            val st = b[i].toInt() and 0xFF
            if (st < 0x80) { i++; continue }
            val type = st and 0xF0
            if (st >= 0xF8) { i += 1 }
            else if (st == 0xF0) { while (i < end && (b[i].toInt() and 0xFF) != 0xF7) i++; i++ }
            else if (type == 0x80 || type == 0x90) {
                if (i + 2 >= end) return
                val n = b[i + 1].toInt() and 0x7F; val v = b[i + 2].toInt() and 0x7F
                if (recvOn) noteIn(n, type == 0x90 && v > 0)
                i += 3
            }
            else if (type == 0xC0 || type == 0xD0) { i += 2 }
            else { i += 3 }
        }
    }

    @Synchronized private fun noteIn(n: Int, on: Boolean) {
        if (on) {
            var k = -1
            for (j in 0 until Synth.NV) if (vNote[j] < 0) { k = j; break }
            if (k < 0) { k = 0; for (j in 1 until Synth.NV) if (vTime[j] < vTime[k]) k = j }
            vNote[k] = n; vTime[k] = ++vClock
            synth.freq[k] = 440f * 2f.pow((n - 69) / 12f)
            synth.gate(k, true)
        } else {
            for (j in 0 until Synth.NV) if (vNote[j] == n) { vNote[j] = -1; synth.gate(j, false) }
        }
    }
}
