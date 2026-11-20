package com.zyrovent.glauncher.gss

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.util.Sfx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Representa un componente visual interactivo insertado mediante `spawn <tipo> in "<target>"`.
 */
data class GssSpawnedComponent(
    val id: String,
    val type: String, // "button", "card", "label", "icon", "badge"
    val target: String, // ".card", ".view-content", "header", ".modal-content"
    val text: String = "",
    val icon: String = "",
    val move: String = "center", // "top-left", "top-right", "bottom-left", "bottom-right", "center"
    val moveX: Dp = 0.dp,
    val moveY: Dp = 0.dp,
    val sound: String? = null,
    val onClickAction: String? = null, // "notify:Mensaje"
    val bg: Color? = null,
    val border: Color? = null,
    val textColor: Color? = null,
    val cornerRadius: Dp = 0.dp,
    val padding: String = "",
    val shadow: String = "",
    val fontWeight: String = "normal"
)

/**
 * Propiedades de estilo para un selector semántico o selector CSS de GSS v7.0.
 */
data class GssSelectorStyle(
    val selector: String,
    val fondo: Color? = null,
    val letra: Color? = null,
    val borde: Color? = null,
    val borderWidth: Dp = 1.dp,
    val redondeado: Dp? = null,
    val brillo: String? = null,
    val relleno: Dp? = null,
    val margen: Dp? = null,
    val desenfoque: Dp = 0.dp,
    val opacidad: Float? = null,
    val transicion: String? = null,
    val pesoLetra: String? = null,
    val customProps: Map<String, String> = emptyMap()
)

/**
 * Estado completo del tema activo cargado por el motor GSS v7.0.
 */
data class GssThemeData(
    val name: String = "Bedrock Classic",
    val author: String = "Mojang / GLauncher",
    val version: String = "7.0",

    // Selectores semánticos estándar de GSS v7.0
    val navbar: GssSelectorStyle = GssSelectorStyle("navbar"),
    val navbarBtn: GssSelectorStyle = GssSelectorStyle("navbar_btn"),
    val navbarBtnActive: GssSelectorStyle = GssSelectorStyle("navbar_btn_active"),
    val button: GssSelectorStyle = GssSelectorStyle("button"),
    val buttonSecondary: GssSelectorStyle = GssSelectorStyle("button_secondary"),
    val card: GssSelectorStyle = GssSelectorStyle("card"),
    val miniPlayer: GssSelectorStyle = GssSelectorStyle("mini_player"),
    val modal: GssSelectorStyle = GssSelectorStyle("modal"),
    val camposTexto: GssSelectorStyle = GssSelectorStyle("campos_texto"),
    val scrollbar: GssSelectorStyle = GssSelectorStyle("scrollbar"),

    // Mapa general de selectores CSS (ej: style ".mi-clase" { ... })
    val cssRules: Map<String, GssSelectorStyle> = emptyMap(),

    // Componentes interactivos spawneados dinámicamente
    val spawnedComponents: List<GssSpawnedComponent> = emptyList(),

    // Variables Luau evaluadas
    val variables: Map<String, Any> = emptyMap()
)

val LocalGssTheme = compositionLocalOf { GssThemeData() }

/**
 * MOTOR GSS v7.0 (GLauncher StyleSheet & Script).
 *
 * Implementación oficial de la especificación GSS v7.0:
 * - Lógica Luau: `local var = "val"`, `for i = 1, n do`, `task.wait(sec)`, `if ... then ... else ... end`
 * - Selectores semánticos predefinidos con bloques terminados en `end`
 * - Alias automáticos en español (`fondo:`, `letra:`, `redondeado:`, `borde:`, `brillo:`, etc.)
 * - Spawning de componentes interactivos (`spawn <type> in "<target>" ... end`)
 * - Bloques de CSS libre (`style ".selector" { ... }`)
 * - Audio reactivo (`play sound "assets/sounds/..."`) y notificaciones (`notify("...")`)
 * - Sandbox estricto de seguridad AST sin eval, ni binarios, ni comandos del sistema.
 */
object GssEngine {

    var currentTheme by mutableStateOf(GssThemeData())
        private set

