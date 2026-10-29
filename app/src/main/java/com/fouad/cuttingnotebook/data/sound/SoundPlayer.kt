package com.fouad.cuttingnotebook.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.fouad.cuttingnotebook.R
import com.fouad.cuttingnotebook.data.settings.AppSettings

enum class Cue { SUCCESS, ERROR, CLICK, OVERFLOW, COMPLETE }

/**
 * The app's audio feedback (spec 11).
 *
 * Nothing here reaches the network, and the success cue is only ever asked for after a write
 * has returned. Playback is skipped outright when the phone is on silent or vibrate, and the
 * volume the system gives the notification stream is what the operator hears.
 */
class SoundPlayer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val loaded = mutableMapOf<Cue, Int>()

    init {
        loaded[Cue.SUCCESS] = pool.load(appContext, R.raw.sfx_success, 1)
        loaded[Cue.ERROR] = pool.load(appContext, R.raw.sfx_error, 1)
        loaded[Cue.CLICK] = pool.load(appContext, R.raw.sfx_click, 1)
        loaded[Cue.OVERFLOW] = pool.load(appContext, R.raw.sfx_overflow, 1)
        loaded[Cue.COMPLETE] = pool.load(appContext, R.raw.sfx_complete, 1)
    }

    fun play(cue: Cue, settings: AppSettings) {
        if (!settings.soundsEnabled) return
        if (cue == Cue.CLICK && !settings.buttonSoundsEnabled) return
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val id = loaded[cue] ?: return
        // A cue that has not finished loading yet returns 0 from play(); nothing to do.
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
        loaded.clear()
    }
}
