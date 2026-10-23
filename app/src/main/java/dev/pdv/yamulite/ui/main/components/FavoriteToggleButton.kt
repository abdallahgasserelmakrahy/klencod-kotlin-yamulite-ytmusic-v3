package dev.pdv.yamulite.ui.main.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import dev.pdv.yamulite.ui.theme.LocalIos

@Composable
fun FavoriteToggleButton(
    isLiked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    inactiveTint: androidx.compose.ui.graphics.Color = LocalIos.current.secondaryLabel,
) {
    val ios = LocalIos.current
    val haptic = LocalHapticFeedback.current
    var showConfirm by remember { mutableStateOf(false) }

    // نبضة نابضة عند الإضافة إلى المفضلة (لا تُشغَّل عند أول ظهور للصف)
    val pop = remember { Animatable(1f) }
    var prev by remember { mutableStateOf(isLiked) }
    LaunchedEffect(isLiked) {
        if (isLiked && !prev) {
            pop.snapTo(0.55f)
            pop.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = 500f))
        }
        prev = isLiked
    }

    Box(
        modifier = modifier.liquidPress(pressedScale = 0.86f, onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (isLiked) showConfirm = true else onToggle()
        }),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isLiked) "إزالة من المفضلة" else "إضافة إلى المفضلة",
            tint = if (isLiked) ios.red else inactiveTint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
    }

    if (showConfirm) {
        LiquidAlert(
            title = "إزالة من المفضلة؟",
            message = "ستتم إزالة المقطع من المفضلة.",
            confirmText = "إزالة",
            dismissText = "إلغاء",
            destructive = true,
            onConfirm = {
                showConfirm = false
                onToggle()
            },
            onDismiss = { showConfirm = false },
        )
    }
}
