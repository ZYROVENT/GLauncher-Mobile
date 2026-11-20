package com.zyrovent.glauncher.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.data.Account
import com.zyrovent.glauncher.data.AuthSession
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconChat
import com.zyrovent.glauncher.ui.components.IconCheck
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconHead
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.IconTrash
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McCompactField
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.PlayerAvatar
import com.zyrovent.glauncher.ui.components.RemoteIcon
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.DangerRed
import com.zyrovent.glauncher.ui.theme.Gold

/**
 * Pantalla Mi Cuenta Rediseñada — limpia, despejada y sin saturación.
 * Divide claramente:
 * - Tarjeta de Identidad y GChat directo a la izquierda.
 * - Lista de perfiles locales espaciosa y sin apreturas a la derecha.
 */
@Composable
fun AccountsScreen(
    state: UiState,
    onAdd: (String) -> String?,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    vm: SessionViewModel = viewModel()
) {
    val context = LocalContext.current
    var showChat by remember { mutableStateOf(false) }
    var newProfile by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    val login by vm.login.collectAsState()
    val session by vm.session.collectAsState()

    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Columna izquierda: Identidad + GChat ----
            BedrockWindow(
                title = "Mi cuenta",
                subtitle = if (session == null) "Modo offline" else "Conectado",
                icon = IconHead,
                modifier = Modifier.fillMaxHeight().weight(0.38f),
                fillHeight = true,
                trailing = {
                    McTag(
                        if (session == null) "OFFLINE" else "ONLINE",
                        if (session == null) BedrockTextDim else BedrockGreen
                    )
                }
            ) {
                // Tarjeta de Usuario con buen espaciado
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockRow)
                        .border(1.dp, BedrockBorderSoft)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (session != null) {
                            RemoteIcon(
                                url = session?.avatarUrl,
                                size = 48.dp,
                                fallback = IconHead
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(BedrockVoid)
                                    .border(1.dp, BedrockBorderSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                PixelIcon(art = IconHead, size = 24.dp, tint = BedrockTextDim)
                            }
                        }

                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            PixelText(
                                text = session?.username ?: "Invitado Local",
                                color = BedrockText,
                                style = MaterialTheme.typography.titleMedium
                            )
                            PixelText(
                                text = if (session != null) "${session?.gcoins ?: 0} GCoins" else "Sin sincronizar",
                                color = if (session != null) Gold else BedrockTextDim,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Botones de acción de cuenta
                if (session == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McButton(
                            text = "INICIAR SESIÓN",
                            onClick = vm::openLogin,
                            tone = McButtonTone.Success,
                            height = 38.dp,
                            modifier = Modifier.weight(1.3f)
                        )
                        McButton(
                            text = "REGISTRAR",
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://glauncher.vercel.app/src/html/register")
                                )
                                context.startActivity(intent)
                            },
                            tone = McButtonTone.Ghost,
                            height = 38.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    McButton(
                        text = "CERRAR SESIÓN",
                        onClick = vm::logout,
                        tone = McButtonTone.Danger,
                        height = 36.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.weight(1f))

                // Acceso directo a GChat
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockRow)
                        .border(1.dp, BedrockGreen.copy(alpha = 0.5f))
                        .clickable { showChat = true }
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BedrockVoid)
                                .border(1.dp, BedrockBorderSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            PixelIcon(art = IconChat, size = 20.dp, tint = BedrockGreen)
                        }
                        Column(Modifier.weight(1f)) {
                            PixelText(text = "GCHAT EN VIVO", color = BedrockText, style = MaterialTheme.typography.bodyLarge)
                            PixelText(text = "Amigos, chat y GIFs", color = BedrockTextDim, style = MaterialTheme.typography.labelSmall)
                        }
                        McButton(
                            text = "ENTRAR",
                            onClick = { showChat = true },
                            tone = McButtonTone.Success,
                            height = 32.dp,
                            modifier = Modifier.width(76.dp)
                        )
                    }
                }
            }

            // ---- Columna derecha: Perfiles Locales Limpios ----
            BedrockWindow(
                title = "Perfiles de Juego",
                subtitle = "${state.accounts.size} perfiles guardados",
                icon = IconHead,
                modifier = Modifier.fillMaxHeight().weight(0.62f),
                fillHeight = true,
                trailing = {
                    McTag(
                        state.selectedAccount?.username ?: "NINGUNO",
                        if (state.selectedAccount == null) BedrockTextDim else BedrockGreen
                    )
                }
            ) {
                // Barra de creación de perfil rápido
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McCompactField(
                        value = newProfile,
                        onValueChange = {
                            newProfile = it
                            addError = null
                        },
                        placeholder = "Nuevo perfil offline (ej: Steve)...",
                        modifier = Modifier.weight(1f),
                        height = 40.dp
                    )
                    McButton(
                        text = "+ CREAR",
                        onClick = {
                            val msg = onAdd(newProfile)
                            addError = msg
                            if (msg == null) newProfile = ""
                        },
                        tone = McButtonTone.Success,
                        height = 40.dp,
                        modifier = Modifier.width(96.dp)
                    )
                }

                addError?.let {
                    Spacer(Modifier.height(4.dp))
                    PixelText(text = it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(10.dp))

                // Lista de perfiles locales
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(state.accounts, key = { it.username }) { acc ->
                        val isSelected = state.selectedAccount?.username == acc.username
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) BedrockRow else BedrockVoid.copy(alpha = 0.5f))
                                .border(1.dp, if (isSelected) BedrockGreen else BedrockBorderSoft)
                                .clickable { onSelect(acc.username) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PlayerAvatar(size = 36.dp)

                            Column(Modifier.weight(1f)) {
                                PixelText(
                                    text = acc.username,
                                    color = if (isSelected) BedrockHighlight else BedrockText,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                PixelText(
                                    text = if (isSelected) "Perfil activo seleccionado" else "Toca para jugar con este perfil",
                                    color = if (isSelected) BedrockGreen else BedrockTextDim,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            if (!isSelected) {
                                McButton(
                                    text = "",
                                    onClick = { onRemove(acc.username) },
                                    icon = IconTrash,
                                    tone = McButtonTone.Danger,
                                    height = 32.dp,
                                    modifier = Modifier.width(38.dp)
                                )
                            } else {
                                PixelIcon(art = IconCheck, size = 18.dp, tint = BedrockGreen)
                            }
                        }
                    }
                }
            }
        }

        // Subpantalla de GChat superpuesta al abrir
        if (showChat) {
            Box(Modifier.fillMaxSize()) {
                ChatScreen(
                    sessionViewModel = vm,
                    onClose = { showChat = false },
                    onLogin = {
                        showChat = false
                        vm.openLogin()
                    }
                )
            }
        }

        // Modal de Login
        if (login.open) {
            LoginModal(
                state = login,
                onUsername = vm::onUsername,
                onPassword = vm::onPassword,
                onSecurityCode = vm::onSecurityCode,
                onSubmit = vm::submitLogin,
                onRegister = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://glauncher.vercel.app/src/html/register")
                    )
                    context.startActivity(intent)
                },
                onClose = vm::closeLogin
            )
        }
    }
}

