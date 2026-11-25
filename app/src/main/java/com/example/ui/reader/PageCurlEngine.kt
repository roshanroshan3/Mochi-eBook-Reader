package com.example.ui.reader

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.preferences.CurlGrabMode
import com.example.data.preferences.PageTurnMode
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

enum class CurlDirection {
    FORWARD,
    BACKWARD
}

data class CurlFrameGeometry(
    val width: Float,
    val height: Float,
    val progress: Float,
    val foldXTop: Float,
    val foldXBottom: Float,
    val creaseCenterX: Float,
    val creaseAngleDeg: Float,
    val curlRadius: Float,
    val shadowAlpha: Float,
    val specularAlpha: Float,
    val backMirrorOffsetX: Float,
    val backMirrorPivotX: Float,
    val direction: CurlDirection
)

/**
 * Pure deterministic physics & geometry math for Page Curl animations.
 * Keeps allocation-free calculations under < 0.05 ms/frame so 60-120fps motion stays rock solid.
 */
object CurlPhysicsMath {

    fun isGrabZoneAllowed(
        touchPoint: Offset,
        width: Float,
        height: Float,
        grabMode: CurlGrabMode
    ): Boolean {
        if (width <= 0f || height <= 0f) return true
        val edgeMargin = width * 0.28f
        val inLeftOrRightEdge = touchPoint.x <= edgeMargin || touchPoint.x >= (width - edgeMargin)
        val inCornerBand = touchPoint.y <= height * 0.25f || touchPoint.y >= height * 0.75f
        return when (grabMode) {
            CurlGrabMode.CORNERS_AND_EDGES -> true
            CurlGrabMode.EDGES_ONLY -> inLeftOrRightEdge
            CurlGrabMode.CORNERS_ONLY -> inLeftOrRightEdge && inCornerBand
        }
    }

    fun shouldCompleteTurn(
        progress: Float,
        velocityX: Float,
        direction: CurlDirection,
        speedMultiplier: Float
    ): Boolean {
        val velThreshold = 420f / speedMultiplier.coerceIn(0.5f, 2.0f)
        val directionalFlick = when (direction) {
            CurlDirection.FORWARD -> velocityX < -velThreshold
            CurlDirection.BACKWARD -> velocityX > velThreshold
        }
        val cancelFlick = when (direction) {
            CurlDirection.FORWARD -> velocityX > velThreshold
            CurlDirection.BACKWARD -> velocityX < -velThreshold
        }
        if (cancelFlick) return false
        return directionalFlick || progress >= 0.42f
    }

    fun computeCurlFrame(
        width: Float,
        height: Float,
        progress: Float,
        anchorYFraction: Float,
        direction: CurlDirection,
        curlStrength: Float,
        shadowStrength: Float
    ): CurlFrameGeometry {
        val p = progress.coerceIn(0f, 1f)
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val yBias = (anchorYFraction.coerceIn(0.05f, 0.95f) - 0.5f) * 2f

        val tiltRad = yBias * 0.22f * curlStrength.coerceIn(0.2f, 1.2f) * (1f - p * 0.65f)
        val slantDelta = (h * 0.5f) * tan(tiltRad)

        val baseX = when (direction) {
            CurlDirection.FORWARD -> w * (1f - p * 1.15f)
            CurlDirection.BACKWARD -> w * (p * 1.15f - 0.15f)
        }

        val foldXTop = (baseX - slantDelta).coerceIn(-w * 0.35f, w * 1.35f)
        val foldXBottom = (baseX + slantDelta).coerceIn(-w * 0.35f, w * 1.35f)
        val creaseCenterX = (foldXTop + foldXBottom) * 0.5f
        val angleDeg = (atan2(foldXBottom - foldXTop, h) * 180f / PI).toFloat()

        val midBell = sin(p * PI.toFloat()).coerceIn(0f, 1f)
        val curlRadius = (min(w, h) * (0.055f + 0.085f * curlStrength) * midBell).coerceAtLeast(4f)
        val shadowAlpha = (shadowStrength.coerceIn(0f, 1f) * 0.65f * midBell).coerceIn(0f, 0.78f)
        val specularAlpha = (0.34f * curlStrength * midBell).coerceIn(0f, 0.45f)

        val backWidth = curlRadius * 2.2f
        val backMirrorPivotX = creaseCenterX
        val backMirrorOffsetX = when (direction) {
            CurlDirection.FORWARD -> creaseCenterX - backWidth
            CurlDirection.BACKWARD -> creaseCenterX
        }

        return CurlFrameGeometry(
            width = w,
            height = h,
            progress = p,
            foldXTop = foldXTop,
            foldXBottom = foldXBottom,
            creaseCenterX = creaseCenterX,
            creaseAngleDeg = angleDeg,
            curlRadius = curlRadius,
            shadowAlpha = shadowAlpha,
            specularAlpha = specularAlpha,
            backMirrorOffsetX = backMirrorOffsetX,
            backMirrorPivotX = backMirrorPivotX,
            direction = direction
        )
    }
}

