package com.zyrovent.glauncher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Racha diaria del launcher.
 *
 * Se guarda en el dispositivo y no depende de la red: abrir Mi Cuenta firma el
 * día una sola vez. La misma forma de datos ya está publicada en
 * `supabase/glauncher.sql`, así que cuando la app tenga identidad de Supabase solo
 * hay que cambiar el origen de [claimToday] por la RPC `claim_daily_streak`.
 */
data class StreakState(
    val current: Int = 0,
    val longest: Int = 0,
    val total: Int = 0,
    /** Últimos siete días, del más viejo al más nuevo: hoy es el último. */
    val days: List<Boolean> = List(7) { false },
    val claimedToday: Boolean = false
)

class StreakStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("glauncher_streak", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<StreakState> = _state.asStateFlow()

    /**
     * Firma el día de hoy. Es idempotente: si ya se firmó, no toca nada.
     * Devuelve true solo la primera vez del día (para celebrar).
     */
    fun claimToday(): Boolean {
        val today = keyOf(Calendar.getInstance())
        val last = prefs.getInt(KEY_LAST, 0)
        if (last == today) {
            _state.value = read()
            return false
        }

        val yesterday = keyOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })
        val current = if (last == yesterday) prefs.getInt(KEY_CURRENT, 0) + 1 else 1
        val longest = maxOf(prefs.getInt(KEY_LONGEST, 0), current)
        val total = prefs.getInt(KEY_TOTAL, 0) + 1
        val history = pushHistory(prefs.getString(KEY_HISTORY, null).orEmpty(), today)

        prefs.edit()
            .putInt(KEY_CURRENT, current)
            .putInt(KEY_LONGEST, longest)
            .putInt(KEY_TOTAL, total)
            .putInt(KEY_LAST, today)
            .putString(KEY_HISTORY, history)
            .apply()

        _state.value = read()
        return true
    }

    private fun read(): StreakState {
        val today = keyOf(Calendar.getInstance())
        val yesterday = keyOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) })
        val last = prefs.getInt(KEY_LAST, 0)
        val stored = prefs.getInt(KEY_CURRENT, 0)

        // Una racha solo sigue viva si se firmó hoy o ayer; si no, ya se rompió.
        val alive = last == today || last == yesterday
        val signed = prefs.getString(KEY_HISTORY, null).orEmpty()
            .split(',')
            .filter { it.isNotBlank() }
            .toSet()

        val days = (6 downTo 0).map { back ->
            val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -back) }
            keyOf(calendar).toString() in signed
        }

        return StreakState(
            current = if (alive) stored else 0,
            longest = prefs.getInt(KEY_LONGEST, 0),
            total = prefs.getInt(KEY_TOTAL, 0),
            days = days,
            claimedToday = last == today
        )
    }

    /** Clave de día `aaaammdd`: ordenable y barata de guardar. */
    private fun keyOf(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)

    /** Historial como lista separada por comas, recortado a 40 días. */
    private fun pushHistory(history: String, today: Int): String {
        val days = history.split(',').filter { it.isNotBlank() }.toMutableList()
        if (days.lastOrNull() == today.toString()) return history
        days.add(today.toString())
        return days.takeLast(40).joinToString(",")
    }

    private companion object {
        const val KEY_CURRENT = "current"
        const val KEY_LONGEST = "longest"
        const val KEY_TOTAL = "total"
        const val KEY_LAST = "last_day"
        const val KEY_HISTORY = "history"
    }
}
