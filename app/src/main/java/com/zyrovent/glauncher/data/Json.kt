package com.zyrovent.glauncher.data

import org.json.JSONObject

/**
 * `org.json` devuelve la cadena `"null"` cuando el valor es JSON null, por lo
 * que `optString(...).ifBlank { null }` deja pasar textos "null" a la UI.
 * Aquí se normaliza todo eso a null de verdad.
 */
fun JSONObject.textOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val raw = optString(key).trim()
    if (raw.isEmpty()) return null
    if (raw.equals("null", ignoreCase = true) || raw.equals("undefined", ignoreCase = true)) return null
    return raw
}

/** Igual que [textOrNull] pero con valor de reserva. */
fun JSONObject.textOr(key: String, fallback: String): String = textOrNull(key) ?: fallback
