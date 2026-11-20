package com.zyrovent.glauncher.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zyrovent.glauncher.UiState
import com.zyrovent.glauncher.gss.GssCodeEditor
import com.zyrovent.glauncher.ui.components.BedrockWindow
import com.zyrovent.glauncher.ui.components.IconBrush
import com.zyrovent.glauncher.ui.components.IconCart
import com.zyrovent.glauncher.ui.components.IconCheck
import com.zyrovent.glauncher.ui.components.IconClose
import com.zyrovent.glauncher.ui.components.IconGamepad
import com.zyrovent.glauncher.ui.components.IconNote
import com.zyrovent.glauncher.ui.components.IconPlay
import com.zyrovent.glauncher.ui.components.IconSearch
import com.zyrovent.glauncher.ui.components.IconStar
import com.zyrovent.glauncher.ui.components.McButton
import com.zyrovent.glauncher.ui.components.McButtonTone
import com.zyrovent.glauncher.ui.components.McCompactField
import com.zyrovent.glauncher.ui.components.McTag
import com.zyrovent.glauncher.ui.components.PixelDivider
import com.zyrovent.glauncher.ui.components.PixelIcon
import com.zyrovent.glauncher.ui.components.PixelText
import com.zyrovent.glauncher.ui.theme.BedrockBorder
import com.zyrovent.glauncher.ui.theme.BedrockBorderSoft
import com.zyrovent.glauncher.ui.theme.BedrockGold
import com.zyrovent.glauncher.ui.theme.BedrockGreen
import com.zyrovent.glauncher.ui.theme.BedrockHighlight
import com.zyrovent.glauncher.ui.theme.BedrockPanel
import com.zyrovent.glauncher.ui.theme.BedrockRow
import com.zyrovent.glauncher.ui.theme.BedrockText
import com.zyrovent.glauncher.ui.theme.BedrockTextDim
import com.zyrovent.glauncher.ui.theme.BedrockVoid
import com.zyrovent.glauncher.ui.theme.Gold

data class ShopItem(
    val id: String,
    val title: String,
    val author: String,
    val category: String, // "HUDs", "Temas .GSS", "Controles", "Insignias"
    val priceGCoins: Int,
    val description: String,
    val version: String = "1.0",
    val primaryColor: Color = BedrockGreen,
    val secondaryColor: Color = BedrockHighlight,
    val gssScript: String = ""
)

/**
 * Tienda Oficial de GLauncher Mobile con Iconos 16x16 nativos y Vista Previa Interactiva.
 */
