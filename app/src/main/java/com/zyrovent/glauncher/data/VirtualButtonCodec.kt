package com.zyrovent.glauncher.data

/**
 * Serialización de los layouts de controles virtuales.
 *
 * Formato compacto: un botón por línea con campos separados por "|". Se usa
 * tanto para el layout activo como para los presets guardados.
 */
object VirtualButtonCodec {

    fun encode(buttons: List<VirtualButton>): String = buttons.joinToString("\n") { b ->
        listOf(
            b.id, b.label, b.kind, b.code.toString(),
            b.x.toString(), b.y.toString(), b.size.toString(),
            b.opacity.toString(), b.color.toString(), b.group
        ).joinToString("|")
    }

    /** null si el texto no tiene ningún botón válido. */
    fun decode(raw: String?): List<VirtualButton>? {
        if (raw.isNullOrBlank()) return null
        val parsed = raw.split("\n").mapNotNull { line ->
            val p = line.split("|")
            if (p.size < 9) return@mapNotNull null
            val code = p[3].toIntOrNull() ?: return@mapNotNull null
            VirtualButton(
                id = p[0],
                label = p[1],
                kind = p[2],
                code = code,
                x = p[4].toFloatOrNull() ?: 0.5f,
                y = p[5].toFloatOrNull() ?: 0.5f,
                size = p[6].toFloatOrNull() ?: 54f,
                opacity = p[7].toFloatOrNull() ?: 0.85f,
                color = p[8].toLongOrNull() ?: 0xFF2B2B2B,
                group = p.getOrNull(9) ?: ""
            )
        }
        return parsed.ifEmpty { null }
    }
}
