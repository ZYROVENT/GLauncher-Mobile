package com.zyrovent.glauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyrovent.glauncher.LauncherViewModel
import com.zyrovent.glauncher.Notice
import com.zyrovent.glauncher.Screen
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.FloatingNavRail
import com.zyrovent.glauncher.ui.components.GameSurface
import com.zyrovent.glauncher.ui.components.GLauncherWallpaper
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.MusicMiniPlayer
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.components.UnifiedTopBar
import com.zyrovent.glauncher.ui.screens.AccountsScreen
import com.zyrovent.glauncher.ui.screens.HomeScreen
import com.zyrovent.glauncher.ui.screens.HomeUpperMenu
import com.zyrovent.glauncher.ui.screens.NotificationsModal
import com.zyrovent.glauncher.ui.screens.ProfileModal
import com.zyrovent.glauncher.ui.screens.UpdateModal
import com.zyrovent.glauncher.ui.screens.LogsOverlay
import com.zyrovent.glauncher.ui.screens.ModsScreen
import com.zyrovent.glauncher.ui.screens.MusicAudioEngine
import com.zyrovent.glauncher.ui.screens.MusicScreen
import com.zyrovent.glauncher.ui.screens.MusicViewModel
import com.zyrovent.glauncher.ui.screens.SettingsScreen
import com.zyrovent.glauncher.ui.screens.VersionsScreen
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BgDeep

@Composable
fun LauncherRoot(vm: LauncherViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    // Versión instalada del launcher (la que se muestra en el menú y el pie).
    val context = LocalContext.current
    val launcherVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.1"
    }
    // Vive aquí para que el audio siga sonando al cambiar de pantalla.
    val musicVm: MusicViewModel = viewModel()

    BackHandler(enabled = state.screen != Screen.HOME && !state.gameRunning) {
        vm.navigate(Screen.HOME)
    }

    var showMenu by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }

    LauncherScaffold(
        state = state,
        logs = logs,
        vm = vm,
        musicVm = musicVm,
        launcherVersion = launcherVersion,
        showMenu = showMenu,
        onShowMenuChange = { showMenu = it },
        showNotifications = showNotifications,
        onShowNotificationsChange = { showNotifications = it },
        showUpdate = showUpdate,
        onShowUpdateChange = { showUpdate = it },
        showProfile = showProfile,
        onShowProfileChange = { showProfile = it }
    )
}