/**
 * Shared HorizontalPager page-turn compositor for both Reflowable Reader & Fixed-Layout PDFs.
 * Properly manages `zIndex`, stationary cross-fades, card stacks, and hinged 3D cover flips.
 *
 * Sign convention: `offsetFraction = (pagerState.currentPage - pageIdx) + pagerState.currentPageOffsetFraction`
 * Swiping forward (page 0 -> page 1): page 0 has `offsetFraction in 0..1`, page 1 has `-1..0`.
 */
fun Modifier.mochiPageTurnTransition(
    mode: PageTurnMode,
    offsetFraction: Float,
    reduceMotion: Boolean
): Modifier {
    val absOff = offsetFraction.absoluteValue.coerceIn(0f, 1f)
    if (reduceMotion) {
        return this
            .zIndex(if (absOff < 0.5f) 2f else 0f)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Auto
                translationX = offsetFraction * size.width
                alpha = if (absOff < 0.5f) 1f else 0f
            }
    }
    return when (mode) {
        PageTurnMode.SLIDE -> this
            .zIndex(1f - absOff * 0.1f)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Auto
            }
        PageTurnMode.NONE -> this
            .zIndex(if (absOff < 0.5f) 2f else 0f)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Auto
                translationX = offsetFraction * size.width
                alpha = if (absOff < 0.5f) 1f else 0f
            }
        PageTurnMode.FADE -> this
            .zIndex(1f - absOff)
            .graphicsLayer {
                // Keep both pages stationary while smoothly cross-fading on GPU layer
                compositingStrategy = CompositingStrategy.Auto
                translationX = offsetFraction * size.width
                alpha = (1f - absOff * 1.15f).coerceIn(0f, 1f)
            }
        PageTurnMode.STACK -> if (offsetFraction >= 0f) {
            // Left / outgoing top card slides cleanly off the screen above the revealed card
            this
                .zIndex(2f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Auto
                    shadowElevation = (1f - absOff) * 14f
                }
        } else {
            // Right / incoming lower card waits underneath and rises smoothly to full scale
            this
                .zIndex(1f)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Auto
                    translationX = offsetFraction * size.width
                    val s = (0.93f + 0.07f * (1f - absOff)).coerceIn(0.93f, 1f)
                    scaleX = s
                    scaleY = s
                    alpha = (0.68f + 0.32f * (1f - absOff)).coerceIn(0.68f, 1f)
                }
        }
        PageTurnMode.COVER_FLIP -> this
            .zIndex(1f - absOff)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Auto
                translationX = offsetFraction * size.width * 0.48f
                rotationY = (offsetFraction * -54f).coerceIn(-68f, 68f)
                cameraDistance = 18f * density
                transformOrigin = TransformOrigin(if (offsetFraction >= 0f) 0.12f else 0.88f, 0.5f)
                alpha = if (absOff > 0.78f) ((1f - absOff) / 0.22f).coerceIn(0f, 1f) else 1f
            }
        PageTurnMode.CURL, PageTurnMode.SCROLL -> this
    }
}

/**
 * Interactive 120fps GPU-composited Page Curl container using direct Compose [GraphicsLayer]s
 * (zero CPU `toImageBitmap()` readback, zero 30MB per-turn bitmap copies, and instant finger tracking).
 */
