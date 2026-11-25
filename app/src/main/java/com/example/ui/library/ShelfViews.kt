package com.example.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.data.db.BookEntity
import com.example.theme.ContrastChecker
import com.example.theme.ShelfStyle
import com.example.ui.designsystem.MochiBookCover
import com.example.ui.designsystem.MochiWavyProgressBar
import com.example.ui.designsystem.mochiSharedBookBounds
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

@Composable
fun ShelfViewContainer(
    shelfStyle: ShelfStyle,
    items: List<BookStackGroup>,
    selectedBookIds: Set<Long>,
    spineThicknessByPages: Boolean,
    shelfBackgroundUri: String?,
    shelfBackgroundDim: Float,
    shelfBackgroundBlur: Float,
    onBookClick: (BookEntity, String) -> Unit,
    onInspectBook: (BookEntity) -> Unit,
    onStackClick: (BookStackGroup) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSwapBooks: (Long, Long) -> Unit,
    onStackBooks: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val isAnyShelf = shelfStyle == ShelfStyle.GLASS_SHELF || shelfStyle == ShelfStyle.WOODEN_SHELF
        // Optional user shelf background image with dim/blur sliders
        if (isAnyShelf && !shelfBackgroundUri.isNullOrBlank()) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val bgRequest = remember(shelfBackgroundUri) {
                coil.request.ImageRequest.Builder(context)
                    .data(shelfBackgroundUri)
                    .size(1080, 1080)
                    .precision(coil.size.Precision.INEXACT)
                    .scale(coil.size.Scale.FILL)
                    .allowHardware(true)
                    .allowRgb565(true)
                    .crossfade(200)
                    .build()
            }
            AsyncImage(
                model = bgRequest,
                contentDescription = "Shelf background",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .then(if (shelfBackgroundBlur > 0f) Modifier.blur(shelfBackgroundBlur.dp) else Modifier)
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = shelfBackgroundDim.coerceIn(0f, 0.85f)))
            )
        }

        when (shelfStyle) {
            ShelfStyle.FLAT_GRID,
            ShelfStyle.GLASS_SHELF,
            ShelfStyle.WOODEN_SHELF -> InteractiveBookGrid(
                items = items,
                shelfStyle = shelfStyle,
                selectedBookIds = selectedBookIds,
                spineThicknessByPages = spineThicknessByPages,
                onBookClick = onBookClick,
                onStackClick = onStackClick,
                onToggleSelect = onToggleSelect,
                onSwapBooks = onSwapBooks,
                onStackBooks = onStackBooks
            )
            ShelfStyle.LIST -> BookListView(
                items = items,
                selectedBookIds = selectedBookIds,
                onBookClick = onBookClick,
                onInspectBook = onInspectBook,
                onStackClick = onStackClick,
                onToggleSelect = onToggleSelect
            )
        }
    }
}

