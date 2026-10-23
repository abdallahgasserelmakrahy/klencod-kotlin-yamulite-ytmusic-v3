package dev.pdv.yamulite.ui.main.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.pdv.yamulite.ui.theme.LocalIos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ───────────────────────────── التفاعل (الضغط) ─────────────────────────────

/**
 * ضغط بنمط Liquid Glass التفاعلي: العنصر يكبر قليلاً ويرتد بنابض عند اللمس (بلا تموّج Material).
 * استخدم pressedScale أقل من 1 للأزرار الصغيرة المسطحة.
 */
fun Modifier.liquidPress(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 1.06f,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "liquidPress",
    )
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(
            interactionSource = src,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** صفّ قائمة iOS: يُظلَّل بلون fill رمادي عند اللمس بدل تموّج Material. */
fun Modifier.iosRowClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val ios = LocalIos.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 40 else 280),
        label = "rowHighlight",
    )
    this
        .drawBehind {
            if (a > 0f) drawRect(ios.fill.copy(alpha = ios.fill.alpha * a))
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(LocalIos.current.separator))
}

// ───────────────────────────── الأزرار ─────────────────────────────

/**
 * زر كبسولة: زجاجي افتراضياً (نص بلون التمييز)، أو [prominent] ملوّن بالكامل.
 * قاعدة Apple: زر بارز واحد أو اثنان في الشاشة كحدّ أقصى.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    destructive: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val ios = LocalIos.current
    val backdrop = LocalGlassBackdrop.current
    val accent = if (destructive) ios.red else ios.tint
    val shape = CircleShape
    val label: @Composable () -> Unit = {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 50.dp).padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) {
                Icon(icon, null, tint = if (prominent) Color.White else accent, modifier = Modifier.size(20.dp))
            }
            Text(
                text,
                color = if (prominent) Color.White else accent,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .liquidPress(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (prominent) {
            Box(
                Modifier
                    .shadow(
                        elevation = 12.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = accent.copy(alpha = 0.30f),
                        spotColor = accent.copy(alpha = 0.45f),
                    )
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.16f), accent)))
                    .border(1.dp, glassRimBrush(0.8f, 0.35f), shape),
                contentAlignment = Alignment.Center,
            ) { label() }
        } else {
            GlassSurface(backdrop, shape) { label() }
        }
    }
}

/** زر أيقونة مسطّح بحجم لمس 44dp (للصفوف: القلب، التنزيل…). */
@Composable
fun LiquidIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalIos.current.secondaryLabel,
    iconSize: Dp = 24.dp,
    size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).liquidPress(pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** زر دائري زجاجي عائم (رجوع، إجراءات الشريط العلوي). */
@Composable
fun GlassCircleButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        backdrop = LocalGlassBackdrop.current,
        shape = CircleShape,
        modifier = modifier
            .size(size)
            .liquidPress(onClick = onClick)
            .semantics { contentDescription = description },
        shadowElevation = 10.dp,
    ) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center, content = content)
    }
}

