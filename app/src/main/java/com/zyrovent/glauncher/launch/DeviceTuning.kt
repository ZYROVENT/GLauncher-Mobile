package com.zyrovent.glauncher.launch

import android.content.Context
import com.zyrovent.glauncher.data.MemoryBudget
import com.zyrovent.glauncher.data.LauncherSettings

/**
 * Ajustes de la máquina virtual calculados desde el hardware real.
 *
 * Los argumentos por defecto de HotSpot no están pensados para un celular: abre
 * varios hilos de JIT y de GC aunque solo haya núcleos lentos, y deja el recolector
 * ocupando CPU que Minecraft necesita para renderizar. Aquí se limita todo eso
 * según los núcleos, la RAM y cuántos mods carga la instancia.
 *
 * Regla de oro: solo se usan banderas que existen en Java 8 **y** en 17/21/25,
 * porque una bandera desconocida aborta el arranque de la VM entera. Las que
 * solo valen en las versiones nuevas se filtran por [javaMajor].
 */
object DeviceTuning {

    enum class Tier { LOW, MID, HIGH }

    data class Profile(
        val cores: Int,
        val totalRamMb: Int,
        val heapMb: Int,
        val tier: Tier,
        val gcThreads: Int,
        val mods: Int
    ) {
        val lowEnd: Boolean get() = tier == Tier.LOW
        val highEnd: Boolean get() = tier == Tier.HIGH
        val modded: Boolean get() = mods > 0
    }

    /** Un mod pesado tipo Fabric con 75 mods ronda los 2500 MB de heap. */
    fun profile(context: Context, heapMb: Int = 0, mods: Int = 0): Profile {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val totalRam = MemoryBudget.totalMb(context)
        val heap = if (heapMb > 0) heapMb else MemoryBudget.safeMaxMb(context)
        // La GPU no se puede medir sin crear un contexto, así que el corte se hace
        // con núcleos + RAM: los Kirin 710 / Snapdragon 6xx tienen 8 núcleos pero
        // son gama baja real para Java + OpenGL traducido.
        val tier = when {
            totalRam >= 6144 && cores >= 8 -> Tier.HIGH
            totalRam >= 4096 && cores >= 6 -> Tier.MID
            cores >= 8 && totalRam >= 3584 -> Tier.MID
            else -> Tier.LOW
        }
        // Mitad de los núcleos: deja CPU libre para el hilo de render y el de audio.
        val gcThreads = (cores / 2).coerceIn(2, 4)
        return Profile(cores, totalRam, heap, tier, gcThreads, mods)
    }

