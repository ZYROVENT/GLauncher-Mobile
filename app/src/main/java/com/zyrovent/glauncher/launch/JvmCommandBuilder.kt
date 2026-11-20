package com.zyrovent.glauncher.launch

import com.zyrovent.glauncher.data.Account
import com.zyrovent.glauncher.data.GamePaths
import com.zyrovent.glauncher.data.LauncherSettings
import java.io.File
import java.util.UUID

/**
 * Construye la línea de comandos del cliente. Es la misma estructura que usa el
 * launcher oficial: un proceso Java con el jar del cliente como classpath.
 */
object JvmCommandBuilder {

    fun build(
        account: Account,
        settings: LauncherSettings,
        paths: GamePaths,
        versionId: String,
        assetIndex: String?,
        runtimeMajor: Int,
        classpath: List<String>,
        nativesDir: File,
        mods: Int = 0
    ): List<String> {
        val args = mutableListOf<String>()
        args += paths.runtimeJava(runtimeMajor).absolutePath
        args += "-Xmx${settings.ramMb}M"
        // Ajustes de la VM calculados desde el hardware del celular y el número
        // de mods de la instancia (metaspace y recolector).
        DeviceTuning.recommendedArgs(paths.appContext, settings.ramMb, runtimeMajor, mods)
            .forEach { args += it }
        // El volcado de la VM (hs_err) se escribe junto a la instancia: es la
        // única pista si el proceso muere dentro de HotSpot.
        args += "-XX:ErrorFile=${paths.instanceDir(versionId).absolutePath}/hs_err_%p.log"
        // java.library.path sale de las carpetas reales que tienen .so en el
        // runtime instalado, más las natives que prepara el launcher.
        val libDirs = (
            listOf(nativesDir, paths.appLibDir, paths.librariesDir) +
                RuntimeLayout.libDirs(paths.runtimeDir(runtimeMajor))
            )
            .distinctBy { it.absolutePath }
            .map { it.absolutePath }
        args += "-Djava.library.path=${libDirs.joinToString(":")}"
        // LWJGL busca aquí dentro; el directorio lo prepara NativeLibs con las
        // natives de la APK y los alias libGLX/libGL que pide el cliente desktop.
        args += "-Dorg.lwjgl.librarypath=${nativesDir.absolutePath}"
        // Gl4es en lugar de libGLX.so.0, que no existe en Android.
        args += "-Dorg.lwjgl.opengl.libname=${NativeLibs.rendererLibrary(settings.renderer)}"
        args += "-Dminecraft.launcher.brand=GLauncher"
        args += "-Dminecraft.launcher.version=1.0"
        splitArgs(settings.customJvmArgs).forEach { args += it }
        args += "-cp"
        args += classpath.joinToString(":")

        // Minecraft anterior a 1.6 no usa el lanzador moderno: la clase principal
        // es Minecraft y recibe usuario y sesión por posición, no con --flags.
        if (isPreModernLauncher(versionId)) {
            args += "net.minecraft.client.Minecraft"
            args += account.username
            args += "0"
            return args
        }

        args += "net.minecraft.client.main.Main"
        args += "--username"
        args += account.username
        args += "--version"
        args += versionId
        args += "--gameDir"
        args += paths.instanceDir(versionId).absolutePath
        args += "--assetsDir"
        args += paths.assetsDir.absolutePath
        args += "--assetIndex"
        args += assetIndex ?: versionId
        args += "--uuid"
        args += offlineUuid(account.username)
        args += "--accessToken"
        args += "0"
        args += "--userType"
        args += "legacy"
        args += "--versionType"
        args += "release"
        return args
    }

    /** 1.5.2 y anteriores: launcher antiguo (clase Minecraft, args posicionales). */
    fun isPreModernLauncher(versionId: String): Boolean {
        val id = versionId.substringBefore('-').trim()
        val parts = id.split('.')
        if (parts.firstOrNull() != "1") return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return false
        return minor < 6
    }

    /** UUID determinista, igual al que usa Minecraft para perfiles offline. */
    fun offlineUuid(username: String): String =
        UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString()

    fun describe(args: List<String>): String = args.joinToString(" ") { arg ->
        if (arg.contains(' ')) "\"$arg\"" else arg
    }

    private fun splitArgs(raw: String): List<String> =
        raw.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
}