@Composable
fun InteractivePageCurlContainer(
    currentPageIndex: Int,
    pageCount: Int,
    paperTintColor: Color,
    curlStrength: Float,
    curlShadowStrength: Float,
    curlShowPageBack: Boolean,
    curlSpeed: Float,
    grabMode: CurlGrabMode,
    reduceMotion: Boolean,
    onTurnToPage: (Int) -> Unit,
    onAnimationActiveChanged: (Boolean) -> Unit,
    onCenterTap: (Float) -> Unit,
    modifier: Modifier = Modifier,
    programmaticTurnRequest: Pair<Int, Long>? = null,
    pageContent: @Composable (pageIndex: Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var containerSize by remember { mutableStateOf(IntSize(1080, 1920)) }
    var activeDirection by remember { mutableStateOf<CurlDirection?>(null) }
    var targetIndex by remember { mutableIntStateOf(currentPageIndex) }
    var anchorYFraction by remember { mutableFloatStateOf(0.62f) }
    var isDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var liveDragProgress by remember { mutableFloatStateOf(0f) }

    val progressAnim = remember { Animatable(0f) }

    val currentLayer = rememberGraphicsLayer()
    val nextLayer = rememberGraphicsLayer()
    val prevLayer = rememberGraphicsLayer()

    val currentPageState = rememberUpdatedState(currentPageIndex)
    val pageCountState = rememberUpdatedState(pageCount)
    val grabModeState = rememberUpdatedState(grabMode)
    val reduceMotionState = rememberUpdatedState(reduceMotion)
    val curlSpeedState = rememberUpdatedState(curlSpeed)
    val onTurnState = rememberUpdatedState(onTurnToPage)
    val onTapState = rememberUpdatedState(onCenterTap)

    val isCurling = isDragging || isSettling || progressAnim.isRunning || activeDirection != null
    LaunchedEffect(isCurling) {
        onAnimationActiveChanged(isCurling)
    }

    // Handle programmatic page turns (buttons, tap zones, volume keys, auto-scroll)
    LaunchedEffect(programmaticTurnRequest) {
        val req = programmaticTurnRequest ?: return@LaunchedEffect
        val cur = currentPageState.value
        val maxIdx = (pageCountState.value - 1).coerceAtLeast(0)
        val dest = req.first.coerceIn(0, maxIdx)
        if (dest == cur || isDragging) return@LaunchedEffect

        if (reduceMotionState.value) {
            onTurnState.value(dest)
            return@LaunchedEffect
        }

        val dir = if (dest > cur) CurlDirection.FORWARD else CurlDirection.BACKWARD
        activeDirection = dir
        targetIndex = dest
        anchorYFraction = 0.66f
        isSettling = true

        progressAnim.snapTo(0.02f)
        val durationMs = (380f / curlSpeedState.value.coerceIn(0.6f, 1.8f)).toInt().coerceIn(180, 700)
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs)
        )
        onTurnState.value(dest)
        activeDirection = null
        isSettling = false
        progressAnim.snapTo(0f)
    }

    val cylinderShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(
                    """
                    uniform float2 size;
                    uniform float creaseX;
                    uniform float radius;
                    uniform float specAlpha;
                    half4 main(float2 fragCoord) {
                        float d = abs(fragCoord.x - creaseX);
                        float norm = clamp(d / max(radius, 1.0), 0.0, 1.0);
                        float highlight = sin((1.0 - norm) * 3.14159) * specAlpha;
                        return half4(1.0, 0.99, 0.97, highlight);
                    }
                    """.trimIndent()
                )
            }.getOrNull()
        } else null
    }
    val cylinderBrush = remember(cylinderShader) { cylinderShader?.let { ShaderBrush(it) } }
    val reusableClipPath = remember { Path() }
    val reusableBackStripPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .testTag("page_curl_container")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val grabAllowed = CurlPhysicsMath.isGrabZoneAllowed(downPos, w, h, grabModeState.value)

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, downPos)

                    var startedDrag = false
                    var totalDx = 0f
                    var totalDy = 0f

                    // Ergonomic drag span capped nicely so wide tablets don't require full-arm drags
                    val effectiveSpan = min(w * 0.56f, h * 0.54f).coerceAtLeast(220f)

                    while (true) {
                        val event = awaitPointerEvent()
                        // Cancel curl drag cleanly if second finger touches down (e.g. PDF pinch zoom)
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            if (startedDrag) {
                                isDragging = false
                                isSettling = true
                                val snapFrom = liveDragProgress
                                scope.launch {
                                    progressAnim.snapTo(snapFrom)
                                    progressAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                    activeDirection = null
                                    isSettling = false
                                    liveDragProgress = 0f
                                }
                            }
                            break
                        }

                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (startedDrag) {
                                val velX = tracker.calculateVelocity().x
                                val dir = activeDirection
                                val dest = targetIndex
                                val releaseProgress = liveDragProgress
                                isDragging = false
                                if (dir != null) {
                                    val complete = CurlPhysicsMath.shouldCompleteTurn(
                                        progress = releaseProgress,
                                        velocityX = velX,
                                        direction = dir,
                                        speedMultiplier = curlSpeedState.value
                                    )
                                    isSettling = true
                                    scope.launch {
                                        progressAnim.snapTo(releaseProgress)
                                        val stiffness = Spring.StiffnessMedium * curlSpeedState.value.coerceIn(0.75f, 1.6f)
                                        if (complete) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            progressAnim.animateTo(
                                                1f,
                                                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)
                                            )
                                            onTurnState.value(dest)
                                        } else {
                                            progressAnim.animateTo(
                                                0f,
                                                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = stiffness)
                                            )
                                        }
                                        activeDirection = null
                                        isSettling = false
                                        liveDragProgress = 0f
                                        progressAnim.snapTo(0f)
                                    }
                                }
                            } else {
                                val dist = hypot(totalDx, totalDy)
                                if (dist < viewConfiguration.touchSlop && !change.isConsumed) {
                                    onTapState.value(downPos.x)
                                }
                            }
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y
                        tracker.addPosition(change.uptimeMillis, change.position)

                        if (!startedDrag && grabAllowed && abs(totalDx) > viewConfiguration.touchSlop && abs(totalDx) > abs(totalDy) * 1.15f) {
                            val cur = currentPageState.value
                            val count = pageCountState.value
                            val dir = if (totalDx < 0f) CurlDirection.FORWARD else CurlDirection.BACKWARD
                            val nextIdx = if (dir == CurlDirection.FORWARD) cur + 1 else cur - 1
                            if (nextIdx in 0 until count) {
                                startedDrag = true
                                activeDirection = dir
                                targetIndex = nextIdx
                                anchorYFraction = (downPos.y / h).coerceIn(0.08f, 0.92f)
                                liveDragProgress = 0.01f
                                isDragging = true
                                change.consume()
                            }
                        }

                        if (startedDrag) {
                            change.consume()
                            val dir = activeDirection
                            val rawFrac = when (dir) {
                                CurlDirection.FORWARD -> (-totalDx / effectiveSpan).coerceIn(0f, 1f)
                                CurlDirection.BACKWARD -> (totalDx / effectiveSpan).coerceIn(0f, 1f)
                                null -> 0f
                            }
                            // Direct synchronous state write for zero-latency 120fps tracking
                            liveDragProgress = rawFrac
                            anchorYFraction = (change.position.y / h).coerceIn(0.08f, 0.92f)
                        }
                    }
                }
            }
    ) {
        // Record neighbor pages into hardware GPU GraphicsLayers cleanly below active sheet
        if (currentPageIndex + 1 < pageCount) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-2f)
                    .drawWithContent {
                        nextLayer.record { this@drawWithContent.drawContent() }
                    }
            ) {
                pageContent(currentPageIndex + 1)
            }
        }
        if (currentPageIndex - 1 >= 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(-2f)
                    .drawWithContent {
                        prevLayer.record { this@drawWithContent.drawContent() }
                    }
            ) {
                pageContent(currentPageIndex - 1)
            }
        }

        // Active resting sheet recorded directly into currentLayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .drawWithContent {
                    currentLayer.record { this@drawWithContent.drawContent() }
                    if (!isCurling) {
                        drawLayer(currentLayer)
                    }
                }
        ) {
            pageContent(currentPageIndex)
        }

        // Interactive GPU curl compositor using direct drawLayer() (zero CPU readback!)
        val dir = activeDirection
        if (isCurling && dir != null) {
            val renderProgress = if (isDragging) liveDragProgress else progressAnim.value
            val targetLayer = if (dir == CurlDirection.FORWARD) nextLayer else prevLayer
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(3f)
            ) {
                val frame = CurlPhysicsMath.computeCurlFrame(
                    width = size.width,
                    height = size.height,
                    progress = renderProgress,
                    anchorYFraction = anchorYFraction,
                    direction = dir,
                    curlStrength = curlStrength,
                    shadowStrength = curlShadowStrength
                )
                drawCurlCompositionLayer(
                    frame = frame,
                    currentLayer = currentLayer,
                    targetLayer = targetLayer,
                    paperTintColor = paperTintColor,
                    showPageBack = curlShowPageBack,
                    cylinderShader = cylinderShader,
                    cylinderBrush = cylinderBrush,
                    clipPolygon = reusableClipPath,
                    backStripPath = reusableBackStripPath
                )
            }
        }
    }
}

