package com.kuunyi.scanner.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FeedbackManager(context: Context) {

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Short high beep — valid scan. */
    fun playValid() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
    }

    /** Two-pulse beep — any invalid result. */
    fun playInvalid() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_NACK, 300)
    }

    /** Single 80 ms pulse — valid scan. */
    fun vibrateValid() {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** Double pulse — invalid scan (pattern: wait 0, on 100, off 80, on 180). */
    fun vibrateInvalid() {
        val waveform = VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 180), -1)
        vibrator.vibrate(waveform)
    }

    fun release() {
        toneGenerator.release()
    }
}