@Composable
fun ShopScreen(state: UiState) {
    var selectedCategory by remember { mutableStateOf("Todo") }
    var searchQuery by remember { mutableStateOf("") }
    var previewItem by remember { mutableStateOf<ShopItem?>(null) }
    var userItems by remember { mutableStateOf(listOf<ShopItem>()) }
    var showPublishModal by remember { mutableStateOf(false) }

    val categories = listOf("Todo", "HUDs", "Temas .GSS", "Controles", "Insignias")

    val catalogItems = userItems.ifEmpty {
        emptyList<ShopItem>()
    }

    val filteredItems = catalogItems.filter { item ->
        (selectedCategory == "Todo" || item.category == selectedCategory) &&
        (searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true) || item.author.contains(searchQuery, ignoreCase = true))
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Columna izquierda: Categorías y Acciones ----
            BedrockWindow(
                title = "Tienda",
                subtitle = "Creaciones GSS & HUDs",
                icon = IconCart,
                modifier = Modifier.fillMaxHeight().weight(0.32f),
                fillHeight = true
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) BedrockRow else Color.Transparent)
                                .border(1.dp, if (isSelected) BedrockGreen else BedrockBorderSoft.copy(alpha = 0.5f))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelIcon(
                                art = when (cat) {
                                    "HUDs" -> IconGamepad
                                    "Temas .GSS" -> IconBrush
                                    "Controles" -> IconGamepad
                                    else -> IconCart
                                },
                                size = 16.dp,
                                tint = if (isSelected) BedrockGreen else BedrockTextDim
                            )
                            Spacer(Modifier.width(8.dp))
                            PixelText(
                                text = cat,
                                color = if (isSelected) BedrockText else BedrockTextDim,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                PixelDivider()

                // Panel explicativo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BedrockVoid.copy(alpha = 0.6f))
                        .border(1.dp, BedrockBorderSoft)
                        .padding(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PixelIcon(art = IconBrush, size = 16.dp, tint = BedrockGold)
                            Spacer(Modifier.width(6.dp))
                            PixelText(
                                text = "GSS ENGINE v7.0",
                                color = BedrockGold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        PixelText(
                            text = "Sandbox AST: Luau + CSS puro. Selecciona cualquier creación para ver su Vista Previa en tiempo real.",
                            color = BedrockTextDim,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                McButton(
                    text = "+ PUBLICAR MI TEMA",
                    onClick = { showPublishModal = true },
                    tone = McButtonTone.Success,
                    height = 36.dp,
                    icon = IconStar,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---- Columna derecha: Catálogo y Vista Previa ----
            BedrockWindow(
                title = "Catálogo - $selectedCategory",
                subtitle = "${filteredItems.size} creaciones disponibles",
                icon = IconBrush,
                modifier = Modifier.fillMaxHeight().weight(0.68f),
                fillHeight = true
            ) {
                // Buscador
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    McCompactField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Buscar temas .gss, huds, autores...",
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )
                    McButton(
                        text = "BUSCAR",
                        onClick = {},
                        icon = IconSearch,
                        tone = McButtonTone.Ghost,
                        height = 36.dp,
                        modifier = Modifier.width(96.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        ShopItemCard(
                            item = item,
                            onPreview = { previewItem = item }
                        )
                    }
                }
            }
        }

        // ================= MODAL DE VISTA PREVIA =================
        previewItem?.let { item ->
            ItemPreviewModal(
                item = item,
                onClose = { previewItem = null },
                onApply = {
                    if (item.gssScript.isNotBlank()) {
                        com.zyrovent.glauncher.gss.GssEngine.parseAndRunGss(item.gssScript, "${item.id}.gss")
                    }
                    previewItem = null
                }
            )
        }

        // Modal de publicación rápida
        if (showPublishModal) {
            PublishItemModal(
                onClose = { showPublishModal = false },
                onPublish = { newItem ->
                    userItems = userItems + newItem
                    showPublishModal = false
                }
            )
        }
    }
}

@Composable
private fun ShopItemCard(
    item: ShopItem,
    onPreview: () -> Unit
) {
    val currentTheme = com.zyrovent.glauncher.gss.GssEngine.currentTheme
    val isApplied = currentTheme.name == item.title

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BedrockRow.copy(alpha = 0.85f))
            .border(1.dp, if (isApplied) BedrockGreen else BedrockBorderSoft)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Icono 16x16 en caja cuadrada
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(BedrockVoid)
                .border(1.dp, BedrockBorderSoft),
            contentAlignment = Alignment.Center
        ) {
            PixelIcon(
                art = when (item.category) {
                    "HUDs" -> IconGamepad
                    "Temas .GSS" -> IconBrush
                    "Controles" -> IconGamepad
                    else -> IconCart
                },
                size = 16.dp,
                tint = item.primaryColor
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText(
                    text = item.title,
                    color = BedrockText,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.width(6.dp))
                McTag(item.category, item.primaryColor)
            }
            PixelText(
                text = "Autor: ${item.author} · v${item.version}",
                color = BedrockGreen,
                style = MaterialTheme.typography.labelSmall
            )
            PixelText(
                text = item.description,
                color = BedrockTextDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText(
                    text = if (item.priceGCoins == 0) "GRATIS" else "${item.priceGCoins} GCoins",
                    color = if (item.priceGCoins == 0) BedrockGreen else Gold,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                McButton(
                    text = "PREVIA",
                    onClick = onPreview,
                    tone = McButtonTone.Ghost,
                    height = 32.dp,
                    modifier = Modifier.width(76.dp)
                )

                McButton(
                    text = if (isApplied) "APLICADO" else "APLICAR",
                    onClick = {
                        if (isApplied) {
                            com.zyrovent.glauncher.gss.GssEngine.resetToDefault()
                        } else if (item.gssScript.isNotBlank()) {
                            com.zyrovent.glauncher.gss.GssEngine.parseAndRunGss(item.gssScript, "${item.id}.gss")
                        }
                    },
                    tone = if (isApplied) McButtonTone.Ghost else McButtonTone.Success,
                    height = 32.dp,
                    modifier = Modifier.width(84.dp)
                )
            }
        }
    }
}

/**
 * Vista previa interactiva que simula la interfaz con los colores y botones del tema.
 */
