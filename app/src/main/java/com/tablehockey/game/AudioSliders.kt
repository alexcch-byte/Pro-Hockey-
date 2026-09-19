package com.tablehockey.game

import android.content.Context
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import com.tablehockey.game.game.MusicManager
import com.tablehockey.game.model.Prefs

/**
 * Binds the shared audio_sliders layout: two 0-100 sliders for sound effects
 * and music. Values persist immediately; music level changes are applied to
 * whatever is playing right away. [onSfxChanged] lets the game screen push
 * the new level into its own SoundManager.
 */
object AudioSliders {

    fun bind(root: View, context: Context, onSfxChanged: ((Float) -> Unit)? = null) {
        val seekSfx = root.findViewById<SeekBar>(R.id.seekSfx)
        val textSfx = root.findViewById<TextView>(R.id.textSfx)
        val seekMusic = root.findViewById<SeekBar>(R.id.seekMusic)
        val textMusic = root.findViewById<TextView>(R.id.textMusic)

        seekSfx.max = 100
        seekMusic.max = 100
        seekSfx.progress = Prefs.sfxVolume(context)
        seekMusic.progress = Prefs.musicVolume(context)
        textSfx.text = label(context, seekSfx.progress)
        textMusic.text = label(context, seekMusic.progress)

        seekSfx.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                textSfx.text = label(context, progress)
                if (fromUser) {
                    Prefs.setSfxVolume(context, progress)
                    onSfxChanged?.invoke(progress / 100f)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) { MusicManager.click(context) }
        })

        seekMusic.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                textMusic.text = label(context, progress)
                if (fromUser) {
                    Prefs.setMusicVolume(context, progress)
                    MusicManager.setUserVolume(context, progress / 100f)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun label(context: Context, v: Int): String =
        if (v <= 0) context.getString(R.string.audio_off) else "$v%"
}