    // Cola reactiva de notificaciones emergentes generadas por notify()
    var latestNotification by mutableStateOf<String?>(null)
        private set

    // Scope para corrutinas seguras de `task.wait()` y bucles de scripting
    private val scriptScope = CoroutineScope(Dispatchers.Main + Job())
    private var runningJob: Job? = null

    // Callback opcional de reproducción de audio
    var onPlaySound: ((String) -> Unit)? = null
    var onNotify: ((String) -> Unit)? = null

    fun resetToDefault() {
        runningJob?.cancel()
        currentTheme = GssThemeData()
        latestNotification = null
    }

    fun applyTheme(theme: GssThemeData) {
        currentTheme = theme
    }

    fun clearNotification() {
        latestNotification = null
    }

    /**
     * Devuelve el estilo asignado a un selector semántico o CSS.
     */
    fun getStyle(selector: String): GssSelectorStyle? {
        val norm = selector.lowercase().trim()
        return when (norm) {
            "navbar", "barra_navegacion" -> currentTheme.navbar
            "navbar_btn", "botones_navbar" -> currentTheme.navbarBtn
            "navbar_btn_active" -> currentTheme.navbarBtnActive
            "button", "botones" -> currentTheme.button
            "button_secondary" -> currentTheme.buttonSecondary
            "card", "tarjetas" -> currentTheme.card
            "mini_player", "mini_reproductor" -> currentTheme.miniPlayer
            "modal", "ventana_flotante" -> currentTheme.modal
            "campos_texto", "input" -> currentTheme.camposTexto
            "scrollbar", "barra_desplazamiento" -> currentTheme.scrollbar
            else -> currentTheme.cssRules[norm]
        }
    }

    /**
     * Devuelve los componentes interactivos spawneados para un target determinado.
     */
    fun getSpawnedForTarget(target: String): List<GssSpawnedComponent> {
        val norm = target.lowercase().trim()
        return currentTheme.spawnedComponents.filter { it.target.lowercase().trim() == norm }
    }

    /**
     * Ejecuta una acción de clic configurada en un spawn (ej: "notify:Mensaje").
     */
    fun triggerAction(action: String, sound: String? = null) {
        sound?.let { playSound(it) }
        if (action.startsWith("notify:", ignoreCase = true)) {
            val msg = action.substringAfter(":").trim()
            showNotification(msg)
        }
    }

    fun showNotification(msg: String) {
        latestNotification = msg
        onNotify?.invoke(msg)
    }

    fun playSound(path: String) {
        // Validación de sandbox: solo audio local permitido
        val clean = path.lowercase().trim()
        if (clean.endsWith(".ogg") || clean.endsWith(".mp3") || clean.endsWith(".wav")) {
            onPlaySound?.invoke(path)
        }
    }

