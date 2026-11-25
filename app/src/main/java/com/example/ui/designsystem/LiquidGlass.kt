package com.example.ui.designsystem

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.theme.ContrastChecker
import com.example.ui.theme.LocalGlassConfig
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Liquid Glass container used exclusively for floating chrome (bottom bar, pills, sheets, toolbars).
 * Never applied to book body text.
 *
 * - API 31+: Blurred translucent frosted layer (frozen when [freezeBlur] is true) + adaptive scrim.
 * - API 33+: Subtle AGSL optical refraction sheen along the top edge.
 * - API 26-30: Semi-opaque tonal surface with identical luminous border and soft shadow.
 * - Solid chrome / Off: Completely opaque tonal surface with zero blur or translucency.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    elevation: Dp = 10.dp,
    freezeBlur: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalMochiPalette.current
    val glassConfig = LocalGlassConfig.current
    val isSolid = glassConfig.isSolid

    val baseFill = remember(palette, glassConfig) {
        val rawAlpha = if (isSolid) {
            1f
        } else {
            (palette.glassTintAlpha * glassConfig.intensity.alphaMultiplier).coerceIn(0.68f, 0.96f)
        }
        ContrastChecker.adaptiveGlassFill(
            glassTint = Color(palette.glassTint),
            glassTintAlpha = rawAlpha,
            underlyingColor = Color(palette.background),
            textColor = Color(palette.text),
            solidChrome = isSolid
        )
    }

    val borderBrush = remember(palette, isSolid) {
        if (isSolid) {
            Brush.verticalGradient(
                listOf(
                    Color(palette.textSecondary).copy(alpha = 0.25f),
                    Color(palette.textSecondary).copy(alpha = 0.20f)
                )
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(palette.glassHighlight),
                    Color(palette.glassBorder),
                    Color(palette.glassBorder).copy(alpha = 0.25f)
                )
            )
        }
    }

    val shaderColor = Color(palette.glassHighlight)
    val cachedLensShader = remember(palette.glassHighlight, isSolid) {
        if (!isSolid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                RuntimeShader(
                    """
                    uniform float2 size;
                    uniform half4 highlightColor;
                    half4 main(float2 fragCoord) {
                        float yNorm = fragCoord.y / max(size.y, 1.0);
                        float topBand = smoothstep(0.45, 0.0, yNorm);
                        return half4(highlightColor.rgb, highlightColor.a * topBand * 0.22);
                    }
                    """.trimIndent()
                ).apply {
                    setFloatUniform("highlightColor", shaderColor.red, shaderColor.green, shaderColor.blue, shaderColor.alpha)
                }
            }.getOrNull()
        } else null
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isSolid) 2.dp else elevation,
                shape = shape,
                ambientColor = Color(palette.glassShadow),
                spotColor = Color(palette.glassShadow)
            )
            .clip(shape)
    ) {
        // Frosted glass backdrop layer with zero-allocation optical specular sheen
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(baseFill)
                .drawBehind {
                    if (!isSolid && !freezeBlur) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && cachedLensShader != null) {
                            cachedLensShader.setFloatUniform("size", size.width, size.height)
                            drawRect(brush = ShaderBrush(cachedLensShader))
                            return@drawBehind
                        }
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(palette.glassHighlight).copy(alpha = 0.20f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.55f
                            )
                        )
                    }
                }
        )

        // Crisp 1dp top-lit luminous border + sharp foreground content
        Box(
            modifier = Modifier
                .border(BorderStroke(1.dp, borderBrush), shape = shape),
            content = content
        )
    }
}

