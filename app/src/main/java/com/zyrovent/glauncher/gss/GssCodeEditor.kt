package com.zyrovent.glauncher.gss

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockVoid

/**
 * VisualTransformation para Syntax Highlighting de GSS v7.0:
 * - Palabras clave Luau (local, for, do, end, if, then, else, task.wait, spawn, in, style, play sound, notify) -> Morado / Verde
 * - Selectores semánticos (navbar, card, button, modal, scrollbar, etc.) -> Cyan neón
 * - Cadenas de texto ("...") -> Amarillo oro
 * - Comentarios (-- ...) -> Gris tenue
 * - Propiedades / Alias (fondo:, letra:, borde:, redondeado:, etc.) -> Verde menta
 */
class GssSyntaxTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val annotated = buildAnnotatedString {
            append(raw)

            val lines = raw.lines()
            var offset = 0
            for (line in lines) {
                val trimmed = line.trimStart()
                val lineStart = offset
                val lineEnd = offset + line.length

                // Comentarios: -- o #
                val commentIdx = line.indexOf("--").takeIf { it >= 0 } ?: line.indexOf("#").takeIf { it >= 0 }
                if (commentIdx != null) {
                    addStyle(
                        SpanStyle(color = Color(0xFF6B7280), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        lineStart + commentIdx,
                        lineEnd
                    )
                }

                // Strings entre comillas "..."
                var inQuote = false
                var quoteStart = 0
                for (i in line.indices) {
                    if (commentIdx != null && i >= commentIdx) break
                    val char = line[i]
                    if (char == '"' || char == '\'') {
                        if (!inQuote) {
                            inQuote = true
                            quoteStart = i
                        } else {
                            inQuote = false
                            addStyle(
                                SpanStyle(color = Color(0xFFFACC15)), // Amarillo
                                lineStart + quoteStart,
                                lineStart + i + 1
                            )
                        }
                    }
                }

                offset += line.length + 1 // +1 por \n
            }

            // Regex de palabras clave Luau / GSS
            val keywordsRegex = Regex("\\b(local|for|do|end|if|then|else|task\\.wait|play sound|notify|spawn|in|style)\\b")
            for (match in keywordsRegex.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFFC084FC), fontWeight = FontWeight.Bold), // Morado Luau
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Regex de Selectores semánticos
            val selectorsRegex = Regex("\\b(navbar|barra_navegacion|navbar_btn|navbar_btn_active|button|botones|button_secondary|card|tarjetas|mini_player|mini_reproductor|modal|ventana_flotante|campos_texto|input|scrollbar|barra_desplazamiento):")
            for (match in selectorsRegex.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFF00F0FF), fontWeight = FontWeight.SemiBold), // Cyan neón
                    match.range.first,
                    match.range.last + 1
                )
            }

            // Regex de Alias en Español / Propiedades
            val propsRegex = Regex("\\b(fondo|background|letra|color|texto_color|redondeado|esquinas|border-radius|borde|border|brillo|sombra|box-shadow|relleno|padding|margen|margin|desenfoque|opacidad|peso_letra|transicion|text|icon|move|move_x|move_y|sound|on_click):")
            for (match in propsRegex.findAll(raw)) {
                addStyle(
                    SpanStyle(color = Color(0xFF4ADE80)), // Verde menta
                    match.range.first,
                    match.range.last + 1
                )
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

/**
 * Editor de código profesional para GSS con números de línea y syntax highlighter.
 */
@Composable
fun GssCodeEditor(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val linesCount = remember(code) { code.lines().size.coerceAtLeast(1) }
    val syntax = remember { GssSyntaxTransformation() }

    Box(
        modifier = modifier
            .background(BedrockVoid)
            .border(1.dp, BedrockBorderSoft)
            .verticalScroll(scrollState)
            .padding(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Columna de números de línea
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .padding(end = 6.dp)
            ) {
                for (i in 1..linesCount) {
                    androidx.compose.material3.Text(
                        text = "$i",
                        color = Color(0xFF4B5563),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }

            // Campo de texto con coloreado de sintaxis
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Color(0xFFE5E7EB),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                cursorBrush = SolidColor(Color(0xFF00F0FF)),
                visualTransformation = syntax
            )
        }
    }
}
