package com.flaretune.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.flaretune.client.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

internal val FlareBrandTeal = Color(0xFF079B84)
internal val FlareBrandBlue = Color(0xFF2287B4)
internal val FlareBrandIndigo = Color(0xFF506FD7)
internal val FlareBrandGradientColors = listOf(FlareBrandTeal, FlareBrandBlue, FlareBrandIndigo)

private data class CubicSegment(
    val x0: Float,
    val y0: Float,
    val cx1: Float,
    val cy1: Float,
    val cx2: Float,
    val cy2: Float,
    val x1: Float,
    val y1: Float,
)

// C1 geometric refinement in a 64x64 viewBox (matched to launcher & splash seed).
private val Wave4Segments = listOf(
    CubicSegment(9f, 35.5f, 12.5f, 28.5f, 15.5f, 20f, 22.5f, 20f),
    CubicSegment(22.5f, 20f, 26.75f, 20f, 29.375f, 26f, 32f, 32f),
    CubicSegment(32f, 32f, 34.625f, 38f, 37.25f, 44f, 41.5f, 44f),
    CubicSegment(41.5f, 44f, 48.5f, 44f, 51.5f, 35.5f, 55f, 28.5f),
)

private fun buildCircle4Segments(
    cx: Float = 32f,
    cy: Float = 32f,
    radius: Float = 18f,
    startDeg: Float = 125f,
): List<CubicSegment> {
    val k = 0.5522847f * radius
    val segs = ArrayList<CubicSegment>(4)
    for (i in 0 until 4) {
        val a0 = ((startDeg + i * 90f) * PI / 180.0).toFloat()
        val a1 = ((startDeg + (i + 1) * 90f) * PI / 180.0).toFloat()
        val cos0 = cos(a0)
        val sin0 = sin(a0)
        val cos1 = cos(a1)
        val sin1 = sin(a1)
        val x0 = cx + radius * cos0
        val y0 = cy + radius * sin0
        val x1 = cx + radius * cos1
        val y1 = cy + radius * sin1
        segs += CubicSegment(
            x0 = x0,
            y0 = y0,
            cx1 = x0 - k * sin0,
            cy1 = y0 + k * cos0,
            cx2 = x1 + k * sin1,
            cy2 = y1 - k * cos1,
            x1 = x1,
            y1 = y1,
        )
    }
    return segs
}

private val Circle4Segments = buildCircle4Segments()

private fun lerpSegment(a: CubicSegment, b: CubicSegment, t: Float): CubicSegment = CubicSegment(
    x0 = lerp(a.x0, b.x0, t),
    y0 = lerp(a.y0, b.y0, t),
    cx1 = lerp(a.cx1, b.cx1, t),
    cy1 = lerp(a.cy1, b.cy1, t),
    cx2 = lerp(a.cx2, b.cx2, t),
    cy2 = lerp(a.cy2, b.cy2, t),
    x1 = lerp(a.x1, b.x1, t),
    y1 = lerp(a.y1, b.y1, t),
)

/**
 * Tracks root-relative bounds of the login and home avatar anchors so cold-open splash
 * and login-success handoff land precisely without visual duplication.
 */
@Stable
internal class AvatarAnchorRegistry {
    var rootOrigin by mutableStateOf(Offset.Zero)
    var loginAvatarBounds by mutableStateOf<Rect?>(null)
    var lastLoginAvatarBounds by mutableStateOf<Rect?>(null)
    var lastLoginInitial by mutableStateOf("")
    var homeAvatarBounds by mutableStateOf<Rect?>(null)
    var hideLoginAvatar by mutableStateOf(false)
    var hideHomeAvatar by mutableStateOf(false)

    fun toRootLocalRect(coords: LayoutCoordinates): Rect {
        val raw = coords.boundsInRoot()
        val origin = rootOrigin
        return Rect(
            left = raw.left - origin.x,
            top = raw.top - origin.y,
            right = raw.right - origin.x,
            bottom = raw.bottom - origin.y,
        )
    }
}