/** شيفرون الرجوع (SF Symbol: chevron.backward) ويتبدّل اتجاهه مع RTL. */
@Composable
fun BackChevron(color: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(width = 12.dp, height = 20.dp)) {
        val sw = 2.8.dp.toPx()
        val a = sw / 2
        val b = size.width - sw / 2
        val tip = if (rtl) b else a
        val tail = if (rtl) a else b
        val p = Path().apply {
            moveTo(tail, a)
            lineTo(tip, size.height / 2)
            lineTo(tail, size.height - a)
        }
        drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** شيفرون التنقّل الأمامي في الصفوف (chevron.forward) ويتبدّل مع RTL. */
@Composable
fun Chevron(color: Color, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(width = 8.dp, height = 14.dp)) {
        val sw = 2.2.dp.toPx()
        val a = sw / 2
        val b = size.width - sw / 2
        val tip = if (rtl) a else b
        val tail = if (rtl) b else a
        val p = Path().apply {
            moveTo(tail, a)
            lineTo(tip, size.height / 2)
            lineTo(tail, size.height - a)
        }
        drawPath(p, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// ───────────────────────────── الشريط العلوي والعناوين ─────────────────────────────

/** ارتفاع صف الأزرار في الشريط العلوي (من دون شريط الحالة). */
val LiquidTopBarHeight = 60.dp

@Composable
fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * الشريط العلوي العائم: تأثير حافة تمرير + زر رجوع زجاجي + عنوان مضمَّن يظهر عند تمرير العنوان الكبير.
 */
@Composable
fun BoxScope.LiquidTopBar(
    backdrop: GlassBackdrop?,
    title: String,
    titleVisible: Boolean,
    onBack: (() -> Unit)?,
    backDescription: String = "رجوع",
) {
    val ios = LocalIos.current
    val top = statusBarTop()
    ScrollEdge(
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + LiquidTopBarHeight + 14.dp),
    )
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = top)
            .height(LiquidTopBarHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassCircleButton(description = backDescription, onClick = onBack) {
                BackChevron(color = ios.label)
            }
        } else {
            Spacer(Modifier.size(44.dp))
        }
        val a by animateFloatAsState(if (titleVisible) 1f else 0f, tween(180), label = "inlineTitle")
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).alpha(a),
            style = MaterialTheme.typography.titleMedium,
            color = ios.label,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(44.dp))
    }
}

/** العنوان الكبير (Large Title) في أعلى القائمة ويتحرك معها. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val ios = LocalIos.current
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, color = ios.label)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ios.secondaryLabel)
        }
    }
}

// ───────────────────────────── حقل البحث ─────────────────────────────

@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
) {
    val ios = LocalIos.current
    val focus = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyLarge
    GlassSurface(LocalGlassBackdrop.current, CircleShape, modifier.height(50.dp), shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Search, null, tint = ios.secondaryLabel, modifier = Modifier.size(22.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = textStyle.copy(color = ios.label),
                cursorBrush = SolidColor(ios.tint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onSearch()
                    focus.clearFocus()
                }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = textStyle, color = ios.secondaryLabel, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            if (value.isNotEmpty()) {
                Box(
                    Modifier
                        .size(22.dp)
                        .liquidPress(pressedScale = 0.85f, onClick = { onValueChange("") })
                        .semantics { contentDescription = "مسح" }
                        .clip(CircleShape)
                        .background(ios.secondaryLabel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close, null,
                        tint = if (ios.isDark) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// ───────────────────────────── التحكم المقسَّم ─────────────────────────────

/**
 * Segmented Control بنمط iOS 26: كبسولة زجاجية وعدسة زجاجية تنزلق نحو الخيار المحدد.
 * اضغط أو اسحب لتبديل الخيار؛ يُحدَّد الخيار عند رفع الإصبع.
 */
