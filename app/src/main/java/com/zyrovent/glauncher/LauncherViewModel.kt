package com.zyrovent.glauncher

import android.app.Application
import android.content.Intent
import java.io.File
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zyrovent.glauncher.data.Account
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LauncherEvent
import com.zyrovent.glauncher.data.LauncherSettings
import com.zyrovent.glauncher.data.LauncherStore
import com.zyrovent.glauncher.data.MojangRepository
import com.zyrovent.glauncher.data.MemoryBudget
import com.zyrovent.glauncher.data.RuntimeManager
import com.zyrovent.glauncher.data.VersionSummary
import com.zyrovent.glauncher.launch.GameInstaller
import com.zyrovent.glauncher.launch.GameProcess
import com.zyrovent.glauncher.launch.InProcessLaunch
import com.zyrovent.glauncher.launch.JvmCommandBuilder
import com.zyrovent.glauncher.launch.LaunchPipeline
import com.zyrovent.glauncher.launch.LaunchResult
import com.zyrovent.glauncher.launch.LaunchStage
import com.zyrovent.glauncher.launch.RuntimeLayout
import com.zyrovent.glauncher.util.Sfx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val label: String) {
    GAME("Partida"),
    HOME("Inicio"),
    VERSIONS("Versiones"),
    ACCOUNTS("Cuentas"),
    MUSIC("GMusic"),
    SHOP("Tienda"),
    MODS("Mods"),
    SETTINGS("Ajustes")
}

data class Notice(val title: String, val detail: String)

