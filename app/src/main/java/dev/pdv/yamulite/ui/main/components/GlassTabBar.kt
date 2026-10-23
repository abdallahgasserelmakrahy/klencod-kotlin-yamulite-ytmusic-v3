package dev.pdv.yamulite.ui.main.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/** المساحة السفلية التي يجب أن تتركها القوائم حتى لا يغطي الشريط العائم آخر عناصرها. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

val GlassBarHeight = 64.dp

data class GlassTabItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

/**
 * شريط تبويبات عائم بنمط Liquid Glass (iOS 26):
 *  - كبسولة زجاجية تعكس ما خلفها (تمويه حقيقي على أندرويد 12+)
 *  - عدسة زجاجية تظلّل التبويب المحدد، تتمدد أثناء الحركة وترتفع عند اللمس
 *  - اضغط واسحب فوق الشريط لتنتقل العدسة مع إصبعك وتُحدَّد عند الرفع
 */
@Composable
fun GlassTabBar(
    items: List<GlassTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: GraphicsLayer?,
    contentOrigin: () -> Offset,
    modifier: Modifier = Modifier,
    /** يصغر الشريط ويخفي التسميات عند تمرير القائمة للأسفل (Tab Bar Minimize في iOS 26). */
    compact: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val count = items.size
    // ترتيب العرض (الداخل دائماً LTR، ونعكس القائمة يدوياً عند العربية)
    val order = remember(count, rtl) { if (rtl) (count - 1 downTo 0).toList() else (0 until count).toList() }
    val selectedSlot = order.indexOf(selectedIndex).coerceAtLeast(0)

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val blurOk = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val haptic = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOrder by rememberUpdatedState(order)

    var pressed by remember { mutableStateOf(false) }
    var fingerSlot by remember { mutableFloatStateOf(0f) }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }

    val target = if (pressed) fingerSlot.coerceIn(0.5f, count - 0.5f) else selectedSlot + 0.5f
    val center by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 420f),
        label = "lensCenter",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "lensPress",
    )
    val barH by animateDpAsState(
        targetValue = if (compact) 52.dp else GlassBarHeight,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "barH",
    )
    val labelA by animateFloatAsState(if (compact) 0f else 1f, label = "labelA")
    val stretch = (abs(target - center)).coerceIn(0f, 1.6f) * 0.28f
    val visualSlot = if (pressed) (center - 0.5f).roundToInt().coerceIn(0, count - 1) else selectedSlot

    // ألوان الزجاج
    val bodyTop = if (dark) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.62f)
    val bodyBottom = if (dark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.40f)
    val fallbackBase = MaterialTheme.colorScheme.surfaceColorAtElevation3(dark)
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
        0.45f to Color.White.copy(alpha = if (dark) 0.07f else 0.25f),
        0.55f to Color.White.copy(alpha = if (dark) 0.07f else 0.25f),
        1f to Color.White.copy(alpha = if (dark) 0.32f else 0.70f),
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(barH)
                .shadow(
                    elevation = 22.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.40f),
                )
                .clip(CircleShape)
                .onGloballyPositioned { barOrigin = it.positionInWindow() },
        ) {
            val itemW = maxWidth / count

            // 1) الخلفية الزجاجية: نسخة مموّهة من المحتوى خلف الشريط
            if (blurOk) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect = BlurEffect(34f, 34f, TileMode.Clamp)
                            clip = true
                            shape = CircleShape
                        }
                        .drawBehind {
                            val o = barOrigin - contentOrigin()
                            translate(-o.x, -o.y) { drawLayer(backdrop!!) }
                        },
                )
            }
            // 2) صبغة الزجاج (أكثف على الإصدارات التي لا تدعم التمويه)
            Box(
                Modifier.fillMaxSize().background(
                    if (blurOk) Brush.verticalGradient(listOf(bodyTop, bodyBottom))
                    else Brush.verticalGradient(
                        listOf(fallbackBase.copy(alpha = 0.92f), fallbackBase.copy(alpha = 0.86f))
                    )
                ),
            )

            // 3) العدسة الزجاجية للتبويب المحدد
            val lensW = (itemW - 8.dp) * (1f + stretch + 0.07f * press)
            val lensH = barH - 8.dp + 6.dp * press
            val lensX = itemW * center - lensW / 2
            val lensTop = if (dark) 0.22f + 0.10f * press else 0.95f
            val lensBottom = if (dark) 0.12f + 0.06f * press else 0.72f
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(lensX.roundToPx(), 0) }
                    .size(lensW, lensH)
                    .shadow(
                        elevation = (if (dark) 4.dp else 7.dp) + 6.dp * press,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.30f),
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = lensTop),
                                Color.White.copy(alpha = lensBottom),
                            )
                        )
                    )
                    .border(0.8.dp, rim, CircleShape),
            )

            // 4) التبويبات + منطق اللمس والسحب
            Row(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        awaitEachGesture {
                            val slotW = size.width / count.toFloat()
                            val down = awaitFirstDown(requireUnconsumed = false)
                            pressed = true
                            fingerSlot = down.position.x / slotW
                            var last = fingerSlot.toInt().coerceIn(0, count - 1)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var change = down
                            do {
                                val event = awaitPointerEvent()
                                change = event.changes.firstOrNull() ?: break
                                fingerSlot = change.position.x / slotW
                                val s = fingerSlot.toInt().coerceIn(0, count - 1)
                                if (s != last) {
                                    last = s
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                change.consume()
                            } while (change.pressed)
                            pressed = false
                            currentOnSelect(currentOrder[last])
                        }
                    },
            ) {
                order.forEachIndexed { slot, originalIndex ->
                    val item = items[originalIndex]
                    val active = slot == visualSlot
                    val tint by animateColorAsState(
                        targetValue = if (active) accent
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.78f else 0.70f),
                        label = "tabTint",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "tabIconScale",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .semantics(mergeDescendants = true) {
                                role = Role.Tab
                                selected = originalIndex == selectedIndex
                                onClick { currentOnSelect(originalIndex); true }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (active) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier
                                .size(25.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 10.5.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.alpha(labelA).height(14.dp * labelA),
                        )
                    }
                }
            }

            // 5) حافة لامعة رفيعة تحيط بالكبسولة
            Box(Modifier.fillMaxSize().border(1.dp, rim, CircleShape))
        }
    }
}

private fun androidx.compose.material3.ColorScheme.surfaceColorAtElevation3(dark: Boolean): Color =
    if (dark) this.surfaceContainerHigh else this.surfaceContainerLowest
