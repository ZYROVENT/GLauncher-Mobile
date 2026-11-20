package com.zyrovent.glauncher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.ui.theme.NeonPurpleSoft
import com.zyrovent.glauncher.ui.theme.TextMuted

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val badge: String? = null
)

/** Barra lateral fina, pegada al borde y sin superficie maciza: un hilo de luz
 *  vertical con los iconos encima. Sin scroll horizontal, sin desbordes. */
@Composable
fun OptimizedNavRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(56.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RailLogo()
        items.forEachIndexed { index, item ->
            RailButton(
                item = item,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun ColumnScope.RailLogo() {
    Box(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFB26BFF), Color(0xFF6E3AF7))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "G",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun RailButton(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF5B2FD6) else Color.Transparent,
        animationSpec = spring(Spring.StiffnessLow)
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            pressed && !selected -> Color(0xFF3A2766).copy(alpha = 0.6f)
            selected && pressed -> Color(0xFF5B2FD6).copy(alpha = 0.9f)
            selected -> Color(0xFF3A2766).copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        animationSpec = spring(Spring.StiffnessLow)
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = 1.4.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected) Color.White else Color(0xFF9E98C0),
            modifier = Modifier.size(20.dp)
        )
    }
}
