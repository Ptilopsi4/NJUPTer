package com.example.njupter.ui.animation

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.njupter.ui.animation.predictiveback.NavigationMotionProfile
import com.example.njupter.ui.animation.predictiveback.PredictiveBackAnimation
import com.example.njupter.ui.animation.predictiveback.PredictiveBackAnimationHandler
import com.example.njupter.ui.animation.predictiveback.PredictiveBackExitDirection
import com.example.njupter.ui.animation.predictiveback.PredictiveBackLayerTransform
import com.example.njupter.ui.animation.predictiveback.predictiveBackAnimationHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val POP_MOTION_DURATION = 300
private const val TAB_MOTION_DURATION = 220
private const val COMMITTED_POP_GUARD_DURATION = 360L

private val StandardDecelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Identifies which retained layer host owns the active back gesture. */
enum class PredictiveBackOwner {
    IMPORT_PAGE,
    SETTINGS_PAGE
}

@Immutable
private data class PredictiveBackMotionState(
    val gestureProgress: Animatable<Float, *>,
    val commitProgress: Animatable<Float, *>,
    val gestureProgressAtCommit: androidx.compose.runtime.MutableFloatState,
    val swipeEdge: androidx.compose.runtime.MutableIntState,
    val touchY: androidx.compose.runtime.MutableFloatState,
    val gestureOwner: MutableState<PredictiveBackOwner?>,
    val commitOwner: MutableState<PredictiveBackOwner?>,
    val handler: PredictiveBackAnimationHandler
)

private val LocalPredictiveBackMotion = compositionLocalOf<PredictiveBackMotionState?> { null }

/**
 * Runs the ReSukiSU lifecycle in the same order: gesture, handler-specific commit animation, then
 * route pop. Removing the page before the commit animation is what previously caused the abrupt
 * jump and made AOSP lose its characteristic shrink-then-expand finish.
 */
@Composable
fun PredictiveBackSurface(
    enabled: Boolean,
    owner: PredictiveBackOwner?,
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gestureProgress = remember { Animatable(0f) }
    val commitProgress = remember { Animatable(0f) }
    val gestureProgressAtCommit = remember { mutableFloatStateOf(0f) }
    val swipeEdge = remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val touchY = remember { mutableFloatStateOf(0f) }
    val gestureOwner = remember { mutableStateOf<PredictiveBackOwner?>(null) }
    val commitOwner = remember { mutableStateOf<PredictiveBackOwner?>(null) }
    val handler = remember(animation, exitDirection) {
        predictiveBackAnimationHandler(animation, exitDirection)
    }
    val currentOwner = rememberUpdatedState(owner)
    val currentOnBack = rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val commitResetJob = remember { arrayOfNulls<Job>(1) }
    val motionState = remember(
        gestureProgress,
        commitProgress,
        gestureProgressAtCommit,
        swipeEdge,
        touchY,
        gestureOwner,
        commitOwner,
        handler
    ) {
        PredictiveBackMotionState(
            gestureProgress = gestureProgress,
            commitProgress = commitProgress,
            gestureProgressAtCommit = gestureProgressAtCommit,
            swipeEdge = swipeEdge,
            touchY = touchY,
            gestureOwner = gestureOwner,
            commitOwner = commitOwner,
            handler = handler
        )
    }

    // ReSukiSU disables predictive progress entirely for the "None" option.
    BackHandler(enabled = enabled && animation == PredictiveBackAnimation.NONE) {
        currentOnBack.value.invoke()
    }

    PredictiveBackHandler(
        enabled = enabled && animation != PredictiveBackAnimation.NONE
    ) { events ->
        var committed = false
        gestureOwner.value = currentOwner.value
        commitOwner.value = null
        commitProgress.snapTo(0f)

        try {
            events.collect { event ->
                swipeEdge.intValue = event.swipeEdge
                touchY.floatValue = event.touchY
                gestureProgress.snapTo(event.progress.coerceIn(0f, 1f))
            }

            committed = true
            gestureProgressAtCommit.floatValue = gestureProgress.value
            commitOwner.value = gestureOwner.value

            if (handler.commitDurationMillis > 0) {
                commitProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = handler.commitDurationMillis,
                        easing = LinearEasing
                    )
                )
            } else {
                commitProgress.snapTo(1f)
            }

            // The destination is already visually complete at this point.
            currentOnBack.value.invoke()

            commitResetJob[0]?.cancel()
            commitResetJob[0] = scope.launch {
                // The normal retained pop clock may run, but is pinned behind the completed frame.
                delay(COMMITTED_POP_GUARD_DURATION)
                commitOwner.value = null
                commitProgress.snapTo(0f)
            }
        } catch (cancellation: CancellationException) {
            commitOwner.value = null
            withContext(NonCancellable) {
                gestureProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                commitProgress.snapTo(0f)
            }
            throw cancellation
        } finally {
            if (committed) gestureProgress.snapTo(0f)
            gestureOwner.value = null
        }
    }

    CompositionLocalProvider(LocalPredictiveBackMotion provides motionState) {
        Box(modifier = modifier) {
            content()
        }
    }
}

