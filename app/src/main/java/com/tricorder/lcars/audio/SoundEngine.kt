package com.tricorder.lcars.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Real-time synthesizer for original, show-style tricorder audio.
 *
 * - One-shot effects (panel taps, scan start/complete, alerts, boot) are rendered once into
 *   PCM buffers and mixed in when triggered.
 * - A continuous "scan voice" is synthesized live on the audio thread. Its pitch, speed and
 *   texture follow [intensity], which the UI feeds from live sensor data, so the scan sound
 *   reacts to what the phone is actually sensing.
 *
 * Call [start] in onResume and [stop] in onPause.
 */
class SoundEngine {

    enum class Fx { TAP, PROBE, TOGGLE, SCAN_START, SCAN_COMPLETE, ALERT, BOOT, PING }

    /** Scan-voice flavors: standard chatter, geo sonar, medical warble, engineering thrum. */
    enum class Voice { NONE, TRICORDER, GEO, MEDICAL, ENGINEERING }

    @Volatile var enabled = true
    @Volatile var voice = Voice.NONE
    /** 0..1, driven by sensor data. */
    @Volatile var intensity = 0f

    private val sr = 44_100
    private val pending = ConcurrentLinkedQueue<FloatArray>()
    private val rng = Random(System.nanoTime())
    private val bank: Map<Fx, List<FloatArray>> = buildBank()

    @Volatile private var running = false
    private var thread: Thread? = null

    fun play(fx: Fx) {
        if (!enabled) return
        val variants = bank[fx] ?: return
        pending.add(variants[rng.nextInt(variants.size)])
    }

    /** Ad-hoc decaying beep (used by the motion tracker for pulse/return tones). */
    fun beep(freq: Double, dur: Double, amp: Double = 0.3, glideTo: Double = freq) {
        if (!enabled) return
        pending.add(seg(freq, glideTo, dur, amp = amp, harm = 0.08, decay = true))
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "lcars-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
        pending.clear()
    }

    // ── Audio thread ──────────────────────────────────────────────────

    private class Playing(val buf: FloatArray, var pos: Int = 0)