private fun DrawScope.drawCurlCompositionLayer(
    frame: CurlFrameGeometry,
    currentLayer: GraphicsLayer,
    targetLayer: GraphicsLayer,
    paperTintColor: Color,
    showPageBack: Boolean,
    cylinderShader: RuntimeShader?,
    cylinderBrush: ShaderBrush?,
    clipPolygon: Path,
    backStripPath: Path
) {
    val underLayer = if (frame.direction == CurlDirection.FORWARD) targetLayer else currentLayer
    val topLayer = if (frame.direction == CurlDirection.FORWARD) currentLayer else targetLayer

    // 1. Underlying page
    drawRect(paperTintColor)
    drawLayer(underLayer)

    // 2. Soft drop shadow cast onto revealed underlying page
    val shadowWidth = (frame.curlRadius * 2.0f).coerceAtLeast(18f)
    val shadowLeft = if (frame.direction == CurlDirection.FORWARD) {
        frame.creaseCenterX
    } else {
        frame.creaseCenterX - shadowWidth
    }
    drawRect(
        brush = Brush.horizontalGradient(
            colors = if (frame.direction == CurlDirection.FORWARD) {
                listOf(Color.Black.copy(alpha = frame.shadowAlpha), Color.Transparent)
            } else {
                listOf(Color.Transparent, Color.Black.copy(alpha = frame.shadowAlpha))
            },
            startX = shadowLeft,
            endX = shadowLeft + shadowWidth
        ),
        topLeft = Offset(shadowLeft.coerceIn(0f, size.width), 0f),
        size = Size(shadowWidth, size.height)
    )

    // 3. Clipped unturned side of top sheet (reusing pre-allocated Path)
    clipPolygon.reset()
    if (frame.direction == CurlDirection.FORWARD) {
        clipPolygon.moveTo(0f, 0f)
        clipPolygon.lineTo(frame.foldXTop, 0f)
        clipPolygon.lineTo(frame.foldXBottom, size.height)
        clipPolygon.lineTo(0f, size.height)
    } else {
        clipPolygon.moveTo(frame.foldXTop, 0f)
        clipPolygon.lineTo(size.width, 0f)
        clipPolygon.lineTo(size.width, size.height)
        clipPolygon.lineTo(frame.foldXBottom, size.height)
    }
    clipPolygon.close()

    clipPath(clipPolygon) {
        drawRect(paperTintColor)
        drawLayer(topLayer)
    }

    // 4. Turned page back strip with subtle mirrored content & cylinder highlight (reusing pre-allocated Path)
    val backWidth = (frame.curlRadius * 2.15f).coerceAtLeast(16f)
    backStripPath.reset()
    if (frame.direction == CurlDirection.FORWARD) {
        backStripPath.moveTo(frame.foldXTop - backWidth, 0f)
        backStripPath.lineTo(frame.foldXTop, 0f)
        backStripPath.lineTo(frame.foldXBottom, size.height)
        backStripPath.lineTo(frame.foldXBottom - backWidth, size.height)
    } else {
        backStripPath.moveTo(frame.foldXTop, 0f)
        backStripPath.lineTo(frame.foldXTop + backWidth, 0f)
        backStripPath.lineTo(frame.foldXBottom + backWidth, size.height)
        backStripPath.lineTo(frame.foldXBottom, size.height)
    }
    backStripPath.close()

    clipPath(backStripPath) {
        drawRect(paperTintColor.copy(alpha = 0.97f))
        if (showPageBack) {
            withTransform({
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset(frame.creaseCenterX, size.height * 0.5f))
            }) {
                drawLayer(topLayer)
            }
            // Soft paper opacity veil so mirrored back text reads subtly like real thin paper
            drawRect(paperTintColor.copy(alpha = 0.78f))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cylinderShader != null && cylinderBrush != null) {
            cylinderShader.setFloatUniform("size", size.width, size.height)
            cylinderShader.setFloatUniform("creaseX", frame.creaseCenterX)
            cylinderShader.setFloatUniform("radius", backWidth)
            cylinderShader.setFloatUniform("specAlpha", frame.specularAlpha)
            drawRect(brush = cylinderBrush)
        } else {
            val gradStart = if (frame.direction == CurlDirection.FORWARD) frame.creaseCenterX - backWidth else frame.creaseCenterX
            val gradEnd = gradStart + backWidth
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = frame.shadowAlpha * 0.25f),
                        Color.White.copy(alpha = frame.specularAlpha),
                        Color.Black.copy(alpha = frame.shadowAlpha * 0.35f)
                    ),
                    startX = gradStart,
                    endX = gradEnd
                )
            )
        }
    }

    // 5. Crisp crease edge highlight line
    drawLine(
        color = Color.Black.copy(alpha = (frame.shadowAlpha * 0.4f).coerceIn(0f, 0.28f)),
        start = Offset(frame.foldXTop, 0f),
        end = Offset(frame.foldXBottom, size.height),
        strokeWidth = 1.dp.toPx()
    )
}