/** Keeps the main shell alive behind the import page, including during predictive back. */
@Composable
fun AppPageTransition(
    showImport: Boolean,
    modifier: Modifier = Modifier,
    mainContent: @Composable () -> Unit,
    importContent: @Composable () -> Unit
) {
    RetainedLayerHost(
        targetState = showImport,
        initiallyRetained = listOf(false),
        previousState = false,
        motionOwner = PredictiveBackOwner.IMPORT_PAGE,
        depthOf = { if (it) 1 else 0 },
        isTabTransition = { _, _ -> false },
        modifier = modifier
    ) { importVisible ->
        if (importVisible) importContent() else mainContent()
    }
}

/** All route motion is retained here; MainActivity only declares the current route. */
@Composable
fun AppNavigationTransition(
    currentTab: Int,
    settingsSubPage: String,
    modifier: Modifier = Modifier,
    content: @Composable (tab: Int, subPage: String) -> Unit
) {
    val target = currentTab to settingsSubPage
    RetainedLayerHost(
        targetState = target,
        initiallyRetained = listOf(0 to "main", 1 to "main"),
        previousState = currentTab to "main",
        motionOwner = PredictiveBackOwner.SETTINGS_PAGE,
        depthOf = { (_, subPage) -> if (subPage == "main") 0 else 1 },
        isTabTransition = { from, to -> from.first != to.first },
        modifier = modifier
    ) { (tab, subPage) ->
        content(tab, subPage)
    }
}

private enum class NavigationTransitionKind {
    PUSH,
    POP,
    TAB,
    SAME_DEPTH
}

private fun <T> transitionKind(
    from: T,
    to: T,
    depthOf: (T) -> Int,
    isTabTransition: (T, T) -> Boolean
): NavigationTransitionKind = when {
    isTabTransition(from, to) -> NavigationTransitionKind.TAB
    depthOf(to) > depthOf(from) -> NavigationTransitionKind.PUSH
    depthOf(to) < depthOf(from) -> NavigationTransitionKind.POP
    else -> NavigationTransitionKind.SAME_DEPTH
}