@Composable
private fun LauncherScaffold(
    state: UiState,
    logs: List<String>,
    vm: LauncherViewModel,
    musicVm: MusicViewModel,
    launcherVersion: String,
    showMenu: Boolean,
    onShowMenuChange: (Boolean) -> Unit,
    showNotifications: Boolean,
    onShowNotificationsChange: (Boolean) -> Unit,
    showUpdate: Boolean,
    onShowUpdateChange: (Boolean) -> Unit,
    showProfile: Boolean,
    onShowProfileChange: (Boolean) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Superficie del juego: se monta en cuanto arranca la partida para que
        // el puente EGL tenga una ANativeWindow antes de que JLI_Launch
        // inicialice OpenGL. Sin esto el JVM arrancaba sin ventana.
        if (state.launching || state.gameRunning) {
            GameSurface(Modifier.fillMaxSize())
        }

        // Controles virtuales y capa interactiva durante la partida. Solo en la
        // pantalla de partida: si el jugador se va a Inicio la partida sigue
        // viva detrás y esto no debe tapar el launcher.
        if (state.gameRunning && state.screen == Screen.GAME) {
            com.zyrovent.glauncher.ui.components.VirtualGamepadOverlay(
                modifier = Modifier.fillMaxSize(),
                settings = state.settings,
                musicVm = musicVm,
                onUpdateSettings = vm::updateSettings,
                // Ir a Inicio ya NO cierra la partida: solo cambia de pantalla.
                onNavigate = { screen -> vm.navigate(screen) },
                onExitGame = { vm.closeGame() }
            )
        }

        // Motor de audio de GMusic: WebView invisible, no tapa la interfaz.
        // En partida va en capa software para no pelearse con la GPU del juego.
        MusicAudioEngine(vm = musicVm, gameActive = state.gameRunning)

        if (state.screen != Screen.GAME) {
            Box(Modifier.fillMaxSize()) {
                GLauncherWallpaper(state.settings.wallpaper)

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        UnifiedTopBar(
                            state = state,
                            launcherVersion = launcherVersion,
                            onMenu = { onShowMenuChange(true) },
                            onNotifications = { onShowNotificationsChange(true) },
                            onAccount = { onShowProfileChange(true) },
                            modifier = Modifier
                                .padding(top = 4.dp, start = 62.dp, end = 10.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding()
                ) { innerPadding ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(start = 58.dp)
                            .imePadding()
                    ) {
                        AnimatedContent(
                            targetState = state.screen,
                            transitionSpec = {
                                (fadeIn(tween(240)) +
                                    slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                                    fadeOut(tween(150))
                            },
                            label = "screenTransition"
                        ) { screen ->
                            when (screen) {
                            // La pantalla de partida no dibuja el launcher: la
                            // cubre el overlay de controles.
                            Screen.GAME -> Unit

                            Screen.HOME -> HomeScreen(
                                state = state,
                                launcherVersion = launcherVersion,
                                onPlay = { vm.launch() },
                                onResumeGame = vm::openGame,
                                onStop = { vm.closeGame() },
                                onPickVersion = { vm.navigate(Screen.VERSIONS) },
                                onOpenAccounts = { vm.navigate(Screen.ACCOUNTS) },
                                onOpenLogs = vm::openLogs,
                                onOpenSettings = { vm.navigate(Screen.SETTINGS) },
                                onNavigate = vm::navigate,
                                onMarkEventsRead = vm::markEventsRead,
                                onClearEvents = vm::clearEvents
                            )

                            Screen.VERSIONS -> VersionsScreen(
                                state = state,
                                onRefresh = vm::refreshVersions,
                                onSelect = vm::selectVersion
                            )

                            Screen.ACCOUNTS -> AccountsScreen(
                                state = state,
                                onAdd = vm::addAccount,
                                onSelect = vm::selectAccount,
                                onRemove = vm::removeAccount
                            )

                            Screen.MUSIC -> MusicScreen(vm = musicVm)

                            Screen.SHOP -> com.zyrovent.glauncher.ui.screens.ShopScreen(state = state)

                            Screen.MODS -> ModsScreen()

                            Screen.SETTINGS -> SettingsScreen(
                                state = state,
                                onWallpaper = vm::setWallpaper,
                                onRam = vm::setRam,
                                onRenderer = vm::setRenderer,
                                onSounds = vm::setSounds,
                                onJvmArgs = vm::setCustomJvmArgs,
                                onOpenLogs = vm::openLogs,
                                onImportRuntime = vm::importRuntime,
                                onRemoveRuntime = vm::removeRuntime,
                                onDownloadRuntime = vm::downloadRuntime,
                                onTestRuntime = vm::testRuntime,
                                onTestGameWindow = vm::testGameWindow,
                                onTestJre = vm::testJreConsole,
                                onUseJoystick = vm::setUseJoystick,
                                onControlsOpacity = vm::setControlsOpacity,
                                onControlsScale = vm::setControlsScale,
                                onMouseSensitivity = vm::setMouseSensitivity
                            )
                            }
                        }
                    }
                }
            }
        }

        if (state.screen != Screen.GAME && !state.launching) {
            val railAlpha by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260),
                label = "railAlpha"
            )
            FloatingNavRail(
                current = state.screen,
                onSelect = vm::navigate,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .graphicsLayer { alpha = railAlpha }
            )
        }

        HomeUpperMenu(
            visible = showMenu,
            state = state,
            launcherVersion = launcherVersion,
            onClose = { onShowMenuChange(false) },
            onNavigate = { screen ->
                onShowMenuChange(false)
                vm.navigate(screen)
            },
            onUpdate = {
                onShowMenuChange(false)
                onShowUpdateChange(true)
            },
            onProfile = {
                onShowMenuChange(false)
                onShowProfileChange(true)
            },
            onNotifications = {
                onShowMenuChange(false)
                onShowNotificationsChange(true)
            },
            onPlay = {
                onShowMenuChange(false)
                vm.launch()
            }
        )

        UpdateModal(
            visible = showUpdate,
            state = state,
            launcherVersion = launcherVersion,
            onClose = { onShowUpdateChange(false) }
        )

        NotificationsModal(
            visible = showNotifications,
            events = state.events,
            remote = state.remoteNotifications,
            onClose = { onShowNotificationsChange(false) },
            onClear = vm::clearEvents
        )

        ProfileModal(
            visible = showProfile,
            account = state.selectedAccount,
            selectedVersion = state.settings.selectedVersion,
            onClose = { onShowProfileChange(false) },
            onOpenAccounts = {
                onShowProfileChange(false)
                vm.navigate(Screen.ACCOUNTS)
            }
        )



        // Mini reproductor eliminado a petición: ahora la música se gestiona directamente desde la notificación o la pestaña GMusic.

        if (state.showLogs) {
            if (state.jreConsole) {
                LogsOverlay(
                    logs = logs,
                    onClose = vm::closeLogs,
                    onClear = vm::clearLogs,
                    title = "Consola JRE",
                    subtitle = "Prueba de la máquina virtual",
                    tag = "JVM",
                    emptyText = "Arrancando la máquina virtual…"
                )
            } else {
                LogsOverlay(logs = logs, onClose = vm::closeLogs, onClear = vm::clearLogs)
            }
        }

        // Notificación emergente toast generada por GSS notify("...")
        val gssNotification = com.zyrovent.glauncher.gss.GssEngine.latestNotification
        if (gssNotification != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 46.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(1.dp, com.zyrovent.glauncher.ui.theme.BedrockGreen)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    PixelText(
                        text = gssNotification,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        state.notice?.let { notice ->
            NoticeDialog(notice = notice, onDismiss = vm::dismissNotice)
        }
    }
}

@Composable
private fun NoticeDialog(notice: Notice, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        BedrockWindow(
            title = notice.title,
            subtitle = "GLauncher",
            modifier = Modifier.widthIn(max = 440.dp)
        ) {
            PixelText(
                text = notice.detail,
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                McButton(
                    text = "ENTENDIDO",
                    onClick = onDismiss,
                    height = 32.dp
                )
            }
        }
    }
}
