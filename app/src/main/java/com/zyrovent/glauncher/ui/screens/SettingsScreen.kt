package com.zyrovent.glauncher.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LauncherSettings
import com.zyrovent.glauncher.data.MemoryBudget
import com.zyrovent.glauncher.data.RuntimeManager
import com.zyrovent.glauncher.data.StorageKind
import com.zyrovent.glauncher.data.StorageReport
import com.zyrovent.glauncher.data.StorageUsage
import com.zyrovent.glauncher.ui.components.BedrockAnimatedSwitch
import com.zyrovent.glauncher.ui.components.BedrockOptionRow
import com.zyrovent.glauncher.ui.components.BedrockSelector
import com.zyrovent.glauncher.ui.components.BedrockSlider
import com.zyrovent.glauncher.ui.components.BedrockTab
import com.zyrovent.glauncher.ui.components.BedrockToggle
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconDisk
import com.zyrovent.glauncher.ui.components.IconDownload
import com.zyrovent.glauncher.ui.components.IconFolder
import com.zyrovent.glauncher.ui.components.IconGear
import com.zyrovent.glauncher.ui.components.IconJava
import com.zyrovent.glauncher.ui.components.IconList
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.IconStar
import com.zyrovent.glauncher.ui.components.IconTrash
import com.zyrovent.glauncher.ui.components.IconWorld
import com.zyrovent.glauncher.ui.components.InfoRow
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McPanel
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.McTextField
import com.zyrovent.glauncher.ui.components.PixelArt
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.SectionLabel
import com.zyrovent.glauncher.ui.components.wallpaperRes
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.Gold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class SettingsCategory(val label: String) {
    GENERAL("General"),
    CONTROLS("Controles"),
    VIDEO("Vídeo"),
    JAVA("Java"),
    STORAGE("Almacenamiento"),
    ABOUT("Acerca de")
}

@Composable
fun SettingsScreen(
    state: UiState,
    onWallpaper: (Int) -> Unit,
    onRam: (Int) -> Unit,
    onRenderer: (String) -> Unit,
    onSounds: (Boolean) -> Unit,
    onJvmArgs: (String) -> Unit,
    onOpenLogs: () -> Unit,
    onImportRuntime: (Uri, Int) -> Unit,
    onRemoveRuntime: (Int) -> Unit,
    onDownloadRuntime: (String, Int) -> Unit,
    onTestRuntime: (Int) -> Unit,
    onTestGameWindow: () -> Unit = {},
    onTestJre: () -> Unit = {},
    onUseJoystick: (Boolean) -> Unit = {},
    onControlsOpacity: (Float) -> Unit = {},
    onControlsScale: (Float) -> Unit = {},
    onMouseSensitivity: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val paths = remember { GamePaths(context) }
    var ram by remember(state.settings.ramMb) { mutableFloatStateOf(state.settings.ramMb.toFloat()) }
    var runtimeUrl by remember { mutableStateOf("") }
    var targetMajor by remember { mutableStateOf(17) }
    var category by remember { mutableStateOf(SettingsCategory.GENERAL) }

    val jrePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportRuntime(uri, targetMajor)
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- Menú de categorías, como la columna izquierda de Bedrock ----
        Column(Modifier.fillMaxHeight().weight(0.32f)) {
            BedrockWindow(
                title = "Ajustes",
                subtitle = "GLauncher 1.1",
                icon = IconGear,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                contentModifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsCategory.entries.forEach { entry ->
                    BedrockTab(
                        text = entry.label,
                        selected = category == entry,
                        onClick = { category = entry },
                        icon = when (entry) {
                            SettingsCategory.GENERAL -> IconGear
                            SettingsCategory.CONTROLS -> IconPlay
                            SettingsCategory.VIDEO -> IconStar
                            SettingsCategory.JAVA -> IconJava
                            SettingsCategory.STORAGE -> IconFolder
                            SettingsCategory.ABOUT -> IconList
                        },
                        badge = when (entry) {
                            SettingsCategory.JAVA -> "8·17·21"
                            else -> null
                        }
                    )
                }
                PixelDivider()
                SectionLabel("Estado del motor")
                InfoRow(
                    "Runtime",
                    if (state.runtimeInstalled) {
                        state.installedRuntimes.joinToString(" · ")
                    } else {
                        "No instalado"
                    },
                    if (state.runtimeInstalled) BedrockGreen else BedrockTextDim
                )
                McButton(
                    text = "VER REGISTRO",
                    onClick = onOpenLogs,
                    icon = IconList,
                    tone = McButtonTone.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                    height = 32.dp
                )
            }
        }

        // ---- Contenido de la categoría seleccionada ----
        Column(Modifier.fillMaxHeight().weight(0.68f)) {
            BedrockWindow(
                title = category.label,
                subtitle = "Toca las filas para cambiar cada opción",
                icon = when (category) {
                    SettingsCategory.GENERAL -> IconGear
                    SettingsCategory.CONTROLS -> IconPlay
                    SettingsCategory.VIDEO -> IconStar
                    SettingsCategory.JAVA -> IconJava
                    SettingsCategory.STORAGE -> IconFolder
                    SettingsCategory.ABOUT -> IconList
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                contentModifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (category) {
                    SettingsCategory.GENERAL -> GeneralSettings(
                        state = state,
                        onWallpaper = onWallpaper,
                        onSounds = onSounds,
                        onJvmArgs = onJvmArgs
                    )

                    SettingsCategory.CONTROLS -> ControlsSettings(
                        state = state,
                        onUseJoystick = onUseJoystick,
                        onControlsOpacity = onControlsOpacity,
                        onControlsScale = onControlsScale,
                        onMouseSensitivity = onMouseSensitivity,
                        onTestGameWindow = onTestGameWindow
                    )

                    SettingsCategory.VIDEO -> VideoSettings(
                        state = state,
                        ram = ram,
                        onRamChange = { ram = it },
                        onRamCommit = { onRam(ram.toInt()) },
                        onRenderer = onRenderer,
                        onTestJre = onTestJre
                    )

                    SettingsCategory.JAVA -> JavaSettings(
                        state = state,
                        runtimeUrl = runtimeUrl,
                        onRuntimeUrl = { runtimeUrl = it },
                        targetMajor = targetMajor,
                        onTargetMajor = { targetMajor = it },
                        onDownload = { url, major -> onDownloadRuntime(url, major) },
                        onPickFile = { jrePicker.launch("*/*") },
                        onTest = onTestRuntime,
                        onRemove = onRemoveRuntime
                    )

                    SettingsCategory.STORAGE -> StorageSettings(
                        paths = paths,
                        onOpenLogs = onOpenLogs
                    )

                    SettingsCategory.ABOUT -> AboutSettings()
                }
            }
        }
    }
}

