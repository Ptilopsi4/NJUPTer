package com.example.njupter.widget.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.material3.ColorProviders as material3ColorProviders
import androidx.glance.unit.ColorProvider

// MATCHING the app's Color.kt course colors
val WidgetLightColors = listOf(
    Color(0xFFFFCDD2), Color(0xFFE1BEE7), Color(0xFFC5CAE9), Color(0xFFBBDEFB),
    Color(0xFFB2DFDB), Color(0xFFDCEDC8), Color(0xFFFFE0B2), Color(0xFFFFCCBC)
)

val WidgetDarkColors = listOf(
    Color(0xFF5C2B29), Color(0xFF4A335C), Color(0xFF33375C), Color(0xFF264057),
    Color(0xFF1E4E56), Color(0xFF2D4B33), Color(0xFF5D4018), Color(0xFF5C263B)
)

fun getColorForIndex(name: String, colorIndex: Int, colors: List<Color>): Color {
    val idx = if (colorIndex in colors.indices) {
        colorIndex
    } else {
        if (colors.isNotEmpty()) (name.hashCode() and Int.MAX_VALUE) % colors.size else 0
    }
    return colors[idx]
}

private fun cp(color: Color) = ColorProvider(color)

object WidgetColorProviders {
    val Light: ColorProviders = colorProviders(
        primary = cp(Color(0xFF1A6D37)),
        onPrimary = cp(Color(0xFFFFFFFF)),
        primaryContainer = cp(Color(0xFFA7F5B7)),
        onPrimaryContainer = cp(Color(0xFF002110)),
        secondary = cp(Color(0xFF506352)),
        onSecondary = cp(Color(0xFFFFFFFF)),
        secondaryContainer = cp(Color(0xFFD3E8D3)),
        onSecondaryContainer = cp(Color(0xFF0E1F13)),
        tertiary = cp(Color(0xFF3A656F)),
        onTertiary = cp(Color(0xFFFFFFFF)),
        tertiaryContainer = cp(Color(0xFFBDEAF6)),
        onTertiaryContainer = cp(Color(0xFF001F26)),
        error = cp(Color(0xFFBA1A1A)),
        errorContainer = cp(Color(0xFFFFDAD6)),
        onError = cp(Color(0xFFFFFFFF)),
        onErrorContainer = cp(Color(0xFF410002)),
        background = cp(Color(0xFFF5FBF0)),
        onBackground = cp(Color(0xFF171D18)),
        surface = cp(Color(0xFFF5FBF0)),
        onSurface = cp(Color(0xFF171D18)),
        surfaceVariant = cp(Color(0xFFDDE5D9)),
        onSurfaceVariant = cp(Color(0xFF424940)),
        outline = cp(Color(0xFF727970)),
        inverseOnSurface = cp(Color(0xFFEDF2E8)),
        inverseSurface = cp(Color(0xFF2C322D)),
        inversePrimary = cp(Color(0xFF8CD8A6)),
        widgetBackground = cp(Color(0xFFF5FBF0))
    )

    val Dark: ColorProviders = colorProviders(
        primary = cp(Color(0xFF81DDA0)),
        onPrimary = cp(Color(0xFF003919)),
        primaryContainer = cp(Color(0xFF005227)),
        onPrimaryContainer = cp(Color(0xFFA7F5B7)),
        secondary = cp(Color(0xFFB7CCB8)),
        onSecondary = cp(Color(0xFF233426)),
        secondaryContainer = cp(Color(0xFF394B3B)),
        onSecondaryContainer = cp(Color(0xFFD3E8D3)),
        tertiary = cp(Color(0xFFA2CED9)),
        onTertiary = cp(Color(0xFF02363F)),
        tertiaryContainer = cp(Color(0xFF214D56)),
        onTertiaryContainer = cp(Color(0xFFBDEAF6)),
        error = cp(Color(0xFFFFB4AB)),
        errorContainer = cp(Color(0xFF93000A)),
        onError = cp(Color(0xFF690005)),
        onErrorContainer = cp(Color(0xFFFFDAD6)),
        background = cp(Color(0xFF0E1510)),
        onBackground = cp(Color(0xFFDEE4DA)),
        surface = cp(Color(0xFF0E1510)),
        onSurface = cp(Color(0xFFDEE4DA)),
        surfaceVariant = cp(Color(0xFF424940)),
        onSurfaceVariant = cp(Color(0xFFC2C8BD)),
        outline = cp(Color(0xFF8C9389)),
        inverseOnSurface = cp(Color(0xFF2C322D)),
        inverseSurface = cp(Color(0xFFDEE4DA)),
        inversePrimary = cp(Color(0xFF1A6D37)),
        widgetBackground = cp(Color(0xFF0E1510))
    )
}

@Composable
fun WidgetTheme(
    colors: ColorProviders,
    content: @Composable () -> Unit
) {
    GlanceTheme(
        colors = colors,
        content = content
    )
}

fun widgetColorProviders(context: Context, isDark: Boolean): ColorProviders {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return material3ColorProviders(
            light = dynamicLightColorScheme(context),
            dark = dynamicDarkColorScheme(context)
        )
    }
    return if (isDark) WidgetColorProviders.Dark else WidgetColorProviders.Light
}
