package com.tricorder.lcars.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Synthesizes short retro-futuristic panel chirps in code (no audio asset files).
 * Each sound is a static AudioTrack rendered once, then replayed on demand.
 */
class Chirper {

    enum class Sound { TAB, TOGGLE }

    private val sampleRate = 44_100
    private val tracks = mutableMapOf<Sound, AudioTrack>()

    var enabled: Boolean = true

    fun play(sound: Sound) {
        if (!enabled) return
        runCatching {
            val track = tracks.getOrPut(sound) { build(sound) }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun build(sound: Sound): AudioTrack {
        val pcm = when (sound) {
            // Rising two-step blip, the classic panel-touch feel.
            Sound.TAB -> synth(listOf(1320.0 to 0.045, 1760.0 to 0.06))
            // Lower descending double-blip for on/off switches.
            Sound.TOGGLE -> synth(listOf(990.0 to 0.05, 660.0 to 0.07))
        }
        val bytes = pcm.size * 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(bytes)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /** Concatenates sine segments (frequency Hz to seconds) with short fade envelopes. */
    private fun synth(segments: List<Pair<Double, Double>>): ShortArray {
        val out = ArrayList<Short>()
        val amp = 0.35 * Short.MAX_VALUE
        for ((freq, seconds) in segments) {
            val n = (sampleRate * seconds).toInt()
            val fade = min(n / 4, (sampleRate * 0.006).toInt()).coerceAtLeast(1)
            for (i in 0 until n) {
                val env = when {
                    i < fade -> i.toDouble() / fade
                    i > n - fade -> (n - i).toDouble() / fade
                    else -> 1.0
                }
                val s = sin(2.0 * PI * freq * i / sampleRate) * amp * env
                out.add(s.toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}
