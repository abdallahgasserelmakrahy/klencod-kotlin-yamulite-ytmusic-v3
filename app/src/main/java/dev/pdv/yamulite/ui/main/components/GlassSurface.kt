package dev.pdv.yamulite.ui.main.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import dev.pdv.yamulite.ui.theme.LocalIos

/** الخلفية التي تنعكس داخل الزجاج: طبقة مسجَّلة + موضعها في النافذة. */
class GlassBackdrop(
    val layer: GraphicsLayer,
    val origin: () -> Offset,
)

/** الخلفية المتاحة للعناصر الزجاجية العائمة داخل [LiquidScaffold] (null = لا تمويه). */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** حافة لامعة بنمط Liquid Glass (ضوء من الأعلى واليسار وانعكاس خافت من الأسفل). */
fun glassRimBrush(alphaTop: Float = 0.55f, alphaBottom: Float = 0.32f): Brush =
    Brush.linearGradient(
        0f to Color.White.copy(alpha = alphaTop),
        0.45f to Color.White.copy(alpha = 0.07f),
        0.55f to Color.White.copy(alpha = 0.07f),
        1f to Color.White.copy(alpha = alphaBottom),
    )

/**
 * سطح Liquid Glass: تمويه حقيقي لما خلفه + صبغة تتكيف مع الفاتح/الداكن + حافة لامعة + ظل.
 * عند غياب [backdrop] (أندرويد أقدم من 12) تُستخدم طبقة شبه معتمة بدل التمويه.
 *
 * @param forceDark للأسطح التي تعلو خلفية داكنة دائماً (مثل شاشة التشغيل).
 * @param tint صبغة ملونة اختيارية (للأزرار البارزة فقط).
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    shape: Shape,
    modifier: Modifier = Modifier,
    forceDark: Boolean = false,
    tint: Color? = null,
    shadowElevation: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = forceDark || LocalIos.current.isDark
    var origin by remember { mutableStateOf(Offset.Zero) }
    val clipShape = shape
    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.28f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.38f else 0.16f),
            )
            .clip(shape)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(30f, 30f, TileMode.Clamp)
                        clip = true
                        this.shape = clipShape
                    }
                    .drawBehind {
                        val o = origin - backdrop.origin()
                        translate(-o.x, -o.y) { drawLayer(backdrop.layer) }
                    },
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f))
                        else listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.40f))
                    )
                ),
            )
        } else {
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xFF3A3A3C).copy(alpha = 0.86f), Color(0xFF2C2C2E).copy(alpha = 0.86f))
                        else listOf(Color.White.copy(alpha = 0.94f), Color(0xFFF2F2F7).copy(alpha = 0.92f))
                    )
                ),
            )
        }
        if (tint != null) {
            Box(Modifier.matchParentSize().background(tint))
        }
        content()
        Box(
            Modifier.matchParentSize().border(
                1.dp,
                if (dark) glassRimBrush() else glassRimBrush(0.95f, 0.70f),
                shape,
            )
        )
    }
}

/**
 * تأثير حافة التمرير (Scroll Edge Effect): المحتوى يتمويه ويتلاشى تحت الأشرطة العائمة
 * بدل أن يصطدم بها أو يُطلى خلفها بلون معتم.
 */
@Composable
fun ScrollEdge(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    top: Boolean = true,
) {
    val ios = LocalIos.current
    val fade = ios.groupedBackground
    var origin by remember { mutableStateOf(Offset.Zero) }
    val mask = if (top) {
        Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black, 1f to Color.Black)
    }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            },
    ) {
        if (backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = BlurEffect(22f, 22f, TileMode.Clamp) }
                    .drawBehind {
                        val o = origin - backdrop.origin()
                        translate(-o.x, -o.y) { drawLayer(backdrop.layer) }
                    },
            )
        }
        val a = if (backdrop != null) 0.55f else 0.94f
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    if (top) listOf(fade.copy(alpha = a), fade.copy(alpha = a * 0.8f))
                    else listOf(fade.copy(alpha = a * 0.8f), fade.copy(alpha = a))
                )
            )
        )
    }
}

/**
 * هيكل الشاشة بطبقتين كما في iOS 26:
 *  - [content] طبقة المحتوى (تمتد خلف الأشرطة، وتُسجَّل نسخة منها للتمويه)
 *  - [overlay] الطبقة الوظيفية العائمة (الأزرار والأشرطة الزجاجية) وتستقبل الخلفية
 * لا تضع عناصر زجاجية داخل [content] أبداً: الزجاج يُعرض فقط في [overlay].
 */
@Composable
fun LiquidScaffold(
    modifier: Modifier = Modifier,
    background: Color = LocalIos.current.groupedBackground,
    overlay: @Composable BoxScope.(GlassBackdrop?) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember(layer, blurOk) {
        if (blurOk) GlassBackdrop(layer) { origin } else null
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .onGloballyPositioned { origin = it.positionInWindow() }
                .drawWithContent {
                    if (blurOk) {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    } else {
                        drawContent()
                    }
                },
            content = content,
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            overlay(backdrop)
        }
    }
}
