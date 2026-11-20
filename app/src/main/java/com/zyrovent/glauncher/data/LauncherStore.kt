package com.zyrovent.glauncher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistencia sencilla sobre SharedPreferences: perfiles, ajustes y el último
 * manifest de versiones descargado (guardado como archivo para no inflar prefs).
 */
class LauncherStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val manifestCacheFile = File(appContext.filesDir, "version_manifest_v2.json")

    // ---------------- Cuentas ----------------

    fun accounts(): List<Account> {
        val raw = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val username = obj.optString("username")
                    if (username.isNotBlank()) {
                        add(
                            Account(
                                username = username,
                                type = runCatching {
                                    AccountType.valueOf(obj.optString("type"))
                                }.getOrDefault(AccountType.OFFLINE)
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAccounts(accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("username", account.username)
                    .put("type", account.type.name)
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun selectedAccountName(): String? = prefs.getString(KEY_SELECTED_ACCOUNT, null)

    fun saveSelectedAccountName(username: String?) {
        prefs.edit().apply {
            if (username == null) remove(KEY_SELECTED_ACCOUNT) else putString(KEY_SELECTED_ACCOUNT, username)
        }.apply()
    }

    // ---------------- Ajustes ----------------

    fun settings(): LauncherSettings {
        val defaults = LauncherSettings()
        return LauncherSettings(
            selectedVersion = prefs.getString(KEY_VERSION, defaults.selectedVersion) ?: defaults.selectedVersion,
            // Un ajuste guardado demasiado alto (2 GB en un cel de 3,7 GB)
            // hacía que Android matara el juego por falta de memoria.
            ramMb = MemoryBudget.clamp(appContext, prefs.getInt(KEY_RAM, defaults.ramMb)),
            renderer = prefs.getString(KEY_RENDERER, defaults.renderer) ?: defaults.renderer,
            sounds = prefs.getBoolean(KEY_SOUNDS, defaults.sounds),
            wallpaper = prefs.getInt(KEY_WALLPAPER, defaults.wallpaper),
            customJvmArgs = prefs.getString(KEY_JVM_ARGS, defaults.customJvmArgs) ?: defaults.customJvmArgs,
            useJoystick = prefs.getBoolean(KEY_USE_JOYSTICK, defaults.useJoystick),
            controlsOpacity = prefs.getFloat(KEY_CONTROLS_OPACITY, defaults.controlsOpacity),
            controlsScale = prefs.getFloat(KEY_CONTROLS_SCALE, defaults.controlsScale),
            mouseSensitivity = prefs.getFloat(KEY_MOUSE_SENSITIVITY, defaults.mouseSensitivity),
            joyDeadzone = prefs.getFloat(KEY_JOY_DEADZONE, defaults.joyDeadzone)
                .coerceIn(0.4f, 1.6f),
            sprintOnFullPush = prefs.getBoolean(KEY_SPRINT_PUSH, defaults.sprintOnFullPush),
            invertCameraY = prefs.getBoolean(KEY_INVERT_Y, defaults.invertCameraY),
            virtualButtons = decodeButtons(prefs.getString(KEY_VIRTUAL_BUTTONS, null)),
            dpadOffsetX = prefs.getFloat("dpad_offset_x", defaults.dpadOffsetX),
            dpadOffsetY = prefs.getFloat("dpad_offset_y", defaults.dpadOffsetY),
            actionsOffsetX = prefs.getFloat("actions_offset_x", defaults.actionsOffsetX),
            actionsOffsetY = prefs.getFloat("actions_offset_y", defaults.actionsOffsetY)
        )
    }

    fun saveSettings(settings: LauncherSettings) {
        prefs.edit()
            .putString(KEY_VERSION, settings.selectedVersion)
            .putInt(KEY_RAM, settings.ramMb)
            .putString(KEY_RENDERER, settings.renderer)
            .putBoolean(KEY_SOUNDS, settings.sounds)
            .putInt(KEY_WALLPAPER, settings.wallpaper)
            .putString(KEY_JVM_ARGS, settings.customJvmArgs)
            .putBoolean(KEY_USE_JOYSTICK, settings.useJoystick)
            .putFloat(KEY_CONTROLS_OPACITY, settings.controlsOpacity)
            .putFloat(KEY_CONTROLS_SCALE, settings.controlsScale)
            .putFloat(KEY_MOUSE_SENSITIVITY, settings.mouseSensitivity)
            .putFloat(KEY_JOY_DEADZONE, settings.joyDeadzone)
            .putBoolean(KEY_SPRINT_PUSH, settings.sprintOnFullPush)
            .putBoolean(KEY_INVERT_Y, settings.invertCameraY)
            .putString(KEY_VIRTUAL_BUTTONS, encodeButtons(settings.virtualButtons))
            .putFloat("dpad_offset_x", settings.dpadOffsetX)
            .putFloat("dpad_offset_y", settings.dpadOffsetY)
            .putFloat("actions_offset_x", settings.actionsOffsetX)
            .putFloat("actions_offset_y", settings.actionsOffsetY)
            .apply()
    }

    // ---------------- Controles virtuales ----------------

    private fun encodeButtons(buttons: List<VirtualButton>): String =
        VirtualButtonCodec.encode(buttons)

    private fun decodeButtons(raw: String?): List<VirtualButton> =
        VirtualButtonCodec.decode(raw) ?: VirtualButton.defaults()

    // ---------------- Manifest en caché ----------------

    fun cachedManifestJson(): String? = runCatching {
        if (manifestCacheFile.exists()) manifestCacheFile.readText().takeIf { it.isNotBlank() } else null
    }.getOrNull()

    fun saveManifestJson(json: String) {
        runCatching { manifestCacheFile.writeText(json) }
    }

    fun activeGssTheme(): String? = prefs.getString(KEY_ACTIVE_GSS_THEME, null)

    fun saveActiveGssTheme(gssContent: String?) {
        prefs.edit().apply {
            if (gssContent == null) remove(KEY_ACTIVE_GSS_THEME) else putString(KEY_ACTIVE_GSS_THEME, gssContent)
        }.apply()
    }

    private companion object {
        const val PREFS_NAME = "glaucher_prefs"
        const val KEY_ACCOUNTS = "accounts"
        const val KEY_SELECTED_ACCOUNT = "selected_account"
        const val KEY_VERSION = "selected_version"
        const val KEY_RAM = "ram_mb"
        const val KEY_RENDERER = "renderer"
        const val KEY_SOUNDS = "sounds"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_JVM_ARGS = "custom_jvm_args"
        const val KEY_USE_JOYSTICK = "use_joystick"
        const val KEY_CONTROLS_OPACITY = "controls_opacity"
        const val KEY_CONTROLS_SCALE = "controls_scale"
        const val KEY_MOUSE_SENSITIVITY = "mouse_sensitivity"
        const val KEY_JOY_DEADZONE = "joy_deadzone"
        const val KEY_SPRINT_PUSH = "sprint_full_push"
        const val KEY_INVERT_Y = "invert_camera_y"
        const val KEY_VIRTUAL_BUTTONS = "virtual_buttons"
        const val KEY_ACTIVE_GSS_THEME = "active_gss_theme"
    }
}