@Composable
private fun ControlsSettings(
    state: UiState,
    onUseJoystick: (Boolean) -> Unit,
    onControlsOpacity: (Float) -> Unit,
    onControlsScale: (Float) -> Unit,
    onMouseSensitivity: (Float) -> Unit,
    onTestGameWindow: () -> Unit
) {
    SectionLabel("Modo de Movimiento")
    BedrockOptionRow(
        label = "Usar Joystick analógico",
        description = "Reemplaza la cruceta D-Pad rígida por un joystick flotante 360°",
        onClick = { onUseJoystick(!state.settings.useJoystick) }
    ) {
        BedrockAnimatedSwitch(
            checked = state.settings.useJoystick,
            onCheckedChange = onUseJoystick
        )
    }

    PixelDivider()
    SectionLabel("Tamaño y Opacidad")

    var scale by remember(state.settings.controlsScale) { mutableFloatStateOf(state.settings.controlsScale) }
    BedrockOptionRow(
        label = "Escala de los controles",
        description = "Ajusta el tamaño general de los botones en pantalla (${"%.1f".format(scale)}x)"
    ) {
        BedrockSlider(
            value = scale,
            onValueChange = { scale = it },
            onValueChangeFinished = { onControlsScale(scale) },
            valueRange = 0.7f..1.5f,
            modifier = Modifier.width(150.dp)
        )
    }

    var opacity by remember(state.settings.controlsOpacity) { mutableFloatStateOf(state.settings.controlsOpacity) }
    BedrockOptionRow(
        label = "Opacidad de botones",
        description = "Transparencia del gamepad en partida (${(opacity * 100).toInt()}%)"
    ) {
        BedrockSlider(
            value = opacity,
            onValueChange = { opacity = it },
            onValueChangeFinished = { onControlsOpacity(opacity) },
            valueRange = 0.2f..1.0f,
            modifier = Modifier.width(150.dp)
        )
    }

    var sensitivity by remember(state.settings.mouseSensitivity) { mutableFloatStateOf(state.settings.mouseSensitivity) }
    BedrockOptionRow(
        label = "Sensibilidad de cámara",
        description = "Velocidad de giro al arrastrar la pantalla (${"%.2f".format(sensitivity)}x)"
    ) {
        BedrockSlider(
            value = sensitivity,
            onValueChange = { sensitivity = it },
            onValueChangeFinished = { onMouseSensitivity(sensitivity) },
            valueRange = 0.3f..3.0f,
            modifier = Modifier.width(150.dp)
        )
    }

    PixelDivider()
    SectionLabel("Probar configuración")
    BedrockOptionRow(
        label = "Probar controles en vivo",
        description = "Abre la ventana de juego para probar el joystick y los botones"
    ) {
        McButton(
            text = "PROBAR",
            onClick = onTestGameWindow,
            tone = McButtonTone.Success,
            height = 36.dp
        )
    }
}

