package com.example.njupter.ui.animation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压缩放。刻意不用 Modifier.composed：composed 会为每个调用点额外开一个组合作用域，
 * 在课表这类高密度节点树里会直接放大滑动/重组成本。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    scaleIn: Float = 0.95f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleIn else 1f,
        animationSpec = tween(120),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
