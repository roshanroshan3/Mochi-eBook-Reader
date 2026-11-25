package com.example.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.PdfFitMode
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ParsedDocument
import com.example.data.reader.PdfPageEngine
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioSessionState
import com.example.data.studio.StudioTool
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LocalMochiMotion
import com.example.ui.studio.AnnotationStudioCanvasLayer
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pure pan-clamping math for zoomed PDF pages so pages never drift off-screen.
 */
object PdfViewportMath {
    fun clampPan(pan: Offset, zoomScale: Float, viewportW: Float, viewportH: Float): Offset {
        if (zoomScale <= 1.02f || viewportW <= 0f || viewportH <= 0f) return Offset.Zero
        val maxX = ((viewportW * (zoomScale - 1f)) * 0.5f).coerceAtLeast(0f)
        val maxY = ((viewportH * (zoomScale - 1f)) * 0.5f).coerceAtLeast(0f)
        return Offset(
            x = pan.x.coerceIn(-maxX, maxX),
            y = pan.y.coerceIn(-maxY, maxY)
        )
    }
}

/**
 * High-resolution Fixed-Layout PDF Viewport + Dual Spread on Tablets + Zero-Spinner Cache Rendering:
 * - Renders via [PdfPageEngine] (instant synchronous cache hit, dedicated thumbnail cache, OOM recovery).
 * - Matches neighbor pre-warm width bucket to the active viewport width so every turn hits RAM cache.
 * - Cleanly separates 1-finger swipe (at 1x) from 2-finger pinch & zoomed pan without restarting gesture detector.
 * - Uses shared [mochiPageTurnTransition] & [InteractivePageCurlContainer].
 */
