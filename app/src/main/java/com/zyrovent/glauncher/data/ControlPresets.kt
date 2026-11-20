package com.zyrovent.glauncher.data

import android.content.Context

/**
 * Presets de controles virtuales guardados por el jugador.
 *
 * Cada preset es un layout completo (botones, posiciones, tamaños y colores)
 * que se guarda con un nombre y se puede volver a cargar desde el editor.
 */
class ControlPresets(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun names(): List<String> =
        prefs.getString(KEY_NAMES, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    fun load(name: String): List<VirtualButton>? =
        VirtualButtonCodec.decode(prefs.getString(keyFor(name), null))

    /** Guarda (o reemplaza) un preset. El nombre se recorta a 20 caracteres. */
    fun save(name: String, buttons: List<VirtualButton>): String {
        val clean = name.trim().take(20).ifBlank { "PRESET" }
        val current = names().toMutableList()
        if (clean !in current) current += clean
        prefs.edit()
            .putString(KEY_NAMES, current.joinToString("\n"))
            .putString(keyFor(clean), VirtualButtonCodec.encode(buttons))
            .apply()
        return clean
    }

    fun delete(name: String) {
        val current = names().filterNot { it == name }
        prefs.edit()
            .putString(KEY_NAMES, current.joinToString("\n"))
            .remove(keyFor(name))
            .apply()
    }

    private fun keyFor(name: String) = "preset_$name"

    private companion object {
        const val PREFS = "glauncher_control_presets"
        const val KEY_NAMES = "names"
    }
}