@Composable
private fun GeneralSettings(
    state: UiState,
    onWallpaper: (Int) -> Unit,
    onSounds: (Boolean) -> Unit,
    onJvmArgs: (String) -> Unit
) {
    SectionLabel("Fondo de pantalla")
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..5).forEach { index ->
            val selected = state.settings.wallpaper == index
            Image(
                painter = painterResource(wallpaperRes(index)),
                contentDescription = "Fondo $index",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 84.dp, height = 48.dp)
                    .border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) BedrockGreen else BedrockBorderSoft
                    )
                    .clickable { onWallpaper(index) }
            )
        }
    }

    BedrockOptionRow(
        label = "Efectos de sonido",
        description = "Sonidos de los assets en botones y errores"
    ) {
        BedrockAnimatedSwitch(checked = state.settings.sounds, onCheckedChange = onSounds)
    }

    SectionLabel("Argumentos JVM extra")
    McTextField(
        value = state.settings.customJvmArgs,
        onValueChange = onJvmArgs,
        placeholder = "-XX:+UseSerialGC ..."
    )
}

@Composable
private fun VideoSettings(
    state: UiState,
    ram: Float,
    onRamChange: (Float) -> Unit,
    onRamCommit: () -> Unit,
    onRenderer: (String) -> Unit,
    onTestJre: () -> Unit
) {
    val renderers = LauncherSettings.RENDERERS
    val appContext = LocalContext.current
    val index = renderers.indexOf(state.settings.renderer).coerceAtLeast(0)
    // Tope real del equipo: pedir más de lo que la RAM sostiene hacía que
    // Android cerrara Minecraft por falta de memoria.
    val maxRam = remember { MemoryBudget.safeMaxMb(appContext) }

    BedrockOptionRow(
        label = "Renderer",
        description = "Cambia la salida gráfica del cliente"
    ) {
        BedrockSelector(
            value = state.settings.renderer,
            onPrevious = { onRenderer(renderers[(index - 1 + renderers.size) % renderers.size]) },
            onNext = { onRenderer(renderers[(index + 1) % renderers.size]) }
        )
    }

    McPanel(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Memoria asignada", Modifier.weight(1f))
            McTag("${ram.toInt()} MB", BedrockGreen)
        }
        BedrockSlider(
            value = ram,
            onValueChange = onRamChange,
            onValueChangeFinished = onRamCommit,
            valueRange = LauncherSettings.MIN_RAM.toFloat()..maxRam.toFloat(),
            steps = ((maxRam - LauncherSettings.MIN_RAM) / LauncherSettings.RAM_STEP - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth()
        )
        PixelText(
            text = "Tu equipo sostiene hasta $maxRam MB. Más que eso y Android cierra el juego por falta de memoria.",
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
    }

    SectionLabel("Motor de ejecución")
    BedrockOptionRow(
        label = "Consola de la JRE",
        description = "Abre una consola y arranca la máquina virtual instalada (-version) sin Minecraft, para ver si JLI_Launch, libjvm.so y el heap arrancan y qué escribe HotSpot."
    ) {
        McButton(
            text = "PROBAR JRE",
            onClick = onTestJre,
            icon = IconPlay,
            tone = McButtonTone.Success,
            height = 36.dp
        )
    }

    InfoRow("Versión activa", state.settings.selectedVersion, BedrockText)
    InfoRow("Pantalla", "Horizontal, sin bordes", BedrockTextDim)
}

@Composable
private fun JavaSettings(
    state: UiState,
    runtimeUrl: String,
    onRuntimeUrl: (String) -> Unit,
    targetMajor: Int,
    onTargetMajor: (Int) -> Unit,
    onDownload: (String, Int) -> Unit,
    onPickFile: () -> Unit,
    onTest: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(art = IconJava, size = 14.dp, tint = Gold)
        Spacer(Modifier.width(7.dp))
        SectionLabel("Runtimes instalados")
    }
    listOf(8, 17, 21, 25).forEach { major ->
        val installed = major in state.installedRuntimes
        val busy = state.runtimeImporting && state.runtimeInstallingMajor == major
        BedrockOptionRow(
            label = "Java $major",
            description = when {
                busy -> "Descargando…"
                installed -> if (major == 25) "Listo: se usa solo en 1.20.5+" else "Listo para lanzar"
                major == 8 -> "Minecraft 1.16.5 y anteriores"
                major == 17 -> "Minecraft 1.17 a 1.20.4"
                major == 21 -> "Minecraft 1.20.5 a 1.21.x"
                else -> "Minecraft 1.20.5+, 1.21 y 26.x (más rápido)"
            }
        ) {
            if (installed) {
                // Solo iconos: con texto se comían la etiqueta de la fila.
                McButton(
                    text = "",
                    onClick = { onTest(major) },
                    icon = IconPlay,
                    height = 26.dp,
                    enabled = !state.runtimeImporting,
                    modifier = Modifier.width(34.dp).padding(end = 4.dp)
                )
                McButton(
                    text = "",
                    onClick = { onRemove(major) },
                    icon = IconTrash,
                    tone = McButtonTone.Ghost,
                    height = 26.dp,
                    modifier = Modifier.width(34.dp)
                )
            } else {
                val preset = RuntimeManager.presetUrl(major)
                // Botón solo-icono: con texto se comía la etiqueta de la fila.
                McButton(
                    text = "",
                    onClick = { preset?.let { onDownload(it, major) } },
                    icon = IconDownload,
                    tone = McButtonTone.Success,
                    enabled = !state.runtimeImporting && preset != null,
                    height = 28.dp,
                    modifier = Modifier.width(38.dp)
                )
            }
            McTag(
                if (installed) "OK" else "—",
                if (installed) BedrockGreen else BedrockTextDim
            )
        }
    }

    PixelDivider()
    SectionLabel("Instalar motor propio")
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        McButton(
            "JAVA 8",
            { onTargetMajor(8) },
            tone = if (targetMajor == 8) McButtonTone.Primary else McButtonTone.Ghost,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )
        McButton(
            "JAVA 17",
            { onTargetMajor(17) },
            tone = if (targetMajor == 17) McButtonTone.Primary else McButtonTone.Ghost,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )
        McButton(
            "JAVA 21",
            { onTargetMajor(21) },
            tone = if (targetMajor == 21) McButtonTone.Primary else McButtonTone.Ghost,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )
        McButton(
            "JAVA 25",
            { onTargetMajor(25) },
            tone = if (targetMajor == 25) McButtonTone.Primary else McButtonTone.Ghost,
            height = 28.dp,
            modifier = Modifier.weight(1f)
        )
    }
    McTextField(
        value = runtimeUrl,
        onValueChange = onRuntimeUrl,
        placeholder = "https://…/jre8-arm64.tar.xz"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        McButton(
            text = "DESCARGAR URL",
            onClick = { onDownload(runtimeUrl, targetMajor) },
            enabled = !state.runtimeImporting && runtimeUrl.isNotBlank(),
            modifier = Modifier.weight(1f),
            height = 30.dp
        )
        McButton(
            text = "ARCHIVO",
            onClick = onPickFile,
            enabled = !state.runtimeImporting,
            tone = McButtonTone.Ghost,
            modifier = Modifier.weight(1f),
            height = 30.dp
        )
    }
    PixelText(
        text = ".zip, .tar.gz y .tar.xz con bin/java. Una build de escritorio (glibc) no " +
            "arranca en Android: el motor debe ser de Android ARM64.",
        color = BedrockTextDim.copy(alpha = 0.85f),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3
    )
}

