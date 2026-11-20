package com.zyrovent.glauncher.util

import android.content.Context
import android.media.MediaPlayer

/** Reproduce los efectos cortos incluidos en res/raw sin bloquear la UI. */
object Sfx {

    private var player: MediaPlayer? = null

    fun play(context: Context, resId: Int) {
        runCatching {
            player?.let { runCatching { it.release() } }
            val created = MediaPlayer.create(context, resId) ?: return
            created.setOnCompletionListener { it.release() }
            created.start()
            player = created
        }
    }
}