@Composable
private fun InteractiveBookGrid(
    items: List<BookStackGroup>,
    shelfStyle: ShelfStyle,
    selectedBookIds: Set<Long>,
    spineThicknessByPages: Boolean,
    onBookClick: (BookEntity, String) -> Unit,
    onStackClick: (BookStackGroup) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSwapBooks: (Long, Long) -> Unit,
    onStackBooks: (Long, Long) -> Unit
) {
    val palette = LocalMochiPalette.current
    val haptics = LocalHapticFeedback.current
    val isGlassShelf = shelfStyle == ShelfStyle.GLASS_SHELF
    val isWoodenShelf = shelfStyle == ShelfStyle.WOODEN_SHELF
    val hasShelfLedge = isGlassShelf || isWoodenShelf

    val itemBounds = remember { mutableStateMapOf<Long, Rect>() }
    var draggingBookId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetId by remember { mutableStateOf<Long?>(null) }
    var dropAsStack by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 840.dp -> 5
            maxWidth >= 600.dp -> 4
            else -> 3
        }
        val rows = remember(items, columns) { items.chunked(columns) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (hasShelfLedge) 10.dp else 18.dp)
        ) {
            rows.forEach { rowItems ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (hasShelfLedge) 8.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        for (colIdx in 0 until columns) {
                            val group = rowItems.getOrNull(colIdx)
                            if (group == null) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                val lead = group.leadBook
                                val isDragging = draggingBookId == lead.id
                                val isHoverTarget = hoveredTargetId == lead.id && draggingBookId != lead.id
                                val isSelected = lead.id in selectedBookIds

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .zIndex(if (isDragging) 10f else 0f)
                                        .onGloballyPositioned { coords ->
                                            itemBounds[lead.id] = coords.boundsInWindow()
                                        }
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                scaleX = 1.06f
                                                scaleY = 1.06f
                                            } else if (isHoverTarget) {
                                                scaleX = if (dropAsStack) 0.94f else 1.02f
                                                scaleY = if (dropAsStack) 0.94f else 1.02f
                                            }
                                        }
                                        .pointerInput(lead.id, items) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    draggingBookId = lead.id
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffset += dragAmount
                                                    val origin = itemBounds[lead.id] ?: return@detectDragGesturesAfterLongPress
                                                    val center = origin.center + dragOffset
                                                    val targetEntry = itemBounds.entries.firstOrNull { (id, rect) ->
                                                        id != lead.id && rect.contains(center)
                                                    }
                                                    hoveredTargetId = targetEntry?.key
                                                    if (targetEntry != null) {
                                                        val targetRect = targetEntry.value
                                                        val inner = targetRect.deflate(targetRect.width * 0.24f)
                                                        dropAsStack = inner.contains(center)
                                                    } else {
                                                        dropAsStack = false
                                                    }
                                                },
                                                onDragEnd = {
                                                    val target = hoveredTargetId
                                                    val asStack = dropAsStack
                                                    draggingBookId = null
                                                    dragOffset = Offset.Zero
                                                    hoveredTargetId = null
                                                    dropAsStack = false
                                                    if (target != null && target != lead.id) {
                                                        if (asStack) {
                                                            onStackBooks(lead.id, target)
                                                        } else {
                                                            onSwapBooks(lead.id, target)
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggingBookId = null
                                                    dragOffset = Offset.Zero
                                                    hoveredTargetId = null
                                                    dropAsStack = false
                                                }
                                            )
                                        }
                                ) {
                                    val morphKey = "shelf_${lead.id}"
                                    ShelfBookCard(
                                        group = group,
                                        morphKey = morphKey,
                                        shelfStyle = shelfStyle,
                                        isSelected = isSelected,
                                        isDropTarget = isHoverTarget,
                                        dropAsStack = dropAsStack,
                                        spineThicknessByPages = spineThicknessByPages,
                                        selectionMode = selectedBookIds.isNotEmpty(),
                                        onClick = {
                                            if (selectedBookIds.isNotEmpty()) {
                                                onToggleSelect(lead.id)
                                            } else if (group.isStack) {
                                                onStackClick(group)
                                            } else {
                                                onBookClick(lead, morphKey)
                                            }
                                        },
                                        onLongClick = { onToggleSelect(lead.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (isGlassShelf) {
                        GlassBookshelfLedge()
                    } else if (isWoodenShelf) {
                        WoodenPlankLedge()
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassBookshelfLedge() {
    val palette = LocalMochiPalette.current
    val glassConfig = com.example.ui.theme.LocalGlassConfig.current
    val isSolid = glassConfig.isSolid
    val baseFill = if (isSolid) {
        Color(palette.surfaceVariant)
    } else {
        Color(palette.glassTint).copy(alpha = 0.72f)
    }
    val highlight = Color(palette.glassHighlight)
    val borderCol = Color(palette.glassBorder)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .shadow(
                elevation = if (isSolid) 2.dp else 8.dp,
                shape = CircleShape,
                ambientColor = Color(palette.glassShadow),
                spotColor = Color(palette.glassShadow)
            )
            .clip(CircleShape)
            .background(baseFill)
            .drawBehind {
                if (!isSolid) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                highlight.copy(alpha = 0.65f),
                                Color.Transparent,
                                borderCol.copy(alpha = 0.25f)
                            )
                        )
                    )
                }
            }
            .border(1.dp, if (isSolid) Color(palette.textSecondary).copy(alpha = 0.25f) else highlight.copy(alpha = 0.75f), CircleShape)
            .testTag("glass_shelf_ledge")
    )
}

