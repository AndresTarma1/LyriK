package com.example.melodist.ui.themes

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import lyrik.composeapp.generated.resources.Res
import lyrik.composeapp.generated.resources.roboto_bold
import lyrik.composeapp.generated.resources.roboto_medium
import lyrik.composeapp.generated.resources.roboto_regular
import org.jetbrains.compose.resources.Font

@Composable
fun robotoFamily(): FontFamily = FontFamily(
    Font(Res.font.roboto_regular, FontWeight.Normal),
    Font(Res.font.roboto_medium, FontWeight.Medium),
    Font(Res.font.roboto_bold, FontWeight.Bold),
)

/**
 * Roboto-based type scale tuned to feel like YouTube Music: medium-weight titles, slightly tighter
 * tracking, comfortable body sizes. Applied app-wide via [AppTheme].
 */
@Composable
fun melodistTypography(): Typography {
    val f = robotoFamily()
    val d = Typography()
    return Typography(
        displayLarge = d.displayLarge.copy(fontFamily = f, fontWeight = FontWeight.Normal),
        displayMedium = d.displayMedium.copy(fontFamily = f, fontWeight = FontWeight.Normal),
        displaySmall = d.displaySmall.copy(fontFamily = f, fontWeight = FontWeight.Normal),
        headlineLarge = d.headlineLarge.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
        headlineMedium = d.headlineMedium.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
        headlineSmall = d.headlineSmall.copy(fontFamily = f, fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.2).sp),
        titleLarge = d.titleLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 21.sp, letterSpacing = (-0.2).sp),
        titleMedium = d.titleMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 16.sp, letterSpacing = 0.sp),
        titleSmall = d.titleSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.sp),
        bodyLarge = d.bodyLarge.copy(fontFamily = f, fontSize = 15.sp, letterSpacing = 0.1.sp),
        bodyMedium = d.bodyMedium.copy(fontFamily = f, fontSize = 13.5.sp, letterSpacing = 0.1.sp),
        bodySmall = d.bodySmall.copy(fontFamily = f, fontSize = 12.sp, letterSpacing = 0.2.sp),
        labelLarge = d.labelLarge.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.1.sp),
        labelMedium = d.labelMedium.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
        labelSmall = d.labelSmall.copy(fontFamily = f, fontWeight = FontWeight.Medium, fontSize = 10.5.sp),
    )
}
