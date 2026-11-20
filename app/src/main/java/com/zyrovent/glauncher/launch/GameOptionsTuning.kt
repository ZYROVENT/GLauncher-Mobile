package com.zyrovent.glauncher.launch

import java.io.File

/**
 * Recorta las opciones gráficas de Minecraft según la gama del equipo, nunca
 * subiéndolas: si el jugador ya tenía algo más bajo se respeta.
 *
 * Esto es la otra mitad del rendimiento: bajar la resolución de render da más
 * fps, pero la distancia de render y las sombras son las que deciden cuántos
 * chunks se construyen y cuánta memoria nativa se queda viva. Con 1 GB de heap
 * el cliente pedía distancia 12 y llegaba a 1,6 GB de RSS: SIGSEGV al entrar al
 * mundo.
 *
 * Se escriben tanto las claves nuevas (`graphicsMode`, `simulationDistance`)
 * como las viejas (`fancyGraphics`), así el mismo archivo vale para 1.7 y 26.x.
 */
object GameOptionsTuning {

    /** Texto de cada cambio realizado, para el registro de arranque. */
    fun tune(
        instanceDir: File,
        heapMb: Int,
        tier: DeviceTuning.Tier = DeviceTuning.Tier.LOW,
        mods: Int = 0,
        onLog: (String) -> Unit
    ) {
        val file = File(instanceDir, "options.txt")
        val options = linkedMapOf<String, String>()
        if (file.exists()) {
            runCatching {
                file.readLines().forEach { line ->
                    val split = line.indexOf(':')
                    if (split > 0) options[line.substring(0, split)] = line.substring(split + 1)
                }
            }.onFailure { onLog("No se pudo leer options.txt: ${it.message}") }
        }

        // Perfil de calidad por gama. En gama alta solo se tocan las claves que
        // no cambian cómo se ve el juego.
        val caps = linkedMapOf<String, Int>()
        val changes = mutableListOf<String>()

        when (tier) {
            DeviceTuning.Tier.LOW -> {
                caps["renderDistance"] = 4
                caps["simulationDistance"] = 4
                caps["biomeBlendRadius"] = 0
                caps["maxFps"] = 60
                hardLow(options, changes)
            }
            DeviceTuning.Tier.MID -> {
                caps["renderDistance"] = 5
                caps["simulationDistance"] = 4
                caps["biomeBlendRadius"] = 1
                caps["maxFps"] = 60
                hardMid(options, changes)
            }
            DeviceTuning.Tier.HIGH -> {
                // Con 6 GB o más solo se limita lo absurdo.
                if (heapMb <= 1536) {
                    caps["renderDistance"] = 8
                    caps["simulationDistance"] = 8
                }
            }
        }
        if (heapMb <= 1024) {
            caps["renderDistance"] = minOf(caps["renderDistance"] ?: 6, 4)
            caps["simulationDistance"] = minOf(caps["simulationDistance"] ?: 6, 4)
        }

        caps.forEach { (key, cap) ->
            val current = options[key]?.toIntOrNull()
            if (current != null && current > cap) {
                options[key] = cap.toString()
                changes += "$key $current→$cap"
            } else if (current == null) {
                options[key] = cap.toString()
                changes += "$key →$cap"
            }
        }

        // Claves que siempre se fijan porque no dependen de la GPU sino del
        // propio juego en Android: sin ellas el cliente pausa al perder el foco
        // (el overlay del mando es otra "ventana") y gasta frames sincronizados.
        alwaysOn(options, changes)

        if (mods >= 15) {
            // Con muchos mods, la distancia de simulación alta dispara el uso de
            // CPU por tick y no se recupera con más memoria.
            val current = options["simulationDistance"]?.toIntOrNull()
            if (current != null && current > 5) {
                options["simulationDistance"] = "5"
                changes += "simulationDistance $current→5 (mods)"
            }
        }

        if (changes.isEmpty()) return

        runCatching {
            instanceDir.mkdirs()
            file.writeText(options.entries.joinToString("\n") { "${it.key}:${it.value}" } + "\n")
            onLog("Opciones gráficas ajustadas al equipo: " + changes.joinToString(", "))
        }.onFailure { onLog("No se pudieron guardar las opciones del juego: ${it.message}") }
    }

    /** Ajustes agresivos de gama baja (Kirin 710, Snapdragon 6xx, Helio). */
    private fun hardLow(options: MutableMap<String, String>, changes: MutableList<String>) {
        set(options, changes, "graphicsMode", "0")       // 0 = rápido, 1 = fancy, 2 = fabulous
        set(options, changes, "fancyGraphics", "false")  // nombre viejo, mismo efecto
        set(options, changes, "mipmapLevels", "0")
        set(options, changes, "ao", "false")
        set(options, changes, "entityShadows", "false")
        set(options, changes, "renderClouds", "\"false\"")
        set(options, changes, "particles", "2")          // 2 = mínimo
        set(options, changes, "glErrorChecking", "false")
        // 1.18+: los mobs se pueden dibujar de más lejos que los bloques sin
        // que cueste casi nada; a la mitad se gana CPU en cada tick.
        set(options, changes, "entityDistanceScaling", "0.5")
    }

    /** Gama media: se conserva lo bonito donde no cuesta fps. */
    private fun hardMid(options: MutableMap<String, String>, changes: MutableList<String>) {
        // Gráficos «rápidos»: la hierba y el agua transparentes son lo que más
        // cuesta en gl4es (cada capa translúcida es otro pase de mezcla sobre
        // toda la pantalla). Los mipmaps y las nubes sí se dejan, que se ven
        // bien y no cuestan frames.
        set(options, changes, "graphicsMode", "0")
        set(options, changes, "fancyGraphics", "false")
        set(options, changes, "mipmapLevels", "2")
        set(options, changes, "ao", "false")
        set(options, changes, "entityShadows", "false")
        set(options, changes, "renderClouds", "\"fast\"")
        set(options, changes, "particles", "1")          // 1 = reducidas
        set(options, changes, "entityDistanceScaling", "0.6")
    }

    private fun alwaysOn(options: MutableMap<String, String>, changes: MutableList<String>) {
        set(options, changes, "enableVsync", "false")
        // 1.17+: Minecraft consulta glGetError después de casi cada llamada y
        // en gl4es eso es un viaje a la GPU por llamada. Apagado solo se pierde
        // el diagnóstico de errores, no nada de lo que se ve.
        set(options, changes, "glErrorChecking", "false")
        // El mando virtual vive en el mismo proceso pero el juego cree que pierde
        // el foco al abrir menús; con esto el mundo sigue corriendo.
        set(options, changes, "pauseOnLostFocus", "false")
        set(options, changes, "tutorialStep", "none")
        set(options, changes, "skipMultiplayerWarning", "true")
        set(options, changes, "joinedFirstServer", "true")
    }

    private fun set(options: MutableMap<String, String>, changes: MutableList<String>, key: String, value: String) {
        val current = options[key]
        if (current == value) return
        options[key] = value
        changes += "$key ${current ?: "(sin definir)"}→$value"
    }
}