@Composable
fun GlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ios = LocalIos.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val n = items.size
    val order = remember(n, rtl) { if (rtl) (n - 1 downTo 0).toList() else (0 until n).toList() }
    val selectedSlot = order.indexOf(selectedIndex).coerceAtLeast(0)
    val haptic = LocalHapticFeedback.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOrder by rememberUpdatedState(order)

    var pressed by remember { mutableStateOf(false) }
    var fingerSlot by remember { mutableFloatStateOf(0f) }
    val target = if (pressed) fingerSlot.coerceIn(0.5f, n - 0.5f) else selectedSlot + 0.5f
    val center by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 480f),
        label = "segCenter",
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "segPress",
    )
    val visualSlot = if (pressed) (center - 0.5f).roundToInt().coerceIn(0, n - 1) else selectedSlot
    val dark = ios.isDark
    val rim = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.55f else 0.95f),
        0.5f to Color.White.copy(alpha = if (dark) 0.07f else 0.25f),
        1f to Color.White.copy(alpha = if (dark) 0.32f else 0.70f),
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        GlassSurface(
            backdrop = LocalGlassBackdrop.current,
            shape = CircleShape,
            modifier = modifier.fillMaxWidth().height(46.dp),
            shadowElevation = 10.dp,
        ) {
            BoxWithConstraints(Modifier.matchParentSize()) {
                val itemW = maxWidth / n
                val lensW = (itemW - 6.dp) * (1f + 0.05f * press)
                val lensH = 46.dp - 6.dp + 4.dp * press
                val lensX = itemW * center - lensW / 2
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(lensX.roundToPx(), 0) }
                        .size(lensW, lensH)
                        .shadow(
                            elevation = (if (dark) 3.dp else 5.dp) + 5.dp * press,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.22f),
                            spotColor = Color.Black.copy(alpha = 0.28f),
                        )
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                if (dark) listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.14f))
                                else listOf(Color.White.copy(alpha = 0.98f), Color.White.copy(alpha = 0.78f))
                            )
                        )
                        .border(0.8.dp, rim, CircleShape),
                )
                Row(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(n) {
                            awaitEachGesture {
                                val slotW = size.width / n.toFloat()
                                val down = awaitFirstDown(requireUnconsumed = false)
                                pressed = true
                                fingerSlot = down.position.x / slotW
                                var last = fingerSlot.toInt().coerceIn(0, n - 1)
                                var change = down
                                do {
                                    val event = awaitPointerEvent()
                                    change = event.changes.firstOrNull() ?: break
                                    fingerSlot = change.position.x / slotW
                                    val s = fingerSlot.toInt().coerceIn(0, n - 1)
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
                        val active = slot == visualSlot
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    selected = originalIndex == selectedIndex
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = items[originalIndex],
                                color = if (active) ios.label else ios.secondaryLabel,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── حالات فارغة ومؤشرات ─────────────────────────────

/** نظير ContentUnavailableView: أيقونة كبيرة رمادية + عنوان + وصف + إجراء اختياري. */
@Composable
fun ContentUnavailable(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val ios = LocalIos.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = ios.tertiaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = ios.label,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = ios.secondaryLabel,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** مؤشر النشاط في iOS: 12 شعاعاً يتلاشى ذيلها أثناء الدوران المتقطّع. */
@Composable
fun IosSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = LocalIos.current.secondaryLabel,
) {
    val t = rememberInfiniteTransition(label = "spinner")
    val step by t.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "spinnerStep",
    )
    Canvas(modifier.size(size)) {
        val head = floor(step).toInt() % 12
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension / 2
        val inner = r * 0.50f
        val sw = r * 0.17f
        for (i in 0 until 12) {
            val age = ((head - i) % 12 + 12) % 12
            val a = 1f - (age / 12f) * 0.85f
            val ang = Math.toRadians(i * 30.0 - 90.0)
            val dx = cos(ang).toFloat()
            val dy = sin(ang).toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * a),
                start = Offset(c.x + dx * inner, c.y + dy * inner),
                end = Offset(c.x + dx * (r - sw / 2), c.y + dy * (r - sw / 2)),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** حلقة تقدّم رفيعة (للتنزيل) مع مربع إيقاف صغير في المنتصف، كما في App Store. */
@Composable
fun IosProgressRing(progress: Float?, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val ios = LocalIos.current
    if (progress == null) {
        IosSpinner(modifier, size)
        return
    }
    Canvas(modifier.size(size)) {
        val sw = 2.5.dp.toPx()
        val inset = sw / 2
        val d = Size(this.size.width - sw, this.size.height - sw)
        drawArc(
            color = ios.fill, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = d, style = Stroke(sw),
        )
        drawArc(
            color = ios.tint, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false, topLeft = Offset(inset, inset), size = d,
            style = Stroke(sw, cap = StrokeCap.Round),
        )
        val sq = this.size.minDimension * 0.30f
        drawRoundRect(
            color = ios.tint,
            topLeft = Offset((this.size.width - sq) / 2, (this.size.height - sq) / 2),
            size = Size(sq, sq),
            cornerRadius = CornerRadius(sq * 0.25f),
        )
    }
}

// ───────────────────────────── التنبيه والإشعار ─────────────────────────────

/**
 * تنبيه Liquid Glass: بطاقة بزوايا كبيرة، أزرار كبسولات، تظهر بنابض مع تمويه ما خلفها.
 * (البطاقة أكثف من الزجاج العادي لأن النص كثير: الإصدار regular حسب إرشادات Apple.)
 */
@Composable
fun LiquidAlert(
    title: String,
    message: String?,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val ios = LocalIos.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                window.setDimAmount(0.32f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val p = window.attributes
                    p.blurBehindRadius = 32
                    window.attributes = p
                }
            }
        }
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val sc by animateFloatAsState(
            targetValue = if (shown) 1f else 1.14f,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
            label = "alertScale",
        )
        val al by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "alertAlpha")
        val shape = RoundedCornerShape(34.dp)
        Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 320.dp)
                    .graphicsLayer { scaleX = sc; scaleY = sc; alpha = al }
                    .shadow(
                        32.dp, shape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.35f),
                    )
                    .clip(shape)
                    .background(
                        if (ios.isDark) Color(0xFF2C2C2E).copy(alpha = 0.90f)
                        else Color.White.copy(alpha = 0.90f)
                    )
                    .border(1.dp, glassRimBrush(if (ios.isDark) 0.5f else 0.9f, if (ios.isDark) 0.3f else 0.6f), shape)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ios.label,
                    textAlign = TextAlign.Center,
                )
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ios.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AlertButton(dismissText, Modifier.weight(1f), AlertButtonKind.Plain, onDismiss)
                    AlertButton(
                        confirmText, Modifier.weight(1f),
                        if (destructive) AlertButtonKind.Destructive else AlertButtonKind.Prominent,
                        onConfirm,
                    )
                }
            }
        }
    }
}