data class UiState(
    val screen: Screen = Screen.HOME,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val settings: LauncherSettings = LauncherSettings(),
    val versions: List<VersionSummary> = emptyList(),
    val latestRelease: String = "",
    val versionsLoading: Boolean = false,
    val versionsError: String? = null,
    val launching: Boolean = false,
    val gameRunning: Boolean = false,
    val progress: Float = 0f,
    val stage: String = "",
    val showLogs: Boolean = false,
    /** El registro se está mostrando como consola de prueba de la JRE. */
    val jreConsole: Boolean = false,
    val notice: Notice? = null,
    /** Historial de avisos, el que ve la campana de notificaciones. */
    val events: List<LauncherEvent> = emptyList(),
    /** Avisos publicados en Supabase (si está configurado). */
    val remoteNotifications: List<com.zyrovent.glauncher.data.RemoteNotification> = emptyList(),
    /** Última versión del launcher publicada en Supabase. */
    val remoteRelease: com.zyrovent.glauncher.data.RemoteRelease? = null,
    val remoteError: String? = null,
    val runtimeInstalled: Boolean = false,
    val installedRuntimes: List<Int> = emptyList(),
    val runtimeImporting: Boolean = false,
    val runtimeInstallingMajor: Int = 17,
    /** IDs de versiones cuyo client.jar ya está en disco. */
    val installedVersions: Set<String> = emptySet()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val store = LauncherStore(application)
    private val repository = MojangRepository(store)
    private val paths = GamePaths(application)
    private val installer = GameInstaller(paths)
    private val runtimeManager = RuntimeManager(application, paths)
    private val gameProcess = GameProcess(
        paths = paths,
        preloadLib = File(application.applicationInfo.nativeLibraryDir, "libtagoff.so").absolutePath
    )
    private val pipeline = LaunchPipeline(
        paths = paths,
        repository = repository,
        installer = installer,
        runtimeManager = runtimeManager,
        gameProcess = gameProcess,
        cacheDir = application.cacheDir
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Flujo propio: el registro cambia sin parar durante una descarga y no debe
     *  redibujar ninguna otra pantalla. */
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var activeProcess: Process? = null

    init {
        val accounts = store.accounts()
        val settings = store.settings()
        val selectedName = store.selectedAccountName()
        val selected = accounts.firstOrNull { it.username == selectedName } ?: accounts.firstOrNull()
        _state.update {
            it.copy(accounts = accounts, selectedAccount = selected, settings = settings)
        }
        if (selectedName == null && selected != null) {
            store.saveSelectedAccountName(selected.username)
        }
        paths.migrateLegacyRuntime()
        refreshRuntime()
        runtimeManager.installed().forEach { major -> verifyRuntime(major, showNotice = false) }
        refreshInstalledVersions()
        if (store.cachedManifestJson() != null) {
            loadVersions(refresh = false)
        } else {
            loadVersions(refresh = true)
        }

        // Avisos y versiones publicadas en Supabase. Si no hay claves o no hay
        // red, la app sigue con los datos locales sin molestar.
        refreshRemote()

        // Todo aviso que aparece en pantalla queda también en el historial de la
        // campana. Sin cambios en el aviso no se duplica nada.
        viewModelScope.launch {
            _state.map { it.notice }.distinctUntilChanged().collect { n ->
                if (n != null) {
                    _state.update { s ->
                        s.copy(
                            events = (listOf(
                                LauncherEvent(System.nanoTime(), n.title, n.detail)
                            ) + s.events).take(40)
                        )
                    }
                }
            }
        }
    }

    // ---------------- Navegación ----------------

    fun navigate(screen: Screen) {
        if (screen != _state.value.screen) playSfx(R.raw.tap)
        _state.update { it.copy(screen = screen) }
    }

    fun openLogs() {
        playSfx(R.raw.open)
        _state.update { it.copy(showLogs = true) }
    }

    fun closeLogs() {
        playSfx(R.raw.back)
        _state.update { it.copy(showLogs = false, jreConsole = false) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** Al abrir la campana todo pasa a leído y se apaga el punto rojo. */
    fun markEventsRead() = _state.update { s ->
        s.copy(events = s.events.map { it.copy(read = true) })
    }

    fun clearEvents() = _state.update { it.copy(events = emptyList()) }

    /**
     * Trae los avisos y la última versión desde Supabase. Todo va best-effort:
     * cualquier fallo (sin claves, sin red, RLS) deja los datos locales intactos.
     */
    fun refreshRemote() {
        if (!com.zyrovent.glauncher.data.SupabaseApi.configured) return
        viewModelScope.launch {
            val avisos = runCatching { com.zyrovent.glauncher.data.SupabaseApi.notifications() }
            val release = runCatching { com.zyrovent.glauncher.data.SupabaseApi.latestRelease() }
            _state.update { state ->
                state.copy(
                    remoteNotifications = avisos.getOrElse { state.remoteNotifications },
                    remoteRelease = release.getOrElse { state.remoteRelease },
                    remoteError = avisos.exceptionOrNull()?.message ?: release.exceptionOrNull()?.message
                )
            }
            if (avisos.isSuccess) {
                Log.i("GLauncher", "Supabase: ${avisos.getOrNull()?.size ?: 0} avisos, versión ${release.getOrNull()?.version ?: "—"}")
            } else {
                Log.i("GLauncher", "Supabase no disponible: ${avisos.exceptionOrNull()?.message}")
            }
        }
    }

    // ---------------- Perfiles ----------------

    fun addAccount(rawName: String): String? {
        val name = rawName.trim()
        when {
            name.length !in 3..16 -> return "El nombre debe tener entre 3 y 16 caracteres."
            !name.matches(Regex("^[A-Za-z0-9_]+$")) -> return "Solo se permiten letras, números y guion bajo."
            _state.value.accounts.any { it.username.equals(name, ignoreCase = true) } ->
                return "Ya existe un perfil con ese nombre."
        }
        val account = Account(name)
        val accounts = _state.value.accounts + account
        store.saveAccounts(accounts)
        store.saveSelectedAccountName(name)
        _state.update { it.copy(accounts = accounts, selectedAccount = account) }
        appendLog("Perfil offline creado: $name")
        playSfx(R.raw.coin)
        return null
    }

    fun selectAccount(username: String) {
        val account = _state.value.accounts.firstOrNull { it.username == username } ?: return
        store.saveSelectedAccountName(account.username)
        _state.update { it.copy(selectedAccount = account) }
        appendLog("Perfil activo: ${account.username}")
        playSfx(R.raw.tap)
    }

    fun removeAccount(username: String) {
        val accounts = _state.value.accounts.filterNot { it.username == username }
        store.saveAccounts(accounts)
        val current = _state.value.selectedAccount
        val nextSelected = if (current?.username == username) accounts.firstOrNull() else current
        store.saveSelectedAccountName(nextSelected?.username)
        _state.update { it.copy(accounts = accounts, selectedAccount = nextSelected) }
        appendLog("Perfil eliminado: $username")
        playSfx(R.raw.back)
    }

    // ---------------- Versiones ----------------

    fun refreshVersions() {
        if (_state.value.versionsLoading) return
        loadVersions(refresh = true)
    }

    private fun loadVersions(refresh: Boolean) {
        _state.update { it.copy(versionsLoading = true, versionsError = null) }
        viewModelScope.launch {
            try {
                val manifest = repository.versions(refresh)
                val current = _state.value.settings.selectedVersion
                val valid = manifest.versions.any { it.id == current }
                val settings = if (!valid && manifest.latestRelease.isNotBlank()) {
                    appendLog("La versión $current no aparece en el manifest; se usa ${manifest.latestRelease}.")
                    _state.value.settings.copy(selectedVersion = manifest.latestRelease)
                } else {
                    _state.value.settings
                }
                if (settings != _state.value.settings) store.saveSettings(settings)
                _state.update {
                    it.copy(
                        versions = manifest.versions,
                        latestRelease = manifest.latestRelease,
                        versionsLoading = false,
                        versionsError = null,
                        settings = settings
                    )
                }
                appendLog("Manifest cargado: ${manifest.versions.size} versiones (última ${manifest.latestRelease}).")
                refreshInstalledVersions()
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        versionsLoading = false,
                        versionsError = error.message ?: "No se pudo conectar con Mojang."
                    )
                }
                appendLog("Error al cargar versiones: ${error.message}")
            }
        }
    }

    fun selectVersion(versionId: String) {
        updateSettings(_state.value.settings.copy(selectedVersion = versionId))
        appendLog("Versión seleccionada: $versionId")
        playSfx(R.raw.tap)
    }

    // ---------------- Ajustes ----------------

    fun updateSettings(settings: LauncherSettings) {
        store.saveSettings(settings)
        _state.update { it.copy(settings = settings) }
    }

    fun setRam(ramMb: Int) = updateSettings(
        _state.value.settings.copy(ramMb = MemoryBudget.clamp(getApplication(), ramMb))
    )

    fun setRenderer(renderer: String) = updateSettings(_state.value.settings.copy(renderer = renderer))

    fun setSounds(enabled: Boolean) {
        updateSettings(_state.value.settings.copy(sounds = enabled))
        if (enabled) playSfx(R.raw.toggle)
    }

    fun setWallpaper(index: Int) = updateSettings(_state.value.settings.copy(wallpaper = index))

    fun setCustomJvmArgs(args: String) = updateSettings(_state.value.settings.copy(customJvmArgs = args))

    fun setUseJoystick(use: Boolean) = updateSettings(_state.value.settings.copy(useJoystick = use))

    fun setControlsOpacity(opacity: Float) = updateSettings(_state.value.settings.copy(controlsOpacity = opacity))

    fun setControlsScale(scale: Float) = updateSettings(_state.value.settings.copy(controlsScale = scale))

    fun setMouseSensitivity(sens: Float) = updateSettings(_state.value.settings.copy(mouseSensitivity = sens))

    // ---------------- Motor Java ----------------

    fun refreshRuntime() {
        val installed = runtimeManager.installed()
        _state.update {
            it.copy(
                installedRuntimes = installed,
                runtimeInstalled = installed.isNotEmpty()
            )
        }
    }

    /** Escanea el disco y actualiza qué versiones tienen client.jar descargado. */
    fun refreshInstalledVersions() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = paths.instancesDir
                .listFiles()
                ?.filter { dir -> dir.isDirectory && paths.clientJar(dir.name).exists() }
                ?.map { it.name }
                ?.toSet()
                ?: emptySet()
            _state.update { it.copy(installedVersions = installed) }
        }
    }

    fun importRuntime(uri: Uri, major: Int) {
        if (_state.value.runtimeImporting) return
        _state.update { it.copy(runtimeImporting = true, runtimeInstallingMajor = major) }
        _logs.value = emptyList()
        appendLog("Importando runtime Java $major...")
        viewModelScope.launch {
            finishRuntimeInstall(
                result = runtimeManager.importZip(uri, major) { line -> appendLog(line) },
                major = major
            )
        }
    }

    /** Descarga un runtime desde una URL directa (.zip, .tar.gz o .tar.xz). */
    fun downloadRuntime(url: String, major: Int) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) {
            _state.update {
                it.copy(
                    notice = Notice(
                        "URL no válida",
                        "Escribe el enlace directo al .zip, .tar.gz o .tar.xz del runtime."
                    )
                )
            }
            return
        }
        if (_state.value.runtimeImporting) return
        _state.update { it.copy(runtimeImporting = true, runtimeInstallingMajor = major) }
        _logs.value = emptyList()
        viewModelScope.launch {
            var lastLogged = -10
            val result = runtimeManager.downloadArchive(
                url = trimmed,
                major = major,
                onLog = { line -> appendLog(line) },
                onProgress = { fraction ->
                    val percent = (fraction * 100).toInt()
                    if (percent >= lastLogged + 20) {
                        lastLogged = percent
                        appendLog("  descarga $percent%")
                    }
                }
            )
            finishRuntimeInstall(result, major)
        }
    }

    /** Ejecuta `java -version` para comprobar que el motor arranca en este dispositivo. */
    fun testRuntime(major: Int) = verifyRuntime(major, showNotice = true)

    private fun verifyRuntime(major: Int, showNotice: Boolean) {
        if (!paths.hasRuntime(major)) {
            if (showNotice) {
                _state.update {
                    it.copy(
                        notice = Notice(
                            "Sin motor Java $major",
                            "Descárgalo o impórtalo antes de probarlo."
                        )
                    )
                }
            }
            return
        }
        appendLog("Probando motor Java $major...")
        viewModelScope.launch(Dispatchers.IO) {
            // Sin exec(): en Android 10+ (targetSdk 29) W^X lo bloquea, así que el
            // motor se valida por sus piezas y su cabecera ELF.
            val abi = runtimeManager.abiInfo(major)
            appendLog("ABI detectada: $abi")
            val check = runtimeManager.check(major)
            appendLog(check.report)
            withContext(Dispatchers.Main) {
                playSfx(if (check.ok) R.raw.success else R.raw.negative)
                if (showNotice) {
                    _state.update {
                        it.copy(
                            notice = Notice(
                                if (check.ok) "Motor Java $major operativo" else "Motor Java $major incompleto",
                                check.report
                            )
                        )
                    }
                }
            }
        }
    }

    private fun finishRuntimeInstall(result: Result<Unit>, major: Int) {
        refreshRuntime()
        result.fold(
            onSuccess = {
                appendLog("Motor Java $major instalado correctamente.")
                playSfx(R.raw.success)
                verifyRuntime(major, showNotice = false)
        _state.update {
            it.copy(
                runtimeImporting = false,
                notice = Notice(
                    "Java $major listo",
                    "Instalado en ${paths.runtimeDir(major).absolutePath}. La versión que " +
                        "lo necesite lo usará automáticamente al pulsar Jugar."
                )
            )
        }
        playSfx(R.raw.success)
    },
            onFailure = { error ->
                appendLog("Error con el runtime: ${error.message}")
                playSfx(R.raw.error)
                _state.update {
                    it.copy(
                        runtimeImporting = false,
                        notice = Notice(
                            "No se pudo instalar",
                            error.message ?: "El archivo no es un runtime válido."
                        )
                    )
                }
            }
        )
    }

    fun removeRuntime(major: Int) {
        if (runtimeManager.removeRuntime(major)) {
            appendLog("Runtime Java $major eliminado.")
        }
        refreshRuntime()
        playSfx(R.raw.hammer)
    }

    // ---------------- Lanzamiento ----------------

    fun launch() {
        if (_state.value.launching) return
        val preload = gameProcess.preloadLib
        appendLog(
            "LD_PRELOAD=" + (preload ?: "ninguno") +
                " · existe=" + (preload != null && File(preload).exists())
        )
        playSfx(R.raw.rocket)
        if (_state.value.launching) return
        if (_state.value.selectedAccount == null) {
            playSfx(R.raw.idiot)
            _state.update {
                it.copy(
                    screen = Screen.ACCOUNTS,
                    stage = "Falta un perfil",
                    notice = Notice(
                        "Primero crea un perfil",
                        "Pulsa Añadir perfil, elige un nombre de jugador y vuelve a intentarlo."
                    )
                )
            }
            return
        }
        _state.update {
            it.copy(
                launching = true,
                progress = 0f,
                stage = LaunchStage.ACCOUNT.label,
                notice = null
            )
        }
        _logs.value = emptyList()
        viewModelScope.launch {
            val snapshot = _state.value
            val result = pipeline.run(
                account = snapshot.selectedAccount,
                versionId = snapshot.settings.selectedVersion,
                settings = snapshot.settings,
                onStage = { stage -> _state.update { it.copy(stage = stage.label) } },
                onLog = { line -> appendLog(line) },
                onProgress = { value -> _state.update { it.copy(progress = value) } }
            )
            when (result) {
                is LaunchResult.Started -> {
                    playSfx(R.raw.coin)
                    refreshInstalledVersions()
                    _state.update {
                        it.copy(
                            // La pantalla pasa a ser la partida: la interfaz del
                            // launcher se aparta y manda el overlay de controles.
                            screen = Screen.GAME,
                            launching = false,
                            gameRunning = true,
                            progress = 1f,
                            stage = "Juego en ejecución"
                        )
                    }
                    val process = result.process
                    if (process != null) {
                        streamProcess(process)
                    } else {
                        streamInProcessGame()
                    }
                }

                is LaunchResult.Blocked -> {
                    appendLog("Detenido: ${result.title}")
                    playSfx(R.raw.error)
                    _state.update {
                        it.copy(
                            launching = false,
                            gameRunning = false,
                            stage = result.title,
                            notice = Notice(result.title, result.detail)
                        )
                    }
                }

                /* else: se canceló desde el progreso (o falló sin mensaje) */
            }
        }
    }

    /**
     * El JVM corre dentro de la propia app (Android 10+): la salida ya la vuelca
     * el pipeline al registro, aquí solo gestionamos el fin del juego.
     */
    private fun streamInProcessGame() {
        activeProcess = null
        val handler: (Int) -> Unit = { code -> finishGame(code) }
        InProcessLaunch.onExit = handler
        InProcessLaunch.lastExitCode?.let(handler)
    }

    private fun finishGame(code: Int) {
        activeProcess = null
        viewModelScope.launch(Dispatchers.Main) {
            _state.update {
                it.copy(
                    gameRunning = false,
                    launching = false,
                    // Al terminar el juego se vuelve al launcher.
                    screen = Screen.HOME,
                    stage = if (code == 0) "Juego cerrado" else "El juego se cerró (código $code)"
                )
            }
            playSfx(if (code == 0) R.raw.success else R.raw.hammer)
        }
    }

    /** Vuelca la salida del cliente al registro y actualiza el estado al terminar. */
    private fun streamProcess(process: Process) {
        activeProcess = process
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        appendLog(line)
                    }
                }
            }
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            activeProcess = null
            appendLog("El proceso terminó con código $code.")
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        gameRunning = false,
                        launching = false,
                        screen = Screen.HOME,
                        stage = if (code == 0) "Juego cerrado" else "El juego se cerró (código $code)"
                    )
                }
                playSfx(if (code == 0) R.raw.success else R.raw.hammer)
            }
        }
    }

    /** Cancela la instalación/descarga en curso y vuelve a la interfaz. */
    fun cancelLaunch() {
        if (!_state.value.launching) return
        appendLog("Lanzamiento cancelado por el usuario.")
        playSfx(R.raw.negative)
        _state.update {
            it.copy(launching = false, gameRunning = false, stage = "")
        }
    }

    /**
     * Vuelve a la ventana del juego. La partida nunca se detuvo: solo estaba
     * tapada por la interfaz del launcher.
     */
    fun openGame() {
        if (!_state.value.gameRunning && !_state.value.launching) return
        playSfx(R.raw.open)
        _state.update { it.copy(screen = Screen.GAME) }
    }

    /**
     * Cierra la partida de verdad.
     *
     * El cliente corre con `JNI_CreateJavaVM` **dentro de este mismo proceso**
     * (Android 10+ no permite lanzar un binario propio), así que no existe un
     * proceso hijo que matar: la única forma de terminar el JVM es terminar el
     * proceso de la app. Antes de eso se deja pedida la actividad del launcher
     * para que, al morir el proceso, el sistema abra una instancia limpia en
     * Inicio en lugar de dejar al usuario en otra app.
     */
    fun closeGame() {
        val process = activeProcess
        if (process != null) {
            appendLog("Deteniendo el proceso Java...")
            runCatching { process.destroy() }
            playSfx(R.raw.hammer)
            _state.update {
                it.copy(gameRunning = false, launching = false, stage = "", screen = Screen.HOME)
            }
            return
        }

        if (!InProcessLaunch.running && !_state.value.gameRunning) {
            appendLog("No hay ninguna partida en marcha.")
            _state.update { it.copy(screen = Screen.HOME) }
            return
        }

        appendLog("Cerrando el JVM del juego: el proceso se reinicia en Inicio.")
        playSfx(R.raw.hammer)
        _state.update { it.copy(stage = "Cerrando el juego…") }

        val context = getApplication<Application>()
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Margen para que el sistema arranque la actividad nueva antes de
            // que muera este proceso.
            Thread.sleep(400)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun stopGame() {
        val process = activeProcess
        if (process == null) {
            if (_state.value.gameRunning) {
                appendLog("Cerrando ventana de prueba del juego...")
                _state.update { it.copy(gameRunning = false, launching = false, stage = "") }
                return
            }
            appendLog(
                if (InProcessLaunch.running) {
                    "El JVM corre dentro de la propia app: no se puede detener desde aquí. " +
                        "Cierra la partida desde el menú del juego."
                } else {
                    "No hay ninguna partida en marcha."
                }
            )
            return
        }
        appendLog("Deteniendo el proceso Java...")
        runCatching { process.destroy() }
        _state.update { it.copy(gameRunning = false, stage = "Deteniendo") }
    }

    /** Abre la ventana de juego y el gamepad virtual para probar controles, superficie y ajustes sin arrancar Minecraft. */
    fun testGameWindow() {
        appendLog("Iniciando modo de prueba de la ventana de juego...")
        _state.update { it.copy(gameRunning = true, launching = false, screen = Screen.GAME, stage = "Modo prueba") }
    }

    /**
     * Prueba de la máquina virtual: abre la consola y arranca la JRE instalada
     * con `-version`, sin Minecraft de por medio. Sirve para ver en pantalla si
     * JLI_Launch, libjvm.so y el heap arrancan, y qué escribe HotSpot.
     */
    fun testJreConsole() {
        playSfx(R.raw.open)
        _state.update { it.copy(showLogs = true, jreConsole = true) }

        if (InProcessLaunch.running) {
            appendLog("El JVM ya está en marcha: espera a que termine antes de repetir la prueba.")
            return
        }

        val installed = paths.installedRuntimes()
        val major = listOf(17, 21, 8).firstOrNull { it in installed } ?: installed.firstOrNull()
        if (major == null) {
            appendLog("No hay ningún motor Java instalado. Descárgalo en Ajustes → Motor Java.")
            return
        }

        val home = paths.runtimeDir(major)
        appendLog("=== PRUEBA DE LA MÁQUINA VIRTUAL (JRE $major) ===")
        appendLog("Carpeta: ${home.absolutePath}")
        appendLog(RuntimeLayout.describe(home))

        if (RuntimeLayout.jvm(home) == null) {
            appendLog("No se encontró libjvm.so dentro del motor: ${RuntimeLayout.tree(home)}")
            return
        }

        val workDir = paths.gameRoot.resolve("jre-test").apply { mkdirs() }
        val libDirs = (RuntimeLayout.libDirs(home) + paths.appLibDir)
            .distinctBy { it.absolutePath }
            .sortedBy { it.absolutePath.length }
        // Sin clase principal: la VM arranca y devuelve un informe de sí misma.
        val argv = listOf(
            "java",
            "-Xmx512M",
            "-XX:+UseSerialGC",
            "-Djava.home=${home.absolutePath}",
            "-Djava.library.path=" + libDirs.joinToString(":") { it.absolutePath }
        )
        appendLog("Orden: " + JvmCommandBuilder.describe(argv))

        InProcessLaunch.onExit = { code ->
            appendLog("=== EL JVM TERMINÓ CON CÓDIGO $code ===")
        }
        val started = runCatching {
            InProcessLaunch.start(
                runtimeHome = home,
                libDirs = libDirs,
                workDir = workDir,
                argv = argv,
                onLine = { line -> appendLog(line) },
                env = gameProcess.environment(major) + mapOf("JAVA_HOME" to home.absolutePath)
            )
        }.getOrElse { error ->
            appendLog("ERROR: ${error.message}")
            false
        }
        appendLog(
            if (started) "JVM en marcha en el proceso de la app. Esperando salida…"
            else "El JVM no arrancó (mira las líneas de arriba)."
        )
        if (started) {
            appendLog("Nota: solo se puede crear una máquina virtual por proceso.")
            appendLog("Reinicia la app antes de pulsar JUGAR.")
        }
    }

    // ---------------- Utilidades ----------------

    private fun appendLog(line: String) {
        Log.i(TAG, line)
        val updated = (_logs.value + line).takeLast(MAX_LOG_LINES)
        _logs.value = updated
        // Persistir también en el archivo de registro del launcher
        runCatching {
            val launcherLog = paths.gameRoot.resolve("logs/glauncher-session.log")
            launcherLog.parentFile?.mkdirs()
            launcherLog.appendText("$line\n")
        }
    }

    private fun playSfx(resId: Int) {
        if (!_state.value.settings.sounds) return
        Sfx.play(getApplication(), resId)
    }

    private companion object {
        const val MAX_LOG_LINES = 2000
        const val TAG = "GLauncher"
    }
}
