package com.example.njupter.ui.animation.predictiveback

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import kotlin.math.pow

enum class PredictiveBackAnimation {
    NONE,
    AOSP,
    MIUIX,
    SCALE,
    KSU_CLASSIC
}

enum class PredictiveBackExitDirection {
    FOLLOW_GESTURE,
    ALWAYS_RIGHT,
    ALWAYS_LEFT
}

@Immutable
data class PredictiveBackLayerTransform(
    val translationXFraction: Float = 0f,
    val translationXDp: Float = 0f,
    val scale: Float = 1f,
    val alpha: Float = 1f,
    val pivotX: Float = 0.5f,
    val pivotY: Float = 0.5f,
    val cornerRadiusDp: Float = 0f,
    val dimAlpha: Float = 0f,
    val shadowElevationDp: Float = 0f,
    val shadowAlpha: Float = 0f
)

/** Route transitions used by the matching ReSukiSU animation handler. */
@Immutable
data class NavigationMotionProfile(
    val pushEnterFromX: Float = 1f,
    val pushExitToX: Float = 0f,
    val pushExitToAlpha: Float = 1f,
    val popEnterFromX: Float = -0.25f,
    val popExitToScale: Float = 0.9f,
    val popExitToAlpha: Float = 0f
)

/**
 * Navigation-agnostic form of ReSukiSU's animation contract. Gesture and commit are deliberately
 * separate: ReSukiSU finishes the visible animation before removing the page from its back stack.
 */
interface PredictiveBackAnimationHandler {
    val commitDurationMillis: Int
    val navigationMotion: NavigationMotionProfile

