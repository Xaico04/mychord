package com.hichord.app
import android.media.*
import kotlin.math.*

class Synth {
    private val sr = 48000
    @Volatile var wave = 0
    @Volatile var vol = 0.4f
    @Volatile var gate = false
    val freq = FloatArray(4)
    val active = BooleanArray(4)
    val scope = FloatArray(512)
    @Volatile var scopePos = 0
    private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var runId = 0

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(max(minBuf, 512 * 2 * 4)).build()
        track = t; running = true; val my = ++runId; t.play()
        Thread {
            val phase = FloatArray(4); val env = FloatArray(4); var lp = 0f
            val buf = FloatArray(256 * 2)
            while (running && runId == my) {
                val w = wave; val g = gate; val v = vol
                for (i in 0 until 256) {
                    var sum = 0f
                    for (k in 0 until 4) {
                        val target = if (g && active[k]) 1f else 0f
                        val step = if (target > env[k]) 1f / (0.02f * sr) else 1f / (0.25f * sr)
                        env[k] = if (env[k] < target) min(target, env[k] + step) else max(target, env[k] - step)
                        if (env[k] <= 0.0001f) continue
                        phase[k] += freq[k] / sr
                        if (phase[k] >= 1f) phase[k] -= 1f
                        val p = phase[k]
                        val s = when (w) {
                            0 -> sin(2f * PI.toFloat() * p)
                            1 -> 4f * abs(p - 0.5f) - 1f
                            2 -> 2f * p - 1f
                            else -> if (p < 0.5f) 1f else -1f
                        }
                        sum += s * env[k]
                    }
                    lp += 0.25f * (sum * 0.25f - lp)
                    val o = (lp * v).coerceIn(-1f, 1f)
                    buf[2 * i] = o; buf[2 * i + 1] = o
                    if (i % 4 == 0) { scope[scopePos] = o; scopePos = (scopePos + 1) % scope.size }
                }
                t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
        }.also { it.priority = Thread.MAX_PRIORITY }.start()
    }

    fun stop() {
        running = false
        try { track?.stop(); track?.release() } catch (e: Exception) {}
        track = null
    }
}