internal val LocalAvatarAnchorRegistry = compositionLocalOf<AvatarAnchorRegistry?> { null }

@Composable
internal fun rememberAvatarAnchorRegistry(): AvatarAnchorRegistry = remember { AvatarAnchorRegistry() }

@Composable
internal fun ProvideAvatarAnchorRegistry(
    registry: AvatarAnchorRegistry,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAvatarAnchorRegistry provides registry, content = content)
}

/**
 * Crisp C1 wave brand emblem using normalized 64x64 cubic Beziers.
 */
@Composable
internal fun FlareFlowWaveMark(
    modifier: Modifier = Modifier,
    strokeFraction: Float = 7.2f / 64f,
    colors: List<Color> = FlareBrandGradientColors,
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 64f
        val scaleY = size.height / 64f
        val path = Path().apply {
            moveTo(Wave4Segments[0].x0 * scaleX, Wave4Segments[0].y0 * scaleY)
            for (seg in Wave4Segments) {
                cubicTo(
                    seg.cx1 * scaleX,
                    seg.cy1 * scaleY,
                    seg.cx2 * scaleX,
                    seg.cy2 * scaleY,
                    seg.x1 * scaleX,
                    seg.y1 * scaleY,
                )
            }
        }
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = colors,
                start = Offset(9f * scaleX, 36f * scaleY),
                end = Offset(55f * scaleX, 28f * scaleY),
            ),
            style = Stroke(
                width = size.minDimension * strokeFraction,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Renders either the symmetrical center-outward C1 wave unfurl or the 4-segment wave-to-circle morph.
 */
@Composable
private fun FlareMorphCanvas(
    drawProgress: Float,
    morphProgress: Float,
    fillProgress: Float,
    fillColor: Color,
    initial: String,
    initialColor: Color,
    strokeFraction: Float = 7.4f / 64f,
    modifier: Modifier = Modifier,
) {
    val pathMeasureLeft = remember { PathMeasure() }
    val pathMeasureRight = remember { PathMeasure() }
    val leftFull = remember { Path() }
    val rightFull = remember { Path() }
    val leftPartial = remember { Path() }
    val rightPartial = remember { Path() }
    val morphedPath = remember { Path() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.minDimension / 64f
            val ox = (size.width - 64f * scale) / 2f
            val oy = (size.height - 64f * scale) / 2f
            fun px(x: Float) = ox + x * scale
            fun py(y: Float) = oy + y * scale

            val center = Offset(px(32f), py(32f))
            val circleRadiusPx = 18f * scale
            val strokePx = lerp(64f * strokeFraction * scale, 3.2f * scale, morphProgress)

            // Interior circle fill emerging cleanly near the end of the morph.
            if (fillProgress > 0.01f) {
                drawCircle(
                    color = fillColor.copy(alpha = fillProgress.coerceIn(0f, 1f)),
                    radius = circleRadiusPx * lerp(0.86f, 1f, fillProgress),
                    center = center,
                )
            }

            if (morphProgress <= 0.001f) {
                val p = drawProgress.coerceIn(0f, 1f)
                // Reversed seg 1 + seg 0 from (32,32) leftwards to (9,35.5)
                val s1 = Wave4Segments[1]
                val s0 = Wave4Segments[0]
                leftFull.reset()
                leftFull.moveTo(px(s1.x1), py(s1.y1))
                leftFull.cubicTo(px(s1.cx2), py(s1.cy2), px(s1.cx1), py(s1.cy1), px(s1.x0), py(s1.y0))
                leftFull.cubicTo(px(s0.cx2), py(s0.cy2), px(s0.cx1), py(s0.cy1), px(s0.x0), py(s0.y0))

                // Forward seg 2 + seg 3 from (32,32) rightwards to (55,28.5)
                val s2 = Wave4Segments[2]
                val s3 = Wave4Segments[3]
                rightFull.reset()
                rightFull.moveTo(px(s2.x0), py(s2.y0))
                rightFull.cubicTo(px(s2.cx1), py(s2.cy1), px(s2.cx2), py(s2.cy2), px(s2.x1), py(s2.y1))
                rightFull.cubicTo(px(s3.cx1), py(s3.cy1), px(s3.cx2), py(s3.cy2), px(s3.x1), py(s3.y1))

                leftPartial.reset()
                rightPartial.reset()
                pathMeasureLeft.setPath(leftFull, false)
                pathMeasureRight.setPath(rightFull, false)
                if (p > 0.005f) {
                    pathMeasureLeft.getSegment(0f, pathMeasureLeft.length * p, leftPartial, true)
                    pathMeasureRight.getSegment(0f, pathMeasureRight.length * p, rightPartial, true)
                }

                val strokeBrush = Brush.linearGradient(
                    colors = FlareBrandGradientColors,
                    start = Offset(px(9f), py(36f)),
                    end = Offset(px(55f), py(28f)),
                )
                val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (p > 0.005f) {
                    drawPath(leftPartial, brush = strokeBrush, style = style)
                    drawPath(rightPartial, brush = strokeBrush, style = style)
                }
            } else {
                morphedPath.reset()
                val m = morphProgress.coerceIn(0f, 1f)
                val first = lerpSegment(Wave4Segments[0], Circle4Segments[0], m)
                morphedPath.moveTo(px(first.x0), py(first.y0))
                for (i in 0 until 4) {
                    val seg = lerpSegment(Wave4Segments[i], Circle4Segments[i], m)
                    morphedPath.cubicTo(
                        px(seg.cx1),
                        py(seg.cy1),
                        px(seg.cx2),
                        py(seg.cy2),
                        px(seg.x1),
                        py(seg.y1),
                    )
                }
                if (m > 0.96f) morphedPath.close()
                val ringAlpha = (1f - fillProgress * 0.72f).coerceIn(0.28f, 1f)
                drawPath(
                    path = morphedPath,
                    brush = Brush.linearGradient(
                        colors = FlareBrandGradientColors.map { it.copy(alpha = ringAlpha) },
                        start = Offset(px(14f), py(18f)),
                        end = Offset(px(50f), py(46f)),
                    ),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        if (fillProgress > 0.15f) {
            val contentAlpha = ((fillProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
            if (initial.isNotBlank()) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = initialColor.copy(alpha = contentAlpha),
                )
            } else {
                FlareFlowWaveMark(
                    modifier = Modifier.fillMaxSize(0.32f).alpha(contentAlpha),
                    colors = listOf(initialColor, initialColor),
                )
            }
        }
    }
}

/**
 * Interactive login avatar badge that catches the cold-open landing and anchors the
 * handoff flight to the Home screen's AccountAvatar upon sign-in.
 */
@Composable
internal fun LoginAvatarBadge(
    username: String,
    busy: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val registry = LocalAvatarAnchorRegistry.current
    val initial = remember(username) { username.trim().take(1).uppercase(Locale.ROOT) }
    val pulseTransition = rememberInfiniteTransition(label = "login_avatar_busy")
    val pulse by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "login_avatar_ring_scale",
    )

    val hidden = registry?.hideLoginAvatar == true
    val ringSize = size + 10.dp

    DisposableEffect(registry) {
        onDispose { registry?.loginAvatarBounds = null }
    }

    Box(
        modifier = modifier
            .size(ringSize)
            .testTag("login_avatar_badge"),
        contentAlignment = Alignment.Center,
    ) {
        // Subtle ambient acoustic halo when busy connecting or empty.
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (busy) pulse else 1f
                    scaleX = s
                    scaleY = s
                    alpha = if (hidden) 0f else if (busy) 0.88f else 0.48f
                },
        ) {
            val stroke = 2.dp.toPx()
            drawCircle(
                brush = Brush.linearGradient(FlareBrandGradientColors),
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke),
            )
        }

        Box(
            modifier = Modifier
                .size(size)
                .onGloballyPositioned { coords ->
                    if (registry != null) {
                        val rect = registry.toRootLocalRect(coords)
                        registry.loginAvatarBounds = rect
                        registry.lastLoginAvatarBounds = rect
                        registry.lastLoginInitial = initial
                    }
                }
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = initial,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.82f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.82f))
                },
                label = "login_avatar_symbol",
            ) { currentInitial ->
                if (currentInitial.isNotBlank()) {
                    Text(
                        text = currentInitial,
                        fontSize = (size.value * 0.44f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    FlareFlowWaveMark(
                        modifier = Modifier.size(size * 0.60f),
                        colors = listOf(
                            MaterialTheme.colorScheme.onPrimary,
                            MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Full-screen cold-open splash overlay:
 * 1) Center micro-glow seed unfurls the symmetrical C1 wave + wordmark.
 * 2) Holds gently if session restoration (`ClientScreen.RESTORING`) is still running.
 * 3) Curls C1 wave via 4-segment Bezier morph into a circular badge and docks into the
 *    live destination avatar (`LoginAvatarBadge` on CONNECT or `AccountAvatar` on LIBRARY).
 */
@Composable
internal fun ColdOpenSplashOverlay(
    state: ClientUiState,
    registry: AvatarAnchorRegistry,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val drawAnim = remember { Animatable(0f) }
    val seedGlowAnim = remember { Animatable(0f) }
    val wordmarkAlpha = remember { Animatable(0f) }
    val morphAnim = remember { Animatable(0f) }
    val fillAnim = remember { Animatable(0f) }
    val flyAnim = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(1f) }
    val breatheAnim = remember { Animatable(1f) }

    var activeDestination by remember { mutableStateOf(state.screen) }
    activeDestination = state.screen
    val latestDestination by rememberUpdatedState(state.screen)
    val latestOnFinished by rememberUpdatedState(onFinished)

    var targetRect by remember { mutableStateOf<Rect?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            registry.hideLoginAvatar = false
            registry.hideHomeAvatar = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            // Act 1 (~340ms): Center seed bloom -> symmetrical C1 wave draw + subtle wordmark.
            seedGlowAnim.snapTo(1f)
            coroutineScope {
                launch { seedGlowAnim.animateTo(0f, tween(280, easing = LinearEasing)) }
                launch { drawAnim.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
                launch {
                    delay(110)
                    wordmarkAlpha.animateTo(1f, tween(210, easing = FastOutSlowInEasing))
                }
            }

            // Hold calmly while initial session restore finishes; exits immediately once screen changes.
            if (latestDestination == ClientScreen.RESTORING) {
                coroutineScope {
                    val breatheJob = launch {
                        while (true) {
                            breatheAnim.animateTo(1.035f, tween(420, easing = FastOutSlowInEasing))
                            breatheAnim.animateTo(0.985f, tween(420, easing = FastOutSlowInEasing))
                        }
                    }
                    snapshotFlow { latestDestination }.first { it != ClientScreen.RESTORING }
                    breatheJob.cancel()
                }
                breatheAnim.animateTo(1f, tween(90))
            }

            // Act 2 (~280ms): Wordmark fades cleanly while 4-segment wave curls into circular badge + fills.
            coroutineScope {
                launch { wordmarkAlpha.animateTo(0f, tween(100, easing = FastOutSlowInEasing)) }
                launch { morphAnim.animateTo(1f, tween(280, easing = CubicBezierEasing(0.32f, 0f, 0.24f, 1f))) }
                launch {
                    delay(130)
                    fillAnim.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                }
            }

            // Wait up to 3 layout frames so destination anchor bounds are measured.
            var frameWait = 0
            while (targetRect == null && frameWait < 3) {
                val measured = when (latestDestination) {
                    ClientScreen.CONNECT -> registry.loginAvatarBounds
                    ClientScreen.LIBRARY -> registry.homeAvatarBounds
                    else -> null
                }
                if (measured != null) {
                    targetRect = measured
                    break
                }
                withFrameNanos { }
                frameWait++
            }

            if (latestDestination == ClientScreen.CONNECT) {
                registry.hideLoginAvatar = true
            } else if (latestDestination == ClientScreen.LIBRARY) {
                registry.hideHomeAvatar = true
            }

            // Act 3 (~320ms): Reveal underlying destination while flying badge straight into its anchor slot.
            coroutineScope {
                launch { scrimAlpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                launch { flyAnim.animateTo(1f, tween(320, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))) }
            }
        } finally {
            registry.hideLoginAvatar = false
            registry.hideHomeAvatar = false
            latestOnFinished()
        }
    }

    val initial = when (activeDestination) {
        ClientScreen.LIBRARY -> state.accountName.ifBlank { state.username.ifBlank { stringResource(R.string.app_name) } }
            .take(1).uppercase(Locale.ROOT)
        ClientScreen.CONNECT -> state.username.trim().take(1).uppercase(Locale.ROOT)
        else -> ""
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cold_open_splash")
            .clearAndSetSemantics { },
    ) {
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val startCanvasSizePx = with(density) { 104.dp.toPx() }
        // In a 64x64 normalized canvas, Circle4 has diameter 36/64 = 0.5625 of the canvas box.
        val startCircleDiameterPx = startCanvasSizePx * (36f / 64f)
        val fallbackTargetSizePx = with(density) {
            if (activeDestination == ClientScreen.CONNECT) 56.dp.toPx() else 34.dp.toPx()
        }
        val resolvedTarget = targetRect ?: Rect(
            left = if (activeDestination == ClientScreen.CONNECT) rootW / 2f - fallbackTargetSizePx / 2f else with(density) { 20.dp.toPx() },
            top = if (activeDestination == ClientScreen.CONNECT) rootH * 0.25f else with(density) { 18.dp.toPx() },
            right = if (activeDestination == ClientScreen.CONNECT) rootW / 2f + fallbackTargetSizePx / 2f else with(density) { (20.dp + 34.dp).toPx() },
            bottom = if (activeDestination == ClientScreen.CONNECT) rootH * 0.25f + fallbackTargetSizePx else with(density) { (18.dp + 34.dp).toPx() },
        )

        val t = flyAnim.value
        val curCenterX = lerp(rootW / 2f, resolvedTarget.center.x, t)
        val curCenterY = lerp(rootH / 2f, resolvedTarget.center.y, t)
        val curCircleDiameterPx = lerp(startCircleDiameterPx, resolvedTarget.width.coerceAtLeast(1f), t)
        val curCanvasSizePx = curCircleDiameterPx / (36f / 64f)

        // Backdrop scrim fades away cleanly as Act 3 begins.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = scrimAlpha.value)),
        )

        // Center seed bloom halo during Act 1 start.
        if (seedGlowAnim.value > 0.01f && morphAnim.value < 0.01f) {
            val haloAlpha = seedGlowAnim.value * 0.34f
            Canvas(Modifier.fillMaxSize()) {
                val cx = rootW / 2f
                val cy = rootH / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(FlareBrandTeal.copy(alpha = haloAlpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = 42.dp.toPx(),
                    ),
                    radius = 42.dp.toPx(),
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = FlareBrandBlue.copy(alpha = seedGlowAnim.value),
                    radius = lerp(4.dp.toPx(), 1.5.dp.toPx(), drawAnim.value),
                    center = Offset(cx, cy),
                )
            }
        }

        // Flying / morphing emblem positioned precisely by center coordinates.
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (curCenterX - curCanvasSizePx / 2f).roundToInt(),
                        y = (curCenterY - curCanvasSizePx / 2f).roundToInt(),
                    )
                }
                .size(with(density) { curCanvasSizePx.toDp() })
                .graphicsLayer {
                    scaleX = breatheAnim.value
                    scaleY = breatheAnim.value
                },
            contentAlignment = Alignment.Center,
        ) {
            FlareMorphCanvas(
                drawProgress = drawAnim.value,
                morphProgress = morphAnim.value,
                fillProgress = fillAnim.value,
                fillColor = MaterialTheme.colorScheme.primary,
                initial = initial,
                initialColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Soft status text during restoring without repeating the brand title.
        if (wordmarkAlpha.value > 0.01f && state.screen == ClientScreen.RESTORING) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = with(density) { 52.dp })
                    .graphicsLayer {
                        alpha = wordmarkAlpha.value
                        translationY = (1f - wordmarkAlpha.value) * with(density) { 8.dp.toPx() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.ft_restoring),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Second-stage avatar relay when signing in succeeds (`ClientScreen.CONNECT` -> `ClientScreen.LIBRARY`):
 * flies the circular avatar badge along a smooth curved arc from its login position directly into
 * Home's top-left `AccountAvatar`.
 */
@Composable
internal fun AvatarRelayOverlay(
    fromRect: Rect,
    initial: String,
    registry: AvatarAnchorRegistry,
    onFinished: () -> Unit,
) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var targetRect by remember { mutableStateOf(registry.homeAvatarBounds) }
    val latestOnFinished by rememberUpdatedState(onFinished)

    DisposableEffect(Unit) {
        onDispose { registry.hideHomeAvatar = false }
    }

    LaunchedEffect(fromRect) {
        registry.hideHomeAvatar = true
        try {
            var frameWait = 0
            while (targetRect == null && frameWait < 4) {
                val measured = registry.homeAvatarBounds
                if (measured != null) {
                    targetRect = measured
                    break
                }
                withFrameNanos { }
                frameWait++
            }
            if (targetRect == null) return@LaunchedEffect
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(360, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
            )
        } finally {
            registry.hideHomeAvatar = false
            latestOnFinished()
        }
    }

    val toRect = targetRect ?: return
    val t = progress.value
    val p0 = fromRect.center
    val p2 = toRect.center
    val arcLiftPx = with(density) { 38.dp.toPx() }.coerceAtMost(hypot(p2.x - p0.x, p2.y - p0.y) * 0.18f)
    val ctrl = Offset(
        x = (p0.x + p2.x) * 0.5f,
        y = minOf(p0.y, p2.y) - arcLiftPx,
    )
    val oneMinusT = 1f - t
    val cx = oneMinusT * oneMinusT * p0.x + 2f * oneMinusT * t * ctrl.x + t * t * p2.x
    val cy = oneMinusT * oneMinusT * p0.y + 2f * oneMinusT * t * ctrl.y + t * t * p2.y
    val curSizePx = lerp(fromRect.width, toRect.width, t)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("avatar_relay_overlay")
            .clearAndSetSemantics { },
    ) {
        val primary = MaterialTheme.colorScheme.primary
        val badgeColor = lerp(primary, primary, t)
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (cx - curSizePx / 2f).roundToInt(),
                        y = (cy - curSizePx / 2f).roundToInt(),
                    )
                }
                .size(with(density) { curSizePx.toDp() })
                .clip(CircleShape)
                .background(badgeColor),
            contentAlignment = Alignment.Center,
        ) {
            val symbolSize = with(density) { (curSizePx * 0.46f).toSp() }
            if (initial.isNotBlank()) {
                Text(
                    text = initial,
                    fontSize = symbolSize,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                FlareFlowWaveMark(
                    modifier = Modifier.fillMaxSize(0.58f),
                    colors = listOf(MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
    }
}