    fun foreground(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform

    fun background(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform

    fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform

    fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform
}

fun predictiveBackAnimationHandler(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection
): PredictiveBackAnimationHandler = when (animation) {
    PredictiveBackAnimation.NONE -> NoPredictiveBackAnimation
    PredictiveBackAnimation.AOSP -> AospPredictiveBackAnimation(exitDirection)
    PredictiveBackAnimation.MIUIX -> MiuixPredictiveBackAnimation(exitDirection)
    PredictiveBackAnimation.SCALE -> ScalePredictiveBackAnimation(exitDirection)
    PredictiveBackAnimation.KSU_CLASSIC -> KernelSuClassicPredictiveBackAnimation(exitDirection)
}

private val StandardDecelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val FastOutSlowIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun easedProgress(progress: Float): Float {
    val clamped = progress.coerceIn(0f, 1f)
    return 1f - (1f - clamped).pow(3)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

private fun directionFor(
    swipeEdge: Int,
    exitDirection: PredictiveBackExitDirection
): Float = when (exitDirection) {
    PredictiveBackExitDirection.FOLLOW_GESTURE ->
        if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
    PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
    PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
}

private fun pivotXFor(swipeEdge: Int): Float =
    if (swipeEdge == BackEventCompat.EDGE_RIGHT) 0.2f else 0.8f

private object NoPredictiveBackAnimation : PredictiveBackAnimationHandler {
    override val commitDurationMillis = 0
    override val navigationMotion = NavigationMotionProfile(
        pushExitToAlpha = 0f
    )

    override fun foreground(progress: Float, swipeEdge: Int, touchYFraction: Float) =
        PredictiveBackLayerTransform()

    override fun background(progress: Float, swipeEdge: Int, touchYFraction: Float) =
        PredictiveBackLayerTransform()

    override fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = PredictiveBackLayerTransform()

    override fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = PredictiveBackLayerTransform()
}

/** Faithful port of ReSukiSU's AOSPCrossActivityAnimation. */
private class AospPredictiveBackAnimation(
    private val exitDirection: PredictiveBackExitDirection
) : PredictiveBackAnimationHandler {
    override val commitDurationMillis = 150
    override val navigationMotion = NavigationMotionProfile()

    private fun dragScale(progress: Float) = 1f - 0.15f * progress.coerceIn(0f, 1f)

    override fun foreground(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = PredictiveBackLayerTransform(
        scale = dragScale(progress),
        pivotX = pivotXFor(swipeEdge),
        pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
        cornerRadiusDp = 28f
    )

    override fun background(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val remaining = 1f - progress.coerceIn(0f, 1f)
        return PredictiveBackLayerTransform(
            translationXDp = -96f * directionFor(swipeEdge, exitDirection),
            scale = dragScale(progress),
            pivotX = pivotXFor(swipeEdge),
            pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
            shadowElevationDp = 18f * remaining,
            shadowAlpha = 0.32f * remaining
        )
    }

    override fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val linearProgress = commitProgress.coerceIn(0f, 1f)
        val emphasizedProgress = StandardDecelerate.transform(linearProgress)
        val startScale = dragScale(gestureProgress)
        return PredictiveBackLayerTransform(
            translationXDp = 96f * directionFor(swipeEdge, exitDirection) * emphasizedProgress,
            scale = lerp(startScale, 0.85f, emphasizedProgress),
            alpha = if (linearProgress >= 0.2f) 0f else 1f - linearProgress * 5f,
            pivotX = pivotXFor(swipeEdge),
            pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
            cornerRadiusDp = 28f
        )
    }

    override fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val emphasizedProgress = StandardDecelerate.transform(commitProgress.coerceIn(0f, 1f))
        val startScale = dragScale(gestureProgress)
        val gestureShadowRemaining = 1f - gestureProgress.coerceIn(0f, 1f)
        val shadowRemaining = gestureShadowRemaining * (1f - emphasizedProgress)
        return PredictiveBackLayerTransform(
            translationXDp = -96f * directionFor(swipeEdge, exitDirection) *
                (1f - emphasizedProgress),
            scale = lerp(startScale, 1f, emphasizedProgress),
            pivotX = pivotXFor(swipeEdge),
            pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
            shadowElevationDp = 18f * shadowRemaining,
            shadowAlpha = 0.32f * shadowRemaining
        )
    }
}

/** Faithful commit behavior of ReSukiSU's ScalePredictiveBackAnimation. */
private class ScalePredictiveBackAnimation(
    private val exitDirection: PredictiveBackExitDirection
) : PredictiveBackAnimationHandler {
    override val commitDurationMillis = 200
    override val navigationMotion = NavigationMotionProfile(pushExitToAlpha = 0f)

    private fun dragScale(progress: Float): Float = 1f - 0.15f * easedProgress(progress)

    override fun foreground(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = PredictiveBackLayerTransform(
        scale = dragScale(progress),
        pivotX = pivotXFor(swipeEdge),
        pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
        cornerRadiusDp = 28f
    )

    override fun background(progress: Float, swipeEdge: Int, touchYFraction: Float) =
        PredictiveBackLayerTransform(dimAlpha = if (progress > 0f) 0.5f else 0f)

    override fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val eased = FastOutSlowIn.transform(commitProgress.coerceIn(0f, 1f))
        return PredictiveBackLayerTransform(
            translationXFraction = directionFor(swipeEdge, exitDirection) * eased,
            scale = dragScale(gestureProgress),
            pivotX = pivotXFor(swipeEdge),
            pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
            cornerRadiusDp = 28f
        )
    }

    override fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = PredictiveBackLayerTransform(
        dimAlpha = 0.5f * (1f - FastOutSlowIn.transform(commitProgress.coerceIn(0f, 1f)))
    )
}

private class MiuixPredictiveBackAnimation(
    private val exitDirection: PredictiveBackExitDirection
) : PredictiveBackAnimationHandler {
    override val commitDurationMillis = 240
    override val navigationMotion = NavigationMotionProfile(pushExitToAlpha = 0f)

    override fun foreground(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val eased = easedProgress(progress)
        return PredictiveBackLayerTransform(
            translationXFraction = directionFor(swipeEdge, exitDirection) * 0.11f * eased,
            scale = 1f - 0.035f * eased,
            pivotX = pivotXFor(swipeEdge),
            pivotY = touchYFraction.coerceIn(0.1f, 0.9f),
            cornerRadiusDp = 32f
        )
    }

    override fun background(progress: Float, swipeEdge: Int, touchYFraction: Float) =
        PredictiveBackLayerTransform(
            translationXFraction = -directionFor(swipeEdge, exitDirection) *
                0.05f * (1f - easedProgress(progress)),
            dimAlpha = 0.24f * (1f - easedProgress(progress))
        )

    override fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = foreground(
        progress = lerp(gestureProgress, 1f, StandardDecelerate.transform(commitProgress)),
        swipeEdge = swipeEdge,
        touchYFraction = touchYFraction
    ).copy(alpha = 1f - StandardDecelerate.transform(commitProgress))

    override fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = background(
        progress = lerp(gestureProgress, 1f, StandardDecelerate.transform(commitProgress)),
        swipeEdge = swipeEdge,
        touchYFraction = touchYFraction
    )
}

private class KernelSuClassicPredictiveBackAnimation(
    private val exitDirection: PredictiveBackExitDirection
) : PredictiveBackAnimationHandler {
    override val commitDurationMillis = 300
    override val navigationMotion = NavigationMotionProfile(
        pushExitToX = -1f,
        popEnterFromX = -1f
    )

    override fun foreground(
        progress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ): PredictiveBackLayerTransform {
        val eased = easedProgress(progress)
        return PredictiveBackLayerTransform(
            scale = 1f - 0.1f * eased,
            alpha = 1f - eased
        )
    }

    override fun background(progress: Float, swipeEdge: Int, touchYFraction: Float) =
        PredictiveBackLayerTransform(
            translationXFraction = -directionFor(swipeEdge, exitDirection) *
                (1f - easedProgress(progress))
        )

    override fun commitForeground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = foreground(
        progress = lerp(gestureProgress, 1f, StandardDecelerate.transform(commitProgress)),
        swipeEdge = swipeEdge,
        touchYFraction = touchYFraction
    )

    override fun commitBackground(
        gestureProgress: Float,
        commitProgress: Float,
        swipeEdge: Int,
        touchYFraction: Float
    ) = background(
        progress = lerp(gestureProgress, 1f, StandardDecelerate.transform(commitProgress)),
        swipeEdge = swipeEdge,
        touchYFraction = touchYFraction
    )
}
