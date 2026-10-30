package io.github.conor2803.coach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.conor2803.coach.R

/** Manrope carries the brand roles (display, headline, title); body and labels stay on the system face. */
private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
    Font(R.font.manrope, FontWeight.ExtraBold),
)

private val base = Typography()

val CoachTypography = Typography(
    displayLarge = base.displayLarge.brand(FontWeight.Bold, (-1.5).sp),
    displayMedium = base.displayMedium.brand(FontWeight.Bold, (-1).sp),
    displaySmall = base.displaySmall.brand(FontWeight.Bold, (-0.5).sp),
    headlineLarge = base.headlineLarge.brand(FontWeight.Bold, (-0.5).sp),
    headlineMedium = base.headlineMedium.brand(FontWeight.Bold, (-0.25).sp),
    headlineSmall = base.headlineSmall.brand(FontWeight.SemiBold, 0.sp),
    titleLarge = base.titleLarge.brand(FontWeight.SemiBold, 0.sp),
    titleMedium = base.titleMedium.brand(FontWeight.SemiBold, 0.sp),
    titleSmall = base.titleSmall.brand(FontWeight.SemiBold, 0.sp),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge,
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

private fun TextStyle.brand(weight: FontWeight, tracking: androidx.compose.ui.unit.TextUnit) =
    copy(fontFamily = Manrope, fontWeight = weight, letterSpacing = tracking)