@Composable
private fun <T> RetainedLayerHost(
    targetState: T,
    initiallyRetained: List<T>,
    previousState: T,
    motionOwner: PredictiveBackOwner,
    depthOf: (T) -> Int,
    isTabTransition: (from: T, to: T) -> Boolean,
    modifier: Modifier,
    content: @Composable (T) -> Unit
) {
    val retainedStates = remember {
        initiallyRetained.distinct().toMutableList().apply {
            if (targetState !in this) add(targetState)
        }
    }
    if (targetState !in retainedStates) retainedStates.add(targetState)

    val transition = updateTransition(
        targetState = targetState,
        label = "retainedNavigationTransition"
    )
    val predictiveMotion = LocalPredictiveBackMotion.current
    val navigationMotion = predictiveMotion?.handler?.navigationMotion ?: NavigationMotionProfile()
    val layerColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.background(layerColor)) {
        retainedStates.forEach { layerState ->
            key(layerState) {
                val from = transition.currentState
                val to = transition.targetState
                val kind = transitionKind(from, to, depthOf, isTabTransition)

                val alpha = transition.animateFloat(
                    transitionSpec = {
                        when (kind) {
                            NavigationTransitionKind.PUSH -> tween(220, easing = StandardDecelerate)
                            NavigationTransitionKind.POP -> tween(POP_MOTION_DURATION, easing = StandardDecelerate)
                            NavigationTransitionKind.TAB -> tween(TAB_MOTION_DURATION, easing = StandardDecelerate)
                            NavigationTransitionKind.SAME_DEPTH -> tween(260, easing = StandardDecelerate)
                        }
                    },
                    label = "layerAlpha"
                ) { state ->
                    layerAlpha(
                        layer = layerState,
                        state = state,
                        from = from,
                        to = to,
                        kind = kind,
                        profile = navigationMotion
                    )
                }

                val translation = transition.animateFloat(
                    transitionSpec = {
                        when (kind) {
                            NavigationTransitionKind.PUSH -> spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = 0.001f
                            )
                            NavigationTransitionKind.POP -> tween(
                                POP_MOTION_DURATION,
                                easing = StandardDecelerate
                            )
                            NavigationTransitionKind.TAB -> tween(
                                TAB_MOTION_DURATION,
                                easing = StandardDecelerate
                            )
                            NavigationTransitionKind.SAME_DEPTH -> tween(
                                260,
                                easing = StandardDecelerate
                            )
                        }
                    },
                    label = "layerTranslation"
                ) { state ->
                    layerTranslation(
                        layer = layerState,
                        state = state,
                        from = from,
                        to = to,
                        kind = kind,
                        profile = navigationMotion
                    )
                }

                val scale = transition.animateFloat(
                    transitionSpec = {
                        when (kind) {
                            NavigationTransitionKind.PUSH -> spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                                visibilityThreshold = 0.001f
                            )
                            NavigationTransitionKind.POP -> tween(
                                POP_MOTION_DURATION,
                                easing = StandardDecelerate
                            )
                            NavigationTransitionKind.TAB -> tween(
                                TAB_MOTION_DURATION,
                                easing = StandardDecelerate
                            )
                            NavigationTransitionKind.SAME_DEPTH -> tween(
                                260,
                                easing = StandardDecelerate
                            )
                        }
                    },
                    label = "layerScale"
                ) { state ->
                    layerScale(
                        layer = layerState,
                        state = state,
                        from = from,
                        to = to,
                        kind = kind,
                        profile = navigationMotion
                    )
                }

                val isActive = layerState == targetState
                val isPredictiveBackground =
                    layerState == previousState && layerState != targetState
                val zIndex = when {
                    kind == NavigationTransitionKind.POP && layerState == from -> 2f
                    layerState == to -> 2f
                    isPredictiveBackground -> 1f
                    else -> 0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(zIndex)
                        .then(if (isActive) Modifier else Modifier.clearAndSetSemantics { })
                        .then(if (isActive || isPredictiveBackground) Modifier else Modifier.invisibleLayerStopsHits())
                        .motionLayer(
                            normalAlpha = { alpha.value },
                            normalTranslation = { translation.value },
                            normalScale = { scale.value },
                            motion = predictiveMotion,
                            owner = motionOwner,
                            isForeground = isActive,
                            isBackground = isPredictiveBackground
                        )
                        // Every retained route is an opaque scene. This also fixes legacy pages
                        // whose own root was a transparent Column rather than a Scaffold.
                        .background(layerColor)
                ) {
                    content(layerState)
                }
            }
        }
    }
}

private fun <T> layerAlpha(
    layer: T,
    state: T,
    from: T,
    to: T,
    kind: NavigationTransitionKind,
    profile: NavigationMotionProfile
): Float = when (kind) {
    NavigationTransitionKind.PUSH -> when (layer) {
        to -> 1f
        from -> if (state == from) 1f else profile.pushExitToAlpha
        else -> 0f
    }
    NavigationTransitionKind.POP -> when (layer) {
        to -> 1f
        from -> if (state == from) 1f else profile.popExitToAlpha
        else -> 0f
    }
    NavigationTransitionKind.TAB,
    NavigationTransitionKind.SAME_DEPTH -> if (layer == state) 1f else 0f
}

private fun <T> layerTranslation(
    layer: T,
    state: T,
    from: T,
    to: T,
    kind: NavigationTransitionKind,
    profile: NavigationMotionProfile
): Float = when (kind) {
    NavigationTransitionKind.PUSH -> when (layer) {
        to -> if (state == from) profile.pushEnterFromX else 0f
        from -> if (state == from) 0f else profile.pushExitToX
        else -> 0f
    }
    NavigationTransitionKind.POP -> when (layer) {
        to -> if (state == from) profile.popEnterFromX else 0f
        else -> 0f
    }
    NavigationTransitionKind.TAB,
    NavigationTransitionKind.SAME_DEPTH -> 0f
}

private fun <T> layerScale(
    layer: T,
    state: T,
    from: T,
    to: T,
    kind: NavigationTransitionKind,
    profile: NavigationMotionProfile
): Float = when (kind) {
    NavigationTransitionKind.POP -> when (layer) {
        from -> if (state == from) 1f else profile.popExitToScale
        else -> 1f
    }
    else -> 1f
}

/**
 * 保留层在 alpha=0 时仍参与命中测试（graphicsLayer 不影响 hit test），
 * 不加拦截会让点击穿透到不可见的残留路由层（幽灵点击）。
 */
private fun Modifier.invisibleLayerStopsHits(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { it.consume() }
        }
    }
}

