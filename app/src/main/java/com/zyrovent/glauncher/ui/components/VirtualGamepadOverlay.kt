package com.zyrovent.glauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.Screen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.ui.screens.MusicViewModel
import com.zyrovent.glauncher.data.LauncherSettings
import com.zyrovent.glauncher.data.VirtualButton
import com.zyrovent.glauncher.launch.GameWindow
import com.zyrovent.glauncher.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun VirtualGamepadOverlay(
    modifier: Modifier = Modifier,
    settings: LauncherSettings = LauncherSettings(),
    musicVm: MusicViewModel? = null,
    onUpdateSettings: (LauncherSettings) -> Unit = {},
    onNavigate: (Screen) -> Unit = {},
    onExitGame: () -> Unit
) {
    var cursorX by remember { mutableStateOf(500f) }
    var cursorY by remember { mutableStateOf(500f) }
    // Última posición tocada: el juego coloca ahí el ratón y recibe el clic.
    var touchCursor by remember { mutableStateOf<Offset?>(null) }
    val tapScope = rememberCoroutineScope()

    // Estados de modales dentro de la partida
    var showMenuModal by remember { mutableStateOf(false) }
    var activeModal by remember { mutableStateOf<String?>(null) } // null = Menú 3 botones, "SETTINGS", "MUSIC"
    var isEditingLayout by remember { mutableStateOf(false) } // Modo acomodador de UI
    // Cada botón es independiente y se puede mover, redimensionar y reasignar.
    var buttons by remember(settings.virtualButtons) { mutableStateOf(settings.virtualButtons) }
    var editingButton by remember { mutableStateOf<VirtualButton?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var dpadOffset by remember(settings.dpadOffsetX, settings.dpadOffsetY) {
        mutableStateOf(Offset(settings.dpadOffsetX, settings.dpadOffsetY))
    }

    var mouseSensitivity by remember(settings.mouseSensitivity) { mutableFloatStateOf(settings.mouseSensitivity) }
    // Ajustes finos del joystick y de la cámara, editables en partida.
    var joyDeadzone by remember(settings.joyDeadzone) { mutableFloatStateOf(settings.joyDeadzone) }
    var sprintPush by remember(settings.sprintOnFullPush) { mutableStateOf(settings.sprintOnFullPush) }
    var invertY by remember(settings.invertCameraY) { mutableStateOf(settings.invertCameraY) }
    var buttonOpacity by remember(settings.controlsOpacity) { mutableFloatStateOf(settings.controlsOpacity) }
    var controlsScale by remember(settings.controlsScale) { mutableFloatStateOf(settings.controlsScale) }
    var useJoystick by remember(settings.useJoystick) { mutableStateOf(settings.useJoystick) }
    var currentTab by remember { mutableStateOf(0) }
    val gssTheme = com.zyrovent.glauncher.gss.LocalGssTheme.current
    val effectiveOpacity = gssTheme.camposTexto.opacidad ?: buttonOpacity
    val effectiveScale = controlsScale
    // Teclado del sistema para escribir en el chat del juego.
    var showKeyboard by remember { mutableStateOf(false) }
    // Estado real de captura del ratón en el juego: en partida la cámara va
    // relativa y sin puntero en pantalla; en menús e inventarios se dibuja el
    // puntero y un toque lo coloca donde tocas.
    // Zona muerta del sistema: franja del panel de navegación (medida en el
    // dispositivo: 80 px abajo y 32 px en cada lado sobre un panel de
    // 1560x720). Ahí los gestos los come Android, así que si se usan para la
    // cámara el puntero salta solo y parece que el mouse se "pega".
    val densidad = LocalDensity.current
    val insetsNav = WindowInsets.navigationBars
    val zonaInferiorPx = maxOf(
        with(densidad) { insetsNav.getBottom(this).toFloat() },
        with(densidad) { 24.dp.toPx() }
    )
    val zonaLateralPx = maxOf(
        maxOf(
            insetsNav.getLeft(densidad, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat(),
            insetsNav.getRight(densidad, androidx.compose.ui.unit.LayoutDirection.Ltr).toFloat()
        ),
        with(densidad) { 12.dp.toPx() }
    )
    var isGrabbing by remember { mutableStateOf(false) }
    // Lectura del estado de captura sin reiniciar el gesto: antes, al detectar
    // el grab, el pointerInput se recreaba y la cámara se soltaba a medio giro.
    val grabbingState = rememberUpdatedState(isGrabbing)
    LaunchedEffect(Unit) {
        while (true) {
            val grabbing = GameWindow.isGrabbing()
            if (grabbing != isGrabbing) {
                isGrabbing = grabbing
                if (!grabbing) touchCursor = Offset(cursorX, cursorY)
            }
            delay(180)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Fondo transparente: debajo está la superficie del juego.
            // Un fondo opaco aquí tapaba Minecraft por completo.
            // Un único gesto para todo: antes había un detector de toque y otro
            // de arrastre compitiendo, y el tap se comía el gesto (no se podía
            // girar la cámara mientras se andaba). Ahora se espera el primer
            // movimiento: si pasa del umbral es arrastre (cámara), si no es tap.
            //
            // En partida el ratón está capturado y el puntero NO se teletransporta
            // al punto tocado: ese salto era el que volvía loca la cámara al
            // golpear. En un menú sí se coloca, que es como se navega el juego.
            // Cada dedo es independiente: este gesto atiende SOLO el suyo y no
            // se cancela porque aparezca otro (joystick, un botón, un segundo
            // dedo libre). Ese cancelado era el que en 1.8.9 dejaba la vista
            // clavada mientras se andaba con el mando.
            .pointerInput(mouseSensitivity, settings.invertCameraY) {
                // Bucle propio en vez de "un gesto = un dedo": mientras el
                // joystick siga apoyado, awaitEachGesture no volvía a empezar y la
                // cámara se quedaba muerta hasta soltar todo. Aquí se espera cada
                // dedo libre y se atiende dentro del mismo gesto, uno tras otro.
                awaitEachGesture {
                    while (true) {
                        // 1) Esperar un dedo que NO sea del mando.
                        var down = awaitFirstDown(requireUnconsumed = false)
                        while (down.isConsumed) {
                            val evento = awaitPointerEvent()
                            down = evento.changes
                                .firstOrNull { it.pressed && !it.previousPressed && !it.isConsumed }
                                ?: continue
                        }

                        val grabbing = grabbingState.value
                        val slop = viewConfiguration.touchSlop
                        val invertir = if (settings.invertCameraY) -1f else 1f
                        // Si el dedo cae en la franja del sistema, se deja pasar
                        // el toque (sirve para pulsar) pero NO se arrastra la
                        // cámara desde ahí.
                        val enZonaMuerta = down.position.y >= size.height - zonaInferiorPx ||
                            down.position.x <= zonaLateralPx ||
                            down.position.x >= size.width - zonaLateralPx
                        var last = down.position
                        var total = Offset.Zero
                        var dragged = false

                        // Clic mantenido en menús: se pulsa al bajar y se suelta
                        // al subir, para poder mantener pulsado (romper bloques).
                        var clicActivo = false
                        fun soltarClic() {
                            if (clicActivo) {
                                clicActivo = false
                                GameWindow.sendMouseButton(GameWindow.MOUSE_BUTTON_LEFT, isDown = false)
                            }
                        }

                        // Al apoyar el dedo NO se mueve el puntero ni se pulsa
                        // nada. Antes se teletransportaba al punto tocado (o se
                        // pulsaba el clic en menús) y eso, al volver a tocar
                        // después de girar, daba un salto que dejaba el ratón
                        // "pegado": el juego recibía un delta enorme.
                        // Ahora todo es relativo y solo un toque corto coloca el
                        // puntero (ver el final del bucle).
                        touchCursor = Offset(cursorX, cursorY)

                        // 2) Arrastrar con ESE dedo hasta que lo suelte.
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.isConsumed) break

                            val delta = change.position - last
                            last = change.position
                            if (delta == Offset.Zero) continue
                            total += delta
                            if (!dragged && total.getDistance() > slop) dragged = true
                            if (!dragged) continue

                            change.consume()
                            soltarClic()
                            if (enZonaMuerta) continue

                            // Siempre relativo: en partida mueve la cámara y en
                            // menús funciona como un ratón, sin depender de si el
                            // juego informa bien del estado de captura (las
                            // versiones con LWJGL 2 no lo hacen igual).
                            cursorX += delta.x * mouseSensitivity
                            cursorY += delta.y * mouseSensitivity * invertir
                            // Sin recortar a los bordes: si no, la cámara se
                            // quedaba clavada al llegar al margen.
                            touchCursor = Offset(cursorX, cursorY)
                            GameWindow.sendCursorPos(cursorX, cursorY)
                        }

                        soltarClic()

                        // Toque corto = clic de verdad. En un menú primero se
                        // coloca el puntero donde tocaste (para dar en la casilla
                        // correcta); en partida no se mueve, solo se pulsa.
                        if (!dragged) {
                            if (!grabbing) {
                                cursorX = down.position.x
                                cursorY = down.position.y
                                touchCursor = Offset(cursorX, cursorY)
                                GameWindow.sendCursorPos(cursorX, cursorY)
                            }
                            GameWindow.sendMouseButton(GameWindow.MOUSE_BUTTON_LEFT, isDown = true)
                            GameWindow.sendMouseButton(GameWindow.MOUSE_BUTTON_LEFT, isDown = false)
                        }
                    }
                }
            }
            // AVISO: aquí vivía la rueda del ratón de dos dedos. Se quitó porque
            // ese gesto consumía TODOS los punteros en cuanto aparecía un segundo
            // dedo libre, y el dedo de la cámara (que sí estaba arrastrando) se
            // quedaba congelado a media partida: era el "cursor se pega".
            // La rueda vuelve con un botón propio cuando toque, no robando dedos.
    ) {
        // Puntero del ratón virtual: flecha blanca tipo escritorio.
        // En partida NO se dibuja (ahí manda la mira del juego) y el movimiento
        // es relativo; en inventarios, menús y GUIs aparece la flecha y el
        // puntero se coloca donde tocas, como un ratón de verdad.
        if (!isGrabbing) {
            // En partida el puntero puede quedar fuera de pantalla (es relativo);
            // al dibujarlo se recorta para que siempre se vea.
            val crudo = touchCursor ?: Offset(cursorX, cursorY)
            Canvas(Modifier.fillMaxSize()) {
                val cursor = Offset(
                    crudo.x.coerceIn(0f, size.width),
                    crudo.y.coerceIn(0f, size.height)
                )
                // Cursor minimalista: una cruz fina con un punto en el centro.
                // Nada de flechas grandes ni aros: tapa menos el inventario y
                // sigue siendo exacto para dar clics en las casillas.
                val arm = 9f
                val gap = 2.5f
                val outline = Color.Black.copy(alpha = 0.45f)
                val brazos = listOf(
                    Offset(cursor.x - arm, cursor.y) to Offset(cursor.x - gap, cursor.y),
                    Offset(cursor.x + gap, cursor.y) to Offset(cursor.x + arm, cursor.y),
                    Offset(cursor.x, cursor.y - arm) to Offset(cursor.x, cursor.y - gap),
                    Offset(cursor.x, cursor.y + gap) to Offset(cursor.x, cursor.y + arm)
                )
                brazos.forEach { (a, b) ->
                    drawLine(outline, a, b, strokeWidth = 3.6f, cap = StrokeCap.Round)
                    drawLine(Color.White, a, b, strokeWidth = 1.3f, cap = StrokeCap.Round)
                }
                drawCircle(outline, radius = 2.6f, center = cursor)
                drawCircle(Color.White, radius = 1.3f, center = cursor)
            }
        }

        // --- Barra Superior (F3, F5, Teclado, Pausa/ESC, Tuerca de Ajustes, Salir) ---
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GamepadKeyButton(text = "F3", key = GameWindow.KEY_F3, width = 54.dp, height = 36.dp, opacity = buttonOpacity)
                GamepadKeyButton(text = "F5", key = GameWindow.KEY_F5, width = 54.dp, height = 36.dp, opacity = buttonOpacity)
                GamepadKeyButton(text = "ESC", key = GameWindow.KEY_ESCAPE, width = 54.dp, height = 36.dp, opacity = buttonOpacity)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Abre el chat del juego (T) y, de paso, el teclado del sistema.
                Box(
                    modifier = Modifier
                        .size(width = 92.dp, height = 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = buttonOpacity.coerceIn(0.35f, 0.95f)))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                GameWindow.sendKey(GameWindow.KEY_T, isDown = true)
                                GameWindow.sendKey(GameWindow.KEY_T, isDown = false)
                                showKeyboard = true
                            })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CHAT ⌨",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Botón minimalista: SOLO la tuerca de ajustes limpia
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2B2B2B).copy(alpha = 0.9f))
                        .border(1.dp, BedrockBorderSoft, RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { showMenuModal = true })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PixelIcon(art = IconGear, size = 18.dp, tint = BedrockGold)
                }

                // Botón de salir de la partida
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFB71C1C).copy(alpha = 0.85f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onExitGame() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SALIR",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // --- CONTROLES VIRTUALES: cada botón es independiente y editable ---
        if (useJoystick) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset { IntOffset(dpadOffset.x.toInt(), dpadOffset.y.toInt()) }
                    .then(
                        if (isEditingLayout) {
                            Modifier
                                .border(2.dp, BedrockGold, RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        dpadOffset += dragAmount
                                    }
                                }
                        } else Modifier
                    )
            ) {
                VirtualJoystick(
                    modifier = Modifier.padding(start = 28.dp, bottom = 28.dp),
                    scale = controlsScale,
                    opacity = buttonOpacity,
                    // Al acomodar el mando el joystick no debe mover al personaje.
                    editing = isEditingLayout,
                    deadzoneScale = settings.joyDeadzone,
                    sprintEnabled = settings.sprintOnFullPush
                )
            }
        }

        VirtualButtonPad(
            buttons = if (useJoystick) buttons.filter { it.group != "move" } else buttons,
            editing = isEditingLayout,
            scale = controlsScale,
            globalOpacity = buttonOpacity,
            // Recibe el desplazamiento y suma sobre la posición actual del
            // estado (no sobre la copia capturada por el gesto), así el botón
            // sigue al dedo de verdad al arrastrarlo.
            onMove = { b, dx, dy ->
                buttons = buttons.map {
                    if (it.id == b.id) {
                        it.copy(
                            x = (it.x + dx).coerceIn(0.02f, 0.98f),
                            y = (it.y + dy).coerceIn(0.03f, 0.97f)
                        )
                    } else {
                        it
                    }
                }
            },
            onSelect = { editingButton = it }
        )

        // --- BANNER SUPERIOR CUANDO SE ESTÁ EN MODO EDICIÓN DE CONTROLES (ACOMODADOR DE UIS) ---
        if (isEditingLayout) {
            // Dos filas: el título y, debajo, los botones. En una sola fila el
            // banner se salía de la pantalla y el botón de cerrar quedaba fuera.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BedrockPanel)
                    .border(2.dp, BedrockGold, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelText(
                        text = "MODO EDICIÓN · ARRASTRA PARA MOVER · TOCA PARA EDITAR",
                        color = BedrockGold,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    McButton(
                        text = "✕ CERRAR",
                        onClick = {
                            isEditingLayout = false
                            editingButton = null
                            onUpdateSettings(
                                settings.copy(
                                    virtualButtons = buttons,
                                    dpadOffsetX = dpadOffset.x,
                                    dpadOffsetY = dpadOffset.y
                                )
                            )
                        },
                        tone = McButtonTone.Danger,
                        height = 30.dp
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                McButton(
                    text = "AÑADIR",
                    onClick = { showAddSheet = true },
                    tone = McButtonTone.Primary,
                    height = 32.dp
                )
                McButton(
                    text = "PRESETS",
                    onClick = { showPresets = true },
                    tone = McButtonTone.Ghost,
                    height = 32.dp
                )
                McButton(
                    text = "REINICIAR",
                    onClick = {
                        dpadOffset = Offset.Zero
                        buttons = VirtualButton.defaults()
                    },
                    tone = McButtonTone.Ghost,
                    height = 32.dp
                )
                McButton(
                    text = "GUARDAR",
                    onClick = {
                        isEditingLayout = false
                        onUpdateSettings(
                            settings.copy(
                                virtualButtons = buttons,
                                dpadOffsetX = dpadOffset.x,
                                dpadOffsetY = dpadOffset.y
                            )
                        )
                    },
                    tone = McButtonTone.Success,
                    height = 32.dp
                )
                }
            }
        }

        // --- MODAL DE MENÚ EN JUEGO (ESTILO POJAV / BEDROCK) ---
        AnimatedVisibility(
            visible = showMenuModal,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .pointerInput(Unit) {
                        detectTapGestures { /* Bloquear clics hacia el juego */ }
                    },
                contentAlignment = Alignment.Center
            ) {
                when (activeModal) {
                    null -> {
                        // --- MODAL PRINCIPAL COMPACTO (3 BOTONES: IR A INICIO, AJUSTES, GMUSIC) ---
                        Column(
                            modifier = Modifier
                                .width(310.dp)
                                .clip(RectangleShape)
                                .background(BedrockPanel)
                                .border(2.dp, BedrockBorder)
                        ) {
                            // Cabecera compacta
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BedrockBar)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PixelIcon(art = IconGear, size = 16.dp, tint = BedrockGold)
                                        PixelText(
                                            text = "MENÚ DE PAUSA",
                                            color = BedrockText,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    PixelText(
                                        text = "Minecraft ${settings.selectedVersion} · ${buttons.size} botones",
                                        color = BedrockTextDim,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF333333))
                                        .border(1.dp, BedrockBorder)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { showMenuModal = false })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PixelIcon(art = IconClose, size = 12.dp, tint = BedrockText)
                                }
                            }

                            // Los 3 botones optimizados
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Ir a Inicio
                                McButton(
                                    text = "IR A INICIO",
                                    onClick = {
                                        showMenuModal = false
                                        onNavigate(Screen.HOME)
                                    },
                                    icon = IconHome,
                                    tone = McButtonTone.Primary,
                                    height = 40.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 2. Ajustes (Abre el modal integrado con controles y opciones)
                                McButton(
                                    text = "AJUSTES",
                                    onClick = { activeModal = "SETTINGS" },
                                    icon = IconGear,
                                    tone = McButtonTone.Success,
                                    height = 40.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 3. GMusic (Abre el reproductor flotante en partida)
                                McButton(
                                    text = "GMUSIC",
                                    onClick = { activeModal = "MUSIC" },
                                    icon = IconNote,
                                    tone = McButtonTone.Primary,
                                    height = 40.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 4. Editar los controles sin salir de la partida
                                McButton(
                                    text = "EDITAR CONTROLES",
                                    onClick = {
                                        showMenuModal = false
                                        isEditingLayout = true
                                    },
                                    icon = IconList,
                                    tone = McButtonTone.Primary,
                                    height = 40.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(4.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Cerrar / Volver a la partida
                                    McButton(
                                        text = "VOLVER",
                                        onClick = { showMenuModal = false },
                                        tone = McButtonTone.Ghost,
                                        height = 36.dp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    McButton(
                                        text = "CERRAR JUEGO",
                                        onClick = {
                                            showMenuModal = false
                                            onExitGame()
                                        },
                                        icon = IconClose,
                                        tone = McButtonTone.Danger,
                                        height = 36.dp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    "SETTINGS" -> {
                        // --- MODAL DEDICADO DE AJUSTES EN PARTIDA (CONTROLES, SENSIBILIDAD, ACCIONES) ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .fillMaxHeight(0.88f)
                                .clip(RectangleShape)
                                .background(BedrockPanel)
                                .border(2.dp, BedrockBorder)
                        ) {
                            // Cabecera del Modal de Ajustes
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BedrockBar)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelIcon(art = IconGear, size = 18.dp, tint = BedrockGold)
                                    PixelText(
                                        text = "AJUSTES & CONTROLES VIRTUALES",
                                        color = BedrockText,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                // Botón de volver al menú de pausa
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF333333))
                                        .border(1.dp, BedrockBorder)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = {
                                                activeModal = null
                                                onUpdateSettings(
                                                    settings.copy(
                                                        useJoystick = useJoystick,
                                                        controlsOpacity = buttonOpacity,
                                                        controlsScale = controlsScale,
                                                        mouseSensitivity = mouseSensitivity,
                                                        joyDeadzone = joyDeadzone,
                                                        sprintOnFullPush = sprintPush,
                                                        invertCameraY = invertY
                                                    )
                                                )
                                            })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PixelIcon(art = IconClose, size = 14.dp, tint = BedrockText)
                                }
                            }

                            // Pestañas (Controles vs Opciones)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BedrockVoid)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                McButton(
                                    text = "MAPEO Y CONTROLES",
                                    onClick = { currentTab = 0 },
                                    tone = if (currentTab == 0) McButtonTone.Success else McButtonTone.Ghost,
                                    height = 36.dp,
                                    modifier = Modifier.weight(1f)
                                )
                                McButton(
                                    text = "SENSIBILIDAD & VISUAL",
                                    onClick = { currentTab = 1 },
                                    tone = if (currentTab == 1) McButtonTone.Success else McButtonTone.Ghost,
                                    height = 36.dp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Contenido del Modal
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (currentTab == 0) {
                                    // --- MAPEO DE CONTROLES VIRTUALES ---
                                    SectionLabel("Tipo de mando")
                                    BedrockOptionRow(
                                        label = "Joystick Analógico",
                                        description = "Cambia entre Joystick libre 360° y cruceta clásica D-Pad",
                                        onClick = { useJoystick = !useJoystick }
                                    ) {
                                        BedrockToggle(checked = useJoystick, onCheckedChange = { useJoystick = it })
                                    }

                                    PixelDivider()
                                    SectionLabel("Controles (cada botón por separado)")

                                    BedrockOptionRow(
                                        label = "Modo Acomodador",
                                        description = "Arrastra cualquier botón para moverlo y tócalo para cambiar su tecla, tamaño o color",
                                        onClick = {
                                            activeModal = null
                                            showMenuModal = false
                                            isEditingLayout = true
                                        }
                                    ) {
                                        McButton(
                                            text = "ABRIR",
                                            onClick = {
                                                activeModal = null
                                                showMenuModal = false
                                                isEditingLayout = true
                                            },
                                            tone = McButtonTone.Success,
                                            height = 34.dp
                                        )
                                    }

                                    buttons.forEach { b ->
                                        BedrockOptionRow(
                                            label = b.label,
                                            description = if (useJoystick && b.group == "move") {
                                                "Oculto mientras uses el joystick"
                                            } else {
                                                "Tecla ${b.code} · ${b.size.toInt()} dp · ${(b.opacity * 100).toInt()}%"
                                            },
                                            onClick = { editingButton = b }
                                        ) {
                                            McButton(
                                                "EDITAR",
                                                onClick = { editingButton = b },
                                                tone = McButtonTone.Primary,
                                                height = 32.dp
                                            )
                                        }
                                    }

                                    McButton(
                                        text = "AÑADIR BOTÓN",
                                        onClick = { showAddSheet = true },
                                        tone = McButtonTone.Success,
                                        height = 38.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    PixelDivider()
                                    SectionLabel("Tamaño y Opacidad")

                                    // Escala Controles
                                    Text(text = "Escala de controles: ${"%.1f".format(controlsScale)}x", color = BedrockTextDim, fontSize = 12.sp)
                                    BedrockSlider(
                                        value = controlsScale,
                                        onValueChange = { controlsScale = it },
                                        onValueChangeFinished = {
                                            onUpdateSettings(settings.copy(controlsScale = controlsScale))
                                        },
                                        valueRange = 0.7f..1.5f
                                    )

                                    // Opacidad de botones
                                    Text(text = "Opacidad de botones: ${(buttonOpacity * 100).toInt()}%", color = BedrockTextDim, fontSize = 12.sp)
                                    BedrockSlider(
                                        value = buttonOpacity,
                                        onValueChange = { buttonOpacity = it },
                                        onValueChangeFinished = {
                                            onUpdateSettings(settings.copy(controlsOpacity = buttonOpacity))
                                        },
                                        valueRange = 0.2f..1.0f
                                    )

                                    PixelDivider()
                                    SectionLabel("Resumen")

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        KeyMapItem(title = "Botones activos", bind = "${buttons.size}", modifier = Modifier.weight(1f))
                                        KeyMapItem(title = "Movimiento", bind = if (useJoystick) "Joystick" else "Botones W A S D", modifier = Modifier.weight(1f))
                                    }
                                } else {
                                    // --- SENSIBILIDAD & VISUAL ---
                                    SectionLabel("Sensibilidad")

                                    Text(text = "Velocidad de giro: ${"%.2f".format(mouseSensitivity)}x", color = BedrockTextDim, fontSize = 12.sp)
                                    BedrockSlider(
                                        value = mouseSensitivity,
                                        onValueChange = { mouseSensitivity = it },
                                        onValueChangeFinished = {
                                            onUpdateSettings(settings.copy(mouseSensitivity = mouseSensitivity))
                                        },
                                        valueRange = 0.3f..3.0f
                                    )

                                    PixelDivider()
                                    SectionLabel("Joystick")

                                    Text(
                                        text = "Zona muerta: ${"%.2f".format(joyDeadzone)}x",
                                        color = BedrockTextDim,
                                        fontSize = 12.sp
                                    )
                                    BedrockSlider(
                                        value = joyDeadzone,
                                        onValueChange = { joyDeadzone = it },
                                        onValueChangeFinished = {
                                            onUpdateSettings(settings.copy(joyDeadzone = joyDeadzone))
                                        },
                                        valueRange = 0.4f..1.6f
                                    )

                                    BedrockOptionRow(
                                        label = "Correr al empujar a tope",
                                        description = "Mantiene Ctrl al llevar el joystick al máximo hacia adelante",
                                        onClick = {
                                            sprintPush = !sprintPush
                                            onUpdateSettings(settings.copy(sprintOnFullPush = sprintPush))
                                        }
                                    ) {
                                        BedrockToggle(checked = sprintPush, onCheckedChange = { sprintPush = it })
                                    }

                                    BedrockOptionRow(
                                        label = "Invertir cámara vertical",
                                        description = "Arriba mira abajo, como en los juegos de consola",
                                        onClick = {
                                            invertY = !invertY
                                            onUpdateSettings(settings.copy(invertCameraY = invertY))
                                        }
                                    ) {
                                        BedrockToggle(checked = invertY, onCheckedChange = { invertY = it })
                                    }

                                    PixelDivider()
                                    SectionLabel("Acciones rápidas en partida")

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        McButton(
                                            text = "ENVIAR F3 (DEBUG)",
                                            onClick = { GameWindow.sendKey(GameWindow.KEY_F3, isDown = true); GameWindow.sendKey(GameWindow.KEY_F3, isDown = false) },
                                            tone = McButtonTone.Primary,
                                            height = 38.dp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        McButton(
                                            text = "ENVIAR F5 (CÁMARA)",
                                            onClick = { GameWindow.sendKey(GameWindow.KEY_F5, isDown = true); GameWindow.sendKey(GameWindow.KEY_F5, isDown = false) },
                                            tone = McButtonTone.Primary,
                                            height = 38.dp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    McButton(
                                        text = "ABRIR CHAT / CONSOLA (T)",
                                        onClick = {
                                            GameWindow.sendKey(GameWindow.KEY_T, isDown = true)
                                            GameWindow.sendKey(GameWindow.KEY_T, isDown = false)
                                            showKeyboard = true
                                        },
                                        tone = McButtonTone.Primary,
                                        height = 38.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(Modifier.weight(1f, fill = false))

                                McButton(
                                    text = "VOLVER",
                                    onClick = {
                                        activeModal = null
                                        onUpdateSettings(
                                            settings.copy(
                                                useJoystick = useJoystick,
                                                controlsOpacity = buttonOpacity,
                                                controlsScale = controlsScale,
                                                mouseSensitivity = mouseSensitivity,
                                                joyDeadzone = joyDeadzone,
                                                sprintOnFullPush = sprintPush,
                                                invertCameraY = invertY
                                            )
                                        )
                                    },
                                    tone = McButtonTone.Success,
                                    height = 42.dp,
                                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                )
                            }
                        }
                    }

                    "MUSIC" -> {
                        // --- MODAL DEDICADO DE GMUSIC DENTRO DEL JUEGO ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .fillMaxHeight(0.88f)
                                .clip(RectangleShape)
                                .background(BedrockPanel)
                                .border(2.dp, BedrockBorder)
                        ) {
                            // Cabecera de GMusic
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BedrockBar)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PixelIcon(art = IconNote, size = 18.dp, tint = BedrockGold)
                                    PixelText(
                                        text = "GMUSIC · REPRODUCTOR EN JUEGO",
                                        color = BedrockText,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF333333))
                                        .border(1.dp, BedrockBorder)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { activeModal = null })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    PixelIcon(art = IconClose, size = 14.dp, tint = BedrockText)
                                }
                            }

                            if (musicVm != null) {
                                val musicState by musicVm.state.collectAsState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Barra de búsqueda rápida
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        McTextField(
                                            value = musicState.query,
                                            onValueChange = musicVm::onQuery,
                                            placeholder = "Buscar canción o artista...",
                                            modifier = Modifier.weight(1f)
                                        )
                                        // Solo el icono: así nunca se come el cuadro de búsqueda.
                                        McButton(
                                            text = "",
                                            onClick = musicVm::search,
                                            icon = IconSearch,
                                            tone = McButtonTone.Success,
                                            height = 40.dp,
                                            modifier = Modifier.width(48.dp)
                                        )
                                    }

                                    // Pista actualmente sonando
                                    val currentTrack = musicState.current
                                    if (currentTrack != null) {
                                        MusicMiniPlayer(
                                            track = currentTrack,
                                            paused = musicState.paused,
                                            buffering = musicState.buffering,
                                            onOpen = {},
                                            onTogglePause = { if (musicState.paused) musicVm.resume() else musicVm.pause() },
                                            onStop = musicVm::stop,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    PixelDivider()

                                    // Lista de resultados
                                    if (musicState.loading) {
                                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                            PixelText(text = "Buscando en YouTube...", color = BedrockGold)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxWidth().weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(musicState.results, key = { _, t -> t.id }) { index, track ->
                                                val isPlaying = musicState.current?.id == track.id && musicState.audioActive
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (isPlaying) BedrockGreen.copy(alpha = 0.2f) else BedrockRow)
                                                        .border(1.dp, if (isPlaying) BedrockGreen else BedrockBorderSoft)
                                                        .clickable { musicVm.play(track) }
                                                        .padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    PixelText(
                                                        text = "${index + 1}",
                                                        color = if (isPlaying) BedrockGreen else BedrockTextDim,
                                                        modifier = Modifier.width(18.dp)
                                                    )
                                                    RemoteIcon(url = track.thumbnail, size = 34.dp, fallback = IconNote)
                                                    Column(Modifier.weight(1f)) {
                                                        PixelText(text = track.title, color = BedrockText, maxLines = 1)
                                                        PixelText(text = "${track.uploader} · ${track.durationLabel}", color = BedrockTextDim, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                                    }
                                                    McButton(
                                                        text = "",
                                                        onClick = { musicVm.play(track) },
                                                        icon = IconPlay,
                                                        tone = if (isPlaying) McButtonTone.Success else McButtonTone.Primary,
                                                        height = 32.dp,
                                                        modifier = Modifier.width(44.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    McButton(
                                        text = "VOLVER",
                                        onClick = { activeModal = null },
                                        tone = McButtonTone.Ghost,
                                        height = 38.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PixelText(text = "GMusic no está listo.", color = BedrockTextDim)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- EDITOR DE UN BOTÓN CONCRETO ---
        editingButton?.let { editing ->
            VirtualButtonEditor(
                button = editing,
                onUpdate = { updated ->
                    buttons = buttons.map { if (it.id == updated.id) updated else it }
                    editingButton = updated
                },
                onDuplicate = {
                    val copy = editing.copy(
                        id = "btn_" + System.currentTimeMillis(),
                        label = editing.label.take(4) + "+",
                        x = (editing.x + 0.06f).coerceAtMost(0.95f),
                        y = (editing.y + 0.06f).coerceAtMost(0.95f)
                    )
                    buttons = buttons + copy
                    editingButton = copy
                },
                onDelete = {
                    buttons = buttons.filter { it.id != editing.id }
                    editingButton = null
                    onUpdateSettings(settings.copy(virtualButtons = buttons))
                },
                onClose = {
                    editingButton = null
                    onUpdateSettings(settings.copy(virtualButtons = buttons))
                }
            )
        }

        // --- AÑADIR UN BOTÓN NUEVO ---
        if (showAddSheet) {
            VirtualButtonAddSheet(
                onAdd = { kind, code, label ->
                    val nuevo = VirtualButton(
                        id = "btn_" + System.currentTimeMillis(),
                        label = label.take(5),
                        kind = kind,
                        code = code,
                        x = 0.5f,
                        y = 0.5f
                    )
                    buttons = buttons + nuevo
                    showAddSheet = false
                    editingButton = nuevo
                },
                onClose = {
                    showAddSheet = false
                    onUpdateSettings(settings.copy(virtualButtons = buttons))
                }
            )
        }

        // --- PRESETS DE CONTROLES ---
        if (showPresets) {
            ControlPresetsSheet(
                buttons = buttons,
                onLoad = { loaded -> buttons = loaded },
                onClose = {
                    showPresets = false
                    onUpdateSettings(settings.copy(virtualButtons = buttons))
                }
            )
        }

        // --- TECLADO DEL SISTEMA PARA EL CHAT ---
        if (showKeyboard) {
            GameKeyboardSheet(onClose = { showKeyboard = false })
        }

    }
}

/**
 * Teclado para escribir en el chat del juego.
 *
 * Escribes el mensaje completo aquí y al tocar ENVIAR se manda letra por letra
 * al chat de Minecraft (`sendChar`) y luego Enter. Antes se mandaba cada letra
 * al instante vaciando el campo en cada pulsación, y eso rompía el teclado del
 * sistema: el campo perdía el texto a medio componer y muchas letras no llegaban
 * al juego.
 */
@Composable
private fun GameKeyboardSheet(onClose: () -> Unit) {
    var buffer by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("Escribe y sale en el chat del juego") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            // Bloquea los toques hacia el juego mientras se escribe.
            .pointerInput(Unit) { detectTapGestures { } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(4.dp))
                .background(BedrockPanel)
                .border(2.dp, BedrockBorder)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelText(
                    text = "TECLADO · CHAT",
                    color = BedrockText,
                    style = MaterialTheme.typography.titleMedium
                )
                McButton("CERRAR", onClick = onClose, tone = McButtonTone.Ghost, height = 30.dp)
            }

            // Campo real (BasicTextField) para poder pedirle el foco y que el
            // teclado del sistema aparezca de verdad.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(BedrockVoid)
                    .border(1.dp, BedrockBorder)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = buffer,
                    onValueChange = { nuevo -> buffer = nuevo.replace("\n", "").take(120) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        enviarMensaje(buffer)
                        buffer = ""
                        hint = "Enviado al juego"
                        onClose()
                    }),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = BedrockText,
                        fontFamily = Minecraftia
                    ),
                    cursorBrush = SolidColor(BedrockGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                if (buffer.isEmpty()) {
                    PixelText(
                        text = hint,
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                McButton(
                    "ENVIAR",
                    onClick = {
                        enviarMensaje(buffer)
                        buffer = ""
                        hint = "Enviado al juego"
                        onClose()
                    },
                    tone = McButtonTone.Success,
                    height = 40.dp,
                    modifier = Modifier.weight(1f)
                )
                McButton(
                    "BORRAR",
                    onClick = {
                        buffer = ""
                        hint = "Mensaje borrado"
                    },
                    tone = McButtonTone.Primary,
                    height = 40.dp,
                    modifier = Modifier.weight(1f)
                )
                McButton(
                    "ESC",
                    onClick = {
                        GameWindow.sendKey(GameWindow.KEY_ESCAPE, isDown = true)
                        GameWindow.sendKey(GameWindow.KEY_ESCAPE, isDown = false)
                        onClose()
                    },
                    tone = McButtonTone.Ghost,
                    height = 40.dp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private const val KEY_ENTER = 257

/**
 * Manda el mensaje al chat del juego letra por letra y luego pulsa Enter.
 * El orden importa: primero los caracteres (así el chat de Minecraft los va
 * metiendo en su caja de texto) y al final Enter para enviarlo.
 */
private fun enviarMensaje(texto: String) {
    if (texto.isEmpty()) return
    texto.forEach { ch -> GameWindow.sendChar(ch) }
    GameWindow.sendKey(KEY_ENTER, isDown = true)
    GameWindow.sendKey(KEY_ENTER, isDown = false)
}

/**
 * Joystick analógico virtual que detecta movimiento 360° y envía W, A, S, D a Minecraft.
 */
@Composable
private fun VirtualJoystick(
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    opacity: Float = 0.6f,
    editing: Boolean = false,
    deadzoneScale: Float = 1.0f,
    sprintEnabled: Boolean = true
) {
    val density = LocalDensity.current
    val baseSize = (140 * scale).dp
    val thumbSize = (56 * scale).dp
    // Radio útil del pomo: (140 - 56) / 2 dp, convertido a píxeles de verdad.
    // Antes se usaba el número en dp como si fueran px, así que en pantallas de
    // densidad alta el pomo se quedaba clavado cerca del centro y no se podía
    // llegar a correr.
    val maxRadiusPx = with(density) { (42 * scale).dp.toPx() }
    // Zona muerta circular: 7 dp (antes 12 dp por eje, que exigía más de 17 dp
    // en diagonal y por eso se sentía duro para arrancar a andar).
    val deadzonePx = with(density) { (7 * scale * deadzoneScale).dp.toPx() }
    // Para dejar de andar basta con volver al 55 % del umbral: así no se corta
    // el paso al hacer micro-ajustes de dirección.
    val releasePx = deadzonePx * 0.55f
    // Cuánto tiene que pesar un eje para pulsar su tecla, sobre el vector
    // unitario: 0.38 = unos 22° de margen alrededor de cada diagonal, que es lo
    // que se siente natural. Al soltar se pide menos (histéresis) para que no
    // titilen las teclas al pasar rozando una dirección.
    val pressAxis = 0.38f
    val releaseAxis = 0.30f

    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var fullPush by remember { mutableStateOf(false) }

    // Rastrear teclas actualmente pulsadas por el joystick para no enviar eventos duplicados
    var isWDown by remember { mutableStateOf(false) }
    var isSDown by remember { mutableStateOf(false) }
    var isADown by remember { mutableStateOf(false) }
    var isDDown by remember { mutableStateOf(false) }
    var isSprintDown by remember { mutableStateOf(false) }

    fun releaseAll() {
        if (isWDown) { isWDown = false; GameWindow.sendKey(GameWindow.KEY_W, isDown = false) }
        if (isSDown) { isSDown = false; GameWindow.sendKey(GameWindow.KEY_S, isDown = false) }
        if (isADown) { isADown = false; GameWindow.sendKey(GameWindow.KEY_A, isDown = false) }
        if (isDDown) { isDDown = false; GameWindow.sendKey(GameWindow.KEY_D, isDown = false) }
        if (isSprintDown) { isSprintDown = false; GameWindow.sendKey(GameWindow.KEY_LEFT_CONTROL, isDown = false) }
    }

    var activo by remember { mutableStateOf(false) }

    // Un toque corto de vibración al cambiar de dirección y uno más largo al
    // enganchar el sprint: sin mirar la pantalla ya sabes si el mando te está
    // leyendo y cuándo vas a correr.
    val vista = LocalView.current
    fun tick(fuerte: Boolean = false) {
        runCatching {
            vista.performHapticFeedback(
                if (fuerte) android.view.HapticFeedbackConstants.LONG_PRESS
                else android.view.HapticFeedbackConstants.KEYBOARD_TAP
            )
        }
    }

    fun updateKeys(offset: Offset) {
        // Acomodando el mando el joystick solo se mueve de sitio.
        if (editing) return
        val distance = sqrt(offset.x * offset.x + offset.y * offset.y)

        // Activación por distancia total, no por eje.
        if (!activo && distance > deadzonePx) activo = true
        else if (activo && distance < releasePx) activo = false

        // Dirección del vector normalizado: empujar en diagonal activa dos
        // teclas sin pelear con los umbrales.
        val ux = if (distance > 0.001f) offset.x / distance else 0f
        val uy = if (distance > 0.001f) offset.y / distance else 0f
        fun eje(componente: Float, estaba: Boolean): Boolean {
            val umbral = if (estaba) releaseAxis else pressAxis
            return activo && componente > umbral
        }

        val needW = eje(-uy, isWDown)
        val needS = eje(uy, isSDown)
        val needA = eje(-ux, isADown)
        val needD = eje(ux, isDDown)

        // Solo se vibra si el dedo sigue apoyado: al levantar se sueltan todas
        // las teclas de golpe y sonarían cuatro tics seguidos.
        val cambiar = distance > 0f
        var huboCambio = false
        if (needW != isWDown) {
            isWDown = needW
            GameWindow.sendKey(GameWindow.KEY_W, isDown = needW)
            huboCambio = true
        }
        if (needS != isSDown) {
            isSDown = needS
            GameWindow.sendKey(GameWindow.KEY_S, isDown = needS)
            huboCambio = true
        }
        if (needA != isADown) {
            isADown = needA
            GameWindow.sendKey(GameWindow.KEY_A, isDown = needA)
            huboCambio = true
        }
        if (needD != isDDown) {
            isDDown = needD
            GameWindow.sendKey(GameWindow.KEY_D, isDown = needD)
            huboCambio = true
        }

        // Correr: empujar el pomo hacia adelante mantiene Ctrl. Se engancha al
        // 90 % del recorrido y se suelta por debajo del 78 %: así al aflojar un
        // poco para girar no se corta el sprint (antes titilaba y el personaje
        // arrancaba y paraba como a tirones).
        val umbralSprint = if (isSprintDown) maxRadiusPx * 0.78f else maxRadiusPx * 0.9f
        val needSprint = sprintEnabled && needW && distance >= umbralSprint
        if (needSprint != isSprintDown) {
            isSprintDown = needSprint
            GameWindow.sendKey(GameWindow.KEY_LEFT_CONTROL, isDown = needSprint)
            if (needSprint) {
                tick(fuerte = true)
                return
            }
        }
        if (huboCambio && cambiar) tick()
    }

    // Si se abre el editor o el overlay se va, hay que soltar lo que quedó pulsado
    // o el personaje sigue caminando solo.
    LaunchedEffect(editing) {
        if (editing) {
            thumbOffset = Offset.Zero
            fullPush = false
            activo = false
            releaseAll()
        }
    }
    DisposableEffect(Unit) { onDispose { releaseAll() } }

    Box(
        modifier = modifier
            .size(baseSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = opacity * 0.7f))
            .border(2.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            // Cruz de guía para saber hacia dónde estás empujando.
            .drawBehind {
                val c = Offset(size.width / 2f, size.height / 2f)
                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(c.x - maxRadiusPx, c.y),
                    Offset(c.x + maxRadiusPx, c.y)
                )
                drawLine(
                    Color.White.copy(alpha = 0.12f),
                    Offset(c.x, c.y - maxRadiusPx),
                    Offset(c.x, c.y + maxRadiusPx)
                )
                // Marca de la zona muerta: pequeña, solo como referencia.
                drawCircle(
                    Color.White.copy(alpha = 0.16f),
                    radius = deadzonePx,
                    center = c,
                    style = Stroke(width = 2f)
                )
            }
            // Gesto propio (no detectDragGestures) porque hace falta
            // **consumir el dedo desde el primer instante**: así el gesto de
            // cámara del overlay sabe que ese dedo es del joystick y no lo
            // confunde con un segundo dedo de scroll.
            .pointerInput(editing, maxRadiusPx, deadzonePx, sprintEnabled) {
                if (editing) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)

                    fun aplicar(offset: Offset) {
                        val dist = sqrt(offset.x * offset.x + offset.y * offset.y)
                        val clamped = if (dist > maxRadiusPx) {
                            Offset(offset.x / dist * maxRadiusPx, offset.y / dist * maxRadiusPx)
                        } else offset
                        thumbOffset = clamped
                        updateKeys(clamped)
                        fullPush = isSprintDown
                    }

                    aplicar(down.position - center)

                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.position - change.previousPosition
                        if (delta != Offset.Zero) {
                            aplicar(thumbOffset + delta)
                        }
                        // El joystick se queda SOLO con su dedo: si consumiera
                        // todos, el segundo dedo (el de la cámara) llegaría
                        // consumido al gesto de arrastre y no se podría girar la
                        // vista mientras se anda con el mando.
                        change.consume()
                    }

                    thumbOffset = Offset.Zero
                    fullPush = false
                    updateKeys(Offset.Zero)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Base: se ilumina a tope cuando vas a correr.
        Canvas(Modifier.fillMaxSize()) {
            if (fullPush) {
                drawCircle(
                    BedrockGold.copy(alpha = 0.55f),
                    radius = size.minDimension / 2f - 2f,
                    style = Stroke(width = 3f)
                )
            }
        }
        // Pomo del joystick
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.x.toInt(), thumbOffset.y.toInt()) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = (opacity + 0.2f).coerceAtMost(0.9f)))
                .border(2.dp, BedrockGold.copy(alpha = 0.8f), CircleShape)
        )
    }
}

@Composable
private fun KeyMapItem(
    title: String,
    bind: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(BedrockRow)
            .border(1.dp, BedrockBorderSoft)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = BedrockTextDim, fontSize = 10.sp)
            Text(text = bind, color = BedrockHighlight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GamepadKeyButton(
    text: String,
    key: Int,
    modifier: Modifier = Modifier,
    width: Dp = 50.dp,
    height: Dp = 50.dp,
    accentColor: Color? = null,
    opacity: Float = 0.6f
) {
    var isPressed by remember { mutableStateOf(false) }

    val bg = when {
        isPressed -> (accentColor ?: Color.White).copy(alpha = (opacity + 0.25f).coerceAtMost(1f))
        accentColor != null -> accentColor.copy(alpha = opacity)
        else -> Color.Black.copy(alpha = opacity)
    }

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .pointerInput(key) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        GameWindow.sendKey(key, isDown = true)
                        tryAwaitRelease()
                        isPressed = false
                        GameWindow.sendKey(key, isDown = false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun GamepadMouseButton(
    text: String,
    button: Int,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    color: Color = Color.Black,
    opacity: Float = 0.6f
) {
    var isPressed by remember { mutableStateOf(false) }

    val bg = if (isPressed) color.copy(alpha = (opacity + 0.3f).coerceAtMost(1f)) else color.copy(alpha = opacity)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .pointerInput(button) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        GameWindow.sendMouseButton(button, isDown = true)
                        tryAwaitRelease()
                        isPressed = false
                        GameWindow.sendMouseButton(button, isDown = false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
