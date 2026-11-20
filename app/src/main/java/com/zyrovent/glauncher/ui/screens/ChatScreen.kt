package com.zyrovent.glauncher.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.data.ChatMessage
import com.zyrovent.glauncher.data.ChatUser
import com.zyrovent.glauncher.data.Gif
import com.zyrovent.glauncher.data.StreakState
import com.zyrovent.glauncher.data.StreakStore
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.FlameMascot
import com.zyrovent.glauncher.ui.components.FlameMood
import com.zyrovent.glauncher.ui.components.GifThumbnail
import com.zyrovent.glauncher.ui.components.IconChat
import com.zyrovent.glauncher.ui.components.IconCheck
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconGif
import com.zyrovent.glauncher.ui.components.IconHead
import com.zyrovent.glauncher.ui.components.IconReply
import com.zyrovent.glauncher.ui.components.IconSearch
import com.zyrovent.glauncher.ui.components.IconSend
import com.zyrovent.glauncher.ui.components.IconStar
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McCompactField
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.RemoteGif
import com.zyrovent.glauncher.ui.components.RemoteIcon
import com.zyrovent.glauncher.ui.components.SectionLabel
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.DangerRed
import com.zyrovent.glauncher.ui.theme.Gold
import kotlinx.coroutines.delay

/**
 * GChat: amigos, solicitudes, conversación en vivo y GIFs de Giphy. Mismo
 * backend que la web (glauncher-api) con refresco de historial cada 5 s.
 *
 * Los mensajes se pintan como en WhatsApp: lo tuyo a la derecha y lo del otro
 * a la izquierda, con su avatar al lado.
 */
