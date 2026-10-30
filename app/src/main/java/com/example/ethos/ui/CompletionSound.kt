package com.example.ethos.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.example.ethos.R

private val MEDIA_ATTRIBUTES = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()

/** Plays the completion sound on the media stream, so media volume controls it. */
fun playCompletionSound(context: Context) {
    val app = context.applicationContext
    val sessionId = (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
    val player = MediaPlayer.create(app, R.raw.goal_completed, MEDIA_ATTRIBUTES, sessionId) ?: return
    player.setOnCompletionListener { it.release() }
    player.start()
}