@Composable
private fun StorageSettings(paths: GamePaths, onOpenLogs: () -> Unit) {
    val report by produceState<StorageReport?>(initialValue = null, paths) {
        value = withContext(Dispatchers.IO) { StorageUsage.report(paths) }
    }

    SectionLabel("Espacio del dispositivo")
    McPanel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        val data = report
        if (data == null) {
            PixelText(
                text = "Midiendo el almacenamiento…",
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelIcon(art = IconDisk, size = 15.dp, tint = BedrockText)
                Spacer(Modifier.width(8.dp))
                PixelText(
                    text = StorageUsage.format(data.usedBytes) + " usados",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                McTag(StorageUsage.format(data.freeBytes) + " LIBRES", BedrockGreen)
            }

            StorageBar(data)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                data.slices.forEach { slice ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(SliceColor(slice.kind))
                                .border(1.dp, BedrockBorder)
                        )
                        Spacer(Modifier.width(5.dp))
                        PixelText(
                            text = slice.label + " " + StorageUsage.format(slice.bytes),
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            InfoRow("Total del dispositivo", StorageUsage.format(data.totalBytes), BedrockTextDim)
        }
    }

    SectionLabel("Versiones instaladas")
    val versions = report?.versions.orEmpty()
    if (report != null && versions.isEmpty()) {
        PixelText(
            text = "Ninguna versión descargada todavía.",
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        versions.forEach { (name, bytes) ->
            StorageRow(IconWorld, name, StorageUsage.format(bytes), BedrockText)
        }
    }

    SectionLabel("Motores Java")
    val runtimes = report?.runtimes.orEmpty()
    if (report != null && runtimes.isEmpty()) {
        PixelText(
            text = "Sin runtimes instalados. Ve a Java para añadir uno.",
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        runtimes.forEach { (major, bytes) ->
            StorageRow(IconJava, "Java $major", StorageUsage.format(bytes), Gold)
        }
    }

    SectionLabel("Rutas")
    McPanel(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        InfoRow("Instancias", paths.instancesDir.absolutePath, BedrockText)
        InfoRow("Runtimes", paths.runtimesDir.absolutePath, BedrockText)
        InfoRow("Raíz del juego", paths.gameRoot.absolutePath, BedrockText)
    }

    McButton(
        text = "ABRIR REGISTRO",
        onClick = onOpenLogs,
        tone = McButtonTone.Ghost,
        modifier = Modifier.fillMaxWidth(),
        height = 32.dp
    )
}

/** Barra tipo Bedrock: tramos de color y lo libre en gris. */
@Composable
private fun StorageBar(report: StorageReport) {
    val total = report.totalBytes.coerceAtLeast(1L)
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(BedrockVoid)
            .border(1.dp, BedrockBorder)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        report.slices.forEach { slice ->
            val weight = (slice.bytes.toFloat() / total).coerceAtLeast(0.005f)
            Box(
                Modifier
                    .fillMaxHeight()
                    .weight(weight)
                    .background(SliceColor(slice.kind))
            )
        }
        val freeWeight = (report.freeBytes.toFloat() / total).coerceAtLeast(0.005f)
        Box(
            Modifier
                .fillMaxHeight()
                .weight(freeWeight)
                .background(BedrockRow.copy(alpha = 0.35f))
        )
    }
}

@Composable
private fun StorageRow(icon: PixelArt, label: String, value: String, color: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(BedrockRow.copy(alpha = 0.5f))
            .border(1.dp, BedrockBorderSoft.copy(alpha = 0.7f))
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PixelIcon(art = icon, size = 13.dp, tint = color.copy(alpha = 0.9f))
        Spacer(Modifier.width(8.dp))
        PixelText(
            text = label,
            color = BedrockText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        PixelText(text = value, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

private fun SliceColor(kind: StorageKind): Color = when (kind) {
    StorageKind.Versions -> BedrockGreen
    StorageKind.Runtimes -> Gold
    StorageKind.Assets -> Color(0xFF54C4E8)
    StorageKind.Logs -> BedrockTextDim
    StorageKind.Other -> Color(0xFF8A8A98)
}

@Composable
private fun AboutSettings() {
    McPanel(padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        PixelText(
            text = "GLauncher 1.1",
            color = BedrockText,
            style = MaterialTheme.typography.titleMedium
        )
        PixelText(
            text = "Launcher no oficial. No afiliado a Mojang ni Microsoft.",
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
        PixelDivider()
        McTag("BETA", Gold)
        PixelText(
            text = "Interfaz inspirada en los ajustes de Minecraft Bedrock.",
            color = BedrockTextDim,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2
        )
    }
    SectionLabel("Atajos")
    InfoRow("Jugar / detener", "Botón principal de Inicio", BedrockTextDim)
    InfoRow("Volver", "Gestos del sistema", BedrockTextDim)
    InfoRow("Motor Java", "Ajustes → Java", BedrockTextDim)
}