@Composable
private fun LoginModal(
    state: LoginUiState,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSecurityCode: (String) -> Unit,
    onSubmit: () -> Unit,
    onRegister: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 560.dp)
                .background(BedrockPanel)
                .border(2.dp, BedrockGreen)
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        PixelText(
                            text = "INICIAR SESIÓN",
                            color = BedrockHighlight,
                            style = MaterialTheme.typography.titleMedium
                        )
                        PixelText(
                            text = "Accede a tu cuenta de GLauncher",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .background(BedrockVoid)
                            .border(1.dp, BedrockBorderSoft)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        PixelIcon(art = IconClose, size = 16.dp, tint = BedrockTextDim)
                    }
                }

                PixelDivider()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PixelText(text = "NOMBRE DE USUARIO", color = BedrockTextDim, style = MaterialTheme.typography.labelSmall)
                    McCompactField(
                        value = state.username,
                        onValueChange = onUsername,
                        placeholder = "Ingresa tu usuario...",
                        height = 40.dp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PixelText(text = "CONTRASEÑA", color = BedrockTextDim, style = MaterialTheme.typography.labelSmall)
                    McCompactField(
                        value = state.password,
                        onValueChange = onPassword,
                        placeholder = "••••••••",
                        visualTransformation = PasswordVisualTransformation(),
                        height = 40.dp
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PixelText(text = "CÓDIGO DE SEGURIDAD (PIN)", color = BedrockTextDim, style = MaterialTheme.typography.labelSmall)
                    McCompactField(
                        value = state.securityCode,
                        onValueChange = onSecurityCode,
                        placeholder = "6 dígitos (ej: 123456)...",
                        height = 40.dp
                    )
                }

                state.error?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DangerRed.copy(alpha = 0.15f))
                            .border(1.dp, DangerRed)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        PixelText(text = it, color = DangerRed, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    McButton(
                        text = "REGISTRARSE",
                        onClick = onRegister,
                        tone = McButtonTone.Ghost,
                        height = 40.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        text = if (state.busy) "ENTRANDO…" else "INGRESAR",
                        onClick = onSubmit,
                        enabled = !state.busy,
                        tone = McButtonTone.Success,
                        height = 40.dp,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}