@Composable
private fun ItemPreviewModal(
    item: ShopItem,
    onClose: () -> Unit,
    onApply: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .background(BedrockPanel)
                .border(2.dp, item.primaryColor)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(art = IconBrush, size = 16.dp, tint = item.primaryColor)
                        Spacer(Modifier.width(8.dp))
                        PixelText(
                            text = "VISTA PREVIA — ${item.title}",
                            color = BedrockText,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Box(Modifier.clickable(onClick = onClose)) {
                        PixelIcon(art = IconClose, size = 16.dp, tint = BedrockTextDim)
                    }
                }

                PixelDivider()

                // Simulación visual en vivo de la UI con los colores del tema
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(BedrockVoid)
                        .border(1.dp, item.primaryColor)
                        .padding(10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Barra superior simulada
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(item.secondaryColor.copy(alpha = 0.2f))
                                .border(1.dp, item.primaryColor.copy(alpha = 0.5f))
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PixelText(text = "GLAUNCHER MOBILE", color = item.primaryColor, style = MaterialTheme.typography.labelSmall)
                            PixelText(text = "100% ⚡", color = BedrockGreen, style = MaterialTheme.typography.labelSmall)
                        }

                        // Tarjeta y botón de ejemplo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .background(item.primaryColor.copy(alpha = 0.15f))
                                    .border(1.dp, item.primaryColor.copy(alpha = 0.4f))
                                    .padding(6.dp)
                            ) {
                                PixelText(text = "Minecraft 1.21.4", color = BedrockText, style = MaterialTheme.typography.bodySmall)
                            }

                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(38.dp)
                                    .background(item.primaryColor)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                PixelText(text = "JUGAR", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                PixelText(
                    text = item.description,
                    color = BedrockTextDim,
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McButton(
                        text = "CERRAR",
                        onClick = onClose,
                        tone = McButtonTone.Ghost,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        text = "APLICAR AHORA",
                        onClick = onApply,
                        tone = McButtonTone.Success,
                        height = 36.dp,
                        icon = IconCheck,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PublishItemModal(
    onClose: () -> Unit,
    onPublish: (ShopItem) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("MiUsuario") }
    var script by remember {
        mutableStateOf(
            """
            -- ===============================
            -- Mi Nuevo Tema .GSS v7.0
            -- ===============================
            local color_neon = "#00f0ff"
            
            navbar:
                fondo: "rgba(10, 15, 20, 0.95)"
                borde: "2px solid " + color_neon
                redondeado: "14px"
            end
            
            button:
                fondo: color_neon
                letra: "#000000"
                redondeado: "10px"
            end
            
            card:
                fondo: "rgba(15, 20, 28, 0.9)"
                borde: "1px solid " + color_neon
                redondeado: "12px"
            end
            
            notify("¡Tema aplicado!")
            """.trimIndent()
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(480.dp)
                .background(BedrockPanel)
                .border(2.dp, BedrockGreen)
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelIcon(art = IconBrush, size = 16.dp, tint = BedrockGreen)
                        Spacer(Modifier.width(8.dp))
                        PixelText(
                            text = "EDITOR DE TEMAS GSS v7.0",
                            color = BedrockHighlight,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Box(Modifier.clickable(onClick = onClose)) {
                        PixelIcon(art = IconClose, size = 16.dp, tint = BedrockTextDim)
                    }
                }

                PixelDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McCompactField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = "Nombre del Tema...",
                        modifier = Modifier.weight(1f),
                        height = 36.dp
                    )

                    McCompactField(
                        value = author,
                        onValueChange = { author = it },
                        placeholder = "Autor...",
                        modifier = Modifier.weight(0.7f),
                        height = 36.dp
                    )
                }

                // Editor de código profesional con coloreado de sintaxis
                GssCodeEditor(
                    code = script,
                    onCodeChange = { script = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    McButton(
                        text = "CANCELAR",
                        onClick = onClose,
                        tone = McButtonTone.Ghost,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                    McButton(
                        text = "PUBLICAR TEMA",
                        onClick = {
                            if (title.isNotBlank()) {
                                onPublish(
                                    ShopItem(
                                        id = "user_${System.currentTimeMillis()}",
                                        title = title,
                                        author = author,
                                        category = "Temas .GSS",
                                        priceGCoins = 0,
                                        description = "Tema publicado por la comunidad con GSS v7.0.",
                                        gssScript = script
                                    )
                                )
                            }
                        },
                        tone = McButtonTone.Success,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