    /**
     * Bandera de la VM para Minecraft. Se añaden después de `-Xmx`, antes de los
     * argumentos personalizados del usuario, para que él pueda sobrescribirlas.
     */
    fun recommendedArgs(
        context: Context,
        ramMb: Int,
        javaMajor: Int = 17,
        mods: Int = 0
    ): List<String> {
        val profile = profile(context, ramMb, mods)
        val args = mutableListOf<String>()

        // Heap inicial bajo: reservar todo de golpe dispara el asesino de Android.
        args += "-Xms${(ramMb / 4).coerceIn(128, 384)}M"
        // La pila por hilo se pide explícita y corta: Minecraft con mods abre
        // decenas de hilos y con la pila por defecto (1 MB) cada uno cuesta 1 MB
        // de RAM nativa que no sale del heap.
        args += "-Xss1m"
        // Metaspace: cada mod registra cientos de clases. Sin tope, en un celular
        // se come la memoria nativa y el sistema mata el proceso.
        args += "-XX:MaxMetaspaceSize=${metaspaceMb(profile)}m"
        // El código compilado de Minecraft no necesita la caché enorme por defecto.
        args += "-XX:ReservedCodeCacheSize=${if (profile.lowEnd) "64" else "96"}m"
        // Sin AWT: el cliente no lo necesita y ahorra hilos.
        args += "-Djava.awt.headless=true"
        // Los contadores de rendimiento de HotSpot escriben en memoria compartida
        // cada poco; en Android eso es E/S y bloqueos de GC gratuitos.
        args += "-XX:+PerfDisableSharedMem"
        // Si el juego se queda sin heap, mejor que termine con un código claro
        // que quedarse congelado en un bucle de recolección.
        args += "-XX:+ExitOnOutOfMemoryError"
        // LWJGL: sin comprobaciones por llamada sale un poco más barato el render;
        // el stub de GLFW de Android ya ignora los errores recuperables.
        args += "-Dorg.lwjgl.util.NoChecks=true"
        args += "-Dorg.lwjgl.util.Debug=false"
        // Java 24+ (JEP 471 / JEP 472): LWJGL sigue tocando memoria con
        // sun.misc.Unsafe y carga sus .so por la vía «restringida». Sin esto el
        // motor suelta avisos en cada arranque y pierde tiempo en cada llamada
        // nativa; con las banderas va limpio. Solo existen desde el 24.
        if (javaMajor >= 24) {
            args += "--sun-misc-unsafe-memory-access=allow"
            args += "--enable-native-access=ALL-UNNAMED"
        }

        when {
            // Gama baja: SerialGC gasta menos CPU y menos memoria que G1, y en
            // heaps de 1 GB su pausa es más corta que la de un G1 sin espacio.
            profile.lowEnd -> args += "-XX:+UseSerialGC"
            // Gama media: G1 con pausa objetivo corta para que el tirón de un
            // chunk no se note; los hilos se limitan para no robar CPU al render.
            profile.tier == Tier.MID -> {
                args += "-XX:+UseG1GC"
                args += "-XX:MaxGCPauseMillis=45"
                args += "-XX:G1HeapRegionSize=4m"
                args += "-XX:ParallelGCThreads=${profile.gcThreads}"
            }
            else -> {
                args += "-XX:+UseG1GC"
                args += "-XX:MaxGCPauseMillis=30"
                args += "-XX:ParallelGCThreads=${profile.gcThreads}"
            }
        }

        // Un solo compilador JIT en segundo plano en vez de varios.
        if (!profile.highEnd) args += "-XX:CICompilerCount=2"

        // Java 17+ reserva más memoria para los cargadores de clases modulares:
        // en gama baja se recorta el espacio de clases cargadas en CDS para que
        // el arranque no intente mapear un archivo que no existe en Android.
        if (javaMajor >= 17 && profile.lowEnd) {
            args += "-Xshare:auto"
        }
        return args
    }

    private fun metaspaceMb(profile: Profile): Int = when {
        profile.mods >= 60 -> 1024
        profile.mods >= 30 -> 768
        profile.mods >= 10 -> 512
        profile.lowEnd -> 192
        else -> 256
    }

    /** Resumen legible para el registro de arranque. */
    fun summary(context: Context, ramMb: Int, mods: Int = 0): String {
        val profile = profile(context, ramMb, mods)
        val gama = when (profile.tier) {
            Tier.LOW -> "gama baja"
            Tier.MID -> "gama media"
            Tier.HIGH -> "gama alta"
        }
        val gc = when (profile.tier) {
            Tier.LOW -> "SerialGC"
            else -> "G1 (${profile.gcThreads} hilos)"
        }
        val modsTexto = if (profile.mods > 0) " · ${profile.mods} mods" else ""
        return "${profile.cores} núcleos · ${profile.totalRamMb} MB RAM · $gama · " +
            "heap ${profile.heapMb} MB · $gc$modsTexto"
    }

    /** Consejo de RAM para el aviso previo al arranque con mods. */
    fun heapAdvice(profile: Profile): String? = when {
        profile.mods >= 40 && profile.heapMb < 2048 ->
            "Con ${profile.mods} mods hacen falta 2048 MB o más de heap; con " +
                "${profile.heapMb} MB puede quedarse sin memoria al cargar el mundo."
        profile.mods >= 15 && profile.heapMb < 1536 ->
            "Con ${profile.mods} mods conviene subir la RAM a 1536 MB o más."
        else -> null
    }

    /** Máximo de heap que el launcher deja pedir cuando hay mods cargados. */
    fun maxRamFor(context: Context, mods: Int): Int =
        if (mods >= 40) LauncherSettings.MAX_RAM else MemoryBudget.safeMaxMb(context)
}