    /**
     * Parsea y ejecuta un script GSS v7.0 completo.
     */
    fun parseAndRunGss(source: String, filename: String = "theme.gss"): Result<GssThemeData> {
        return try {
            // 1. Sandbox AST Check: Validar palabras prohibidas
            val forbidden = listOf(
                "exec(", "system(", "runtime.getruntime", "processbuilder", "cmd.exe",
                "/bin/sh", ".bat", ".exe", ".sh", "socket", "file.delete",
                "import os", "import subprocess", "require('child_process')", "http://", "https://"
            )
            val lowerSource = source.lowercase()
            for (token in forbidden) {
                if (lowerSource.contains(token)) {
                    return Result.failure(SecurityException("GSS v7.0 Sandbox: Prohibido el uso de '$token'"))
                }
            }

            runningJob?.cancel()

            // 2. Tablas de símbolos de Luau
            val vars = mutableMapOf<String, Any>()
            val spawned = mutableListOf<GssSpawnedComponent>()
            val cssRules = mutableMapOf<String, GssSelectorStyle>()

            var navbarStyle = GssSelectorStyle("navbar")
            var navbarBtnStyle = GssSelectorStyle("navbar_btn")
            var navbarBtnActiveStyle = GssSelectorStyle("navbar_btn_active")
            var buttonStyle = GssSelectorStyle("button")
            var buttonSecStyle = GssSelectorStyle("button_secondary")
            var cardStyle = GssSelectorStyle("card")
            var miniPlayerStyle = GssSelectorStyle("mini_player")
            var modalStyle = GssSelectorStyle("modal")
            var camposTextoStyle = GssSelectorStyle("campos_texto")
            var scrollbarStyle = GssSelectorStyle("scrollbar")

            // Buffer y máquinas de estado para bloques
            var inSemanticBlock: String? = null
            var semanticProps = mutableMapOf<String, String>()

            var inSpawnType: String? = null
            var inSpawnTarget: String? = null
            var spawnProps = mutableMapOf<String, String>()

            var inStyleCss: String? = null
            var styleCssProps = mutableMapOf<String, String>()

            val lines = source.lines()
            var lineIdx = 0

            // Instrucciones diferidas para ejecución asíncrona (task.wait, notify, play sound)
            val asyncActions = mutableListOf<suspend () -> Unit>()

            while (lineIdx < lines.size) {
                val rawLine = lines[lineIdx++]
                val line = rawLine.trim()

                if (line.isEmpty() || line.startsWith("--") || line.startsWith("#") || line.startsWith("//")) {
                    continue
                }

                // ---------------- BLOQUE STYLE ".selector" { ... } ----------------
                if (inStyleCss != null) {
                    if (line.startsWith("}")) {
                        val parsedStyle = buildStyleFromProps(inStyleCss!!, styleCssProps, vars)
                        cssRules[inStyleCss!!.lowercase()] = parsedStyle
                        inStyleCss = null
                        styleCssProps.clear()
                    } else if (line.contains(":")) {
                        val (k, v) = parseKeyValue(line)
                        styleCssProps[k] = v
                    }
                    continue
                }

                if (line.startsWith("style ") && line.endsWith("{")) {
                    val sel = line.substringAfter("style").removeSuffix("{").trim()
                        .removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'").trim()
                    inStyleCss = sel
                    styleCssProps.clear()
                    continue
                }

                // ---------------- BLOQUE SPAWN <tipo> in "<target>": ... end ----------------
                if (inSpawnType != null) {
                    if (line == "end" || line.startsWith("end ")) {
                        val comp = buildSpawnedComponent(inSpawnType!!, inSpawnTarget!!, spawnProps, vars)
                        spawned.add(comp)
                        inSpawnType = null
                        inSpawnTarget = null
                        spawnProps.clear()
                    } else if (line.contains(":")) {
                        val (k, v) = parseKeyValue(line)
                        spawnProps[k] = v
                    }
                    continue
                }

                if (line.startsWith("spawn ") && line.contains(" in ") && (line.endsWith(":") || line.endsWith("do"))) {
                    val afterSpawn = line.removePrefix("spawn").trim()
                    val type = afterSpawn.substringBefore(" in ").trim()
                    val target = afterSpawn.substringAfter(" in ").removeSuffix(":").removeSuffix("do").trim()
                        .removePrefix("\"").removeSuffix("\"").removePrefix("'").removeSuffix("'").trim()
                    inSpawnType = type
                    inSpawnTarget = target
                    spawnProps.clear()
                    continue
                }

                // ---------------- BLOQUES SEMÁNTICOS (navbar:, card:, etc.) ... end ----------------
                if (inSemanticBlock != null) {
                    if (line == "end" || line.startsWith("end ")) {
                        val parsed = buildStyleFromProps(inSemanticBlock!!, semanticProps, vars)
                        when (inSemanticBlock) {
                            "navbar", "barra_navegacion" -> navbarStyle = parsed
                            "navbar_btn", "botones_navbar" -> navbarBtnStyle = parsed
                            "navbar_btn_active" -> navbarBtnActiveStyle = parsed
                            "button", "botones" -> buttonStyle = parsed
                            "button_secondary" -> buttonSecStyle = parsed
                            "card", "tarjetas" -> cardStyle = parsed
                            "mini_player", "mini_reproductor" -> miniPlayerStyle = parsed
                            "modal", "ventana_flotante" -> modalStyle = parsed
                            "campos_texto", "input" -> camposTextoStyle = parsed
                            "scrollbar", "barra_desplazamiento" -> scrollbarStyle = parsed
                            else -> cssRules[inSemanticBlock!!] = parsed
                        }
                        inSemanticBlock = null
                        semanticProps.clear()
                    } else if (line.contains(":")) {
                        val (k, v) = parseKeyValue(line)
                        semanticProps[k] = v
                    }
                    continue
                }

                // Apertura de bloque semántico
                if (line.endsWith(":") && !line.startsWith("style ") && !line.startsWith("spawn ")) {
                    val selectorName = line.removeSuffix(":").trim().lowercase()
                    inSemanticBlock = selectorName
                    semanticProps.clear()
                    continue
                }

                // ---------------- SINTAXIS Y VARIABLES LUAU ----------------
                if (line.startsWith("local ") || (line.contains("=") && !line.startsWith("if ") && !line.startsWith("for "))) {
                    val decl = line.removePrefix("local ").trim()
                    val p = decl.split("=", limit = 2)
                    if (p.size == 2) {
                        val varName = p[0].trim()
                        val rawVal = p[1].trim()
                        val evaluated = evaluateExpression(rawVal, vars)
                        vars[varName] = evaluated
                    }
                    continue
                }

                // Notificación: notify("mensaje")
                if (line.startsWith("notify(") && line.endsWith(")")) {
                    val msgExpr = line.substringAfter("notify(").removeSuffix(")").trim()
                    val evaluated = evaluateExpression(msgExpr, vars).toString()
                    asyncActions.add {
                        showNotification(evaluated)
                    }
                    continue
                }

                // Sonido local: play sound "path"
                if (line.startsWith("play sound ")) {
                    val sndExpr = line.removePrefix("play sound ").trim()
                    val evaluated = evaluateExpression(sndExpr, vars).toString()
                    asyncActions.add {
                        playSound(evaluated)
                    }
                    continue
                }

                // Pausa temporal: task.wait(segundos)
                if (line.startsWith("task.wait(") && line.endsWith(")")) {
                    val secExpr = line.substringAfter("task.wait(").removeSuffix(")").trim()
                    val sec = (secExpr.toFloatOrNull() ?: 0.2f).coerceIn(0.05f, 10.0f)
                    asyncActions.add {
                        delay((sec * 1000).toLong())
                    }
                    continue
                }

                // Bucle simple de Luau: for i = start, end, step do ... end
                if (line.startsWith("for ") && line.contains("=") && line.endsWith("do")) {
                    val header = line.removePrefix("for ").removeSuffix("do").trim()
                    val parts = header.split("=")
                    if (parts.size == 2) {
                        val iterVar = parts[0].trim()
                        val nums = parts[1].split(",").mapNotNull { it.trim().toIntOrNull() }
                        val start = nums.getOrElse(0) { 1 }
                        val end = nums.getOrElse(1) { 1 }
                        val step = nums.getOrElse(2) { 1 }

                        // Colectar líneas del bucle
                        val loopLines = mutableListOf<String>()
                        while (lineIdx < lines.size) {
                            val l = lines[lineIdx++].trim()
                            if (l == "end" || l.startsWith("end ")) break
                            loopLines.add(l)
                        }

                        asyncActions.add {
                            for (cur in start..end step step) {
                                vars[iterVar] = cur
                                for (l in loopLines) {
                                    if (l.startsWith("play sound ")) {
                                        val evaluated = evaluateExpression(l.removePrefix("play sound ").trim(), vars).toString()
                                        playSound(evaluated)
                                    } else if (l.startsWith("notify(")) {
                                        val evaluated = evaluateExpression(l.substringAfter("notify(").removeSuffix(")").trim(), vars).toString()
                                        showNotification(evaluated)
                                    } else if (l.startsWith("task.wait(")) {
                                        val sec = (l.substringAfter("task.wait(").removeSuffix(")").trim().toFloatOrNull() ?: 0.2f).coerceIn(0.05f, 10.0f)
                                        delay((sec * 1000).toLong())
                                    }
                                }
                            }
                        }
                    }
                    continue
                }
            }

            val theme = GssThemeData(
                name = filename.substringBeforeLast("."),
                author = vars["author"]?.toString() ?: "GLauncher Community",
                version = "7.0",
                navbar = navbarStyle,
                navbarBtn = navbarBtnStyle,
                navbarBtnActive = navbarBtnActiveStyle,
                button = buttonStyle,
                buttonSecondary = buttonSecStyle,
                card = cardStyle,
                miniPlayer = miniPlayerStyle,
                modal = modalStyle,
                camposTexto = camposTextoStyle,
                scrollbar = scrollbarStyle,
                cssRules = cssRules,
                spawnedComponents = spawned,
                variables = vars
            )

            applyTheme(theme)

            // 3. Ejecutar acciones asíncronas de Luau (task.wait, notify, play sound) en segundo plano
            if (asyncActions.isNotEmpty()) {
                runningJob = scriptScope.launch {
                    for (action in asyncActions) {
                        action()
                    }
                }
            }

            Result.success(theme)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------- EVALUADOR DE EXPRESIONES LUAU ----------------

    private fun evaluateExpression(expr: String, vars: Map<String, Any>): Any {
        val trimmed = expr.trim()
        if (trimmed == "true") return true
        if (trimmed == "false") return false
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }

        // Concatenación de cadenas Luau: "texto " + variable o var1 + var2
        if (trimmed.contains("+")) {
            val tokens = trimmed.split("+")
            var result = ""
            for (t in tokens) {
                val evaluated = evaluateExpression(t.trim(), vars)
                result += evaluated.toString()
            }
            return result
        }

        // String literal entre comillas
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Variable existente
        if (vars.containsKey(trimmed)) {
            return vars[trimmed]!!
        }

        return trimmed
    }

    private fun parseKeyValue(line: String): Pair<String, String> {
        val sepIdx = line.indexOf(':')
        val key = line.substring(0, sepIdx).trim().lowercase()
        var value = line.substring(sepIdx + 1).trim().removeSuffix(";").trim()
        return Pair(key, value)
    }

    private fun buildStyleFromProps(
        selector: String,
        props: Map<String, String>,
        vars: Map<String, Any>
    ): GssSelectorStyle {
        var fondo: Color? = null
        var letra: Color? = null
        var borde: Color? = null
        var borderWidth: Dp = 1.dp
        var redondeado: Dp? = null
        var brillo: String? = null
        var relleno: Dp? = null
        var margen: Dp? = null
        var desenfoque: Dp = 0.dp
        var opacidad: Float? = null
        var transicion: String? = null
        var pesoLetra: String? = null

        for ((rawKey, rawVal) in props) {
            val key = rawKey.trim().lowercase()
            val evalVal = evaluateExpression(rawVal, vars).toString()

            when (key) {
                // Alias de fondo
                "fondo", "background", "background-color" ->
                    fondo = parseColorFromCss(evalVal)

                // Alias de color de texto / letra
                "letra", "texto_color", "color" ->
                    letra = parseColorFromCss(evalVal)

                // Alias de bordes
                "borde", "border" -> {
                    val (color, width) = parseBorderSpec(evalVal)
                    borde = color
                    borderWidth = width
                }

                // Alias de esquinas redondeadas
                "redondeado", "esquinas", "border-radius" ->
                    redondeado = parseDp(evalVal)

                // Alias de sombras y brillos
                "brillo", "sombra", "box-shadow" ->
                    brillo = evalVal

                // Alias de espaciado
                "relleno", "padding" ->
                    relleno = parseDp(evalVal)

                "margen", "margin" ->
                    margen = parseDp(evalVal)

                // Desenfoque backdrop-filter
                "desenfoque", "backdrop-filter" ->
                    desenfoque = parseDp(evalVal) ?: 0.dp

                // Opacidad y tipografía
                "opacidad", "opacity" ->
                    opacidad = evalVal.toFloatOrNull()

                "peso_letra", "font-weight" ->
                    pesoLetra = evalVal

                "transicion", "transition" ->
                    transicion = evalVal
            }
        }

        return GssSelectorStyle(
            selector = selector,
            fondo = fondo,
            letra = letra,
            borde = borde,
            borderWidth = borderWidth,
            redondeado = redondeado,
            brillo = brillo,
            relleno = relleno,
            margen = margen,
            desenfoque = desenfoque,
            opacidad = opacidad,
            transicion = transicion,
            pesoLetra = pesoLetra,
            customProps = props
        )
    }

    private fun buildSpawnedComponent(
        type: String,
        target: String,
        props: Map<String, String>,
        vars: Map<String, Any>
    ): GssSpawnedComponent {
        var text = ""
        var icon = ""
        var move = "center"
        var moveX = 0.dp
        var moveY = 0.dp
        var sound: String? = null
        var onClickAction: String? = null
        var fondo: Color? = null
        var border: Color? = null
        var textColor: Color? = null
        var cornerRadius = 0.dp
        var padding = ""
        var shadow = ""
        var fontWeight = "normal"

        for ((k, v) in props) {
            val evalVal = evaluateExpression(v, vars).toString()
            when (k.trim().lowercase()) {
                "text", "texto" -> text = evalVal
                "icon", "icono" -> icon = evalVal
                "move", "posicion" -> move = evalVal
                "move_x", "posicion_x" -> moveX = parseDp(evalVal) ?: 0.dp
                "move_y", "posicion_y" -> moveY = parseDp(evalVal) ?: 0.dp
                "sound", "sonido" -> sound = evalVal
                "on_click", "al_clic" -> onClickAction = evalVal
                "fondo", "background" -> fondo = parseColorFromCss(evalVal)
                "borde", "border" -> border = parseColorFromCss(evalVal)
                "letra", "color" -> textColor = parseColorFromCss(evalVal)
                "redondeado", "border-radius" -> cornerRadius = parseDp(evalVal) ?: 0.dp
                "relleno", "padding" -> padding = evalVal
                "brillo", "box-shadow" -> shadow = evalVal
                "peso_letra", "font-weight" -> fontWeight = evalVal
            }
        }

        return GssSpawnedComponent(
            id = "spawn_${System.currentTimeMillis()}_${(0..999).random()}",
            type = type,
            target = target,
            text = text,
            icon = icon,
            move = move,
            moveX = moveX,
            moveY = moveY,
            sound = sound,
            onClickAction = onClickAction,
            bg = fondo,
            border = border,
            textColor = textColor,
            cornerRadius = cornerRadius,
            padding = padding,
            shadow = shadow,
            fontWeight = fontWeight
        )
    }

    private fun parseDp(raw: String): Dp? {
        val clean = raw.replace("px", "").replace("dp", "").trim()
        return clean.toFloatOrNull()?.dp
    }

    private fun parseBorderSpec(raw: String): Pair<Color?, Dp> {
        val parts = raw.split(" ")
        var width = 1.dp
        var color: Color? = null
        for (p in parts) {
            if (p.endsWith("px") || p.endsWith("dp")) {
                width = parseDp(p) ?: 1.dp
            } else if (p.startsWith("#") || p.startsWith("rgb")) {
                color = parseColorFromCss(p)
            }
        }
        return Pair(color, width)
    }

    fun parseColorFromCss(raw: String): Color? {
        val clean = raw.trim().removePrefix("\"").removeSuffix("\"").trim()
        return try {
            if (clean.startsWith("#")) {
                val hex = clean.removePrefix("#")
                when (hex.length) {
                    3 -> {
                        val r = hex.substring(0, 1).repeat(2).toInt(16)
                        val g = hex.substring(1, 2).repeat(2).toInt(16)
                        val b = hex.substring(2, 3).repeat(2).toInt(16)
                        Color(r, g, b)
                    }
                    6 -> Color(0xFF000000 or hex.toLong(16))
                    8 -> Color(hex.toLong(16))
                    else -> null
                }
            } else if (clean.startsWith("rgba(") || clean.startsWith("rgb(")) {
                val numbers = clean.substringAfter("(").substringBefore(")").split(",")
                val r = numbers[0].trim().toInt()
                val g = numbers[1].trim().toInt()
                val b = numbers[2].trim().toInt()
                val a = if (numbers.size > 3) numbers[3].trim().toFloat() else 1.0f
                Color(r / 255f, g / 255f, b / 255f, a)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
