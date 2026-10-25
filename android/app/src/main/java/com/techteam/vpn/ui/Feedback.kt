package com.techteam.vpn.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.techteam.vpn.R
import com.techteam.vpn.data.AppSettings

/**
 * Taps and sounds: a click on the button, a confirm buzz and chime when the VPN connects, a
 * softer one when it disconnects, a reject buzz when it fails. Silent and vibrate modes mute the sounds.
 */
object Feedback {
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun connected(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        play(view.context, R.raw.connect)
    }

    fun disconnected(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        play(view.context, R.raw.disconnect)
    }

    fun failed(view: View) {
        view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    }

    private fun play(context: Context, sound: Int) {
        if (!AppSettings.get(context).sounds.value) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val player = MediaPlayer.create(context, sound, attributes, audio.generateAudioSessionId()) ?: return
        player.setVolume(0.7f, 0.7f)
        player.setOnCompletionListener { it.release() }
        player.start()
    }
}