@Composable
fun ChatScreen(
    sessionViewModel: SessionViewModel = viewModel(),
    vm: ChatViewModel = viewModel(),
    onClose: (() -> Unit)? = null,
    onLogin: () -> Unit = {}
) {
    val session by sessionViewModel.session.collectAsState()
    val state by vm.state.collectAsState()
    val active = state.active
    // Id propio: el del perfil o el aprendido del último mensaje enviado.
    val myId = state.myId ?: session?.userId

    // Racha de chat: cuenta los días en los que se escribe al menos un mensaje.
    val context = LocalContext.current
    val streakStore = remember { StreakStore(context.applicationContext) }
    val streak by streakStore.state.collectAsState()
    var celebrating by remember { mutableStateOf(false) }

    LaunchedEffect(celebrating) {
        if (celebrating) {
            delay(2_200)
            celebrating = false
        }
    }

    fun signDay() {
        if (streakStore.claimToday()) celebrating = true
    }

    // Sin sesión no hay chat: se muestra la elección de tipo de cuenta.
    if (session == null) {
        GchatWelcome(onLogin = onLogin, onClose = onClose)
        return
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- Lista de conversaciones ----
        Column(Modifier.fillMaxHeight().weight(0.38f)) {
            BedrockWindow(
                title = "GChat",
                subtitle = "Amigos (${state.bundle.friends.size})",
                icon = IconChat,
                modifier = Modifier.fillMaxWidth().weight(1f),
                fillHeight = true,
                contentModifier = Modifier.fillMaxWidth(),
                trailing = {
                    if (onClose != null) {
                        McButton(
                            text = "",
                            onClick = onClose,
                            icon = IconClose,
                            tone = McButtonTone.Ghost,
                            height = 30.dp,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            ) {
                // Buscador simplificado
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McCompactField(
                        value = state.query,
                        onValueChange = vm::onQuery,
                        placeholder = "Buscar jugador…",
                        modifier = Modifier.weight(1f),
                        height = 36.dp,
                        fontSize = 12
                    )
                    McButton(
                        text = "BUSCAR",
                        onClick = vm::search,
                        enabled = !state.searching && state.query.isNotBlank(),
                        icon = IconSearch,
                        tone = McButtonTone.Ghost,
                        height = 36.dp,
                        modifier = Modifier.width(84.dp)
                    )
                }

                if (state.searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel("Resultados")
                    state.searchResults.take(2).forEach { user ->
                        ContactRow(
                            user = user,
                            onClick = { vm.addFriend(user.username) },
                            tag = "AÑADIR"
                        )
                    }
                    PixelDivider()
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    if (state.bundle.friends.isEmpty()) {
                        item {
                            PixelText(
                                text = "Sin amigos aún. Busca un usuario arriba.",
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                    }
                    itemsIndexed(state.bundle.friends) { _, friend ->
                        ContactRow(
                            user = friend,
                            selected = active?.id == friend.id,
                            onClick = { vm.openChat(friend) },
                            tag = "CHAT"
                        )
                    }
                    if (state.bundle.pending.isNotEmpty()) {
                        item { SectionLabel("Solicitudes (${state.bundle.pending.size})") }
                        itemsIndexed(state.bundle.pending) { _, user ->
                            PendingRow(
                                user = user,
                                onAccept = { vm.acceptFriend(user.id) },
                                onReject = { vm.removeFriend(user.id) }
                            )
                        }
                    }
                }
            }
        }

        // ---- Conversación ----
        Column(Modifier.fillMaxHeight().weight(0.62f)) {
            BedrockWindow(
                title = active?.username ?: "Chat",
                subtitle = if (active == null) "Selecciona a un amigo" else "En vivo",
                icon = if (active == null) IconChat else IconHead,
                modifier = Modifier.fillMaxWidth().weight(1f),
                trailing = {
                    if (active != null) {
                        McButton(
                            text = "",
                            onClick = vm::closeChat,
                            icon = IconClose,
                            tone = McButtonTone.Ghost,
                            height = 30.dp,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                },
                fillHeight = true,
                contentModifier = Modifier.fillMaxWidth()
            ) {
                if (active == null) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(72.dp)
                                    .background(BedrockVoid.copy(alpha = 0.65f))
                                    .border(1.dp, BedrockBorderSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                PixelIcon(art = IconChat, size = 34.dp, tint = BedrockGreen)
                            }
                            PixelText(
                                text = "NINGUNA CONVERSACIí“N ABIERTA",
                                color = BedrockText,
                                style = MaterialTheme.typography.labelMedium
                            )
                            PixelText(
                                text = "Elige un amigo a la izquierda. Toca un mensaje para\n" +
                                    "responderlo o usa el botón GIF para enviar uno.",
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3
                            )
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.messages.size) {
                        if (state.messages.isNotEmpty()) {
                            listState.animateScrollToItem(state.messages.lastIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 4.dp)
                    ) {
                        // Clave por índice: el servidor puede devolver ids nulos o
                        // repetidos y Compose reventaría con «key was already used».
                        itemsIndexed(
                            state.messages,
                            key = { index, message -> "m-$index-${message.id ?: ""}" }
                        ) { _, message ->
                            MessageBubble(
                                message = message,
                                mine = isMine(message, myId, session?.username, active),
                                author = if (message.senderId == active.id) active.username else "Tú",
                                avatarUrl = active.avatarUrl,
                                onReply = { vm.setReply(message) }
                            )
                        }
                    }

                    // ---- Respuesta pendiente ----
                    state.replyTo?.let { reply ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(BedrockRow.copy(alpha = 0.85f))
                                .border(1.dp, Gold.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelIcon(art = IconReply, size = 13.dp, tint = Gold)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                PixelText(
                                    text = "Respondiendo a " +
                                        if (reply.senderId == active.id) active.username else "ti mismo",
                                    color = Gold,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                PixelText(
                                    text = if (reply.isGif) "GIF" else reply.content,
                                    color = BedrockTextDim,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clickable(onClick = vm::clearReply),
                                contentAlignment = Alignment.Center
                            ) {
                                PixelIcon(art = IconClose, size = 10.dp, tint = BedrockTextDim)
                            }
                        }
                    }

                    // Selector de GIFs (Giphy)
                    if (state.gifOpen) {
                        GifPicker(
                            query = state.gifQuery,
                            gifs = state.gifs,
                            loading = state.gifLoading,
                            onQuery = vm::onGifQuery,
                            onPick = { gif -> signDay(); vm.sendGif(gif) }
                        )
                    }

                    // Barra de envío
                    PixelDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        McButton(
                            text = "GIF",
                            onClick = vm::toggleGifs,
                            icon = IconGif,
                            tone = if (state.gifOpen) McButtonTone.Success else McButtonTone.Ghost,
                            height = 42.dp,
                            modifier = Modifier.width(64.dp)
                        )
                        McCompactField(
                            value = state.draft,
                            onValueChange = vm::onDraft,
                            placeholder = if (state.replyTo != null) "Responder…" else "Escribe un mensaje…",
                            modifier = Modifier.weight(1f),
                            height = 42.dp
                        )
                        McButton(
                            text = "ENVIAR",
                            onClick = { signDay(); vm.send() },
                            enabled = !state.sending && state.draft.isNotBlank(),
                            icon = IconSend,
                            tone = McButtonTone.Success,
                            height = 42.dp,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * El servidor puede devolver el remitente como id numérico, como username o
 * como "me": se aceptan los tres. El id del amigo siempre gana como «suyo».
 */
private fun isMine(
    message: ChatMessage,
    myId: String?,
    myUsername: String?,
    active: ChatUser
): Boolean {
    val sender = message.senderId.trim()
    if (sender.isEmpty()) return false
    if (sender == active.id || sender.equals(active.username, ignoreCase = true)) return false
    if (sender == "me") return true
    if (!myId.isNullOrBlank() && sender == myId) return true
    return !myUsername.isNullOrBlank() && sender.equals(myUsername, ignoreCase = true)
}

/**
 * Modal de entrada a GChat. Se abre encima del apartado cuando no hay sesión y
 * deja elegir el tipo de cuenta con dos botones grandes: GLauncher (chat real)
 * o perfil local (solo para jugar).
 */
@Composable
private fun GchatWelcome(onLogin: () -> Unit, onClose: (() -> Unit)?) {
    var localNotice by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(BedrockVoid.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.72f)
                .background(BedrockRow.copy(alpha = 0.98f))
                .border(2.dp, BedrockBorder)
        ) {
            // ---- Cabecera del modal ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .background(BedrockVoid.copy(alpha = 0.6f))
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelIcon(art = IconChat, size = 15.dp, tint = BedrockGreen)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    PixelText(
                        text = "GCHAT",
                        color = BedrockText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    PixelText(
                        text = "Selecciona el tipo de inicio de sesión",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (onClose != null) {
                    McButton(
                        text = "",
                        onClick = onClose,
                        icon = IconClose,
                        tone = McButtonTone.Ghost,
                        height = 32.dp,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }

            PixelDivider()

            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // ---- Marca ----
                Column(
                    Modifier.width(190.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    FlameMascot(mood = FlameMood.Cool, size = 88.dp)
                    Spacer(Modifier.height(8.dp))
                    PixelText(
                        text = "Amigos, GIFs y racha diaria",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        McTag("CHAT", BedrockGreen)
                        McTag("RACHA", Gold)
                    }
                }

                // ---- Botones de tipo de cuenta ----
                Column(Modifier.weight(1f)) {
                    SectionLabel("Tipo de inicio de sesión")
                    Spacer(Modifier.height(8.dp))

                    LoginChoice(
                        title = "CUENTA GLAUNCHER",
                        description = "Amigos, mensajes en vivo y racha sincronizada.",
                        tag = "ENTRAR",
                        color = BedrockGreen,
                        icon = IconChat,
                        onClick = onLogin
                    )

                    Spacer(Modifier.height(8.dp))

                    LoginChoice(
                        title = "CUENTA LOCAL",
                        description = "Tu perfil offline. Juega sin conexión, sin chat.",
                        tag = "ELEGIR",
                        color = BedrockTextDim,
                        icon = IconHead,
                        onClick = { localNotice = true }
                    )

                    if (localNotice) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(BedrockVoid.copy(alpha = 0.65f))
                                .border(1.dp, Gold.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelIcon(art = IconStar, size = 11.dp, tint = Gold)
                            PixelText(
                                text = "Con perfil local no puedes chatear. La racha cuenta " +
                                    "los días que escribes con cuenta GLauncher.",
                                color = BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    PixelText(
                        text = "Puedes cerrar GChat y jugar con tu perfil local cuando quieras.",
                        color = BedrockTextDim.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2
                    )
                }
            }
        }
    }
}

/** Botón grande del modal: toda la fila es pulsable, no solo la etiqueta. */
@Composable
private fun LoginChoice(
    title: String,
    description: String,
    tag: String,
    color: androidx.compose.ui.graphics.Color,
    icon: com.zyrovent.glauncher.ui.components.PixelArt,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .background(BedrockVoid.copy(alpha = 0.55f))
            .border(1.dp, color.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(BedrockRow.copy(alpha = 0.8f))
                .border(1.dp, BedrockBorderSoft),
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(art = icon, size = 20.dp, tint = color)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PixelText(
                text = title,
                color = BedrockText,
                style = MaterialTheme.typography.titleMedium
            )
            PixelText(
                text = description,
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
        McTag(tag, color)
    }
}

@Composable
private fun ChatStreakStrip(streak: StreakState, celebrating: Boolean) {
    // La llama responde al tocarla: cambia de gesto y suelta una frase.
    val reactionMoods = remember {
        listOf(FlameMood.Wink, FlameMood.Happy, FlameMood.Cool, FlameMood.Sleepy)
    }
    val reactions = remember {
        listOf("¡Qué racha buena!", "¡No la rompas hoy!", "Te queda estilo", "Zzz… escribe algo")
    }
    var petStep by remember { mutableIntStateOf(-1) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(petStep) {
        if (petStep >= 0) {
            delay(1_800)
            petStep = -1
        }
    }

    val petting = petStep >= 0
    val baseMood = when {
        streak.current == 0  -> FlameMood.Off
        streak.current < 3   -> FlameMood.Sleepy
        streak.current < 7   -> FlameMood.Wink
        streak.current < 30  -> FlameMood.Cool
        streak.current == 7  -> FlameMood.Excited
        streak.current == 30 -> FlameMood.Excited
        streak.current == 100 -> FlameMood.Excited
        else                 -> FlameMood.Party
    }
    val mood = when {
        celebrating -> FlameMood.Happy
        petting     -> reactionMoods[petStep % reactionMoods.size]
        streak.current == 7 || streak.current == 30 || streak.current == 100 -> FlameMood.Excited
        else        -> baseMood
    }

    // El borde late despacio mientras el día siga sin firmar; al celebrar, se
    // queda fijo en oro y sale un +1 subiendo junto a la llama.
    val pulse = rememberInfiniteTransition(label = "streakPulse")
    val breathe by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_900, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "streakBreathe"
    )
    val rise by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1_100, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "streakRise"
    )

    val borderAlpha = when {
        celebrating -> 1f
        streak.claimedToday -> 0.30f
        else -> 0.22f + 0.28f * breathe
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(BedrockRow.copy(alpha = 0.65f))
            .border(1.dp, Gold.copy(alpha = borderAlpha))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            FlameMascot(
                mood = mood,
                size = 72.dp,
                modifier = Modifier.clickable {
                    petStep = if (petStep < 0) 0 else petStep + 1
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
            if (celebrating) {
                PixelText(
                    text = "+1",
                    color = Gold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - rise
                        translationY = -rise * 32f
                    }
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelText(
                    text = "RACHA DE CHAT",
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.labelSmall
                )
                if (streak.claimedToday) McTag("HOY", Gold)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                PixelText(
                    text = streak.current.toString(),
                    color = Gold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.width(4.dp))
                Column(verticalArrangement = Arrangement.Bottom) {
                    PixelText(
                        text = if (streak.current == 1) "dia" else "dias",
                        color = BedrockTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                    PixelText(
                        text = mood.label.uppercase(),
                        color = when (mood) {
                            FlameMood.Excited -> Gold
                            FlameMood.Party   -> Color(0xFFFF9800)
                            FlameMood.Cool    -> Color(0xFF00E5FF)
                            FlameMood.Happy   -> Gold
                            else              -> BedrockTextDim.copy(alpha = 0.65f)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                StreakDots(streak.days)
            }
            PixelText(
                text = when {
                    petting      -> reactions[petStep % reactions.size]
                    celebrating  -> "Dia firmado! racha " + streak.current
                    streak.current == 7   -> "HITO: 7 dias seguidos!"
                    streak.current == 30  -> "HITO: 30 dias increible!"
                    streak.current == 100 -> "HITO: 100 dias leyenda!"
                    streak.claimedToday  -> mood.cosmetic + " record " + streak.longest
                    else -> "escribe para firmar hoy"
                },
                color = if (petting || streak.claimedToday || celebrating ||
                    streak.current == 7 || streak.current == 30 || streak.current == 100) {
                    Gold.copy(alpha = 0.85f)
                } else {
                    BedrockTextDim
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1
            )
        }
    }
}


/** Ultimos siete dias: dorado el firmado, hundido el pendiente.
 *  Muestra la inicial del dia de la semana debajo de cada punto
 *  y hace pulsar con glow dorado el punto de hoy si no esta firmado. */
@Composable
private fun StreakDots(days: List<Boolean>) {
    val dayLabels = remember {
        // 7 labels: empieza en el dia mas antiguo (days[0]) y acaba en hoy (days[6])
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        // DAY_OF_WEEK: 1=Dom, 2=Lun, ... 7=Sab
        val names = listOf("D","L","M","X","J","V","S")
        (0..6).map { offset ->
            // offset 0 = 6 dias atras, offset 6 = hoy
            val dayIdx = ((today - 1) - (6 - offset) + 7) % 7
            names[dayIdx]
        }
    }

    val pulse = rememberInfiniteTransition(label = "dotPulse")
    val glow by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotGlow"
    )

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEachIndexed { index, signed ->
            val today = index == days.lastIndex
            val dotColor = when {
                signed -> Gold
                today  -> Gold.copy(alpha = 0.15f + 0.30f * glow)
                else   -> BedrockVoid.copy(alpha = 0.8f)
            }
            val borderColor = when {
                signed -> Gold
                today  -> Gold.copy(alpha = 0.55f + 0.45f * glow)
                else   -> BedrockBorderSoft.copy(alpha = 0.5f)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(dotColor)
                        .border(1.dp, borderColor)
                )
                PixelText(
                    text = if (index < dayLabels.size) dayLabels[index] else "",
                    color = if (signed) Gold else BedrockTextDim.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 6.sp)
                )
            }
        }
    }
}

@Composable
private fun GifPicker(
    query: String,
    gifs: List<com.zyrovent.glauncher.data.Gif>,
    loading: Boolean,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(BedrockVoid.copy(alpha = 0.6f))
            .border(1.dp, BedrockBorderSoft)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            McCompactField(
                value = query,
                onValueChange = onQuery,
                placeholder = "Buscar GIF...",
                height = 36.dp,
                fontSize = 12
            )
            PixelText(
                text = if (loading) "CARGANDO..." else "POWERED BY GIPHY",
                color = BedrockTextDim.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        LazyRow(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(gifs) { _, gif ->
                GifThumbnail(
                    url = gif.previewUrl,
                    onClick = { onPick(gif.url) },
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}
@Composable
private fun PendingRow(user: ChatUser, onAccept: () -> Unit, onReject: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BedrockVoid.copy(alpha = 0.4f))
            .border(1.dp, BedrockBorderSoft.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteIcon(url = user.avatarUrl, size = 24.dp, fallback = IconHead)
        PixelText(
            text = user.username,
            color = BedrockText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        McButton(
            text = "",
            onClick = onAccept,
            icon = IconCheck,
            tone = McButtonTone.Success,
            height = 30.dp,
            modifier = Modifier.width(38.dp)
        )
        McButton(
            text = "",
            onClick = onReject,
            icon = IconClose,
            tone = McButtonTone.Danger,
            height = 30.dp,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun ContactRow(
    user: ChatUser,
    onClick: () -> Unit,
    tag: String,
    selected: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (selected) BedrockRow else BedrockVoid.copy(alpha = 0.4f))
            .border(1.dp, if (selected) BedrockGreen.copy(alpha = 0.6f) else BedrockBorderSoft.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(28.dp)) {
            RemoteIcon(url = user.avatarUrl, size = 28.dp, fallback = IconHead)
            // Punto verde de conectado, como en cualquier mensajería.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(8.dp)
                    .background(BedrockGreen)
                    .border(1.dp, BedrockVoid)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            PixelText(text = user.username, color = BedrockText, style = MaterialTheme.typography.bodyMedium)
            PixelText(
                text = user.status,
                color = BedrockTextDim.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
        McTag(tag, if (selected) BedrockGreen else BedrockTextDim)
    }
}

/**
 * Burbuja de mensaje. Las tuyas se pegan a la derecha en verde; las del amigo
 * a la izquierda en gris, con su avatar, igual que en WhatsApp.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    mine: Boolean,
    author: String,
    avatarUrl: String?,
    onReply: () -> Unit
) {
    val shape = RoundedCornerShape(3.dp)
    val fill = if (mine) BedrockGreen.copy(alpha = 0.20f) else BedrockRow
    val stroke = if (mine) BedrockGreen.copy(alpha = 0.55f) else BedrockBorderSoft

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            RemoteIcon(url = avatarUrl, size = 22.dp, fallback = IconHead)
            Spacer(Modifier.width(5.dp))
        }

        Box(
            Modifier
                .widthIn(max = 280.dp)
                .background(fill, shape)
                .border(1.dp, stroke, shape)
                .clickable(onClick = onReply)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (!mine) {
                    PixelText(
                        text = author,
                        color = Gold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                // Cita del mensaje al que se responde (nunca el texto "null").
                val quoted = message.replyToUsername
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                if (quoted != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(BedrockVoid.copy(alpha = 0.5f), shape)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelIcon(art = IconReply, size = 9.dp, tint = Gold)
                        Spacer(Modifier.width(5.dp))
                        PixelText(
                            text = quoted,
                            color = Gold,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
                if (message.isGif) {
                    RemoteGif(
                        url = message.content,
                        modifier = Modifier.width(164.dp).height(116.dp)
                    )
                } else {
                    PixelText(
                        text = message.content,
                        color = BedrockText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 12
                    )
                }
            }
        }

        if (mine) {
            Spacer(Modifier.width(5.dp))
            // Marca de enviado, como el check de WhatsApp.
            Box(
                Modifier
                    .size(17.dp)
                    .background(BedrockVoid.copy(alpha = 0.4f), shape)
                    .border(1.dp, BedrockBorder.copy(alpha = 0.45f), shape),
                contentAlignment = Alignment.Center
            ) {
                PixelIcon(art = IconCheck, size = 9.dp, tint = BedrockGreen)
            }
        }
    }
}
