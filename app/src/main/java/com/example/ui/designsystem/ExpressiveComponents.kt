package com.example.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.BookEntity
import com.example.theme.ContrastChecker
import com.example.theme.DecorProgressStyle
import com.example.ui.theme.LiterataFontFamily
import com.example.ui.theme.LocalMochiPalette
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Material 3 Expressive connected segmented pill switcher.
 */
@Composable
fun <T> MochiConnectedSegments(
    items: List<T>,
    selected: T,
    labelProvider: (T) -> String,
    testTagProvider: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val motion = LocalMochiMotion.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(Color(palette.surfaceVariant))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selected
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) Color(palette.accentContainer) else Color.Transparent,
                animationSpec = motion.effectsDefault(),
                label = "segmentBg"
            )
            val textColor = if (isSelected) {
                ContrastChecker.bestOnColor(Color(palette.accentContainer))
            } else {
                Color(palette.textSecondary)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(CircleShape)
                    .background(bgColor)
                    .clickable(role = Role.Tab) { onSelect(item) }
                    .testTag(testTagProvider(item))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Expressive wavy / themed progress indicator supporting Cute & Cozy decor tokens.
 */
@Composable
fun MochiWavyProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    style: DecorProgressStyle = LocalMochiPalette.current.progressStyle
) {
    val palette = LocalMochiPalette.current
    val motion = LocalMochiMotion.current
    val clamped = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clamped,
        animationSpec = motion.spatialDefault(),
        label = "progressAnim"
    )

    val phase = if (!motion.reduceMotion && style == DecorProgressStyle.WAVY) {
        val infinite = rememberInfiniteTransition(label = "wavePhase")
        val value by infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        value
    } else {
        0f
    }

    val activeColor = Color(palette.accent)
    val trackColor = Color(palette.surfaceVariant)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
    ) {
        val midY = size.height / 2f
        val fillX = size.width * animatedProgress

        // Track
        drawLine(
            color = trackColor,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        if (fillX > 2f) {
            if (style == DecorProgressStyle.WAVY && !motion.reduceMotion) {
                val path = Path()
                val amp = 2.5.dp.toPx()
                val wavelength = 18.dp.toPx()
                path.moveTo(0f, midY + sin(phase) * amp)
                var x = 0f
                while (x <= fillX) {
                    val y = midY + sin((x / wavelength) * (2f * PI).toFloat() + phase) * amp
                    path.lineTo(x, y)
                    x += 3f
                }
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, midY),
                    end = Offset(fillX, midY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Original vector Mochi rice-cake mascot with small bookmark ribbon.
 * Used exclusively in onboarding and calm empty states; honors Reduce Motion.
 */
@Composable
fun MochiBlobMascot(
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    bodyColor: Color = Color(0xFFFFFCF9),
    ribbonColor: Color = Color(LocalMochiPalette.current.accent),
    cheekColor: Color = Color(0xFFFFB3C6)
) {
    val palette = LocalMochiPalette.current
    val motion = LocalMochiMotion.current

    val breathe = if (motion.reduceMotion) {
        0f
    } else {
        val infinite = rememberInfiniteTransition(label = "mochiBreathe")
        val v by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathe"
        )
        v
    }

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val squash = breathe * h * 0.025f

        // Soft shadow underneath
        drawOval(
            color = Color(palette.glassShadow).copy(alpha = 0.22f),
            topLeft = Offset(w * 0.16f, h * 0.80f),
            size = Size(w * 0.68f, h * 0.12f)
        )

        // Plump rice-cake body
        val bodyPath = Path().apply {
            val left = w * 0.12f
            val right = w * 0.88f
            val top = h * 0.24f + squash
            val bottom = h * 0.84f
            moveTo(left, bottom - h * 0.14f)
            cubicTo(
                left - w * 0.02f, top + h * 0.15f,
                w * 0.25f, top,
                w * 0.50f, top
            )
            cubicTo(
                w * 0.75f, top,
                right + w * 0.02f, top + h * 0.15f,
                right, bottom - h * 0.14f
            )
            cubicTo(
                right, bottom,
                w * 0.72f, bottom + h * 0.02f,
                w * 0.50f, bottom + h * 0.01f
            )
            cubicTo(
                w * 0.28f, bottom + h * 0.02f,
                left, bottom,
                left, bottom - h * 0.14f
            )
            close()
        }
        drawPath(path = bodyPath, color = bodyColor)
        drawPath(
            path = bodyPath,
            color = Color(palette.textSecondary).copy(alpha = 0.18f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Small bookmark ribbon tucked on upper right
        val ribbonPath = Path().apply {
            moveTo(w * 0.61f, h * 0.23f + squash)
            lineTo(w * 0.73f, h * 0.28f + squash)
            lineTo(w * 0.66f, h * 0.48f + squash)
            lineTo(w * 0.60f, h * 0.42f + squash)
            lineTo(w * 0.53f, h * 0.45f + squash)
            close()
        }
        drawPath(path = ribbonPath, color = ribbonColor)

        // Calm eyes & rosy cheeks
        val eyeY = h * 0.54f + squash * 0.5f
        val eyeColor = Color(0xFF3A2830)
        drawCircle(color = eyeColor, radius = w * 0.028f, center = Offset(w * 0.38f, eyeY))
        drawCircle(color = eyeColor, radius = w * 0.028f, center = Offset(w * 0.58f, eyeY))

        drawOval(
            color = cheekColor.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.27f, eyeY + h * 0.03f),
            size = Size(w * 0.09f, h * 0.05f)
        )
        drawOval(
            color = cheekColor.copy(alpha = 0.55f),
            topLeft = Offset(w * 0.60f, eyeY + h * 0.03f),
            size = Size(w * 0.09f, h * 0.05f)
        )
    }
}

/**
 * Renders either a cached cover bitmap (via Coil) or a crisp generated typographic cover
 * based on the book's title, format, and current theme highlights.
 */
@Composable
fun MochiBookCover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp
) {
    val palette = LocalMochiPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val context = androidx.compose.ui.platform.LocalContext.current
    val coverPath = book.coverPath
    val coverFile = remember(coverPath) {
        coverPath?.let { File(it) }?.takeIf { it.exists() }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.16f), shape)
    ) {
        if (coverFile != null) {
            val imageRequest = remember(coverFile, book.id) {
                coil.request.ImageRequest.Builder(context)
                    .data(coverFile)
                    .memoryCacheKey("cover_${book.id}_${coverFile.lastModified()}")
                    .size(420, 620)
                    .precision(coil.size.Precision.INEXACT)
                    .scale(coil.size.Scale.FILL)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .diskCachePolicy(coil.request.CachePolicy.DISABLED)
                    .crossfade(160)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "${book.title} cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val hash = abs(book.title.hashCode())
            val c1 = Color(palette.highlightColors[hash % palette.highlightColors.size])
            val c2 = Color(palette.highlightColors[(hash + 3) % palette.highlightColors.size])
            val textColor = ContrastChecker.bestOnColor(c1)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(c1, c2, Color(palette.accentContainer))
                        )
                    )
                    .padding(10.dp)
            ) {
                // Subtle spine crease on the left
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(width = 3.dp, height = 200.dp)
                        .background(Color.Black.copy(alpha = 0.12f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = book.format.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.75f)
                    )
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = LiterataFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            color = textColor,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (book.authors.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = book.authors,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
