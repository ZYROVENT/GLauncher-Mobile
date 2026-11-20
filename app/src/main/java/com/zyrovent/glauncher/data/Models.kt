package com.zyrovent.glauncher.data

/** Tipo de perfil con el que se juega. Solo el modo offline funciona sin backend propio. */
enum class AccountType(val label: String) {
    OFFLINE("Offline"),
    MICROSOFT("Microsoft")
}

/**
 * Un perfil de jugador. En modo offline el nombre es lo único que el juego necesita
 * para el argumento --username; el UUID se deriva del nombre.
 */
data class Account(
    val username: String,
    val type: AccountType = AccountType.OFFLINE
)

/** Una versión publicada en el manifest oficial de Mojang. */
data class VersionSummary(
    val id: String,
    val type: String,
    val releaseTime: String,
    val metadataUrl: String
) {
    val isSnapshot: Boolean get() = type != "release"
    val displayType: String get() = when (type) {
        "release" -> "Release"
        "snapshot" -> "Snapshot"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}

data class VersionManifest(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<VersionSummary>
)

/** Archivo de librería de Maven que necesita el cliente. */
data class LibraryArtifact(
    val path: String,
    val url: String,
    val sha1: String,
    val size: Long
)

/** Archivos que necesita el cliente de una versión concreta. */
data class VersionFiles(
    val id: String,
    val clientUrl: String,
    val clientSha1: String,
    val clientSize: Long,
    val assetIndexId: String?,
    val assetIndexUrl: String? = null,
    val assetIndexSha1: String? = null,
    val metadataUrl: String,
    val metadataJson: String = "",
    val libraries: List<LibraryArtifact> = emptyList(),
    /** Versión mayor de Java que pide esta versión de Minecraft (8, 17, 21...). */
    val javaMajor: Int? = null
)

/**
 * Un aviso del launcher (arranque, instalación, error…). Por ahora se genera en
 * local a partir de los avisos reales del ViewModel; cuando entre Supabase será
 * la misma forma la que venga de la tabla `notifications`.
 */
data class LauncherEvent(
    val id: Long,
    val title: String,
    val detail: String,
    val time: Long = System.currentTimeMillis(),
    val read: Boolean = false
)

/** Ajustes persistidos del launcher. */
data class LauncherSettings(
    val selectedVersion: String = DEFAULT_VERSION,
    // 1024 es el valor que el equipo de prueba sostiene sin que Android mate
    // el proceso; el tope real se ajusta en MemoryBudget según la RAM del cel.
    val ramMb: Int = 1024,
    val renderer: String = RENDERERS.first(),
    val sounds: Boolean = true,
    val wallpaper: Int = 2,
    val customJvmArgs: String = "",
    val useJoystick: Boolean = true,
    val controlsOpacity: Float = 0.6f,
    val controlsScale: Float = 1.0f,
    val mouseSensitivity: Float = 1.0f,
    // Radio de la zona muerta del joystick (1.0 = la de siempre). Pequeño en
    // pantallas de 5" ayuda a no arrancar a andar con un roce.
    val joyDeadzone: Float = 1.0f,
    // Correr al empujar el joystick a tope (mantiene Ctrl). Se puede apagar si
    // prefieres esprintar con doble toque.
    val sprintOnFullPush: Boolean = true,
    // Invertir el eje vertical de la cámara (como en algunos juegos de consola).
    val invertCameraY: Boolean = false,
    // Controles virtuales: cada botón es independiente y editable.
    val virtualButtons: List<VirtualButton> = VirtualButton.defaults(),
    val dpadOffsetX: Float = 0f,
    val dpadOffsetY: Float = 0f,
    val actionsOffsetX: Float = 0f,
    val actionsOffsetY: Float = 0f
) {
    companion object {
        const val DEFAULT_VERSION = "1.21.4"
        val RENDERERS = listOf("GL4ES", "Zinc", "VirGL")
        const val MIN_RAM = 512
        const val MAX_RAM = 6144
        const val RAM_STEP = 512
    }
}

/**
 * Un botón del mando virtual. La posición se guarda normalizada (0..1) para que
 * el mismo layout sirva en cualquier resolución y en vertical u horizontal.
 */
data class VirtualButton(
    val id: String,
    val label: String,
    val kind: String = KIND_KEY,
    val code: Int,
    val x: Float,
    val y: Float,
    val size: Float = 54f,
    val opacity: Float = 0.85f,
    val color: Long = 0xFF2B2B2B,
    /** Los botones del grupo "move" se ocultan cuando se usa el joystick. */
    val group: String = ""
) {
    companion object {
        const val KIND_KEY = "key"
        const val KIND_MOUSE = "mouse"

        fun defaults(): List<VirtualButton> = listOf(
            VirtualButton("w", "W", KIND_KEY, 87, 0.115f, 0.60f, 52f, 0.85f, 0xFF2B2B2B, "move"),
            VirtualButton("a", "A", KIND_KEY, 65, 0.070f, 0.76f, 52f, 0.85f, 0xFF2B2B2B, "move"),
            VirtualButton("s", "S", KIND_KEY, 83, 0.115f, 0.90f, 52f, 0.85f, 0xFF2B2B2B, "move"),
            VirtualButton("d", "D", KIND_KEY, 68, 0.160f, 0.76f, 52f, 0.85f, 0xFF2B2B2B, "move"),
            VirtualButton("shift", "⇧", KIND_KEY, 340, 0.205f, 0.58f, 42f, 0.75f, 0xFF3E3E3E, "move"),
            VirtualButton("pri", "PRI", KIND_MOUSE, 0, 0.885f, 0.80f, 56f, 0.85f, 0xFFE53935),
            VirtualButton("sec", "SEC", KIND_MOUSE, 1, 0.955f, 0.66f, 56f, 0.85f, 0xFF1E88E5),
            VirtualButton("jump", "JUMP", KIND_KEY, 32, 0.860f, 0.56f, 56f, 0.85f, 0xFF43A047),
            VirtualButton("inv", "INV", KIND_KEY, 69, 0.795f, 0.82f, 52f, 0.85f, 0xFF2B2B2B)
        )

        /** Teclas disponibles para crear botones nuevos. */
        val KEY_CHOICES: List<Pair<String, Int>> = listOf(
            "ESPACIO" to 32,
            "SHIFT" to 340,
            "CTRL" to 341,
            "ALT" to 342,
            "E" to 69,
            "Q" to 81,
            "T" to 84,
            "F" to 70,
            "R" to 82,
            "G" to 71,
            "C" to 67,
            "X" to 88,
            "Z" to 90,
            "1" to 49,
            "2" to 50,
            "3" to 51,
            "4" to 52,
            "5" to 53,
            "6" to 54,
            "7" to 55,
            "8" to 56,
            "9" to 57,
            "F1" to 290,
            "F2" to 291,
            "F3" to 292,
            "F4" to 293,
            "F5" to 294,
            "TAB" to 258,
            "ESC" to 256
        )

        val MOUSE_CHOICES: List<Pair<String, Int>> = listOf(
            "CLIC IZQ" to 0,
            "CLIC DER" to 1,
            "RUEDA" to 2
        )
    }
}
