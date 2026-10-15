package moe.shizuku.manager.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import moe.shizuku.manager.ShizukuSettings

/** Shizuku brand indigo, matching the XML theme's primaryColor. */
private val BrandColor = Color(0xFF3F51B5)

/**
 * True while the pure black (AMOLED) scheme is in use. Cards and the page are the
 * same colour in that scheme, so surfaces need something else to stay visible see
 * [moe.shizuku.manager.ui.component.SegmentedColumn].
 */
val LocalAmoledTheme = staticCompositionLocalOf { false }

/**
 * Whether the Liquid Glass theme is on, and the shared [HazeState] every glass surface reads
 * its backdrop from. A surface calls [Modifier.liquidGlassSurface] rather than reading this
 * directly; it is exposed mainly so [LiquidGlassBackdrop] can register itself as the source.
 */
data class GlassContext(val enabled: Boolean, val dark: Boolean, val hazeState: HazeState)

val LocalGlass = staticCompositionLocalOf { GlassContext(enabled = false, dark = false, hazeState = HazeState()) }

/** Bumped when a theme preference changes so the theme re-reads the prefs. */
object ThemeState {
    var version by mutableIntStateOf(0)
        private set

    fun refresh() {
        version++
    }
}

@Composable
fun ShizukuTheme(content: @Composable () -> Unit) {
    // Read the counter so theme changes recompose the tree.
    ThemeState.version

    val context = LocalContext.current
    val prefs = ShizukuSettings.getPreferences()

    val darkTheme = when (ShizukuSettings.getNightMode()) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme()
    }

    // On by default: the system palette is the one another app can replace, and replacing it is
    // the whole point of ColorBlendr and the apps like it - they push an overlay that rewrites
    // the system's own colour roles, and a screen that reads those roles follows.
    val useSystemColor = prefs.getBoolean(ShizukuSettings.Keys.KEY_USE_SYSTEM_COLOR, true) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val amoled = darkTheme && prefs.getBoolean(ShizukuSettings.Keys.KEY_BLACK_NIGHT_THEME, false)

    // The system's scheme taken whole, not re-derived from one of its colours.
    //
    // This was `seed = dynamicScheme(context).primary` fed back into material-kolor, which is a
    // Material 3 palette but not the one the system is showing: it regenerates from a single
    // seed and throws away the secondary, the tertiary, the neutral tones and every per-shade
    // override - exactly the parts a ColorBlendr theme sets. Reading the roles themselves is
    // what makes the app match the colours the user picked rather than merely resemble them.
    val baseScheme = if (useSystemColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // The brand seed, for when the system palette is not wanted: a fixed seed avoids a
        // washed-out grey palette on a desaturated wallpaper.
        rememberDynamicColorScheme(
            seedColor = BrandColor,
            isDark = darkTheme,
            isAmoled = amoled,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2021,
        )
    }

    val liquidGlass = prefs.getBoolean(ShizukuSettings.Keys.KEY_LIQUID_GLASS, false)
    val hazeState = rememberHazeState()

    // Glass surfaces (see liquidGlassSurface below) paint their own blurred, refractive
    // background, so the shared colour scheme only needs to go near-transparent: just enough
    // tint that a surface this file doesn't know about (a dialog, a menu) still reads as a
    // pane rather than vanishing.
    //
    // The AMOLED switch only repaints the base surface roles, while the container roles that
    // the cards and the navigation bar actually use kept the standard dark greys a black page
    // with grey cards. Spread the black across every surface role; row dividers keep the list
    // readable without the card shape.
    val colorScheme = if (liquidGlass) baseScheme.asLiquidGlass(darkTheme) else if (amoled) baseScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color.Black,
        surfaceContainerHigh = Color.Black,
        surfaceContainerHighest = Color.Black,
    ) else baseScheme

    // Match the status/navigation bar icons to the app's theme, not the system's;
    // otherwise a white in-app theme gets light icons on a white bar (invisible).
    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(darkTheme) {
            val window = view.context.findActivity()?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Glass reuses the AMOLED flag on purpose: every card, row and pill that draws a hairline
    // outline on the black theme draws the same hairline here, and on glass it is the edge
    // that makes a translucent surface read as a pane instead of a smudge.
    CompositionLocalProvider(
        LocalAmoledTheme provides (amoled || liquidGlass),
        LocalGlass provides GlassContext(liquidGlass, darkTheme, hazeState),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
        ) {
            // One Box in every mode, so flipping the switch only adds or removes the backdrop
            // and the screens above keep their state instead of being rebuilt.
            Box(modifier = Modifier.fillMaxSize()) {
                if (liquidGlass) LiquidGlassBackdrop(darkTheme, hazeState)
                content()
            }
        }
    }
}