@Composable
private fun WoodenPlankLedge() {
    val palette = LocalMochiPalette.current
    val woodBase = Color(palette.shelfWoodTint)
    val woodHighlight = Color(palette.shelfPlankHighlight)
    val woodShadow = Color(palette.shelfPlankShadow)
    val ledge = Color(palette.shelfLedgeColor)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .drawBehind {
                // Top plank surface gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(woodHighlight, woodBase, ledge)
                    ),
                    size = Size(size.width, size.height * 0.65f)
                )
                // Subtle procedural wood grain lines
                val grainColor = woodShadow.copy(alpha = 0.22f)
                for (i in 1..4) {
                    val y = size.height * (i * 0.14f)
                    drawLine(
                        color = grainColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y + (if (i % 2 == 0) 2f else -2f)),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
                // Front plank lip + shadow
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(ledge, woodShadow)
                    ),
                    topLeft = Offset(0f, size.height * 0.65f),
                    size = Size(size.width, size.height * 0.35f)
                )
            }
            .testTag("wooden_shelf_ledge")
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookCard(
    group: BookStackGroup,
    morphKey: String,
    shelfStyle: ShelfStyle,
    isSelected: Boolean,
    isDropTarget: Boolean,
    dropAsStack: Boolean,
    spineThicknessByPages: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val book = group.leadBook
    val isWooden = shelfStyle == ShelfStyle.WOODEN_SHELF
    val isGlassShelf = shelfStyle == ShelfStyle.GLASS_SHELF
    val hasLedge = isWooden || isGlassShelf
    val spineWidth = if (hasLedge && spineThicknessByPages) {
        (3.dp + ((book.pageCountEstimate / 120f).coerceIn(0f, 6f)).dp)
    } else {
        0.dp
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("shelf_book_${book.id}")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Stack offset layers behind lead book
            if (group.isStack) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(start = 6.dp, bottom = 6.dp)
                        .graphicsLayer {
                            translationX = 5.dp.toPx()
                            translationY = (-5).dp.toPx()
                            alpha = 0.72f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(palette.surfaceVariant))
                        .border(1.dp, Color(palette.accent).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                if (spineWidth > 0.dp) {
                    Box(
                        modifier = Modifier
                            .width(spineWidth)
                            .aspectRatio(0.08f)
                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                            .background(Color(palette.surfaceVariant))
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.68f)
                        .mochiSharedBookBounds(morphKey)
                        .shadow(
                            elevation = if (hasLedge) 8.dp else 4.dp,
                            shape = RoundedCornerShape(12.dp),
                            ambientColor = Color(palette.glassShadow)
                        )
                        .then(
                            if (isDropTarget) {
                                Modifier.border(2.5.dp, Color(palette.accent), RoundedCornerShape(12.dp))
                            } else {
                                Modifier
                            }
                        )
                ) {
                    MochiBookCover(
                        book = book,
                        cornerRadius = 12.dp,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Stack badge
                    if (group.isStack) {
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.accent),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = "Stack",
                                    tint = ContrastChecker.bestOnColor(Color(palette.accent)),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${group.books.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ContrastChecker.bestOnColor(Color(palette.accent))
                                )
                            }
                        }
                    }

                    // Progress pill
                    if (book.progress > 0.01f && !isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.surface).copy(alpha = 0.90f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "${(book.progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(palette.text),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Drop-to-stack visual hint
                    if (isDropTarget && dropAsStack) {
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.accent),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                text = "Stack",
                                style = MaterialTheme.typography.labelSmall,
                                color = ContrastChecker.bestOnColor(Color(palette.accent)),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Selection checkmark
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) Color(palette.accent).copy(alpha = 0.28f)
                                    else Color.Black.copy(alpha = 0.12f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = if (isSelected) "Selected" else "Select",
                                tint = if (isSelected) Color(palette.accent) else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isWooden) {
            // Contact shadow on wooden shelf plank
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(horizontal = 4.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                        )
                    )
            )
        } else if (isGlassShelf) {
            // Soft luminous reflection shadow on glass shelf ledge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(horizontal = 4.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(palette.glassShadow).copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (group.isStack) (group.stackTitle ?: book.title) else book.title,
                style = MaterialTheme.typography.labelLarge,
                color = Color(palette.text),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (group.isStack) "${group.books.size} books" else book.authors,
                style = MaterialTheme.typography.bodySmall,
                color = Color(palette.textSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookListView(
    items: List<BookStackGroup>,
    selectedBookIds: Set<Long>,
    onBookClick: (BookEntity, String) -> Unit,
    onInspectBook: (BookEntity) -> Unit,
    onStackClick: (BookStackGroup) -> Unit,
    onToggleSelect: (Long) -> Unit
) {
    val palette = LocalMochiPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { group ->
            val book = group.leadBook
            val isSelected = book.id in selectedBookIds
            val morphKey = "list_${book.id}"

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) Color(palette.accentContainer) else Color(palette.surface),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Color(palette.textSecondary).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .combinedClickable(
                        onClick = {
                            if (selectedBookIds.isNotEmpty()) {
                                onToggleSelect(book.id)
                            } else if (group.isStack) {
                                onStackClick(group)
                            } else {
                                onBookClick(book, morphKey)
                            }
                        },
                        onLongClick = { onToggleSelect(book.id) }
                    )
                    .testTag("list_book_${book.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MochiBookCover(
                        book = book,
                        cornerRadius = 10.dp,
                        modifier = Modifier
                            .width(56.dp)
                            .aspectRatio(0.7f)
                            .mochiSharedBookBounds(morphKey)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (group.isStack) "${group.stackTitle} (${group.books.size})" else book.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(palette.text),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = CircleShape,
                                color = Color(palette.surfaceVariant)
                            ) {
                                Text(
                                    text = book.format.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(palette.textSecondary),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${book.authors} · ${formatFileSize(book.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(palette.textSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MochiWavyProgressBar(
                                progress = book.progress,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${(book.progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(palette.textSecondary)
                            )
                        }
                    }
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color(palette.accent)
                        )
                    } else if (!group.isStack) {
                        IconButton(onClick = { onInspectBook(book) }) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Book details",
                                tint = Color(palette.textSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StackDetailSheet(
    stack: BookStackGroup,
    onDismiss: () -> Unit,
    onOpenBook: (BookEntity, String) -> Unit,
    onInspectBook: (BookEntity) -> Unit,
    onUnstackBook: (Long) -> Unit
) {
    val palette = LocalMochiPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stack.stackTitle ?: "Book Stack",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(palette.text)
            )
            Text(
                text = "${stack.books.size} books in this stack",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(palette.textSecondary)
            )
            Spacer(modifier = Modifier.height(14.dp))
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(stack.books, key = { it.id }) { book ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.55f),
                        onClick = {
                            onDismiss()
                            onOpenBook(book, "")
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MochiBookCover(
                                book = book,
                                cornerRadius = 8.dp,
                                modifier = Modifier
                                    .width(46.dp)
                                    .aspectRatio(0.7f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(palette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = book.authors,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(palette.textSecondary)
                                )
                            }
                            IconButton(onClick = {
                                onDismiss()
                                onInspectBook(book)
                            }) {
                                Icon(Icons.Rounded.Info, contentDescription = "Book details", tint = Color(palette.textSecondary))
                            }
                            TextButton(onClick = { onUnstackBook(book.id) }) {
                                Text("Unstack", color = Color(palette.link))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    return String.format("%.1f MB", mb)
}
