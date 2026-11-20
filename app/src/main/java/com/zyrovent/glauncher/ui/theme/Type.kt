package com.zyrovent.glauncher.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zyrovent.glauncher.R

val Minecraftia = FontFamily(Font(R.font.minecraftia, FontWeight.Normal))
val MinecraftTen = FontFamily(Font(R.font.minecraft_ten, FontWeight.Normal))
val Montserrat = FontFamily(Font(R.font.montserrat_bold, FontWeight.Bold))

val GLauncherTypography = Typography(
    displayLarge = TextStyle(fontFamily = MinecraftTen, fontSize = 44.sp, letterSpacing = 2.sp),
    displayMedium = TextStyle(fontFamily = MinecraftTen, fontSize = 32.sp, letterSpacing = 1.5.sp),
    headlineMedium = TextStyle(fontFamily = Minecraftia, fontSize = 22.sp, letterSpacing = 1.sp),
    // Sin estos, Compose caía en Roboto y el texto dejaba de parecer Minecraft.
    headlineSmall = TextStyle(fontFamily = Minecraftia, fontSize = 19.sp, letterSpacing = 0.8.sp),
    titleSmall = TextStyle(fontFamily = Minecraftia, fontSize = 14.sp, letterSpacing = 0.4.sp),
    titleLarge = TextStyle(fontFamily = Montserrat, fontSize = 17.sp, letterSpacing = 0.3.sp),
    titleMedium = TextStyle(fontFamily = Minecraftia, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = Montserrat, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = Montserrat, fontSize = 12.5.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = Montserrat, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = Minecraftia, fontSize = 17.sp, letterSpacing = 1.sp),
    labelMedium = TextStyle(fontFamily = Minecraftia, fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Minecraftia, fontSize = 11.sp, letterSpacing = 0.5.sp)
)
