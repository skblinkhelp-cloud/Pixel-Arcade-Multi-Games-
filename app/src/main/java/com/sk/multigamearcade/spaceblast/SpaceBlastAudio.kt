package com.sk.multigamearcade.spaceblast

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Real-time procedural arcade sound synthesizer using Android AudioTrack.
 * Generates energetic retro sounds dynamically with zero external asset dependencies.
 */
object SpaceBlastAudio {

    private val audioScope = CoroutineScope(Dispatchers.Default)
    var soundEnabled: Boolean = true

    private fun playTone(
        startFreq: Float,
        endFreq: Float,
        durationMs: Int,
        volume: Float = 0.5f,
        type: String = "SINE"
    ) {
        if (!soundEnabled) return

        audioScope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (durationMs * sampleRate / 1000)
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val progress = i.toFloat() / numSamples
                    val currentFreq = startFreq + (endFreq - startFreq) * progress
                    val angle = 2.0 * Math.PI * currentFreq * i / sampleRate

                    val sampleValue = when (type) {
                        "SAW" -> (2.0 * (angle % (2 * Math.PI)) / (2 * Math.PI) - 1.0)
                        "SQUARE" -> if (sin(angle) > 0) 0.8 else -0.8
                        "NOISE" -> (Math.random() * 2.0 - 1.0)
                        else -> sin(angle)
                    }

                    val envelope = (1.0f - progress) * volume
                    buffer[i] = (sampleValue * envelope * Short.MAX_VALUE).toInt().toShort()
                }

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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(numSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, numSamples)
                track.play()

                // Release track after playing
                Thread.sleep(durationMs.toLong() + 50)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore audio hardware exception safety fallback
            }
        }
    }

    fun playLaserSound() {
        playTone(startFreq = 880f, endFreq = 220f, durationMs = 60, volume = 0.35f, type = "SAW")
    }

    fun playHitSound() {
        playTone(startFreq = 440f, endFreq = 120f, durationMs = 40, volume = 0.30f, type = "SQUARE")
    }

    fun playExplosionSound() {
        playTone(startFreq = 250f, endFreq = 50f, durationMs = 220, volume = 0.50f, type = "NOISE")
    }

    fun playPowerUpSound() {
        playTone(startFreq = 400f, endFreq = 1200f, durationMs = 180, volume = 0.45f, type = "SINE")
    }

    fun playCoinSound() {
        playTone(startFreq = 987f, endFreq = 1318f, durationMs = 90, volume = 0.40f, type = "SINE")
    }

    fun playBossWarningSound() {
        playTone(startFreq = 150f, endFreq = 300f, durationMs = 400, volume = 0.60f, type = "SQUARE")
    }

    fun playDamageSound() {
        playTone(startFreq = 300f, endFreq = 80f, durationMs = 150, volume = 0.55f, type = "SAW")
    }

    fun playSpecialAbilitySound() {
        playTone(startFreq = 300f, endFreq = 1500f, durationMs = 350, volume = 0.65f, type = "SAW")
    }

    fun playClickSound() {
        playTone(startFreq = 600f, endFreq = 800f, durationMs = 30, volume = 0.25f, type = "SINE")
    }

    fun playVictorySound() {
        playTone(startFreq = 523f, endFreq = 1046f, durationMs = 300, volume = 0.60f, type = "SINE")
    }
}
