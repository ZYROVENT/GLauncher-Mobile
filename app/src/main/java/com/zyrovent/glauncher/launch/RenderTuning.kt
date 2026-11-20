package com.zyrovent.glauncher.launch

import android.content.Context
import com.zyrovent.glauncher.data.MemoryBudget

/**
 * Cuántos píxeles de verdad debe dibujar Minecraft.
 *
 * El cliente pinta en una TextureView que ocupa toda la pantalla. En un equipo
 * de 2019 con panel de 1560x720 eso son más de un millón de píxeles por
 * fotograma, y el cuello de botella es el llenado (fillrate) de la GPU, no la
 * CPU. Renderizar por debajo de la resolución nativa y dejar que la textura se
 * estire es la mejora más barata que existe: el juego hace la mitad de trabajo
 * y se ve prácticamente igual en una pantalla de 6 pulgadas.
 *
 * La escala se elige con núcleos + RAM total, que es lo que se puede medir sin
 * crear un contexto OpenGL. El puente EGL recibe el tamaño reducido como si
 * fuera la pantalla completa, así que Minecraft, sus menús y el HUD se adaptan
 * solos, y [GameWindow.sendCursorPos] devuelve los toques a ese espacio.
 */
object RenderTuning {

    /**
     * Escala para este equipo (1.0 = resolución nativa).
     *
     * [width]/[height] son opcionales; cuando se conocen, se aplica un suelo por
     * tamaño de panel: en una pantalla de 360 px de alto (teléfonos con dock o
     * ventanas pequeñas) bajar de 0,85 no ahorra GPU —ya se dibujan muy pocos
     * píxeles— y en cambio deja el HUD y el chat ilegibles.
     */
    fun scale(context: Context, width: Int = 0, height: Int = 0): Float {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val ramMb = MemoryBudget.totalMb(context)
        val base = when {
            ramMb < 3072 -> 0.65f
            cores >= 8 && ramMb >= 6144 -> 1.0f
            cores >= 8 && ramMb >= 4096 -> 0.9f
            // Octa-core de gama media (Kirin 710, Snapdragon 6xx, Helio P…):
            // muchos núcleos pero GPU floja, es justo donde 40 fps se arreglan
            // bajando píxeles. 0,70 son ~24 % menos de trabajo de relleno que
            // 0,75 y en un panel de 1560x720 no se aprecia el difuminado.
            cores >= 8 -> 0.70f
            cores >= 6 -> 0.75f
            cores >= 4 && ramMb >= 4096 -> 0.72f
            else -> 0.65f
        }
        val shortSide = if (width > 0 && height > 0) minOf(width, height) else 0
        return when {
            shortSide == 0 -> base
            shortSide <= 400 -> maxOf(base, 0.85f)
            shortSide <= 600 -> maxOf(base, 0.75f)
            else -> base
        }
    }

    /** Texto para el registro: escala y resolución efectiva. */
    fun describe(scale: Float, width: Int, height: Int): String =
        "Render ${(scale * 100).toInt()}% · búfer ${(width * scale).toInt()}x${(height * scale).toInt()} de ${width}x$height"
}