/**
 * The scheme with the page transparent and every container a faint, tinted pane, so a surface
 * this theme doesn't know to wrap in real glass (a dialog, a menu, a third-party composable)
 * still reads as one instead of vanishing against the backdrop. Text and accent roles are left
 * as they were, which keeps contrast and lets the system palette (or the brand seed) still
 * colour the accents.
 */
private fun ColorScheme.asLiquidGlass(dark: Boolean): ColorScheme {
    val tint = if (dark) Color(0xFF1B2247) else Color.White
    return copy(
        background = Color.Transparent,
        surface = tint.copy(alpha = if (dark) 0.22f else 0.30f),
        surfaceDim = Color.Transparent,
        surfaceBright = tint.copy(alpha = if (dark) 0.28f else 0.38f),
        surfaceContainerLowest = tint.copy(alpha = if (dark) 0.18f else 0.24f),
        surfaceContainerLow = tint.copy(alpha = if (dark) 0.24f else 0.32f),
        surfaceContainer = tint.copy(alpha = if (dark) 0.30f else 0.40f),
        surfaceContainerHigh = tint.copy(alpha = if (dark) 0.36f else 0.46f),
        surfaceContainerHighest = tint.copy(alpha = if (dark) 0.44f else 0.54f),
        outlineVariant = Color.White.copy(alpha = if (dark) 0.22f else 0.85f),
    )
}

/**
 * The real, refractive Liquid Glass material (via [Haze](https://github.com/chrisbanes/haze)),
 * applied when the theme is on and a no-op otherwise, so every call site can chain it
 * unconditionally. [shape] both bounds the glass's blur/refraction and should match whatever
 * shape the caller clips its own surface to - pass the same `MaterialTheme.shapes.large` (or
 * whatever shape) given to the `Surface` this modifies.
 *
 * Haze samples whatever was drawn under [LiquidGlassBackdrop] (registered as its one source),
 * so this only has an effect inside [ShizukuTheme]. On a device or API level that cannot run
 * the blur shader, Haze itself falls back to a plain translucent tint - the same look the
 * colour-scheme override above already gives every other surface - so nothing more is needed
 * here for that case.
 */
@Composable
fun Modifier.liquidGlassSurface(shape: Shape): Modifier {
    val glass = LocalGlass.current
    if (!glass.enabled) return this

    val style = remember(shape, glass.dark) {
        GlassStyle.regular.then {
            tint(Color.White.copy(alpha = if (glass.dark) 0.08f else 0.22f))
            shape(shape as? androidx.compose.foundation.shape.RoundedCornerShape ?: androidx.compose.foundation.shape.RoundedCornerShape(androidx.compose.ui.unit.Dp(28f)))
        }
    }
    return this.hazeGlass(input = HazeInput.Sources(glass.hazeState), style = style)
}

/**
 * A slow-moving field of coloured light behind the page, registered as the one [HazeState]
 * source every [liquidGlassSurface] blurs and refracts: the colour and the motion are what the
 * glass above is made of.
 */
@Composable
private fun LiquidGlassBackdrop(dark: Boolean, hazeState: HazeState) {
    val transition = rememberInfiniteTransition(label = "liquidGlass")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidGlassPhase"
    )

    Canvas(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
        val w = size.width
        val h = size.height
        val reach = max(w, h)
        val t = (phase * 2.0 * PI).toFloat()

        drawRect(
            brush = Brush.verticalGradient(
                colors = if (dark) listOf(Color(0xFF060A1A), Color(0xFF0D1230))
                else listOf(Color(0xFFE6EEFF), Color(0xFFF4ECFF))
            )
        )

        fun blob(color: Color, alpha: Float, cx: Float, cy: Float, radius: Float) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius
                )
            )
        }

        val a = if (dark) 0.55f else 0.60f
        blob(
            if (dark) Color(0xFF22D3EE) else Color(0xFF7DD3FC), a,
            w * (0.25f + 0.12f * cos(t)), h * (0.20f + 0.08f * sin(t)), reach * 0.55f
        )
        blob(
            if (dark) Color(0xFF8B5CF6) else Color(0xFFC4B5FD), a,
            w * (0.80f + 0.10f * sin(t + 1.6f)), h * (0.35f + 0.10f * cos(t + 1.6f)), reach * 0.60f
        )
        blob(
            if (dark) Color(0xFF3B82F6) else Color(0xFF93C5FD), a,
            w * (0.30f + 0.12f * sin(t + 3.1f)), h * (0.78f + 0.08f * cos(t + 3.1f)), reach * 0.60f
        )
        blob(
            if (dark) Color(0xFFEC4899) else Color(0xFFF9A8D4), a * 0.60f,
            w * (0.85f + 0.08f * cos(t + 4.4f)), h * (0.88f + 0.06f * sin(t + 4.4f)), reach * 0.45f
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}