    private fun loop() {
        val minBuf = AudioTrack.getMinBufferSize(
            sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBuf * 2, BLOCK * 8))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val mix = FloatArray(BLOCK)
        val out = ShortArray(BLOCK)
        val active = ArrayList<Playing>()
        try {
            track.play()
            while (running) {
                while (true) {
                    val b = pending.poll() ?: break
                    active.add(Playing(b))
                }
                mix.fill(0f)
                val it = active.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    val n = min(BLOCK, p.buf.size - p.pos)
                    for (i in 0 until n) mix[i] += p.buf[p.pos + i]
                    p.pos += n
                    if (p.pos >= p.buf.size) it.remove()
                }
                renderVoice(mix)
                for (i in 0 until BLOCK) {
                    // One-pole low-pass (~4.5 kHz) takes the glassy edge off everything.
                    master += (mix[i] - master) * 0.47f
                    out[i] = (tanh(master * 1.1f) * 30000f).toInt().toShort()
                }
                track.write(out, 0, BLOCK)
            }
        } catch (e: Exception) {
            // Audio device lost: fail silently, the UI keeps working.
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    // Live voice state (audio thread only)
    private var gain = 0f
    private var master = 0f
    private var activeVoice = Voice.NONE
    private var t = 0.0
    private var blipRemain = 0
    private var blipLen = 1
    private var blipFreq = 880.0
    private var blipPhase = 0.0
    private var gapRemain = 0
    private var whirrPhase = 0.0
    private var humPhase = 0.0
    private var carrierPhase = 0.0
    private var sawPhase = 0.0
    private var lowpass = 0f
    private var pingTimer = 0

    private fun renderVoice(mix: FloatArray) {
        val target = voice
        val wantOn = target != Voice.NONE && enabled
        if (target != Voice.NONE) activeVoice = target
        if (!wantOn && gain == 0f) return

        val k = intensity.coerceIn(0f, 1f)
        val dt = 1.0 / sr
        for (i in mix.indices) {
            gain += ((if (wantOn) 1f else 0f) - gain) * 0.0012f
            if (!wantOn && gain < 0.0004f) {
                gain = 0f
                break
            }
            val s = when (activeVoice) {
                Voice.TRICORDER -> chatter(k, 1f) + whirr(k) + hum()
                Voice.GEO -> chatter(k, 0.8f) * 0.9f + whirr(k) * 0.8f + hum()
                Voice.MEDICAL -> medical(k)
                Voice.ENGINEERING -> engineering(k)
                Voice.NONE -> 0f
            }
            mix[i] += s * gain
            t += dt
        }

        // Geo voice adds a sonar ping whose repetition rate rises with field strength.
        if (wantOn && activeVoice == Voice.GEO) {
            pingTimer -= mix.size
            if (pingTimer <= 0) {
                pingTimer = (sr * (2.4 - 1.6 * k)).toInt()
                bank[Fx.PING]?.firstOrNull()?.let { pending.add(it) }
            }
        }
    }

    /** Rapid random high-register data blips: the core tricorder "chatter". */
    private fun chatter(k: Float, rate: Float): Float {
        if (blipRemain <= 0) {
            if (gapRemain > 0) {
                gapRemain--
                return 0f
            }
            blipFreq = CHATTER[rng.nextInt(CHATTER.size)] * (1.0 + 0.35 * k)
            blipLen = ((0.022 + rng.nextDouble() * 0.040) * sr).toInt()
            blipRemain = blipLen
            gapRemain = ((0.010 + rng.nextDouble() * 0.055) * (1.0 - 0.75 * k) / rate * sr).toInt()
        }
        val pos = blipLen - blipRemain
        val fade = (0.005 * sr).toInt()
        val env = min(1.0, min(pos.toDouble() / fade, (blipLen - pos).toDouble() / fade))
        // Slight downward glide gives each blip a rounded "burble" instead of a beep.
        val glide = 1.0 + 0.06 * (1.0 - pos.toDouble() / blipLen)
        blipPhase += 2 * PI * blipFreq * glide / sr
        if (blipPhase > 2 * PI) blipPhase -= 2 * PI
        blipRemain--
        return ((sin(blipPhase) + 0.12 * sin(2 * blipPhase)) * 0.19 * env).toFloat()
    }

    /** Fast-vibrato high whine under the chatter. */
    private fun whirr(k: Float): Float {
        val f = 820.0 + 380.0 * k + 25.0 * sin(2 * PI * 18.0 * t)
        whirrPhase += 2 * PI * f / sr
        if (whirrPhase > 2 * PI) whirrPhase -= 2 * PI
        val amp = 0.032 * (0.6 + 0.4 * sin(2 * PI * 2.7 * t))
        return (sin(whirrPhase) * amp).toFloat()
    }

    private fun hum(): Float {
        humPhase += 2 * PI * 118.0 / sr
        if (humPhase > 2 * PI) humPhase -= 2 * PI
        return (sin(humPhase) * 0.035).toFloat()
    }

    /** Medical scanner: smooth pulsing warble with slow pitch sweep and vibrato. */
    private fun medical(k: Float): Float {
        val f = 430.0 + 120.0 * sin(2 * PI * 0.55 * t) + 200.0 * k + 18.0 * sin(2 * PI * 6.5 * t)
        carrierPhase += 2 * PI * f / sr
        if (carrierPhase > 2 * PI) carrierPhase -= 2 * PI
        val pulse = 0.55 + 0.45 * sin(2 * PI * (2.4 + 2.5 * k) * t)
        val tone = (sin(carrierPhase) + 0.18 * sin(2 * carrierPhase + 0.3)) * pulse * 0.30
        return tone.toFloat() + chatter(k, 0.3f) * 0.45f
    }

    /** Engineering: filtered low sawtooth throb plus sparse chatter. */
    private fun engineering(k: Float): Float {
        sawPhase += (52.0 + 30.0 * k) / sr
        if (sawPhase > 1.0) sawPhase -= 1.0
        val saw = (2 * sawPhase - 1).toFloat()
        lowpass += (saw - lowpass) * 0.035f
        val throb = (0.6 + 0.4 * sin(2 * PI * (1.4 + 3.0 * k) * t)).toFloat()
        return lowpass * 0.38f * throb + hum() + chatter(k, 0.45f) * 0.55f
    }

    // ── One-shot effect bank ──────────────────────────────────────────

    private fun buildBank(): Map<Fx, List<FloatArray>> = mapOf(
        Fx.TAP to listOf(
            cat(seg(988.0, 988.0, 0.05), seg(1319.0, 1319.0, 0.06)),
            cat(seg(880.0, 880.0, 0.045), seg(1175.0, 1175.0, 0.055)),
            cat(seg(1047.0, 1047.0, 0.04), silence(0.02), seg(1047.0, 1047.0, 0.04)),
            cat(seg(784.0, 784.0, 0.045), seg(988.0, 988.0, 0.045), seg(1319.0, 1319.0, 0.05)),
        ),
        Fx.PROBE to listOf(probe()),
        Fx.TOGGLE to listOf(cat(seg(740.0, 740.0, 0.06), seg(494.0, 494.0, 0.08))),
        Fx.SCAN_START to listOf(
            cat(
                seg(300.0, 1400.0, 0.32, amp = 0.40),
                seg(1568.0, 1568.0, 0.04), silence(0.02),
                seg(1319.0, 1319.0, 0.04), silence(0.02),
                seg(1760.0, 1760.0, 0.05),
            )
        ),
        Fx.SCAN_COMPLETE to listOf(
            cat(
                seg(1047.0, 1047.0, 0.09), silence(0.02),
                seg(1319.0, 1319.0, 0.09), silence(0.02),
                seg(1568.0, 1568.0, 0.38, decay = true),
            )
        ),
        Fx.ALERT to listOf(
            cat(
                seg(660.0, 660.0, 0.15, harm = 0.4), seg(494.0, 494.0, 0.15, harm = 0.4),
                seg(660.0, 660.0, 0.15, harm = 0.4), seg(494.0, 494.0, 0.15, harm = 0.4),
                seg(660.0, 660.0, 0.15, harm = 0.4), seg(494.0, 494.0, 0.22, harm = 0.4, decay = true),
            )
        ),
        Fx.PING to listOf(ping()),
        Fx.BOOT to listOf(boot()),
    )

    /** Touch probe: soft downward chirp with a short echo. */
    private fun probe(): FloatArray {
        val out = FloatArray((0.4 * sr).toInt())
        val c = seg(1175.0, 840.0, 0.10, amp = 0.32, harm = 0.1, decay = true)
        mixInto(out, c, 0)
        mixInto(out, c, (0.12 * sr).toInt(), 0.3f)
        return out
    }

    /** Tone segment with linear frequency sweep and soft edges. */
    private fun seg(
        f0: Double, f1: Double, dur: Double,
        amp: Double = 0.40, harm: Double = 0.25, decay: Boolean = false,
    ): FloatArray {
        val n = (dur * sr).toInt()
        val out = FloatArray(n)
        val fade = max(1, (0.004 * sr).toInt())
        var ph = 0.0
        for (i in 0 until n) {
            val x = i.toDouble() / n
            ph += 2 * PI * (f0 + (f1 - f0) * x) / sr
            var env = min(1.0, min(i.toDouble() / fade, (n - i).toDouble() / fade))
            if (decay) env *= exp(-4.5 * x)
            out[i] = ((sin(ph) + harm * sin(2 * ph)) * amp * env).toFloat()
        }
        return out
    }

    private fun silence(dur: Double) = FloatArray((dur * sr).toInt())

    private fun cat(vararg parts: FloatArray): FloatArray {
        val out = FloatArray(parts.sumOf { it.size })
        var o = 0
        for (p in parts) {
            p.copyInto(out, o)
            o += p.size
        }
        return out
    }

    private fun mixInto(dst: FloatArray, src: FloatArray, offset: Int, scale: Float = 1f) {
        for (i in src.indices) {
            val j = offset + i
            if (j >= dst.size) break
            dst[j] += src[i] * scale
        }
    }

    /** Sonar-style ping with two echoes. */
    private fun ping(): FloatArray {
        val out = FloatArray((1.3 * sr).toInt())
        val p = seg(880.0, 860.0, 0.55, amp = 0.30, harm = 0.08, decay = true)
        mixInto(out, p, 0)
        mixInto(out, p, (0.23 * sr).toInt(), 0.35f)
        mixInto(out, p, (0.46 * sr).toInt(), 0.14f)
        return out
    }

    /** Power-up: rising sweep, a burst of data chatter, then a resolving chord. */
    private fun boot(): FloatArray {
        val out = FloatArray((2.2 * sr).toInt())
        mixInto(out, seg(60.0, 600.0, 1.3, amp = 0.32, harm = 0.5), 0)
        val r = Random(42)
        var pos = (0.55 * sr).toInt()
        while (pos < (1.45 * sr).toInt()) {
            val f = CHATTER[r.nextInt(CHATTER.size)]
            val b = seg(f, f, 0.02 + r.nextDouble() * 0.025, amp = 0.22)
            mixInto(out, b, pos)
            pos += b.size + (0.01 * sr).toInt() + r.nextInt((0.03 * sr).toInt())
        }
        val chordAt = (1.4 * sr).toInt()
        for (f in listOf(523.3, 784.0, 1046.5)) {
            mixInto(out, seg(f, f, 0.75, amp = 0.16, harm = 0.1, decay = true), chordAt)
        }
        return out
    }

    companion object {
        private const val BLOCK = 512
        private val CHATTER = doubleArrayOf(587.3, 659.3, 784.0, 880.0, 987.8, 1174.7, 1318.5, 1568.0)
    }
}
