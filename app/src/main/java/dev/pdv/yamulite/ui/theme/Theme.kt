package dev.pdv.yamulite.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * ألوان iOS 26 الدلالية (System Colors) كما في Human Interface Guidelines.
 * كل لون له نسخة فاتحة وداكنة، ولا يُستخدم أي لون ثابت خارج هذه القائمة.
 */
@Immutable
data class IosPalette(
    val isDark: Boolean,
    /** systemBlue: لون التمييز الافتراضي لـ iOS. غيّر هذا السطر وحده لتغيير هوية التطبيق. */
    val tint: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** systemFill / secondarySystemFill / tertiarySystemFill */
    val fill: Color,
    val secondaryFill: Color,
    val tertiaryFill: Color,
    /** systemGroupedBackground */
    val groupedBackground: Color,
    /** secondarySystemGroupedBackground (بطاقات القوائم المجمّعة) */
    val groupedCard: Color,
    val red: Color,
    val green: Color,
)

val IosLight = IosPalette(
    isDark = false,
    tint = Color(0xFF007AFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x33787880),
    secondaryFill = Color(0x29787880),
    tertiaryFill = Color(0x1F767680),
    groupedBackground = Color(0xFFF2F2F7),
    groupedCard = Color(0xFFFFFFFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
)

val IosDark = IosPalette(
    isDark = true,
    tint = Color(0xFF0A84FF),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x5C787880),
    secondaryFill = Color(0x52787880),
    tertiaryFill = Color(0x3D767680),
    groupedBackground = Color(0xFF000000),
    groupedCard = Color(0xFF1C1C1E),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
)

val LocalIos = staticCompositionLocalOf { IosLight }

/** سلّم الخطوط في iOS (Dynamic Type الافتراضي): Large Title 34 … Caption2 11. */
private val IosTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** زوايا iOS 26 متحدة المركز: كلما كبر العنصر كبر نصف قطره، والأزرار كبسولات. */
private val IosShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun YaMuLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val ios = if (darkTheme) IosDark else IosLight
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = ios.tint,
            onPrimary = Color.White,
            background = ios.groupedBackground,
            onBackground = ios.label,
            surface = ios.groupedBackground,
            onSurface = ios.label,
            surfaceVariant = ios.fill,
            onSurfaceVariant = ios.secondaryLabel,
            surfaceContainerLowest = ios.groupedBackground,
            surfaceContainerLow = ios.groupedCard,
            surfaceContainer = ios.groupedCard,
            surfaceContainerHigh = Color(0xFF2C2C2E),
            surfaceContainerHighest = Color(0xFF3A3A3C),
            outline = ios.separator,
            outlineVariant = ios.separator,
            error = ios.red,
            onError = Color.White,
            errorContainer = ios.red.copy(alpha = 0.18f),
            onErrorContainer = ios.red,
        )
    } else {
        lightColorScheme(
            primary = ios.tint,
            onPrimary = Color.White,
            background = ios.groupedBackground,
            onBackground = ios.label,
            surface = ios.groupedBackground,
            onSurface = ios.label,
            surfaceVariant = ios.fill,
            onSurfaceVariant = ios.secondaryLabel,
            surfaceContainerLowest = ios.groupedCard,
            surfaceContainerLow = ios.groupedCard,
            surfaceContainer = ios.groupedCard,
            surfaceContainerHigh = ios.groupedCard,
            surfaceContainerHighest = ios.groupedBackground,
            outline = ios.separator,
            outlineVariant = ios.separator,
            error = ios.red,
            onError = Color.White,
            errorContainer = ios.red.copy(alpha = 0.14f),
            onErrorContainer = ios.red,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // شريطا النظام شفافان ليظهر المحتوى خلفهما (حافة إلى حافة) كما في iOS
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalIos provides ios) {
        MaterialTheme(
            colorScheme = colors,
            typography = IosTypography,
            shapes = IosShapes,
            content = content,
        )
    }
}