data class GlassNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun LiquidGlassBottomBar(
    destinations: List<GlassNavDestination>,
    currentRoute: String,
    visible: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val motion = LocalMochiMotion.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialDefault()) { it / 2 },
        exit = fadeOut(motion.effectsDefault()) + slideOutVertically(motion.spatialDefault()) { it / 2 },
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LiquidGlassSurface(
                shape = CircleShape,
                elevation = 14.dp,
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    destinations.forEach { dest ->
                        val selected = currentRoute == dest.route
                        val interactionSource = remember { MutableInteractionSource() }
                        val pillColor = if (selected) Color(palette.accentContainer) else Color.Transparent
                        val iconTint = if (selected) {
                            ContrastChecker.bestOnColor(Color(palette.accentContainer))
                        } else {
                            Color(palette.textSecondary)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(CircleShape)
                                .background(pillColor)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(bounded = true),
                                    role = Role.Tab,
                                    onClick = { onSelect(dest.route) }
                                )
                                .testTag(dest.testTag)
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = dest.label,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = dest.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = iconTint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    LiquidGlassSurface(
        shape = CircleShape,
        elevation = 6.dp,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp, minWidth = 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

object LiquidGlassSliderMath {
    fun fractionForX(xPx: Float, totalWidthPx: Float, thumbSizePx: Float): Float {
        val usable = (totalWidthPx - thumbSizePx).coerceAtLeast(1f)
        return ((xPx - thumbSizePx * 0.5f) / usable).coerceIn(0f, 1f)
    }
}

/**
 * Liquid Glass Slider replacing stock Material Sliders:
 * - Translucent frosted pill track with specular edge highlight (or crisp solid tone when solidChrome = true).
 * - Accent gradient active fill + optional chapter/snap tick markers.
 * - Floating refractive glass orb thumb that smoothly expands while scrubbing.
 * - Unified gesture detector with immediate thumb lock + updated closure references.
 * - Minimum 48dp accessible touch height & full semantics support.
 */
@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    tickFractions: List<Float> = emptyList(),
    enabled: Boolean = true
) {
    val palette = LocalMochiPalette.current
    val glassConfig = LocalGlassConfig.current
    val isSolid = glassConfig.isSolid
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 1e-6f } ?: 1f
    val clampedFraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(clampedFraction) }
    var sliderWidthPx by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) dragFraction else clampedFraction
    val animatedFraction by animateFloatAsState(targetValue = activeFraction, label = "glass_slider_frac")
    val displayFraction = if (isDragging) activeFraction else animatedFraction

    val thumbDiameter by animateDpAsState(
        targetValue = if (isDragging) 28.dp else 22.dp,
        label = "glass_thumb_size"
    )
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 10.dp,
        label = "glass_track_height"
    )
    val thumbSizePx = with(density) { thumbDiameter.toPx() }

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentRange by rememberUpdatedState(valueRange)
    val currentFractionState by rememberUpdatedState(activeFraction)
    val thumbSizeState by rememberUpdatedState(thumbSizePx)

    val trackBg = remember(palette, isSolid) {
        if (isSolid) {
            Color(palette.surfaceVariant)
        } else {
            Color(palette.glassTint).copy(alpha = 0.55f)
        }
    }
    val borderCol = remember(palette, isSolid) {
        if (isSolid) Color(palette.textSecondary).copy(alpha = 0.28f)
        else Color(palette.glassHighlight).copy(alpha = 0.72f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .onSizeChanged { sliderWidthPx = it.width.toFloat() }
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(valueRange.start, valueRange.endInclusive),
                    range = valueRange
                )
                if (enabled) {
                    setProgress { target ->
                        val next = target.coerceIn(currentRange.start, currentRange.endInclusive)
                        currentOnValueChange(next)
                        currentOnFinished?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    val tSize = thumbSizeState
                    val usable = (widthPx - tSize).coerceAtLeast(1f)
                    val thumbCenter = (tSize * 0.5f) + currentFractionState * usable
                    val startedOnThumb = abs(down.position.x - thumbCenter) <= (viewConfiguration.touchSlop * 1.5f + tSize * 0.65f)

                    var dragLocked = startedOnThumb
                    var lastFrac = currentFractionState
                    if (dragLocked) {
                        down.consume()
                        isDragging = true
                        val frac = LiquidGlassSliderMath.fractionForX(down.position.x, widthPx, tSize)
                        lastFrac = frac
                        dragFraction = frac
                        val r = currentRange
                        val rSpan = (r.endInclusive - r.start).takeIf { it > 1e-6f } ?: 1f
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnValueChange(r.start + frac * rSpan)
                    }

                    var totalDx = 0f
                    var totalDy = 0f
                    var releasedCleanly = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break
                        if (!change.pressed) {
                            releasedCleanly = !change.isConsumed || dragLocked
                            if (dragLocked) change.consume()
                            break
                        }

                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!dragLocked) {
                            if (change.isConsumed) break
                            val hSlop = viewConfiguration.touchSlop * 0.45f
                            if (abs(totalDx) >= hSlop && abs(totalDx) >= abs(totalDy) * 0.55f) {
                                dragLocked = true
                                isDragging = true
                                change.consume()
                            } else if (abs(totalDy) > viewConfiguration.touchSlop * 1.35f && abs(totalDy) > abs(totalDx) * 1.8f) {
                                break
                            }
                        }

                        if (dragLocked) {
                            change.consume()
                            val frac = LiquidGlassSliderMath.fractionForX(change.position.x, widthPx, tSize)
                            val crossedEnd = (lastFrac > 0.01f && frac <= 0.005f) || (lastFrac < 0.99f && frac >= 0.995f)
                            if (crossedEnd) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastFrac = frac
                            dragFraction = frac
                            val r = currentRange
                            val rSpan = (r.endInclusive - r.start).takeIf { it > 1e-6f } ?: 1f
                            currentOnValueChange(r.start + frac * rSpan)
                        }
                    }

                    if (dragLocked) {
                        isDragging = false
                        currentOnFinished?.invoke()
                    } else if (releasedCleanly && hypot(totalDx, totalDy) <= viewConfiguration.touchSlop * 1.25f) {
                        val frac = LiquidGlassSliderMath.fractionForX(down.position.x, widthPx, tSize)
                        val r = currentRange
                        val rSpan = (r.endInclusive - r.start).takeIf { it > 1e-6f } ?: 1f
                        dragFraction = frac
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnValueChange(r.start + frac * rSpan)
                        currentOnFinished?.invoke()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = sliderWidthPx.coerceAtLeast(thumbSizePx + 1f)
        val usablePx = (totalWidthPx - thumbSizePx).coerceAtLeast(1f)
        val thumbCenterX = (thumbSizePx / 2f) + displayFraction * usablePx

        // Frosted / Solid Glass Pill Track
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            // Base glass track
            drawRoundRect(
                color = trackBg,
                cornerRadius = corner
            )
            // Specular top sheen along track
            if (!isSolid) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(palette.glassHighlight).copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    ),
                    cornerRadius = corner
                )
            }
            // Active glowing accent fill
            val fillW = (thumbCenterX).coerceIn(size.height, size.width)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        Color(palette.accent).copy(alpha = if (enabled) 0.78f else 0.4f),
                        Color(palette.accent).copy(alpha = if (enabled) 1f else 0.5f)
                    )
                ),
                size = Size(fillW, size.height),
                cornerRadius = corner
            )
            // Optional ticks (e.g., chapter markers)
            tickFractions.forEach { frac ->
                val tx = ((thumbSizePx / 2f) + frac.coerceIn(0f, 1f) * usablePx).coerceIn(4f, size.width - 4f)
                drawCircle(
                    color = if (frac <= displayFraction) {
                        ContrastChecker.bestOnColor(Color(palette.accent)).copy(alpha = 0.6f)
                    } else {
                        Color(palette.textSecondary).copy(alpha = 0.5f)
                    },
                    radius = 2.dp.toPx(),
                    center = Offset(tx, size.height / 2f)
                )
            }
            // Crisp hairline glass border
            drawRoundRect(
                color = borderCol,
                cornerRadius = corner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }

        // Refractive Glass Orb Thumb
        val thumbXOffset = (thumbCenterX - thumbSizePx / 2f).roundToInt()
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbXOffset, 0) }
                .size(thumbDiameter)
                .shadow(
                    elevation = if (isDragging) 10.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = Color(palette.glassShadow),
                    spotColor = Color(palette.glassShadow)
                )
                .clip(CircleShape)
                .background(
                    if (isSolid) {
                        Brush.radialGradient(listOf(Color(palette.accent), Color(palette.accent)))
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.94f),
                                Color(palette.glassTint).copy(alpha = 0.85f)
                            )
                        )
                    }
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.95f),
                            Color(palette.accent).copy(alpha = 0.65f)
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!isSolid) {
                Box(
                    modifier = Modifier
                        .size(thumbDiameter * 0.44f)
                        .clip(CircleShape)
                        .background(Color(palette.accent).copy(alpha = if (enabled) 0.9f else 0.4f))
                )
            }
        }
    }
}

data class GlassSwitchItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Compact frosted Liquid Glass segmented pill switcher (e.g., Grid / List / Shelf).
 */
@Composable
fun <T> LiquidGlassViewSwitcher(
    items: List<GlassSwitchItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val palette = LocalMochiPalette.current
    LiquidGlassSurface(
        shape = CircleShape,
        elevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.value == selected
                val bg = if (isSelected) Color(palette.accentContainer) else Color.Transparent
                val fg = if (isSelected) {
                    ContrastChecker.bestOnColor(Color(palette.accentContainer))
                } else {
                    Color(palette.textSecondary)
                }
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = if (compact) 38.dp else 44.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .clickable { onSelect(item.value) }
                        .testTag(item.testTag)
                        .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = fg,
                        modifier = Modifier.size(16.dp)
                    )
                    if (!compact || isSelected) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = fg,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

