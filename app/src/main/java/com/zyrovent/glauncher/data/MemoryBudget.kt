package com.zyrovent.glauncher.data

import android.app.ActivityManager
import android.content.Context

/**
 * Presupuesto de memoria para el JVM del juego.
 *
 * Android mata el proceso entero cuando el sistema se queda sin RAM, así que
 * pedir más heap del que el celular aguanta cierra Minecraft de golpe (pasó al
 * asignar 2 GB en un equipo de 3,7 GB totales). El tope se calcula desde la RAM
 * real del dispositivo: 35 % del total, redondeado al paso del slider.
 */
object MemoryBudget {

    /** RAM física total del dispositivo en MB. */
    fun totalMb(context: Context): Int = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        (info.totalMem / (1024L * 1024L)).toInt()
    }.getOrDefault(2048)

    /** Máximo de heap que este dispositivo puede sostener sin que lo maten. */
    fun safeMaxMb(context: Context): Int {
        val cap = (totalMb(context) * 0.35f).toInt()
        val stepped = (cap / LauncherSettings.RAM_STEP) * LauncherSettings.RAM_STEP
        return stepped.coerceIn(LauncherSettings.MIN_RAM, LauncherSettings.MAX_RAM)
    }

    /** Ajusta un valor pedido por el usuario al rango que el equipo soporta. */
    fun clamp(context: Context, requestedMb: Int): Int =
        requestedMb.coerceIn(LauncherSettings.MIN_RAM, safeMaxMb(context))
}