private fun Modifier.motionLayer(
    normalAlpha: () -> Float,
    normalTranslation: () -> Float,
    normalScale: () -> Float,
    motion: PredictiveBackMotionState?,
    owner: PredictiveBackOwner,
    isForeground: Boolean,
    isBackground: Boolean
): Modifier {
    val layerModifier = graphicsLayer {
        val gestureOwned = motion?.gestureOwner?.value == owner
        val commitOwned = motion?.commitOwner?.value == owner
        val gestureProgress = if (gestureOwned) motion?.gestureProgress?.value ?: 0f else 0f
        val gestureRunning = gestureOwned && gestureProgress > 0f
        val commitRunning = gestureOwned && commitOwned
        val touchYFraction = if (size.height > 0f) {
            (motion?.touchY?.floatValue ?: size.height / 2f) / size.height
        } else {
            0.5f
        }

        val transform = when {
            commitRunning && isForeground -> motion?.handler?.commitForeground(
                gestureProgress = motion.gestureProgressAtCommit.floatValue,
                commitProgress = motion.commitProgress.value,
                swipeEdge = motion.swipeEdge.intValue,
                touchYFraction = touchYFraction
            )
            commitRunning && isBackground -> motion?.handler?.commitBackground(
                gestureProgress = motion.gestureProgressAtCommit.floatValue,
                commitProgress = motion.commitProgress.value,
                swipeEdge = motion.swipeEdge.intValue,
                touchYFraction = touchYFraction
            )
            gestureRunning && isForeground -> motion?.handler?.foreground(
                progress = gestureProgress,
                swipeEdge = motion.swipeEdge.intValue,
                touchYFraction = touchYFraction
            )
            gestureRunning && isBackground -> motion?.handler?.background(
                progress = gestureProgress,
                swipeEdge = motion.swipeEdge.intValue,
                touchYFraction = touchYFraction
            )
            else -> null
        } ?: PredictiveBackLayerTransform()

        when {
            commitRunning && (isForeground || isBackground) -> applyTransform(transform)
            commitOwned -> {
                // Route state has changed after the visible commit. Pin its completed target while
                // the retained transition state catches up, preventing a second pop animation.
                alpha = if (isForeground) 1f else 0f
                translationX = 0f
                scaleX = 1f
                scaleY = 1f
                shadowElevation = 0f
                ambientShadowColor = Color.Transparent
                spotShadowColor = Color.Transparent
                clip = false
            }
            gestureRunning && (isForeground || isBackground) -> applyTransform(transform)
            else -> {
                alpha = normalAlpha()
                translationX = size.width * normalTranslation()
                scaleX = normalScale()
                scaleY = normalScale()
                shadowElevation = 0f
                ambientShadowColor = Color.Transparent
                spotShadowColor = Color.Transparent
                clip = false
            }
        }
    }

    return layerModifier.drawWithContent {
        drawContent()
        if (motion != null && motion.gestureOwner.value == owner && isBackground) {
            val transform = if (motion.commitOwner.value == owner) {
                motion.handler.commitBackground(
                    gestureProgress = motion.gestureProgressAtCommit.floatValue,
                    commitProgress = motion.commitProgress.value,
                    swipeEdge = motion.swipeEdge.intValue,
                    touchYFraction = if (size.height > 0f) {
                        motion.touchY.floatValue / size.height
                    } else {
                        0.5f
                    }
                )
            } else {
                motion.handler.background(
                    progress = motion.gestureProgress.value,
                    swipeEdge = motion.swipeEdge.intValue,
                    touchYFraction = if (size.height > 0f) {
                        motion.touchY.floatValue / size.height
                    } else {
                        0.5f
                    }
                )
            }
            if (transform.dimAlpha > 0f) {
                drawRect(Color.Black.copy(alpha = transform.dimAlpha))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyTransform(
    transform: PredictiveBackLayerTransform
) {
    alpha = transform.alpha
    translationX = size.width * transform.translationXFraction + transform.translationXDp.dp.toPx()
    scaleX = transform.scale
    scaleY = transform.scale
    transformOrigin = TransformOrigin(transform.pivotX, transform.pivotY)
    shadowElevation = transform.shadowElevationDp.dp.toPx()
    ambientShadowColor = Color.Black.copy(alpha = transform.shadowAlpha * 0.7f)
    spotShadowColor = Color.Black.copy(alpha = transform.shadowAlpha)
    if (transform.cornerRadiusDp > 0f) {
        clip = true
        shape = RoundedCornerShape(transform.cornerRadiusDp.dp)
    } else {
        clip = false
        shape = RectangleShape
    }
}