private enum class AlertButtonKind { Plain, Prominent, Destructive }

@Composable
private fun AlertButton(text: String, modifier: Modifier, kind: AlertButtonKind, onClick: () -> Unit) {
    val ios = LocalIos.current
    val bg = when (kind) {
        AlertButtonKind.Plain -> ios.fill
        AlertButtonKind.Prominent -> ios.tint
        AlertButtonKind.Destructive -> ios.red
    }
    Box(
        modifier
            .height(48.dp)
            .liquidPress(pressedScale = 0.96f, onClick = onClick)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (kind == AlertButtonKind.Plain) ios.label else Color.White,
            maxLines = 1,
        )
    }
}

/** مضيف الإشعارات (Snackbar) بكبسولة زجاجية بدل لوحة Material. */
@Composable
fun LiquidSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val ios = LocalIos.current
    SnackbarHost(hostState, modifier) { data ->
        GlassSurface(
            LocalGlassBackdrop.current,
            RoundedCornerShape(26.dp),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = ios.label,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}

// ───────────────────────────── القوائم المجمّعة (Inset Grouped) ─────────────────────────────

@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ios = LocalIos.current
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.bodySmall,
                color = ios.secondaryLabel,
                modifier = Modifier.padding(horizontal = 36.dp).padding(bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(ios.groupedCard),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = ios.secondaryLabel,
                modifier = Modifier.padding(horizontal = 36.dp).padding(top = 8.dp),
            )
        }
    }
}

/** صف اختيار بعلامة ✓ بلون التمييز (النمط القياسي لقوائم الاختيار في iOS). */
@Composable
fun GroupedCheckRow(title: String, selected: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val ios = LocalIos.current
    val a by animateFloatAsState(if (selected) 1f else 0f, tween(160), label = "check")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .iosRowClick(onClick = onClick)
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { this.selected = selected },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ios.label, modifier = Modifier.weight(1f))
            Icon(
                Icons.Rounded.Check, null,
                tint = ios.tint,
                modifier = Modifier.size(22.dp).alpha(a),
            )
        }
        if (showDivider) HairlineDivider(Modifier.padding(start = 20.dp))
    }
}

/** صف إجراء في وسط البطاقة (مثل «تسجيل الخروج» بالأحمر). */
@Composable
fun GroupedActionRow(title: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .iosRowClick(onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