@Composable
fun FixedLayoutPdfViewport(
    document: ParsedDocument,
    currentPageIndex: Int,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    studioActive: Boolean,
    studioLayers: List<AnnotationLayerEntity>,
    studioMarkups: List<MarkupObjectEntity>,
    studioScales: List<PageScaleEntity>,
    studioSession: StudioSessionState,
    programmaticTurnRequest: Pair<Int, Long>?,
    onPageChanged: (Int) -> Unit,
    onAnimationActiveChanged: (Boolean) -> Unit,
    onCenterTap: (Float) -> Unit,
    onCreateMarkupDraft: (Int, StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelectedMarkups: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isDualPdfSpread: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motion = LocalMochiMotion.current

    val pageCount = document.pdfPageCount.coerceAtLeast(1)
    val safePage = currentPageIndex.coerceIn(0, pageCount - 1)
    val spreadStep = if (isDualPdfSpread && !studioActive) 2 else 1
    val spreadCount = ((pageCount + spreadStep - 1) / spreadStep).coerceAtLeast(1)
    val currentSpread = (safePage / spreadStep).coerceIn(0, spreadCount - 1)

    var zoomScale by remember(safePage, isDualPdfSpread) { mutableFloatStateOf(1f) }
    var panOffset by remember(safePage, isDualPdfSpread) { mutableStateOf(Offset.Zero) }

    val pagerState = rememberPagerState(initialPage = currentSpread) { spreadCount }
    var lastReportedSpread by remember { mutableIntStateOf(currentSpread) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_fixed_viewport")
    ) {
        val viewportWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(360f)
        val columnTargetWidthPx = if (spreadStep == 2) {
            (viewportWPx * 0.48f).roundToInt().coerceIn(420, 1280)
        } else {
            viewportWPx.roundToInt().coerceIn(480, 1408)
        }

        // Pre-warm neighbor pages using EXACT same width bucket so page turns render on frame 0
        LaunchedEffect(safePage, spreadStep, columnTargetWidthPx, settings.pdfSmartDarkInvert, document.bookUri) {
            val pagesToWarm = buildList {
                for (delta in 1..2) {
                    val f = safePage + delta * spreadStep
                    if (f < pageCount) add(f)
                    if (spreadStep == 2 && f + 1 < pageCount) add(f + 1)
                }
                val prev = safePage - spreadStep
                if (prev >= 0) {
                    add(prev)
                    if (spreadStep == 2 && prev + 1 < pageCount) add(prev + 1)
                }
            }
            pagesToWarm.forEach { idx ->
                PdfPageEngine.renderPageBitmap(
                    context = context,
                    uriString = document.bookUri,
                    pageIndex = idx,
                    targetWidthPx = columnTargetWidthPx,
                    smartDarkInvert = settings.pdfSmartDarkInvert
                )
            }
        }

        // Sync pager when jumping externally (scrubber / TOC / undo / thumbnails / buttons)
        LaunchedEffect(currentSpread, settings.pageTurnMode, spreadStep) {
            if (settings.pageTurnMode != PageTurnMode.CURL &&
                !pagerState.isScrollInProgress &&
                pagerState.currentPage != currentSpread &&
                currentSpread != lastReportedSpread
            ) {
                pagerState.scrollToPage(currentSpread)
            }
            lastReportedSpread = currentSpread
        }

        LaunchedEffect(programmaticTurnRequest, settings.pageTurnMode, spreadStep) {
            val req = programmaticTurnRequest ?: return@LaunchedEffect
            if (settings.pageTurnMode == PageTurnMode.CURL) return@LaunchedEffect
            val destSpread = (req.first.coerceIn(0, pageCount - 1) / spreadStep).coerceIn(0, spreadCount - 1)
            if (destSpread != pagerState.currentPage) {
                if (motion.reduceMotion || settings.pageTurnMode == PageTurnMode.NONE) {
                    pagerState.scrollToPage(destSpread)
                } else {
                    pagerState.animateScrollToPage(destSpread, animationSpec = motion.spatialDefault())
                }
            }
        }

        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, settings.pageTurnMode, spreadStep) {
            if (settings.pageTurnMode != PageTurnMode.CURL) {
                onAnimationActiveChanged(pagerState.isScrollInProgress)
                if (pagerState.currentPage != lastReportedSpread) {
                    lastReportedSpread = pagerState.currentPage
                    val destPage = (pagerState.currentPage * spreadStep).coerceIn(0, pageCount - 1)
                    onPageChanged(destPage)
                }
            }
        }

        val useCurl = settings.pageTurnMode == PageTurnMode.CURL && zoomScale <= 1.05f && !studioActive
        val markupsByPage = remember(studioMarkups) { studioMarkups.groupBy { it.pageIndex } }
        val scalesByPage = remember(studioScales) { studioScales.associateBy { it.pageIndex } }
        val pageBgColor = if (settings.pdfSmartDarkInvert) Color(0xFF1A1C20) else Color(palette.paperTint)

        val mappedTurnRequest = remember(programmaticTurnRequest, spreadStep) {
            programmaticTurnRequest?.let { (pageIdx, ts) ->
                (pageIdx.coerceIn(0, pageCount - 1) / spreadStep) to ts
            }
        }

        if (useCurl) {
            InteractivePageCurlContainer(
                currentPageIndex = currentSpread,
                pageCount = spreadCount,
                paperTintColor = pageBgColor,
                curlStrength = settings.curlStrength,
                curlShadowStrength = settings.curlShadowStrength,
                curlShowPageBack = settings.curlShowPageBack,
                curlSpeed = settings.curlSpeed,
                grabMode = settings.curlGrabMode,
                reduceMotion = motion.reduceMotion,
                onTurnToPage = { destSpread ->
                    onPageChanged((destSpread * spreadStep).coerceIn(0, pageCount - 1))
                },
                onAnimationActiveChanged = onAnimationActiveChanged,
                onCenterTap = onCenterTap,
                programmaticTurnRequest = mappedTurnRequest
            ) { spreadIdx ->
                PdfSpreadContent(
                    document = document,
                    spreadIdx = spreadIdx,
                    spreadStep = spreadStep,
                    pageCount = pageCount,
                    pageBgColor = pageBgColor,
                    settings = settings,
                    palette = palette,
                    studioActive = false,
                    studioLayers = studioLayers,
                    markupsByPage = markupsByPage,
                    scalesByPage = scalesByPage,
                    studioSession = studioSession,
                    zoomScale = 1f,
                    panOffset = Offset.Zero,
                    onTransformChange = { newZoom, newPan ->
                        zoomScale = newZoom
                        panOffset = newPan
                    },
                    onCenterTap = null,
                    onCreateMarkupDraft = onCreateMarkupDraft,
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelectedMarkups = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                key = { it },
                userScrollEnabled = !studioActive && zoomScale <= 1.05f,
                modifier = Modifier.fillMaxSize()
            ) { spreadIdx ->
                val offsetFraction = (pagerState.currentPage - spreadIdx) + pagerState.currentPageOffsetFraction
                PdfSpreadContent(
                    document = document,
                    spreadIdx = spreadIdx,
                    spreadStep = spreadStep,
                    pageCount = pageCount,
                    pageBgColor = pageBgColor,
                    settings = settings,
                    palette = palette,
                    studioActive = studioActive && spreadIdx == pagerState.currentPage,
                    studioLayers = studioLayers,
                    markupsByPage = markupsByPage,
                    scalesByPage = scalesByPage,
                    studioSession = studioSession,
                    zoomScale = if (spreadIdx == pagerState.currentPage) zoomScale else 1f,
                    panOffset = if (spreadIdx == pagerState.currentPage) panOffset else Offset.Zero,
                    onTransformChange = { z, p ->
                        if (spreadIdx == pagerState.currentPage) {
                            zoomScale = z
                            panOffset = p
                        }
                    },
                    onCenterTap = if (!studioActive && zoomScale <= 1.05f) onCenterTap else null,
                    onCreateMarkupDraft = onCreateMarkupDraft,
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelectedMarkups = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                    modifier = Modifier.mochiPageTurnTransition(
                        mode = settings.pageTurnMode,
                        offsetFraction = offsetFraction,
                        reduceMotion = motion.reduceMotion
                    )
                )
            }
        }
    }
}

@Composable
private fun PdfSpreadContent(
    document: ParsedDocument,
    spreadIdx: Int,
    spreadStep: Int,
    pageCount: Int,
    pageBgColor: Color,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    studioActive: Boolean,
    studioLayers: List<AnnotationLayerEntity>,
    markupsByPage: Map<Int, List<MarkupObjectEntity>>,
    scalesByPage: Map<Int, PageScaleEntity>,
    studioSession: StudioSessionState,
    zoomScale: Float,
    panOffset: Offset,
    onTransformChange: (Float, Offset) -> Unit,
    onCenterTap: ((Float) -> Unit)?,
    onCreateMarkupDraft: (Int, StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelectedMarkups: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val leftPageIdx = spreadIdx * spreadStep
    val rightPageIdx = if (spreadStep == 2 && leftPageIdx + 1 < pageCount) leftPageIdx + 1 else null

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(pageBgColor)
    ) {
        if (leftPageIdx < pageCount) {
            PdfSinglePageSurface(
                document = document,
                pageIndex = leftPageIdx,
                settings = settings,
                palette = palette,
                studioActive = studioActive,
                studioLayers = studioLayers,
                pageMarkups = markupsByPage[leftPageIdx].orEmpty(),
                pageScale = scalesByPage[leftPageIdx],
                studioSession = studioSession,
                zoomScale = zoomScale,
                panOffset = panOffset,
                onTransformChange = onTransformChange,
                onCenterTap = onCenterTap,
                onCreateMarkupDraft = { tool, geo -> onCreateMarkupDraft(leftPageIdx, tool, geo) },
                onSelectMarkups = onSelectMarkups,
                onMoveSelectedMarkups = onMoveSelectedMarkups,
                onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        if (spreadStep == 2) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (palette.isDark) 0.30f else 0.09f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (rightPageIdx != null) {
                    PdfSinglePageSurface(
                        document = document,
                        pageIndex = rightPageIdx,
                        settings = settings,
                        palette = palette,
                        studioActive = false,
                        studioLayers = studioLayers,
                        pageMarkups = markupsByPage[rightPageIdx].orEmpty(),
                        pageScale = scalesByPage[rightPageIdx],
                        studioSession = studioSession,
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        onTransformChange = onTransformChange,
                        onCenterTap = onCenterTap,
                        onCreateMarkupDraft = { tool, geo -> onCreateMarkupDraft(rightPageIdx, tool, geo) },
                        onSelectMarkups = onSelectMarkups,
                        onMoveSelectedMarkups = onMoveSelectedMarkups,
                        onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSinglePageSurface(
    document: ParsedDocument,
    pageIndex: Int,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    studioActive: Boolean,
    studioLayers: List<AnnotationLayerEntity>,
    pageMarkups: List<MarkupObjectEntity>,
    pageScale: PageScaleEntity?,
    studioSession: StudioSessionState,
    zoomScale: Float,
    panOffset: Offset,
    onTransformChange: (Float, Offset) -> Unit,
    onCreateMarkupDraft: (StudioTool, StudioGeometryJson) -> Unit,
    onSelectMarkups: (Set<Long>) -> Unit,
    onMoveSelectedMarkups: (Float, Float) -> Unit,
    onCalibrateSegmentMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onCenterTap: ((Float) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ptPair = document.pdfPageSizes.getOrNull(pageIndex) ?: (612 to 792)
    val pageWidthPts = ptPair.first.coerceAtLeast(100).toFloat()
    val pageHeightPts = ptPair.second.coerceAtLeast(100).toFloat()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (settings.pdfFitMode == PdfFitMode.FIT_WIDTH) 0.dp else 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxWPx = with(density) { maxWidth.toPx() }.coerceAtLeast(300f)
        val maxHPx = with(density) { maxHeight.toPx() }.coerceAtLeast(300f)

        // At 1x zoom keep resolution crisp & memory-light (<=1408px); bump cleanly when zoomed >1.3x
        val effectiveZoomTier = if (zoomScale <= 1.3f) 1f else zoomScale.coerceIn(1.3f, 2.2f)
        val desiredRenderPx = (maxWPx * effectiveZoomTier).roundToInt().coerceIn(480, 2200)

        var bitmap by remember(document.bookUri, pageIndex, settings.pdfSmartDarkInvert) {
            mutableStateOf(
                PdfPageEngine.getCachedBitmap(
                    uriString = document.bookUri,
                    pageIndex = pageIndex,
                    targetWidthPx = desiredRenderPx,
                    smartDarkInvert = settings.pdfSmartDarkInvert
                )
            )
        }
        var cropRect by remember(document.bookUri, pageIndex, settings.pdfAutoMarginCrop) {
            mutableStateOf(
                if (settings.pdfAutoMarginCrop) {
                    PdfPageEngine.getCachedCropBounds(document.bookUri, pageIndex) ?: RectF(0f, 0f, 1f, 1f)
                } else {
                    RectF(0f, 0f, 1f, 1f)
                }
            )
        }

        LaunchedEffect(document.bookUri, pageIndex, desiredRenderPx, settings.pdfSmartDarkInvert, settings.pdfAutoMarginCrop) {
            val bmp = PdfPageEngine.renderPageBitmap(
                context = context,
                uriString = document.bookUri,
                pageIndex = pageIndex,
                targetWidthPx = desiredRenderPx,
                smartDarkInvert = settings.pdfSmartDarkInvert
            )
            if (bmp != null) {
                bitmap = bmp
                if (settings.pdfAutoMarginCrop) {
                    cropRect = PdfPageEngine.getOrComputeContentBounds(document.bookUri, pageIndex, bmp)
                } else {
                    cropRect = RectF(0f, 0f, 1f, 1f)
                }
            }
        }

        val effectiveCropW = (cropRect.width()).coerceIn(0.3f, 1f)
        val effectiveCropH = (cropRect.height()).coerceIn(0.3f, 1f)
        val effectiveAspect = (pageWidthPts * effectiveCropW) / (pageHeightPts * effectiveCropH)

        val (pageBoxW, pageBoxH) = if (settings.pdfFitMode == PdfFitMode.FIT_WIDTH) {
            maxWidth to (maxWidth / effectiveAspect)
        } else {
            val containerAspect = maxWPx / maxHPx
            if (containerAspect > effectiveAspect) {
                (maxHeight * effectiveAspect) to maxHeight
            } else {
                maxWidth to (maxWidth / effectiveAspect)
            }
        }

        val bmp = bitmap
        if (bmp == null || bmp.isRecycled) {
            Box(
                modifier = Modifier
                    .size(pageBoxW, pageBoxH)
                    .background(Color(palette.surfaceVariant).copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(palette.accent), modifier = Modifier.size(30.dp))
            }
        } else {
            val zoomState = rememberUpdatedState(zoomScale)
            val panState = rememberUpdatedState(panOffset)
            val onTransformState = rememberUpdatedState(onTransformChange)
            val onCenterTapState = rememberUpdatedState(onCenterTap)

            // Keyed ONLY on pageIndex & studioActive so pinch/pan NEVER restarts mid-gesture
            val gestureModifier = if (!studioActive) {
                Modifier.pointerInput(pageIndex, studioActive) {
                    var lastTapTimeMs = 0L
                    var lastTapPos = Offset.Zero

                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downPos = firstDown.position
                        var movedDist = 0f
                        var isTransforming = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }
                            if (pressedChanges.isEmpty()) {
                                if (!isTransforming && movedDist < viewConfiguration.touchSlop) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTimeMs <= viewConfiguration.doubleTapTimeoutMillis &&
                                        (downPos - lastTapPos).getDistance() < viewConfiguration.touchSlop * 3f
                                    ) {
                                        lastTapTimeMs = 0L
                                        val curZoom = zoomState.value
                                        if (curZoom > 1.25f) {
                                            onTransformState.value(1f, Offset.Zero)
                                        } else {
                                            val targetZoom = 2.35f
                                            val rawPan = Offset(
                                                (size.width * 0.5f - downPos.x) * (targetZoom - 1f),
                                                (size.height * 0.5f - downPos.y) * (targetZoom - 1f)
                                            )
                                            val clamped = PdfViewportMath.clampPan(
                                                rawPan, targetZoom, size.width.toFloat(), size.height.toFloat()
                                            )
                                            onTransformState.value(targetZoom, clamped)
                                        }
                                    } else {
                                        lastTapTimeMs = now
                                        lastTapPos = downPos
                                        if (zoomState.value <= 1.05f) {
                                            onCenterTapState.value?.invoke(downPos.x)
                                        }
                                    }
                                }
                                break
                            }

                            val multiTouch = pressedChanges.size >= 2
                            val curZoom = zoomState.value

                            if (multiTouch) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (abs(zoomChange - 1f) > 0.002f || panChange != Offset.Zero) {
                                    isTransforming = true
                                    val nextZ = (curZoom * zoomChange).coerceIn(1f, 8f)
                                    val nextPan = PdfViewportMath.clampPan(
                                        panState.value + panChange,
                                        nextZ,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                    onTransformState.value(nextZ, nextPan)
                                    pressedChanges.forEach { it.consume() }
                                }
                            } else {
                                val change = pressedChanges.first()
                                val delta = change.positionChange()
                                movedDist += hypot(delta.x, delta.y)
                                // Only consume 1-finger pan when actually zoomed in! At 1x, leave swipe for HorizontalPager/PageCurl.
                                if (curZoom > 1.05f && movedDist > viewConfiguration.touchSlop) {
                                    isTransforming = true
                                    val nextPan = PdfViewportMath.clampPan(
                                        panState.value + delta,
                                        curZoom,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                    onTransformState.value(curZoom, nextPan)
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            } else Modifier

            val imageBitmap = remember(bmp) { bmp.asImageBitmap() }

            Box(
                modifier = Modifier
                    .size(pageBoxW, pageBoxH)
                    .then(gestureModifier)
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
                    .shadow(8.dp, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (settings.pdfSmartDarkInvert) Color(0xFF1D1F24) else Color.White)
            ) {
                // Zero-copy Canvas crop draw instead of Bitmap.createBitmap on UI thread!
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val useCrop = settings.pdfAutoMarginCrop && (cropRect.left > 0.01f || cropRect.right < 0.99f)
                    val sx = if (useCrop) (cropRect.left * imageBitmap.width).roundToInt().coerceIn(0, imageBitmap.width - 2) else 0
                    val sy = if (useCrop) (cropRect.top * imageBitmap.height).roundToInt().coerceIn(0, imageBitmap.height - 2) else 0
                    val sw = if (useCrop) (cropRect.width() * imageBitmap.width).roundToInt().coerceIn(2, imageBitmap.width - sx) else imageBitmap.width
                    val sh = if (useCrop) (cropRect.height() * imageBitmap.height).roundToInt().coerceIn(2, imageBitmap.height - sy) else imageBitmap.height
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset(sx, sy),
                        srcSize = IntSize(sw, sh),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
                    )
                }

                // CAD Studio vector overlay sits directly over PDF page inside zoom/pan transform
                AnnotationStudioCanvasLayer(
                    pageWidthPts = pageWidthPts,
                    pageHeightPts = pageHeightPts,
                    pageMarkups = pageMarkups,
                    layers = studioLayers,
                    pageScale = pageScale,
                    session = studioSession,
                    studioActive = studioActive,
                    palmRejectionEnabled = settings.studioPalmRejection,
                    onCreateMarkupDraft = onCreateMarkupDraft,
                    onSelectMarkups = onSelectMarkups,
                    onMoveSelected = onMoveSelectedMarkups,
                    onCalibrateSegmentMeasured = onCalibrateSegmentMeasured,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfThumbnailsSheet(
    bookUri: String,
    pageCount: Int,
    currentPageIndex: Int,
    smartDarkInvert: Boolean,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalMochiPalette.current
    val context = LocalContext.current
    val pageNumbers = remember(pageCount) { (0 until pageCount).toList() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(palette.surface),
        modifier = Modifier.testTag("pdf_thumbnails_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Pages ($pageCount)",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(palette.text)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(420.dp)
            ) {
                items(pageNumbers, key = { it }) { idx ->
                    var thumb by remember(bookUri, idx, smartDarkInvert) {
                        mutableStateOf(PdfPageEngine.getCachedThumbnail(bookUri, idx, smartDarkInvert))
                    }
                    LaunchedEffect(bookUri, idx, smartDarkInvert) {
                        if (thumb == null) {
                            delay((idx % 4) * 18L)
                            thumb = PdfPageEngine.renderThumbnailBitmap(
                                context = context,
                                uriString = bookUri,
                                pageIndex = idx,
                                smartDarkInvert = smartDarkInvert
                            )
                        }
                    }
                    val selected = idx == currentPageIndex
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(palette.surfaceVariant).copy(alpha = 0.5f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) Color(palette.accent) else Color(palette.textSecondary).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectPage(idx)
                                onDismiss()
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.76f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (smartDarkInvert) Color(0xFF1D1F24) else Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                val t = thumb
                                if (t != null && !t.isRecycled) {
                                    Image(
                                        bitmap = t.asImageBitmap(),
                                        contentDescription = "Thumbnail page ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("${idx + 1}", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                                }
                            }
                            Text(
                                text = "Page ${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color(palette.accent) else Color(palette.textSecondary)
                            )
                        }
                    }
                }
            }
        }
    }
}
