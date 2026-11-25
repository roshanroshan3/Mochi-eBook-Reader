package com.example.ui.reader

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BookEntity
import com.example.data.preferences.AutoScrollMode
import com.example.data.preferences.OrientationLock
import com.example.data.preferences.PageSpreadMode
import com.example.data.preferences.PageTurnMode
import com.example.data.preferences.ReaderAction
import com.example.data.preferences.ReaderGesture
import com.example.data.preferences.ReaderSettings
import com.example.data.reader.ContentBlock
import com.example.data.reader.LazyBookPager
import com.example.data.reader.LazyBookSnapshot
import com.example.data.reader.PageSlice
import com.example.data.reader.PaginatedBook
import com.example.data.reader.ParsedDocument
import com.example.data.reader.ReaderDocumentCache
import com.example.data.reader.ReaderFontResolver
import com.example.data.reader.ReaderLocator
import com.example.data.reader.ReaderPage
import com.example.data.reader.ReaderPaginator
import com.example.theme.ContrastChecker
import com.example.theme.MochiThemePalette
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LocalMochiMotion
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.designsystem.mochiSharedBookBounds
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMochiPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.annotations.AnnotationExporter
import com.example.data.annotations.AnnotationType
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightColorHelper
import com.example.data.annotations.HighlightStyle
import com.example.data.annotations.AnnotationWithBook
import com.example.data.db.AnnotationEntity
import com.example.data.db.VocabularyWordEntity
import com.example.data.dictionary.DictionaryLookupResult
import com.example.data.dictionary.EnglishLemmatizer
import com.example.data.dictionary.SystemTranslateIntentHelper
import com.example.ui.dictionary.DictionaryGlassPopup
import com.example.ui.dictionary.DictionaryManagerSheet
import com.example.ui.dictionary.DictionaryViewModel
import com.example.data.preferences.PdfFitMode
import com.example.data.studio.MeasurementUnit
import com.example.data.studio.StudioExportBundle
import com.example.data.studio.StudioExporter
import com.example.data.studio.StudioGeometry
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioStyleJson
import com.example.data.studio.StudioTool
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.MarkupObjectEntity
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.notes.ExportTemplatesSheet
import com.example.ui.notes.MarkdownNoteEditorSheet
import com.example.ui.notes.QuoteCardStudioSheet
import com.example.ui.studio.AnnotationStudioCanvasLayer
import com.example.ui.studio.CalibrateScaleDialog
import com.example.ui.studio.StudioExportSheet
import com.example.ui.studio.StudioFloatingDock
import com.example.ui.studio.StudioLayersSheet
import com.example.ui.studio.StudioPropertiesSheet
import com.example.ui.studio.StudioTextPromptDialog
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    settings: ReaderSettings,
    themes: List<MochiThemePalette>,
    activeTheme: MochiThemePalette,
    warmLightEnabled: Boolean,
    morphKey: String = "",
    onBack: () -> Unit,
    onOpenBookById: (Long) -> Unit,
    onFindWikilinkTarget: suspend (String) -> Long?,
    onOpenBookInfo: (BookEntity) -> Unit,
    onToggleChrome: (Boolean?) -> Unit,
    onUpdatePage: (Int, PaginatedBook) -> Unit,
    onUpdateLocator: ((ReaderLocator) -> Unit)? = null,
    onUpdateScrollProgression: (Int, Float, String) -> Unit,
    onJumpWithUndo: (ReaderLocator) -> Unit,
    onUndoJump: () -> ReaderLocator?,
    onDismissUndo: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenFootnote: (String) -> Unit,
    onCloseFootnote: () -> Unit,
    onOpenImageZoom: (String) -> Unit,
    onCloseImageZoom: () -> Unit,
    onToggleAutoScroll: (Boolean?) -> Unit,
    onUpdateSettings: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onSelectGlobalTheme: (MochiThemePalette) -> Unit,
    onSetBookThemeOverride: (String?) -> Unit,
    onToggleWarmLight: (Boolean) -> Unit,
    onImportFont: () -> Unit,
    onCreateHighlight: (
        locator: ReaderLocator,
        quoteExact: String,
        quotePrefix: String,
        quoteSuffix: String,
        colorRole: Int,
        style: HighlightStyle,
        noteMarkdown: String,
        tags: String,
        sticker: CozySticker
    ) -> Unit,
    onUpdateAnnotation: (AnnotationEntity, String, String, Int, HighlightStyle, CozySticker) -> Unit,
    onCreateOrUpdateBookmark: (AnnotationEntity?, ReaderLocator, String, String, Int) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onSaveTemplate: (String, String, Long) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onSaveVaultFolderUri: (String?) -> Unit,
    onShowMessage: (String) -> Unit,
    // Milestone E: Fixed PDF & CAD Studio callbacks
    onUpdateFixedPdfPage: (Int, Int) -> Unit = { _, _ -> },
    onToggleStudioMode: (Boolean?) -> Unit = {},
    onSelectStudioTool: (StudioTool) -> Unit = {},
    onToggleStudioSnap: () -> Unit = {},
    onToggleStudioOrtho: () -> Unit = {},
    onUpdateStudioStyle: (StudioStyleJson) -> Unit = {},
    onSelectStudioLayer: (Long) -> Unit = {},
    onCreateStudioLayer: (String) -> Unit = {},
    onUpdateStudioLayer: (AnnotationLayerEntity) -> Unit = {},
    onDeleteStudioLayer: (Long) -> Unit = {},
    onCreateStudioMarkup: (Int, StudioTool, StudioGeometryJson, String) -> Unit = { _, _, _, _ -> },
    onUpdateStudioMarkup: (MarkupObjectEntity) -> Unit = {},
    onSelectStudioMarkups: (Set<Long>) -> Unit = {},
    onMoveSelectedStudioMarkups: (Float, Float) -> Unit = { _, _ -> },
    onAlignSelectedStudioMarkups: (StudioGeometry.AlignMode) -> Unit = {},
    onCopySelectedStudioMarkups: () -> Unit = {},
    onPasteStudioMarkups: (Int) -> Unit = {},
    onDeleteSelectedStudioMarkups: () -> Unit = {},
    onUndoStudio: () -> Unit = {},
    onRedoStudio: () -> Unit = {},
    onSavePageScale: (Int, Float, MeasurementUnit) -> Unit = { _, _, _ -> },
    onImportStudioBundle: (StudioExportBundle) -> Unit = {},
    onEnsurePdfReflowLoaded: () -> Unit = {},
    dictionaryViewModel: DictionaryViewModel = viewModel()
) {
    val palette = LocalMochiPalette.current
    val motion = LocalMochiMotion.current
    val widthClass = LocalMochiWidthClass.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val dictState by dictionaryViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    // Screen-awake, brightness, and orientation lock side effects
    val activity = context as? Activity
    DisposableEffect(activity, settings.keepScreenAwake, settings.brightness, settings.orientationLock) {
        val window = activity?.window
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val prevBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

        if (window != null) {
            if (settings.keepScreenAwake) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            if (settings.brightness >= 0f) {
                val lp = window.attributes
                lp.screenBrightness = settings.brightness.coerceIn(0.05f, 1.0f)
                window.attributes = lp
            }
        }
        if (activity != null) {
            activity.requestedOrientation = when (settings.orientationLock) {
                OrientationLock.UNLOCKED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val lp = window.attributes
                lp.screenBrightness = prevBrightness
                window.attributes = lp
            }
            activity?.requestedOrientation = prevOrientation
        }
    }

    var showAaSheet by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showAnnotationsPanel by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var showDictLookupPopup by remember { mutableStateOf(false) }
    var dictPopupQuery by remember { mutableStateOf("") }
    var dictPopupContextSentence by remember { mutableStateOf("") }
    var dictLookupResult by remember { mutableStateOf<DictionaryLookupResult?>(null) }
    var showDictManagerSheet by remember { mutableStateOf(false) }
    var activeSelectionDraft by remember { mutableStateOf<TextSelectionDraft?>(null) }
    var selectionQuickLookup by remember { mutableStateOf<DictionaryLookupResult?>(null) }
    var popoverAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var editingAnnotationNote by remember { mutableStateOf<AnnotationEntity?>(null) }
    var pendingDraftForNote by remember { mutableStateOf<Triple<TextSelectionDraft, Int, HighlightStyle>?>(null) }
    var quoteCardAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var brightnessHudValue by remember { mutableStateOf<Float?>(null) }
    var rulerYFraction by remember { mutableFloatStateOf(0.45f) }
    var readerAreaWidthPx by remember { mutableFloatStateOf(1080f) }

    // Milestone E & F overlay state
    var isPageAnimating by remember { mutableStateOf(false) }
    var programmaticTurnRequest by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var showPdfThumbnails by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showStudioInspector by remember { mutableStateOf(false) }
    var showStudioLayers by remember { mutableStateOf(false) }
    var showStudioExport by remember { mutableStateOf(false) }
    var calibrateMeasuredPts by remember { mutableStateOf<Float?>(null) }
    var pendingTextNoteDraft by remember { mutableStateOf<Pair<Int, StudioGeometryJson>?>(null) }

    LaunchedEffect(brightnessHudValue) {
        if (brightnessHudValue != null) {
            delay(1100)
            brightnessHudValue = null
        }
    }

    // Auto-hide chrome cleanly after 4.5s if no sheet is open
    LaunchedEffect(
        state.chromeVisible,
        state.studioActive,
        showAaSheet,
        showToc,
        showAnnotationsPanel,
        showSearchSheet,
        showCommandPalette,
        showBookmarkDialog,
        showExportSheet,
        showDictLookupPopup,
        showDictManagerSheet,
        showPdfThumbnails,
        showStudioInspector,
        showStudioLayers,
        showStudioExport,
        activeSelectionDraft
    ) {
        if (state.chromeVisible && !state.studioActive && !showAaSheet && !showToc && !showAnnotationsPanel &&
            !showSearchSheet && !showCommandPalette && !showBookmarkDialog &&
            !showExportSheet && !showDictLookupPopup && !showDictManagerSheet &&
            !showPdfThumbnails && !showOverflowMenu && !showStudioInspector && !showStudioLayers && !showStudioExport &&
            activeSelectionDraft == null
        ) {
            delay(4500)
            onToggleChrome(false)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .mochiSharedBookBounds(morphKey)
            .background(Color(palette.paperTint))
            .focusRequester(focusRequester)
    ) {
        val availableWidth = maxWidth
        val isTabletSideToc = widthClass == MochiWidthClass.EXPANDED && (showToc || showAnnotationsPanel)
        val readerAreaWidth = if (isTabletSideToc) (availableWidth - 320.dp).coerceAtLeast(320.dp) else availableWidth

        val isDualSpread = when (settings.pageSpreadMode) {
            PageSpreadMode.SINGLE -> false
            PageSpreadMode.DUAL -> readerAreaWidth >= 520.dp
            PageSpreadMode.AUTO -> readerAreaWidth >= 700.dp
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color(palette.accent))
                    Text("Opening book…", style = MaterialTheme.typography.bodyMedium, color = Color(palette.textSecondary))
                }
            }
            return@BoxWithConstraints
        }

        if (state.errorMessage != null || state.document == null || state.book == null) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Unable to open book", style = MaterialTheme.typography.titleLarge, color = Color(palette.text))
                    Text(state.errorMessage ?: "Unknown error", style = MaterialTheme.typography.bodyMedium, color = Color(palette.textSecondary))
                    TextButton(onClick = onBack) { Text("Back to Library") }
                }
            }
            return@BoxWithConstraints
        }

        val rawDocument = state.document
        val book = state.book
        val isPdfDoc = rawDocument.isFixedLayoutPdf
        val isPdfReflowActive = isPdfDoc && settings.pdfReflowEnabled
        val isDualPdfSpread = isPdfDoc && !isPdfReflowActive &&
            widthClass == MochiWidthClass.EXPANDED &&
            settings.pageSpreadMode == PageSpreadMode.DUAL &&
            !state.studioActive

        LaunchedEffect(isPdfDoc, settings.pdfReflowEnabled, rawDocument.bookUri) {
            if (isPdfDoc && settings.pdfReflowEnabled) {
                onEnsurePdfReflowLoaded()
            }
        }

        val document = remember(rawDocument, isPdfReflowActive, state.pdfReflowChapters) {
            if (isPdfReflowActive) {
                rawDocument.copy(
                    chapters = state.pdfReflowChapters ?: rawDocument.chapters,
                    isFixedLayoutPdf = false
                )
            } else {
                rawDocument
            }
        }
        val bookLanguage = remember(book.id, document) {
            document.chapters.firstOrNull()?.blocks
                ?.filterIsInstance<ContentBlock.FrontMatterCard>()
                ?.firstOrNull()
                ?.entries
                ?.firstOrNull { (k, _) -> k.equals("language", true) || k.equals("lang", true) }
                ?.second
                ?.trim()
                ?.lowercase()
                ?.substringBefore('-')
                ?.ifBlank { "en" }
                ?: "en"
        }
        val highlightVocabWords = remember(dictState.vocabulary, settings.highlightUnknownWordsEnabled) {
            if (settings.highlightUnknownWordsEnabled) {
                dictState.vocabulary.filter { it.masteryLevel != "MASTERED" }
            } else emptyList()
        }
        val openDictionaryFor: (String, String) -> Unit = { rawWord, contextSentence ->
            dictPopupQuery = rawWord
            dictPopupContextSentence = contextSentence
            showDictLookupPopup = true
            scope.launch {
                dictLookupResult = if (rawWord.isBlank()) {
                    null
                } else {
                    dictionaryViewModel.lookupWord(
                        query = rawWord,
                        contextSentence = contextSentence,
                        bookLanguage = bookLanguage,
                        perLanguageDefaults = settings.perLanguageDefaultDict
                    )
                }
            }
        }

        LaunchedEffect(activeSelectionDraft?.selectedText, bookLanguage, settings.perLanguageDefaultDict) {
            val d = activeSelectionDraft
            if (d == null) {
                selectionQuickLookup = null
            } else {
                val wordsCount = d.endWordIndex - d.startWordIndex + 1
                selectionQuickLookup = if (wordsCount in 1..3) {
                    dictionaryViewModel.lookupWord(
                        query = d.selectedText,
                        contextSentence = d.extractContextSentence(),
                        bookLanguage = bookLanguage,
                        perLanguageDefaults = settings.perLanguageDefaultDict
                    )
                } else null
            }
        }

        val textMeasurer = rememberTextMeasurer(cacheSize = 48)
        val density = LocalDensity.current

        val horizontalPaddingDp = settings.marginHorizontalDp.dp
        val verticalPaddingDp = settings.marginVerticalDp.dp
        val singleColumnWidthDp = if (isDualSpread) (readerAreaWidth - 48.dp) / 2f else readerAreaWidth
        val pageWidthPx = with(density) { (singleColumnWidthDp - horizontalPaddingDp * 2).toPx().roundToInt().coerceAtLeast(120) }
        val pageHeightPx = with(density) { (maxHeight - verticalPaddingDp * 2 - 76.dp).toPx().roundToInt().coerceAtLeast(180) }

        val isFixedPdf = document.isFixedLayoutPdf
        val pdfPageCount = document.pdfPageCount.coerceAtLeast(1)
        val pdfPageIdx = state.currentLocator.chapterIndex.coerceIn(0, pdfPageCount - 1)

        val fixedPdfSnapshot = remember(isFixedPdf, pdfPageCount) {
            if (!isFixedPdf) null
            else LazyBookSnapshot(
                chunks = emptyList(),
                chunkStartPages = intArrayOf(0, pdfPageCount),
                chapterStartPages = (0 until pdfPageCount).associateWith { it },
                chapterEndPages = (0 until pdfPageCount).associateWith { it },
                totalPages = pdfPageCount,
                version = 1,
                isReady = true,
                pageLookup = { idx ->
                    ReaderPage(
                        pageIndex = idx,
                        chapterIndex = idx,
                        chapterTitle = "Page ${idx + 1}",
                        startCharOffset = 0,
                        endCharOffset = 0,
                        slices = emptyList()
                    )
                }
            )
        }

        val lazyPager = remember(
            document,
            isFixedPdf,
            settings.fontId,
            settings.fontSizeSp,
            settings.lineHeightMultiplier,
            settings.paragraphSpacingDp,
            settings.alignment,
            settings.firstLineIndentDp,
            settings.hyphenationEnabled,
            settings.letterSpacingSp,
            palette.id,
            palette.text,
            pageWidthPx,
            pageHeightPx
        ) {
            if (isFixedPdf) null
            else LazyBookPager(
                document = document,
                settings = settings,
                palette = palette,
                textMeasurer = textMeasurer,
                density = density,
                pageWidthPx = pageWidthPx,
                pageHeightPx = pageHeightPx
            )
        }

        DisposableEffect(lazyPager) {
            onDispose {
                lazyPager?.clear()
            }
        }

        var reflowSnapshot by remember(lazyPager) {
            mutableStateOf(lazyPager?.currentSnapshot() ?: LazyBookSnapshot.empty(book.title))
        }
        val lazySnapshot: LazyBookSnapshot = if (isFixedPdf) fixedPdfSnapshot!! else reflowSnapshot
        val effectiveTotalPages = if (isFixedPdf) pdfPageCount else lazySnapshot.totalPages.coerceAtLeast(1)

        fun reportLocator(pageIdx: Int, loc: ReaderLocator) {
            if (onUpdateLocator != null) {
                onUpdateLocator(loc)
            } else {
                val page = lazySnapshot.getPage(pageIdx)
                onUpdatePage(
                    pageIdx,
                    PaginatedBook(
                        pages = listOfNotNull(page),
                        chapterStartPages = lazySnapshot.chapterStartPages
                    )
                )
            }
        }

        val spreadStep = if (!isFixedPdf && isDualSpread) 2 else 1
        val spreadCount = if (isFixedPdf) pdfPageCount else ((effectiveTotalPages + spreadStep - 1) / spreadStep).coerceAtLeast(1)
        val targetPageIndex = remember(lazySnapshot, state.currentLocator, isFixedPdf, pdfPageIdx) {
            if (isFixedPdf) pdfPageIdx else lazySnapshot.pageIndexForLocator(state.currentLocator)
        }
        val initialSpread = (targetPageIndex / spreadStep).coerceIn(0, spreadCount - 1)
        val pagerState = rememberPagerState(initialPage = initialSpread) { spreadCount }
        val scrollState = rememberLazyListState()
        var lastSpreadFromPager by remember { mutableIntStateOf(initialSpread) }

        // Initialize active + adjacent chunks off the UI thread when document/layout changes
        LaunchedEffect(lazyPager) {
            val pager = lazyPager ?: return@LaunchedEffect
            val (snap, initialIdx) = pager.initializeAroundLocator(state.currentLocator)
            reflowSnapshot = snap
            val snapSpreads = ((snap.totalPages + spreadStep - 1) / spreadStep).coerceAtLeast(1)
            val targetSpread = (initialIdx / spreadStep).coerceIn(0, snapSpreads - 1)
            if (!pagerState.isScrollInProgress && pagerState.currentPage != targetSpread) {
                pagerState.scrollToPage(targetSpread)
            }
            lastSpreadFromPager = targetSpread
        }

        // Sync pager when locator jumps (TOC, Search, Scrubber, Undo Jump, font resize) without interrupting active animation
        LaunchedEffect(targetPageIndex, spreadStep, settings.pageTurnMode, isFixedPdf, spreadCount) {
            if (!isFixedPdf && settings.pageTurnMode != PageTurnMode.SCROLL && settings.pageTurnMode != PageTurnMode.CURL) {
                val targetSpread = (targetPageIndex / spreadStep).coerceIn(0, spreadCount - 1)
                if (!pagerState.isScrollInProgress && pagerState.currentPage != targetSpread && targetSpread != lastSpreadFromPager) {
                    pagerState.scrollToPage(targetSpread)
                }
                lastSpreadFromPager = targetSpread
            }
        }

        // Persist locator when user turns pages & freeze Liquid Glass blur during turn animation
        LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, lazySnapshot.version, settings.pageTurnMode, isFixedPdf) {
            if (!isFixedPdf && settings.pageTurnMode != PageTurnMode.SCROLL && settings.pageTurnMode != PageTurnMode.CURL) {
                isPageAnimating = pagerState.isScrollInProgress
                if (pagerState.currentPage != lastSpreadFromPager) {
                    lastSpreadFromPager = pagerState.currentPage
                    val pageIdx = (pagerState.currentPage * spreadStep).coerceIn(0, effectiveTotalPages - 1)
                    reportLocator(pageIdx, lazySnapshot.locatorForPage(pageIdx))
                }
            }
        }

        val currentPageIdx = when {
            isFixedPdf -> pdfPageIdx
            settings.pageTurnMode == PageTurnMode.SCROLL -> targetPageIndex
            settings.pageTurnMode == PageTurnMode.CURL -> targetPageIndex
            else -> (pagerState.currentPage * spreadStep).coerceIn(0, effectiveTotalPages - 1)
        }
        val currentSpreadIdx = (currentPageIdx / spreadStep).coerceIn(0, spreadCount - 1)
        val currentPage = lazySnapshot.getPage(currentPageIdx)
        val currentChapterIndex = if (isFixedPdf) pdfPageIdx else (currentPage?.chapterIndex ?: lazySnapshot.chapterIndexForPage(currentPageIdx))
        val currentChapterTitle = if (isFixedPdf) "Page ${pdfPageIdx + 1}" else (currentPage?.chapterTitle ?: lazySnapshot.chapterTitleForPage(currentPageIdx))
        val remainingInChapter = lazySnapshot.pagesRemainingInChapter(currentPageIdx)
        val estMinutesLeft = ((effectiveTotalPages - 1 - currentPageIdx) * 1.15f).roundToInt().coerceAtLeast(0)

        // Pre-load ONLY active + adjacent chunks/images and evict distant pages on Dispatchers.Default / Dispatchers.IO
        LaunchedEffect(currentPageIdx, lazySnapshot.version, isDualSpread, isFixedPdf, lazyPager, readerAreaWidthPx) {
            if (isFixedPdf || lazyPager == null) return@LaunchedEffect
            val (updated, anchored) = lazyPager.ensureAdjacentPagesLoaded(currentPageIdx, isDualSpread)
            if (updated.version != reflowSnapshot.version) {
                reflowSnapshot = updated
            }
            val updatedSpreads = ((updated.totalPages + spreadStep - 1) / spreadStep).coerceAtLeast(1)
            val targetSpread = (anchored / spreadStep).coerceIn(0, updatedSpreads - 1)
            if (anchored != currentPageIdx && !pagerState.isScrollInProgress && pagerState.currentPage != targetSpread) {
                lastSpreadFromPager = targetSpread
                pagerState.scrollToPage(targetSpread)
            }
            val adjImages = updated.imageKeysNearPage(anchored, isDualSpread)
            if (adjImages.isNotEmpty()) {
                ReaderDocumentCache.prewarmImageBlocks(
                    context = context,
                    bookUri = document.bookUri,
                    inlineImages = document.images,
                    imageKeys = adjImages,
                    maxDimensionPx = readerAreaWidthPx.roundToInt().coerceIn(720, 1440)
                )
            }
        }

        // SAF Export & Import Launchers for Milestone E CAD Studio
        val exportAnnotatedPdfLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/pdf")
        ) { outUri ->
            if (outUri != null) {
                scope.launch {
                    val scaleMap = state.studioScales.associateBy { it.pageIndex }
                    runCatching {
                        StudioExporter.exportAnnotatedPdfCopy(
                            context = context,
                            sourcePdfUri = document.bookUri,
                            outputUri = outUri,
                            layers = state.studioLayers,
                            markups = state.studioMarkups,
                            scales = scaleMap
                        )
                    }.onSuccess { pagesExported ->
                        onShowMessage("Exported annotated PDF copy ($pagesExported pages)")
                    }.onFailure { err ->
                        onShowMessage("PDF export failed: ${err.localizedMessage ?: "error"}")
                    }
                }
            }
        }

        val exportSvgLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("image/svg+xml")
        ) { outUri ->
            if (outUri != null) {
                scope.launch {
                    val ptPair = document.pdfPageSizes.getOrNull(currentPageIdx) ?: (612 to 792)
                    val svg = StudioExporter.exportSvg(
                        pageIndex = currentPageIdx,
                        pageWidthPts = ptPair.first,
                        pageHeightPts = ptPair.second,
                        layers = state.studioLayers,
                        markups = state.studioMarkups
                    )
                    runCatching {
                        context.contentResolver.openOutputStream(outUri)?.use { it.write(svg.toByteArray()) }
                    }.onSuccess {
                        onShowMessage("Exported page ${currentPageIdx + 1} layers to SVG")
                    }.onFailure {
                        onShowMessage("SVG export failed")
                    }
                }
            }
        }

        val exportStudioJsonLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json")
        ) { outUri ->
            if (outUri != null) {
                scope.launch {
                    val bundle = StudioExporter.buildExportBundle(book.title, state.studioLayers, state.studioMarkups)
                    val json = StudioExportBundle.toJson(bundle)
                    runCatching {
                        context.contentResolver.openOutputStream(outUri)?.use { it.write(json.toByteArray()) }
                    }.onSuccess {
                        onShowMessage("Exported Studio project JSON")
                    }.onFailure {
                        onShowMessage("JSON export failed")
                    }
                }
            }
        }

        val importStudioJsonLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { inUri ->
            if (inUri != null) {
                scope.launch {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(inUri)?.bufferedReader()?.readText().orEmpty()
                        StudioExportBundle.fromJson(raw) ?: error("Invalid Studio JSON")
                    }.onSuccess { bundle ->
                        onImportStudioBundle(bundle)
                        onShowMessage("Imported ${bundle.markups.size} markups")
                    }.onFailure {
                        onShowMessage("Could not import Studio project JSON")
                    }
                }
            }
        }

        fun navigatePageDelta(deltaSpreads: Int) {
            if (isFixedPdf) {
                val step = if (isDualPdfSpread) 2 else 1
                val next = (pdfPageIdx + deltaSpreads * step).coerceIn(0, pdfPageCount - 1)
                if (next != pdfPageIdx) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (motion.reduceMotion || settings.pageTurnMode == PageTurnMode.NONE) {
                        onUpdateFixedPdfPage(next, pdfPageCount)
                    } else {
                        programmaticTurnRequest = next to System.currentTimeMillis()
                    }
                }
                return
            }
            if (settings.pageTurnMode == PageTurnMode.SCROLL) {
                scope.launch {
                    val target = (scrollState.firstVisibleItemIndex + deltaSpreads).coerceIn(0, document.chapters.lastIndex)
                    scrollState.animateScrollToItem(target)
                }
                return
            }
            if (settings.pageTurnMode == PageTurnMode.CURL) {
                val next = (currentSpreadIdx + deltaSpreads).coerceIn(0, spreadCount - 1)
                if (next != currentSpreadIdx) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (motion.reduceMotion) {
                        val pageIdx = (next * spreadStep).coerceIn(0, effectiveTotalPages - 1)
                        reportLocator(pageIdx, lazySnapshot.locatorForPage(pageIdx))
                    } else {
                        programmaticTurnRequest = next to System.currentTimeMillis()
                    }
                }
                return
            }
            val next = (pagerState.currentPage + deltaSpreads).coerceIn(0, spreadCount - 1)
            if (next != pagerState.currentPage) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    if (motion.reduceMotion || settings.pageTurnMode == PageTurnMode.NONE) {
                        pagerState.scrollToPage(next)
                    } else {
                        pagerState.animateScrollToPage(next, animationSpec = motion.spatialDefault())
                    }
                }
            }
        }

        fun executeReaderAction(action: ReaderAction) {
            when (action) {
                ReaderAction.NEXT_PAGE -> navigatePageDelta(1)
                ReaderAction.PREV_PAGE -> navigatePageDelta(-1)
                ReaderAction.TOGGLE_CHROME -> onToggleChrome(null)
                ReaderAction.NEXT_CHAPTER -> {
                    val curChap = currentChapterIndex
                    val nextChap = (curChap + 1).coerceAtMost(document.chapters.lastIndex)
                    val p = lazySnapshot.chapterStartPages[nextChap] ?: 0
                    onJumpWithUndo(lazySnapshot.locatorForPage(p))
                }
                ReaderAction.PREV_CHAPTER -> {
                    val curChap = currentChapterIndex
                    val prevChap = (curChap - 1).coerceAtLeast(0)
                    val p = lazySnapshot.chapterStartPages[prevChap] ?: 0
                    onJumpWithUndo(lazySnapshot.locatorForPage(p))
                }
                ReaderAction.TOGGLE_THEME -> {
                    val alt = themes.firstOrNull { it.isDark != activeTheme.isDark }
                    if (alt != null) onSelectGlobalTheme(alt)
                }
                ReaderAction.TOGGLE_AUTO_SCROLL -> {
                    if (settings.autoScrollMode == AutoScrollMode.OFF) {
                        onUpdateSettings { it.copy(autoScrollMode = AutoScrollMode.CONTINUOUS) }
                    }
                    onToggleAutoScroll(null)
                }
                ReaderAction.OPEN_TOC -> showToc = !showToc
                ReaderAction.OPEN_SEARCH -> showSearchSheet = true
                ReaderAction.UNDO_JUMP -> onUndoJump()
                ReaderAction.NONE -> {}
            }
        }

        // 5 Auto-Scroll modes execution
        var rollingBlindY by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(state.autoScrollActive, settings.autoScrollMode, settings.autoScrollSpeed) {
            if (!state.autoScrollActive || settings.autoScrollMode == AutoScrollMode.OFF) return@LaunchedEffect
            while (state.autoScrollActive) {
                val speed = settings.autoScrollSpeed.coerceIn(0.3f, 3.0f)
                when (settings.autoScrollMode) {
                    AutoScrollMode.CONTINUOUS, AutoScrollMode.BY_PIXEL -> {
                        if (settings.pageTurnMode == PageTurnMode.SCROLL) {
                            scrollState.dispatchRawDelta(2.4f * speed)
                            delay(16)
                        } else {
                            delay((3800L / speed).toLong())
                            navigatePageDelta(1)
                        }
                    }
                    AutoScrollMode.BY_LINE -> {
                        if (settings.pageTurnMode == PageTurnMode.SCROLL) {
                            scrollState.dispatchRawDelta(28f)
                            delay((950L / speed).toLong())
                        } else {
                            delay((3200L / speed).toLong())
                            navigatePageDelta(1)
                        }
                    }
                    AutoScrollMode.BY_PAGE -> {
                        delay((5500L / speed).toLong())
                        navigatePageDelta(1)
                    }
                    AutoScrollMode.ROLLING_BLIND -> {
                        rollingBlindY = (rollingBlindY + 0.0045f * speed)
                        if (rollingBlindY >= 1f) {
                            rollingBlindY = 0f
                            navigatePageDelta(1)
                        }
                        delay(16)
                    }
                    AutoScrollMode.OFF -> break
                }
            }
        }

        val parsedAnnotations = remember(state.annotations) {
            state.annotations.map { it to ReaderLocator.fromJson(it.locatorJson) }
        }
        val nonBookmarkAnnotationsByChapter = remember(parsedAnnotations) {
            parsedAnnotations
                .filter { (ann, _) -> AnnotationType.fromRaw(ann.type) != AnnotationType.BOOKMARK }
                .groupBy({ (_, loc) -> loc?.chapterIndex ?: -1 }, { (ann, _) -> ann })
        }

        val currentBookmark = remember(parsedAnnotations, currentChapterIndex, state.currentLocator) {
            val curProg = state.currentLocator.progression
            val curChap = currentChapterIndex
            parsedAnnotations.firstOrNull { (ann, loc) ->
                AnnotationType.fromRaw(ann.type) == AnnotationType.BOOKMARK &&
                    loc != null &&
                    loc.chapterIndex == curChap &&
                    abs(loc.progression - curProg) < 0.025f
            }?.first
        }

        val handleSelectionLongPress: (String, Int, Int, String, Int) -> Unit = { sliceText, charOffsetInSlice, chapIdx, chapTitle, baseCharOffset ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val prog = currentPage?.let { lazySnapshot.locatorForPage(it.pageIndex).progression } ?: state.currentLocator.progression
            val draft = TextSelectionDraft.fromTapOffset(
                fullText = sliceText,
                tapCharOffset = charOffsetInSlice,
                chapterIndex = chapIdx,
                chapterTitle = chapTitle,
                baseCharOffset = baseCharOffset,
                progression = prog
            )
            if (draft != null) {
                if (settings.quickHighlightEnabled) {
                    val style = HighlightStyle.fromRaw(settings.activeHighlightStyle)
                    onCreateHighlight(
                        ReaderLocator(
                            chapterIndex = draft.chapterIndex,
                            chapterTitle = draft.chapterTitle,
                            charOffset = draft.charOffset,
                            progression = draft.progression,
                            quoteAnchor = draft.selectedText.take(64)
                        ),
                        draft.selectedText,
                        draft.prefixSnippet,
                        draft.suffixSnippet,
                        settings.activeHighlightColorRole,
                        style,
                        "",
                        "",
                        CozySticker.NONE
                    )
                    onShowMessage("Quick-highlighted passage")
                } else {
                    activeSelectionDraft = draft
                }
            }
        }

        val handleTapAtRootX: (Float) -> Unit = { rootX ->
            val w = readerAreaWidthPx.coerceAtLeast(1f)
            val gesture = when {
                rootX < w * 0.28f -> ReaderGesture.TAP_LEFT
                rootX > w * 0.72f -> ReaderGesture.TAP_RIGHT
                else -> ReaderGesture.TAP_CENTER
            }
            executeReaderAction(settings.actionFor(gesture))
        }

        val commandItems = remember(book.id, settings, currentBookmark, state.studioActive, isFixedPdf, isPdfDoc, currentPageIdx) {
            buildList {
                add(
                    ReaderCommandItem("studio", if (state.studioActive) "Exit Studio Markup" else "Open Studio Markup", "CAD tools, snapping, measure, callouts & layers", "studio cad draw markup annotate") {
                        onToggleStudioMode(null)
                    }
                )
                if (isPdfDoc) {
                    add(
                        ReaderCommandItem(
                            "pdf_reflow",
                            if (settings.pdfReflowEnabled) "Switch to Original Fixed PDF" else "Switch to PDF Text Reflow",
                            "Reflow embedded PDF text with custom font, margins & dictionary",
                            "pdf reflow text mode original"
                        ) {
                            onUpdateSettings { it.copy(pdfReflowEnabled = !it.pdfReflowEnabled) }
                        }
                    )
                }
                if (isFixedPdf) {
                    add(
                        ReaderCommandItem("pdf_thumbs", "Page Thumbnails", "Jump visually across PDF pages", "thumbnails pages grid") {
                            showPdfThumbnails = true
                        }
                    )
                    add(
                        ReaderCommandItem("pdf_fit", "Toggle Fit Page / Fit Width", "Switch PDF zoom fit mode", "fit width page pdf") {
                            onUpdateSettings {
                                it.copy(pdfFitMode = if (it.pdfFitMode == PdfFitMode.FIT_PAGE) PdfFitMode.FIT_WIDTH else PdfFitMode.FIT_PAGE)
                            }
                        }
                    )
                    add(
                        ReaderCommandItem("pdf_dark", "Toggle Smart Dark Invert", "Invert PDF background for night reading", "dark invert night pdf") {
                            onUpdateSettings { it.copy(pdfSmartDarkInvert = !it.pdfSmartDarkInvert) }
                        }
                    )
                }
                add(ReaderCommandItem("studio_line", "Studio: Line Tool", "Draw snapped geometric line", "line tool studio") { onToggleStudioMode(true); onSelectStudioTool(StudioTool.LINE) })
                add(ReaderCommandItem("studio_arrow", "Studio: Arrow Tool", "Draw directional arrow callout", "arrow tool studio") { onToggleStudioMode(true); onSelectStudioTool(StudioTool.ARROW) })
                add(ReaderCommandItem("studio_measure", "Studio: Measure Distance", "Measure calibrated distance on page", "measure dimension studio") { onToggleStudioMode(true); onSelectStudioTool(StudioTool.DIMENSION_DISTANCE) })
                add(ReaderCommandItem("studio_calibrate", "Studio: Calibrate Scale", "Set real-world page units (mm/cm/m/in/ft)", "calibrate scale studio") { onToggleStudioMode(true); onSelectStudioTool(StudioTool.CALIBRATE) })
                add(ReaderCommandItem("studio_layers", "Studio: Layers", "Show/hide, lock, or adjust opacity of annotation layers", "layers studio") { showStudioLayers = true })
                add(ReaderCommandItem("studio_snap", "Studio: Toggle Snap", "Toggle Endpoint/Midpoint/Intersection/Grid snapping", "snap toggle studio") { onToggleStudioSnap() })
                add(ReaderCommandItem("studio_ortho", "Studio: Toggle Ortho 45°", "Constrain segments to 45° multiples", "ortho angle lock studio") { onToggleStudioOrtho() })
                add(ReaderCommandItem("studio_undo", "Studio: Undo Markup", "Undo the last CAD markup action", "undo markup studio") { onUndoStudio() })
                add(ReaderCommandItem("studio_export", "Export Annotated PDF / SVG / JSON", "Write non-destructive PDF copy or SVG vectors", "export annotated pdf svg json") { showStudioExport = true })
                add(
                    ReaderCommandItem("toc", "Table of Contents", "Open chapters & vault backlinks", "toc chapters outline") {
                        showAnnotationsPanel = false
                        showToc = true
                    }
                )
                add(
                    ReaderCommandItem("annotations", "Book Annotations & Highlights", "Browse highlights, notes, and bookmarks", "highlights notes bookmarks annotations") {
                        showToc = false
                        showAnnotationsPanel = true
                    }
                )
                add(
                    ReaderCommandItem("bookmark", if (currentBookmark != null) "Edit Current Bookmark" else "Bookmark This Page", "Save a named bookmark at current spot", "bookmark save mark") {
                        showBookmarkDialog = true
                    }
                )
                add(ReaderCommandItem("export_notes", "Export Book Notes", "Markdown, JSON, CSV or Obsidian Vault", "export obsidian markdown csv json") { showExportSheet = true })
                add(ReaderCommandItem("dictionary", "Look Up Word in Dictionary", "Open offline dictionary & lemma search", "dictionary define lookup word vocab") { openDictionaryFor("", "") })
                add(ReaderCommandItem("dict_manager", "Manage Offline Dictionaries", "Import StarDict, dictd, Wiktionary & reorder", "manage dictionaries stardict dictd wiktionary") { showDictManagerSheet = true })
                add(ReaderCommandItem("unknown_words", "Toggle Highlight Unknown Words", "Underline unmastered vocabulary while reading", "highlight unknown words vocabulary") { onUpdateSettings { it.copy(highlightUnknownWordsEnabled = !it.highlightUnknownWordsEnabled) } })
                add(ReaderCommandItem("tap_define", "Toggle Tap Word to Define", "Tap words in center text to define offline", "tap define dictionary") { onUpdateSettings { it.copy(tapToDefineEnabled = !it.tapToDefineEnabled) } })
                add(ReaderCommandItem("quick_hl", "Toggle Quick-Highlight Mode", "Instant highlight on long-press", "quick highlight") { onUpdateSettings { it.copy(quickHighlightEnabled = !it.quickHighlightEnabled) } })
                add(ReaderCommandItem("search", "Search in Book", "Find text across all chapters", "find search") { showSearchSheet = true })
                add(ReaderCommandItem("aa", "Typography & Page Settings", "Open Aa customization sheet", "font size margins aa") { showAaSheet = true })
                add(ReaderCommandItem("next_ch", "Next Chapter", "Jump to next chapter", "chapter next") { executeReaderAction(ReaderAction.NEXT_CHAPTER) })
                add(ReaderCommandItem("prev_ch", "Previous Chapter", "Jump to previous chapter", "chapter previous") { executeReaderAction(ReaderAction.PREV_CHAPTER) })
                add(
                    ReaderCommandItem("dual", "Toggle Dual Page Spread", "Single or two-page tablet spread", "dual spread tablet") {
                        onUpdateSettings {
                            val next = if (it.pageSpreadMode == PageSpreadMode.DUAL) PageSpreadMode.SINGLE else PageSpreadMode.DUAL
                            it.copy(pageSpreadMode = next)
                        }
                    }
                )
                add(ReaderCommandItem("ruler", "Toggle Reading Ruler", "Highlight active reading band", "focus ruler") { onUpdateSettings { it.copy(readingRulerEnabled = !it.readingRulerEnabled) } })
                add(ReaderCommandItem("autoscroll", "Toggle Auto-Scroll", "Start or pause auto-scrolling", "auto scroll play") { executeReaderAction(ReaderAction.TOGGLE_AUTO_SCROLL) })
                add(ReaderCommandItem("undo", "Undo Last Jump", "Return to previous reading spot", "undo back jump") { onUndoJump() })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.VolumeUp -> {
                                executeReaderAction(settings.actionFor(ReaderGesture.VOLUME_UP))
                                true
                            }
                            Key.VolumeDown -> {
                                executeReaderAction(settings.actionFor(ReaderGesture.VOLUME_DOWN))
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            // Main Reading Surface
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .onSizeChanged { readerAreaWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .then(
                        if (isFixedPdf || state.studioActive || settings.pageTurnMode == PageTurnMode.CURL) {
                            Modifier
                        } else {
                            Modifier.pointerInput(settings.tapZonePreset, settings.actionMap) {
                                detectTapGestures(
                                    onTap = { offset -> handleTapAtRootX(offset.x) },
                                    onLongPress = { executeReaderAction(settings.actionFor(ReaderGesture.LONG_PRESS)) }
                                )
                            }
                        }
                    )
            ) {
                val handleStudioCreateDraft: (Int, StudioTool, StudioGeometryJson) -> Unit = { pageIdx, tool, geo ->
                    if (tool == StudioTool.TEXT_NOTE || tool == StudioTool.CALLOUT) {
                        pendingTextNoteDraft = pageIdx to geo.copy(stampKind = if (tool == StudioTool.CALLOUT) StudioTool.CALLOUT.name else StudioTool.TEXT_NOTE.name)
                    } else {
                        onCreateStudioMarkup(pageIdx, tool, geo, geo.text)
                    }
                }

                if (isFixedPdf) {
                    FixedLayoutPdfViewport(
                        document = document,
                        currentPageIndex = pdfPageIdx,
                        settings = settings,
                        palette = palette,
                        studioActive = state.studioActive,
                        studioLayers = state.studioLayers,
                        studioMarkups = state.studioMarkups,
                        studioScales = state.studioScales,
                        studioSession = state.studioSession,
                        programmaticTurnRequest = programmaticTurnRequest,
                        onPageChanged = { p -> onUpdateFixedPdfPage(p, pdfPageCount) },
                        onAnimationActiveChanged = { isPageAnimating = it },
                        onCenterTap = handleTapAtRootX,
                        onCreateMarkupDraft = handleStudioCreateDraft,
                        onSelectMarkups = onSelectStudioMarkups,
                        onMoveSelectedMarkups = onMoveSelectedStudioMarkups,
                        onCalibrateSegmentMeasured = { pts -> calibrateMeasuredPts = pts },
                        isDualPdfSpread = isDualPdfSpread,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (settings.pageTurnMode == PageTurnMode.SCROLL) {
                    LazyColumn(
                        state = scrollState,
                        contentPadding = PaddingValues(
                            horizontal = horizontalPaddingDp,
                            vertical = verticalPaddingDp + 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(document.chapters, key = { it.index }) { chapter ->
                            val chapAnnotations = nonBookmarkAnnotationsByChapter[chapter.index].orEmpty()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                                    .align(Alignment.Center),
                                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
                            ) {
                                chapter.blocks.forEach { block ->
                                    RenderRichOrTextBlock(
                                        block = block,
                                        chapterIndex = chapter.index,
                                        chapterTitle = chapter.title,
                                        chapterAnnotations = chapAnnotations,
                                        highlightVocabWords = highlightVocabWords,
                                        readerAreaWidthPx = readerAreaWidthPx,
                                        settings = settings,
                                        palette = palette,
                                        bookUri = document.bookUri,
                                        images = document.images,
                                        isScrollingOrAnimating = scrollState.isScrollInProgress,
                                        onFootnoteClick = onOpenFootnote,
                                        onImageClick = onOpenImageZoom,
                                        onWikilinkClick = { title ->
                                            scope.launch {
                                                val targetId = onFindWikilinkTarget(title)
                                                if (targetId != null) {
                                                    onOpenBookById(targetId)
                                                } else {
                                                    onShowMessage("Note \"[[$title]]\" not found in library")
                                                }
                                            }
                                        },
                                        onAnnotationTap = { ann -> popoverAnnotation = ann },
                                        onWordTapDefine = openDictionaryFor,
                                        onTextTapFallback = handleTapAtRootX,
                                        onTextLongPress = { txt, off ->
                                            handleSelectionLongPress(txt, off, chapter.index, chapter.title, 0)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    LaunchedEffect(state.currentLocator.chapterIndex) {
                        val targetChap = state.currentLocator.chapterIndex.coerceIn(0, document.chapters.lastIndex)
                        if (!scrollState.isScrollInProgress && scrollState.firstVisibleItemIndex != targetChap) {
                            scrollState.scrollToItem(targetChap)
                        }
                    }

                    LaunchedEffect(scrollState.firstVisibleItemIndex) {
                        val chapIdx = scrollState.firstVisibleItemIndex.coerceIn(0, document.chapters.lastIndex)
                        val prog = if (document.chapters.size <= 1) 1f else chapIdx.toFloat() / (document.chapters.size - 1).toFloat()
                        onUpdateScrollProgression(chapIdx, prog, document.chapters[chapIdx].title)
                    }
                } else if (settings.pageTurnMode == PageTurnMode.CURL) {
                    InteractivePageCurlContainer(
                        currentPageIndex = currentSpreadIdx,
                        pageCount = spreadCount,
                        paperTintColor = Color(palette.paperTint),
                        curlStrength = settings.curlStrength,
                        curlShadowStrength = settings.curlShadowStrength,
                        curlShowPageBack = settings.curlShowPageBack,
                        curlSpeed = settings.curlSpeed,
                        grabMode = settings.curlGrabMode,
                        reduceMotion = motion.reduceMotion,
                        onTurnToPage = { destSpread ->
                            val pageIdx = (destSpread * spreadStep).coerceIn(0, effectiveTotalPages - 1)
                            reportLocator(pageIdx, lazySnapshot.locatorForPage(pageIdx))
                        },
                        onAnimationActiveChanged = { isPageAnimating = it },
                        onCenterTap = handleTapAtRootX,
                        programmaticTurnRequest = programmaticTurnRequest,
                        modifier = Modifier.fillMaxSize()
                    ) { spreadIdx ->
                        val leftPageIdx = spreadIdx * spreadStep
                        val leftPage = lazySnapshot.getPage(leftPageIdx)
                        val rightPage = if (isDualSpread) lazySnapshot.getPage(leftPageIdx + 1) else null

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(palette.paperTint))
                                .padding(bottom = 30.dp)
                        ) {
                            if (leftPage != null) {
                                SinglePageCanvas(
                                    page = leftPage,
                                    chapterAnnotations = nonBookmarkAnnotationsByChapter[leftPage.chapterIndex].orEmpty(),
                                    highlightVocabWords = highlightVocabWords,
                                    readerAreaWidthPx = readerAreaWidthPx,
                                    settings = settings,
                                    palette = palette,
                                    bookUri = document.bookUri,
                                    images = document.images,
                                    isScrollingOrAnimating = isPageAnimating,
                                    onFootnoteClick = onOpenFootnote,
                                    onImageClick = onOpenImageZoom,
                                    onWikilinkClick = { title ->
                                        scope.launch {
                                            val id = onFindWikilinkTarget(title)
                                            if (id != null) onOpenBookById(id)
                                            else onShowMessage("Note \"[[$title]]\" not found")
                                        }
                                    },
                                    onAnnotationTap = { ann -> popoverAnnotation = ann },
                                    onWordTapDefine = openDictionaryFor,
                                    onTextTapFallback = handleTapAtRootX,
                                    onSliceLongPress = { txt, off, baseOffset ->
                                        handleSelectionLongPress(txt, off, leftPage.chapterIndex, leftPage.chapterTitle, baseOffset)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(palette.accent).copy(alpha = 0.6f)
                                    )
                                }
                            }

                            if (isDualSpread) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = if (palette.isDark) 0.28f else 0.08f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
                                ) {
                                    if (rightPage != null) {
                                        SinglePageCanvas(
                                            page = rightPage,
                                            chapterAnnotations = nonBookmarkAnnotationsByChapter[rightPage.chapterIndex].orEmpty(),
                                            highlightVocabWords = highlightVocabWords,
                                            readerAreaWidthPx = readerAreaWidthPx,
                                            settings = settings,
                                            palette = palette,
                                            bookUri = document.bookUri,
                                            images = document.images,
                                            isScrollingOrAnimating = isPageAnimating,
                                            onFootnoteClick = onOpenFootnote,
                                            onImageClick = onOpenImageZoom,
                                            onWikilinkClick = { title ->
                                                scope.launch {
                                                    val id = onFindWikilinkTarget(title)
                                                    if (id != null) onOpenBookById(id)
                                                    else onShowMessage("Note \"[[$title]]\" not found")
                                                }
                                            },
                                            onAnnotationTap = { ann -> popoverAnnotation = ann },
                                            onWordTapDefine = openDictionaryFor,
                                            onTextTapFallback = handleTapAtRootX,
                                            onSliceLongPress = { txt, off, baseOffset ->
                                                handleSelectionLongPress(txt, off, rightPage.chapterIndex, rightPage.chapterTitle, baseOffset)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        key = { it },
                        modifier = Modifier.fillMaxSize()
                    ) { spreadIdx ->
                        val offsetFraction = (pagerState.currentPage - spreadIdx) + pagerState.currentPageOffsetFraction
                        val transformModifier = Modifier.mochiPageTurnTransition(
                            mode = settings.pageTurnMode,
                            offsetFraction = offsetFraction,
                            reduceMotion = motion.reduceMotion
                        )

                        val leftPageIdx = spreadIdx * spreadStep
                        val leftPage = lazySnapshot.getPage(leftPageIdx)
                        val rightPage = if (isDualSpread) lazySnapshot.getPage(leftPageIdx + 1) else null

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(transformModifier)
                                .background(Color(palette.paperTint))
                                .padding(bottom = 30.dp)
                        ) {
                            if (leftPage != null) {
                                SinglePageCanvas(
                                    page = leftPage,
                                    chapterAnnotations = nonBookmarkAnnotationsByChapter[leftPage.chapterIndex].orEmpty(),
                                    highlightVocabWords = highlightVocabWords,
                                    readerAreaWidthPx = readerAreaWidthPx,
                                    settings = settings,
                                    palette = palette,
                                    bookUri = document.bookUri,
                                    images = document.images,
                                    isScrollingOrAnimating = isPageAnimating,
                                    onFootnoteClick = onOpenFootnote,
                                    onImageClick = onOpenImageZoom,
                                    onWikilinkClick = { title ->
                                        scope.launch {
                                            val id = onFindWikilinkTarget(title)
                                            if (id != null) onOpenBookById(id)
                                            else onShowMessage("Note \"[[$title]]\" not found")
                                        }
                                    },
                                    onAnnotationTap = { ann -> popoverAnnotation = ann },
                                    onWordTapDefine = openDictionaryFor,
                                    onTextTapFallback = handleTapAtRootX,
                                    onSliceLongPress = { txt, off, baseOffset ->
                                        handleSelectionLongPress(txt, off, leftPage.chapterIndex, leftPage.chapterTitle, baseOffset)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(palette.accent).copy(alpha = 0.6f)
                                    )
                                }
                            }

                            if (isDualSpread) {
                                // Subtle center book spine gutter shadow
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = if (palette.isDark) 0.28f else 0.08f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = horizontalPaddingDp, vertical = verticalPaddingDp)
                                ) {
                                    if (rightPage != null) {
                                        SinglePageCanvas(
                                            page = rightPage,
                                            chapterAnnotations = nonBookmarkAnnotationsByChapter[rightPage.chapterIndex].orEmpty(),
                                            highlightVocabWords = highlightVocabWords,
                                            readerAreaWidthPx = readerAreaWidthPx,
                                            settings = settings,
                                            palette = palette,
                                            bookUri = document.bookUri,
                                            images = document.images,
                                            isScrollingOrAnimating = isPageAnimating,
                                            onFootnoteClick = onOpenFootnote,
                                            onImageClick = onOpenImageZoom,
                                            onWikilinkClick = { title ->
                                                scope.launch {
                                                    val id = onFindWikilinkTarget(title)
                                                    if (id != null) onOpenBookById(id)
                                                    else onShowMessage("Note \"[[$title]]\" not found")
                                                }
                                            },
                                            onAnnotationTap = { ann -> popoverAnnotation = ann },
                                            onWordTapDefine = openDictionaryFor,
                                            onTextTapFallback = handleTapAtRootX,
                                            onSliceLongPress = { txt, off, baseOffset ->
                                                handleSelectionLongPress(txt, off, rightPage.chapterIndex, rightPage.chapterTitle, baseOffset)
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reflowable Page Snapshot CAD Studio overlay when Studio Mode enabled on non-PDF pages
                if (!isFixedPdf && state.studioActive) {
                    val pageMarkups = remember(state.studioMarkups, currentPageIdx) {
                        state.studioMarkups.filter { it.pageIndex == currentPageIdx }
                    }
                    val pageScale = state.studioScales.firstOrNull { it.pageIndex == currentPageIdx }
                    AnnotationStudioCanvasLayer(
                        pageWidthPts = 612f,
                        pageHeightPts = 792f,
                        pageMarkups = pageMarkups,
                        layers = state.studioLayers,
                        pageScale = pageScale,
                        session = state.studioSession,
                        studioActive = true,
                        palmRejectionEnabled = settings.studioPalmRejection,
                        onCreateMarkupDraft = { tool, geo -> handleStudioCreateDraft(currentPageIdx, tool, geo) },
                        onSelectMarkups = onSelectStudioMarkups,
                        onMoveSelected = onMoveSelectedStudioMarkups,
                        onCalibrateSegmentMeasured = { pts -> calibrateMeasuredPts = pts },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Left-edge vertical brightness swipe zone
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(26.dp)
                        .fillMaxHeight(0.75f)
                        .pointerInput(settings.brightness) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val base = if (settings.brightness < 0f) 0.65f else settings.brightness
                                val next = (base - dragAmount / 650f).coerceIn(0.05f, 1.0f)
                                onUpdateSettings { it.copy(brightness = next) }
                                brightnessHudValue = next
                            }
                        }
                )

                // Reading Ruler / Focus Band overlay
                if (settings.readingRulerEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { _, dragAmount ->
                                    rulerYFraction = (rulerYFraction + dragAmount.y / size.height).coerceIn(0.15f, 0.85f)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val bandTop = size.height * (rulerYFraction - 0.07f)
                            val bandBottom = size.height * (rulerYFraction + 0.07f)
                            val dim = Color.Black.copy(alpha = 0.28f)
                            drawRect(dim, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, bandTop))
                            drawRect(
                                dim,
                                topLeft = Offset(0f, bandBottom),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height - bandBottom)
                            )
                            drawLine(
                                color = Color(palette.accent).copy(alpha = 0.55f),
                                start = Offset(0f, bandTop),
                                end = Offset(size.width, bandTop),
                                strokeWidth = 1.5.dp.toPx()
                            )
                            drawLine(
                                color = Color(palette.accent).copy(alpha = 0.55f),
                                start = Offset(0f, bandBottom),
                                end = Offset(size.width, bandBottom),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                }

                // Rolling Blind auto-scroll curtain
                if (state.autoScrollActive && settings.autoScrollMode == AutoScrollMode.ROLLING_BLIND) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val lineY = size.height * rollingBlindY
                        drawRect(
                            color = Color(palette.paperTint).copy(alpha = 0.35f),
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(size.width, lineY)
                        )
                        drawLine(
                            color = Color(palette.accent),
                            start = Offset(0f, lineY),
                            end = Offset(size.width, lineY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Calm Mini Progress Line (always calm at bottom when enabled & chrome hidden)
                if (settings.miniStatusBarEnabled && !state.chromeVisible) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentChapterTitle.ifBlank { book.title },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(palette.textSecondary).copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Page ${currentPageIdx + 1} of $effectiveTotalPages · $remainingInChapter left in chapter",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(palette.textSecondary).copy(alpha = 0.72f)
                        )
                    }
                }

                // Brightness HUD pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = brightnessHudValue != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                ) {
                    LiquidGlassSurface(shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.BrightnessMedium, contentDescription = null, tint = Color(palette.accent))
                            Text(
                                text = "${((brightnessHudValue ?: 0.5f) * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(palette.text)
                            )
                        }
                    }
                }

                // Auto-Scroll floating control pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = settings.autoScrollMode != AutoScrollMode.OFF,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = if (state.chromeVisible) 132.dp else 44.dp)
                ) {
                    LiquidGlassSurface(shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { onToggleAutoScroll(null) }) {
                                Icon(
                                    imageVector = if (state.autoScrollActive) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (state.autoScrollActive) "Pause auto-scroll" else "Start auto-scroll",
                                    tint = Color(palette.accent)
                                )
                            }
                            Text(
                                text = settings.autoScrollMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(palette.text),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }

                // Undo Jump Pill
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.undoJumpLocator != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (state.chromeVisible) 76.dp else 18.dp)
                ) {
                    LiquidGlassSurface(shape = CircleShape) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { onUndoJump() },
                                modifier = Modifier.testTag("undo_jump_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Undo jump")
                            }
                            IconButton(onClick = onDismissUndo, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Rounded.Info,
                                    contentDescription = "Dismiss",
                                    tint = Color.Transparent
                                )
                            }
                        }
                    }
                }

                // PDF Text Reflow status / scanned-page fallback pill
                val currentReflowChapIdx = currentChapterIndex
                val isCurrentPdfPageScanned = isPdfReflowActive &&
                    !state.isExtractingPdfReflow &&
                    state.pdfReflowChapters != null &&
                    (currentReflowChapIdx in state.pdfScannedPages || state.pdfExtractedPageCount == 0)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPdfReflowActive && (state.isExtractingPdfReflow || isCurrentPdfPageScanned),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (state.chromeVisible) 78.dp else 16.dp)
                ) {
                    LiquidGlassSurface(
                        shape = CircleShape,
                        freezeBlur = isPageAnimating,
                        modifier = Modifier.testTag("pdf_reflow_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isExtractingPdfReflow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(palette.accent)
                                )
                                Text(
                                    text = "Extracting PDF text…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(palette.text)
                                )
                            } else {
                                Text(
                                    text = "Scanned PDF page",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(palette.textSecondary)
                                )
                                TextButton(
                                    onClick = { onUpdateSettings { it.copy(pdfReflowEnabled = false) } },
                                    modifier = Modifier.testTag("pdf_switch_original_button")
                                ) {
                                    Text("Original PDF")
                                }
                            }
                        }
                    }
                }

                // Floating Liquid Glass Top Pill — clean, uncluttered hierarchy across phone & tablet
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.chromeVisible && !state.studioActive,
                    enter = fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialFast()) { -it },
                    exit = fadeOut(motion.effectsDefault()) + slideOutVertically(motion.spatialFast()) { -it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val isTabletChrome = widthClass != MochiWidthClass.COMPACT
                    LiquidGlassSurface(
                        shape = CircleShape,
                        elevation = 12.dp,
                        freezeBlur = isPageAnimating,
                        modifier = Modifier.widthIn(max = 820.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color(palette.text))
                            }
                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color(palette.text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentChapterTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(palette.textSecondary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isTabletChrome) {
                                IconButton(onClick = { onToggleStudioMode(null) }, modifier = Modifier.testTag("reader_studio_button")) {
                                    Icon(
                                        Icons.Rounded.Draw,
                                        contentDescription = "CAD Studio Markup",
                                        tint = if (state.studioActive) Color(palette.accent) else Color(palette.text)
                                    )
                                }
                                if (isPdfDoc) {
                                    IconButton(
                                        onClick = { onUpdateSettings { it.copy(pdfReflowEnabled = !it.pdfReflowEnabled) } },
                                        modifier = Modifier.testTag("reader_pdf_reflow_toggle")
                                    ) {
                                        Icon(
                                            Icons.Rounded.Article,
                                            contentDescription = if (settings.pdfReflowEnabled) "Original PDF view" else "PDF Text Reflow",
                                            tint = if (settings.pdfReflowEnabled) Color(palette.accent) else Color(palette.text)
                                        )
                                    }
                                }
                                IconButton(onClick = { showBookmarkDialog = true }, modifier = Modifier.testTag("reader_bookmark_button")) {
                                    Icon(
                                        imageVector = if (currentBookmark != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = if (currentBookmark != null) "Edit bookmark" else "Bookmark page",
                                        tint = if (currentBookmark != null) Color(palette.accent) else Color(palette.text)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showToc = false
                                        showAnnotationsPanel = !showAnnotationsPanel
                                    },
                                    modifier = Modifier.testTag("reader_annotations_button")
                                ) {
                                    Icon(Icons.Rounded.Bookmarks, contentDescription = "Annotations & Highlights", tint = Color(palette.text))
                                }
                            }

                            if (isFixedPdf) {
                                IconButton(onClick = { showPdfThumbnails = true }, modifier = Modifier.testTag("reader_pdf_thumbnails_button")) {
                                    Icon(Icons.Rounded.GridView, contentDescription = "Page Thumbnails", tint = Color(palette.text))
                                }
                            } else {
                                IconButton(onClick = { showSearchSheet = true }, modifier = Modifier.testTag("reader_search_button")) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search in book", tint = Color(palette.text))
                                }
                            }

                            IconButton(
                                onClick = {
                                    showAnnotationsPanel = false
                                    showToc = !showToc
                                },
                                modifier = Modifier.testTag("reader_toc_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = "Table of Contents", tint = Color(palette.text))
                            }

                            TextButton(
                                onClick = { showAaSheet = true },
                                modifier = Modifier.testTag("reader_aa_button")
                            ) {
                                Text(
                                    text = "Aa",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(palette.accent)
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { showOverflowMenu = true },
                                    modifier = Modifier.testTag("reader_more_button")
                                ) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More reader actions", tint = Color(palette.text))
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    if (!isTabletChrome) {
                                        DropdownMenuItem(
                                            text = { Text(if (currentBookmark != null) "Edit Bookmark" else "Bookmark This Page") },
                                            leadingIcon = {
                                                Icon(
                                                    if (currentBookmark != null) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                showOverflowMenu = false
                                                showBookmarkDialog = true
                                            },
                                            modifier = Modifier.testTag("reader_bookmark_button")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Highlights & Notes") },
                                            leadingIcon = { Icon(Icons.Rounded.Bookmarks, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                showToc = false
                                                showAnnotationsPanel = true
                                            },
                                            modifier = Modifier.testTag("reader_annotations_button")
                                        )
                                        if (isPdfDoc) {
                                            DropdownMenuItem(
                                                text = { Text(if (settings.pdfReflowEnabled) "Original Fixed PDF" else "PDF Text Reflow") },
                                                leadingIcon = { Icon(Icons.Rounded.Article, contentDescription = null) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    onUpdateSettings { it.copy(pdfReflowEnabled = !it.pdfReflowEnabled) }
                                                },
                                                modifier = Modifier.testTag("reader_pdf_reflow_toggle")
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("CAD Studio Markup") },
                                            leadingIcon = { Icon(Icons.Rounded.Draw, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onToggleStudioMode(null)
                                            },
                                            modifier = Modifier.testTag("reader_studio_button")
                                        )
                                    }
                                    if (isFixedPdf) {
                                        DropdownMenuItem(
                                            text = { Text(if (settings.pdfFitMode == PdfFitMode.FIT_PAGE) "Fit to Width" else "Fit Whole Page") },
                                            leadingIcon = { Icon(Icons.Rounded.FitScreen, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onUpdateSettings {
                                                    it.copy(pdfFitMode = if (it.pdfFitMode == PdfFitMode.FIT_PAGE) PdfFitMode.FIT_WIDTH else PdfFitMode.FIT_PAGE)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (settings.pdfSmartDarkInvert) "Disable Night PDF Invert" else "Smart Dark PDF Invert") },
                                            leadingIcon = { Icon(Icons.Rounded.Contrast, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onUpdateSettings { it.copy(pdfSmartDarkInvert = !it.pdfSmartDarkInvert) }
                                            }
                                        )
                                    }
                                    if (widthClass == MochiWidthClass.EXPANDED) {
                                        DropdownMenuItem(
                                            text = { Text(if (settings.pageSpreadMode == PageSpreadMode.DUAL) "Single Page View" else "Two-Page Spread") },
                                            leadingIcon = { Icon(Icons.Rounded.ViewColumn, contentDescription = null) },
                                            onClick = {
                                                showOverflowMenu = false
                                                onUpdateSettings {
                                                    it.copy(pageSpreadMode = if (it.pageSpreadMode == PageSpreadMode.DUAL) PageSpreadMode.SINGLE else PageSpreadMode.DUAL)
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Command Palette") },
                                        leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showCommandPalette = true
                                        },
                                        modifier = Modifier.testTag("reader_command_palette_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Book Details & Notes Export") },
                                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            onOpenBookInfo(book)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Floating Passage Selection Toolbar
                val draft = activeSelectionDraft
                androidx.compose.animation.AnimatedVisibility(
                    visible = draft != null,
                    enter = fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialFast()) { it },
                    exit = fadeOut(motion.effectsDefault()) + slideOutVertically(motion.spatialFast()) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    if (draft != null) {
                        TextSelectionGlassToolbar(
                            draft = draft,
                            activeColorRole = settings.activeHighlightColorRole,
                            activeStyle = HighlightStyle.fromRaw(settings.activeHighlightStyle),
                            colorBlindSafe = settings.colorBlindPaletteEnabled,
                            quickDefinition = selectionQuickLookup,
                            onDraftChange = { activeSelectionDraft = it },
                            onSelectColorAndStyle = { role, st ->
                                onUpdateSettings { it.copy(activeHighlightColorRole = role, activeHighlightStyle = st.name) }
                            },
                            onSaveHighlight = { d, role, st ->
                                onCreateHighlight(
                                    ReaderLocator(
                                        chapterIndex = d.chapterIndex,
                                        chapterTitle = d.chapterTitle,
                                        charOffset = d.charOffset,
                                        progression = d.progression,
                                        quoteAnchor = d.selectedText.take(64)
                                    ),
                                    d.selectedText,
                                    d.prefixSnippet,
                                    d.suffixSnippet,
                                    role,
                                    st,
                                    "",
                                    "",
                                    CozySticker.NONE
                                )
                                activeSelectionDraft = null
                                onShowMessage("Highlight saved")
                            },
                            onOpenNoteEditor = { d, role, st ->
                                pendingDraftForNote = Triple(d, role, st)
                                activeSelectionDraft = null
                            },
                            onDefineSelection = { d ->
                                val q = d.selectedText
                                val sent = d.extractContextSentence()
                                activeSelectionDraft = null
                                openDictionaryFor(q, sent)
                            },
                            onTranslateSelection = { d ->
                                val launched = SystemTranslateIntentHelper.launchSystemTranslate(context, d.selectedText)
                                if (!launched) {
                                    onShowMessage("No translator app found on device.")
                                }
                            },
                            onCopyText = { d ->
                                val md = AnnotationExporter.formatQuickSelectionCopy(
                                    quote = d.selectedText,
                                    bookTitle = book.title,
                                    bookAuthor = book.authors,
                                    chapterTitle = d.chapterTitle,
                                    progression = d.progression,
                                    withCitation = settings.copyWithCitationEnabled
                                )
                                copyTextToClipboard(context, md)
                                activeSelectionDraft = null
                                onShowMessage(if (settings.copyWithCitationEnabled) "Copied with citation" else "Copied passage")
                            },
                            onCreateQuoteCard = { d, role ->
                                val loc = ReaderLocator(
                                    chapterIndex = d.chapterIndex,
                                    chapterTitle = d.chapterTitle,
                                    charOffset = d.charOffset,
                                    progression = d.progression,
                                    quoteAnchor = d.selectedText.take(64)
                                )
                                quoteCardAnnotation = AnnotationEntity(
                                    bookId = book.id,
                                    type = AnnotationType.HIGHLIGHT.name,
                                    colorRole = role,
                                    locatorJson = ReaderLocator.toJson(loc),
                                    quoteExact = d.selectedText,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                activeSelectionDraft = null
                            },
                            onSearchSelection = { q ->
                                activeSelectionDraft = null
                                onSearchQueryChange(q)
                                showSearchSheet = true
                            },
                            onDismiss = { activeSelectionDraft = null }
                        )
                    }
                }

                // Floating Liquid Glass Bottom Scrubber (uses LiquidGlassSlider + freezes blur during turn animations)
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.chromeVisible && !state.studioActive,
                    enter = fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialFast()) { it },
                    exit = fadeOut(motion.effectsDefault()) + slideOutVertically(motion.spatialFast()) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    var scrubbingPage by remember { mutableStateOf<Int?>(null) }
                    val displayPage = scrubbingPage ?: currentPageIdx
                    val displayPageObj = lazySnapshot.getPage(displayPage)
                    val displayChapTitle = displayPageObj?.chapterTitle ?: lazySnapshot.chapterTitleForPage(displayPage)
                    val displayRem = lazySnapshot.pagesRemainingInChapter(displayPage)
                    val totalPagesFloat = (effectiveTotalPages - 1).coerceAtLeast(1).toFloat()
                    val chapterTicks = remember(lazySnapshot, totalPagesFloat, isFixedPdf) {
                        if (isFixedPdf) emptyList()
                        else lazySnapshot.chapterStartPages.values.map { (it / totalPagesFloat).coerceIn(0f, 1f) }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(max = 720.dp)
                    ) {
                        if (scrubbingPage != null && displayChapTitle.isNotBlank()) {
                            LiquidGlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                freezeBlur = isPageAnimating,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = displayChapTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(palette.text),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Page ${displayPage + 1} of $effectiveTotalPages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(palette.link)
                                    )
                                }
                            }
                        }

                        LiquidGlassSurface(
                            shape = RoundedCornerShape(26.dp),
                            elevation = 12.dp,
                            freezeBlur = isPageAnimating
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1-tap ergonomic action strip matching top paid readers
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = false,
                                        onClick = { executeReaderAction(ReaderAction.TOGGLE_THEME) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (activeTheme.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(if (activeTheme.isDark) "Day Theme" else "Night Theme") },
                                        modifier = Modifier.testTag("quick_theme_toggle_chip")
                                    )
                                    if (isPdfDoc) {
                                        FilterChip(
                                            selected = settings.pdfReflowEnabled,
                                            onClick = { onUpdateSettings { it.copy(pdfReflowEnabled = !it.pdfReflowEnabled) } },
                                            label = { Text(if (settings.pdfReflowEnabled) "Reflow On" else "Text Reflow") }
                                        )
                                    }
                                    if (isFixedPdf) {
                                        FilterChip(
                                            selected = settings.pdfFitMode == PdfFitMode.FIT_WIDTH,
                                            onClick = {
                                                onUpdateSettings {
                                                    it.copy(pdfFitMode = if (it.pdfFitMode == PdfFitMode.FIT_PAGE) PdfFitMode.FIT_WIDTH else PdfFitMode.FIT_PAGE)
                                                }
                                            },
                                            label = { Text(if (settings.pdfFitMode == PdfFitMode.FIT_WIDTH) "Fit Width" else "Fit Page") }
                                        )
                                        FilterChip(
                                            selected = settings.pdfSmartDarkInvert,
                                            onClick = { onUpdateSettings { it.copy(pdfSmartDarkInvert = !it.pdfSmartDarkInvert) } },
                                            label = { Text("PDF Invert") }
                                        )
                                    }
                                    if (widthClass == MochiWidthClass.EXPANDED) {
                                        FilterChip(
                                            selected = settings.pageSpreadMode == PageSpreadMode.DUAL,
                                            onClick = {
                                                onUpdateSettings {
                                                    it.copy(pageSpreadMode = if (it.pageSpreadMode == PageSpreadMode.DUAL) PageSpreadMode.SINGLE else PageSpreadMode.DUAL)
                                                }
                                            },
                                            label = { Text("2-Page") }
                                        )
                                    }
                                    FilterChip(
                                        selected = state.autoScrollActive,
                                        onClick = { executeReaderAction(ReaderAction.TOGGLE_AUTO_SCROLL) },
                                        label = { Text(if (state.autoScrollActive) "Scrolling" else "Auto-Scroll") }
                                    )
                                }

                                LiquidGlassSlider(
                                    value = displayPage.toFloat(),
                                    onValueChange = { scrubbingPage = it.roundToInt().coerceIn(0, effectiveTotalPages - 1) },
                                    onValueChangeFinished = {
                                        val dest = scrubbingPage ?: currentPageIdx
                                        scrubbingPage = null
                                        if (dest != currentPageIdx) {
                                            if (isFixedPdf) {
                                                onUpdateFixedPdfPage(dest, pdfPageCount)
                                            } else {
                                                onJumpWithUndo(lazySnapshot.locatorForPage(dest))
                                            }
                                        }
                                    },
                                    valueRange = 0f..totalPagesFloat,
                                    tickFractions = chapterTicks,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reader_scrubber")
                                )

                                val pct = (((displayPage + 1).toFloat() / effectiveTotalPages.coerceAtLeast(1)) * 100f).roundToInt().coerceIn(0, 100)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isFixedPdf) {
                                            "Page ${displayPage + 1} of $effectiveTotalPages ($pct%)"
                                        } else {
                                            "Page ${displayPage + 1} of $effectiveTotalPages · $pct% · $displayRem left in ch."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(palette.textSecondary)
                                    )
                                    Text(
                                        text = if (estMinutesLeft > 0) "~$estMinutesLeft min left" else "End of book",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(palette.textSecondary)
                                    )
                                }
                            }
                        }
                    }
                }

                // Milestone E: Floating CAD Studio Dock when Studio Mode is active
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.studioActive,
                    enter = fadeIn(motion.effectsDefault()) + slideInVertically(motion.spatialFast()) { it },
                    exit = fadeOut(motion.effectsDefault()) + slideOutVertically(motion.spatialFast()) { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 760.dp)
                ) {
                    StudioFloatingDock(
                        session = state.studioSession,
                        onSelectTool = onSelectStudioTool,
                        onToggleSnap = onToggleStudioSnap,
                        onToggleOrtho = onToggleStudioOrtho,
                        onUndo = onUndoStudio,
                        onRedo = onRedoStudio,
                        onOpenInspector = { showStudioInspector = true },
                        onOpenLayers = { showStudioLayers = true },
                        onOpenExport = { showStudioExport = true },
                        onCopySelected = {
                            onCopySelectedStudioMarkups()
                            onShowMessage("Copied selected markup")
                        },
                        onPasteClipboard = { onPasteStudioMarkups(currentPageIdx) },
                        onAlignLeft = { onAlignSelectedStudioMarkups(StudioGeometry.AlignMode.LEFT) },
                        onAlignCenter = { onAlignSelectedStudioMarkups(StudioGeometry.AlignMode.CENTER_X) },
                        onAlignRight = { onAlignSelectedStudioMarkups(StudioGeometry.AlignMode.RIGHT) },
                        onDeleteSelected = onDeleteSelectedStudioMarkups,
                        onExitStudio = { onToggleStudioMode(false) }
                    )
                }
            }

            // Tablet Persistent Side Pane (TOC or Annotations) on Expanded Screens
            if (isTabletSideToc) {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    if (showAnnotationsPanel) {
                        BookAnnotationsPanelContent(
                            annotations = state.annotations,
                            colorBlindSafe = settings.colorBlindPaletteEnabled,
                            onJumpToAnnotation = { ann ->
                                ReaderLocator.fromJson(ann.locatorJson)?.let(onJumpWithUndo)
                            },
                            onEditAnnotation = { editingAnnotationNote = it },
                            onQuoteCard = { quoteCardAnnotation = it },
                            onDeleteAnnotation = onDeleteAnnotation,
                            onOpenExport = { showExportSheet = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        TocContentList(
                            document = document,
                            chapterStartPages = lazySnapshot.chapterStartPages,
                            currentChapterIndex = currentChapterIndex,
                            onJumpToChapter = { chapIdx ->
                                val p = lazySnapshot.chapterStartPages[chapIdx] ?: 0
                                onJumpWithUndo(lazySnapshot.locatorForPage(p))
                            },
                            onOpenBacklinkBook = { target -> onOpenBookById(target.id) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Phone/Medium TOC Bottom Sheet
        if (showToc && widthClass != MochiWidthClass.EXPANDED) {
            ModalBottomSheet(
                onDismissRequest = { showToc = false },
                containerColor = Color(palette.surface)
            ) {
                TocContentList(
                    document = document,
                    chapterStartPages = lazySnapshot.chapterStartPages,
                    currentChapterIndex = currentChapterIndex,
                    onJumpToChapter = { chapIdx ->
                        val p = lazySnapshot.chapterStartPages[chapIdx] ?: 0
                        onJumpWithUndo(lazySnapshot.locatorForPage(p))
                        showToc = false
                    },
                    onOpenBacklinkBook = { target ->
                        showToc = false
                        onOpenBookById(target.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                )
            }
        }

        // Phone/Medium Book Annotations Bottom Sheet
        if (showAnnotationsPanel && widthClass != MochiWidthClass.EXPANDED) {
            BookAnnotationsSheet(
                annotations = state.annotations,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onJumpToAnnotation = { ann ->
                    ReaderLocator.fromJson(ann.locatorJson)?.let(onJumpWithUndo)
                },
                onEditAnnotation = {
                    showAnnotationsPanel = false
                    editingAnnotationNote = it
                },
                onQuoteCard = {
                    showAnnotationsPanel = false
                    quoteCardAnnotation = it
                },
                onDeleteAnnotation = onDeleteAnnotation,
                onOpenExport = {
                    showAnnotationsPanel = false
                    showExportSheet = true
                },
                onDismiss = { showAnnotationsPanel = false }
            )
        }

        if (showAaSheet) {
            AaGlassSheet(
                settings = settings,
                themes = themes,
                activeTheme = activeTheme,
                bookThemeOverrideId = state.bookThemeOverrideId,
                warmLightEnabled = warmLightEnabled,
                onDismiss = { showAaSheet = false },
                onUpdateSettings = onUpdateSettings,
                onSelectGlobalTheme = onSelectGlobalTheme,
                onSetBookThemeOverride = onSetBookThemeOverride,
                onToggleWarmLight = onToggleWarmLight,
                onImportFont = onImportFont,
                isPdfDocument = isPdfDoc
            )
        }

        if (showSearchSheet) {
            ReaderSearchSheet(
                query = state.searchQuery,
                results = state.searchResults,
                onQueryChange = onSearchQueryChange,
                onSelectMatch = { match ->
                    onJumpWithUndo(
                        ReaderLocator(
                            chapterIndex = match.chapterIndex,
                            chapterTitle = match.chapterTitle,
                            charOffset = match.charOffset,
                            progression = 0f,
                            quoteAnchor = match.matchText
                        )
                    )
                },
                onDismiss = { showSearchSheet = false }
            )
        }

        if (showCommandPalette) {
            CommandPaletteDialog(
                themes = themes,
                totalPages = effectiveTotalPages,
                commands = commandItems,
                onGoToPage = { pIdx -> onJumpWithUndo(lazySnapshot.locatorForPage(pIdx)) },
                onSelectTheme = onSelectGlobalTheme,
                onDismiss = { showCommandPalette = false }
            )
        }

        if (state.activeFootnote != null) {
            FootnotePopoverDialog(
                footnote = state.activeFootnote,
                onDismiss = onCloseFootnote
            )
        }

        val zoomKey = state.zoomImageKey
        val hasZoomImage = zoomKey != null && (
            document.images.containsKey(zoomKey) ||
                document.images.containsKey(zoomKey.substringAfterLast('/')) ||
                document.archiveImageKeys.contains(zoomKey) ||
                document.archiveImageKeys.any {
                    it.substringAfterLast('/').equals(zoomKey.substringAfterLast('/'), ignoreCase = true)
                }
            )
        if (zoomKey != null && hasZoomImage) {
            FullScreenImageViewer(
                bookUri = document.bookUri,
                inlineImages = document.images,
                imageKey = zoomKey,
                onDismiss = onCloseImageZoom
            )
        }

        if (showBookmarkDialog) {
            val curLoc = currentPage?.let { lazySnapshot.locatorForPage(it.pageIndex) } ?: state.currentLocator
            BookmarkDialog(
                chapterTitle = curLoc.chapterTitle.ifBlank { book.title },
                pageNumber = currentPageIdx + 1,
                existingBookmark = currentBookmark,
                onSave = { label ->
                    onCreateOrUpdateBookmark(
                        currentBookmark,
                        curLoc,
                        label,
                        curLoc.quoteAnchor,
                        settings.activeHighlightColorRole
                    )
                    showBookmarkDialog = false
                    onShowMessage(if (currentBookmark != null) "Bookmark updated" else "Bookmark saved")
                },
                onDelete = currentBookmark?.let { bm ->
                    {
                        onDeleteAnnotation(bm.id)
                        showBookmarkDialog = false
                        onShowMessage("Bookmark removed")
                    }
                },
                onDismiss = { showBookmarkDialog = false }
            )
        }

        val activePopover = popoverAnnotation?.let { p -> state.annotations.find { it.id == p.id } ?: p }
        if (activePopover != null) {
            HighlightPopoverDialog(
                annotation = activePopover,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onChangeColorStyle = { role, st ->
                    onUpdateAnnotation(activePopover, activePopover.noteMarkdown, activePopover.tags, role, st, CozySticker.fromRaw(activePopover.sticker))
                },
                onEditNote = {
                    popoverAnnotation = null
                    editingAnnotationNote = activePopover
                },
                onCreateQuoteCard = {
                    popoverAnnotation = null
                    quoteCardAnnotation = activePopover
                },
                onCopy = {
                    val md = AnnotationExporter.formatCopyMarkdown(activePopover, book, settings.copyWithCitationEnabled)
                    copyTextToClipboard(context, md)
                    popoverAnnotation = null
                    onShowMessage("Copied highlight")
                },
                onDelete = {
                    onDeleteAnnotation(activePopover.id)
                    popoverAnnotation = null
                    onShowMessage("Highlight deleted")
                },
                onDismiss = { popoverAnnotation = null }
            )
        }

        val notePendingDraft = pendingDraftForNote
        if (notePendingDraft != null) {
            val (d, role, st) = notePendingDraft
            MarkdownNoteEditorSheet(
                title = "Add Highlight & Note",
                quoteText = d.selectedText,
                initialNote = "",
                initialTags = "",
                initialColorRole = role,
                initialStyle = st,
                initialSticker = CozySticker.NONE,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onDismiss = { pendingDraftForNote = null },
                onSave = { note, tags, newRole, newStyle, sticker ->
                    onCreateHighlight(
                        ReaderLocator(
                            chapterIndex = d.chapterIndex,
                            chapterTitle = d.chapterTitle,
                            charOffset = d.charOffset,
                            progression = d.progression,
                            quoteAnchor = d.selectedText.take(64)
                        ),
                        d.selectedText,
                        d.prefixSnippet,
                        d.suffixSnippet,
                        newRole,
                        newStyle,
                        note,
                        tags,
                        sticker
                    )
                    pendingDraftForNote = null
                    onShowMessage("Saved highlight & note")
                }
            )
        }

        val noteEditTarget = editingAnnotationNote
        if (noteEditTarget != null) {
            MarkdownNoteEditorSheet(
                title = "Edit Note",
                quoteText = noteEditTarget.quoteExact,
                initialNote = noteEditTarget.noteMarkdown,
                initialTags = noteEditTarget.tags,
                initialColorRole = noteEditTarget.colorRole,
                initialStyle = HighlightStyle.fromRaw(noteEditTarget.style),
                initialSticker = CozySticker.fromRaw(noteEditTarget.sticker),
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                showStyleAndColor = AnnotationType.fromRaw(noteEditTarget.type) != AnnotationType.BOOKMARK,
                onDismiss = { editingAnnotationNote = null },
                onSave = { note, tags, role, st, sticker ->
                    onUpdateAnnotation(noteEditTarget, note, tags, role, st, sticker)
                    editingAnnotationNote = null
                    onShowMessage("Note updated")
                }
            )
        }

        val cardTarget = quoteCardAnnotation
        if (cardTarget != null) {
            QuoteCardStudioSheet(
                annotation = cardTarget,
                book = book,
                themes = themes,
                initialTheme = activeTheme,
                initialFontId = settings.fontId,
                importedFonts = settings.importedFonts,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onDismiss = { quoteCardAnnotation = null },
                onShowMessage = onShowMessage
            )
        }

        if (showExportSheet) {
            ExportTemplatesSheet(
                items = state.annotations.map { AnnotationWithBook(it, book) },
                templates = state.exportTemplates,
                scopedBook = book,
                vaultFolderUri = settings.vaultExportFolderUri,
                onSaveTemplate = onSaveTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onSaveVaultFolderUri = onSaveVaultFolderUri,
                onDismiss = { showExportSheet = false },
                onShowMessage = onShowMessage
            )
        }

        if (showDictLookupPopup) {
            DictionaryGlassPopup(
                initialQuery = dictPopupQuery,
                result = dictLookupResult,
                contextSentence = dictPopupContextSentence,
                bookTitle = book.title,
                bookLanguage = bookLanguage,
                savedVocabulary = dictState.vocabulary,
                onSearchQuery = { q ->
                    dictPopupQuery = q
                    scope.launch {
                        dictLookupResult = if (q.isBlank()) null else dictionaryViewModel.lookupWord(
                            query = q,
                            contextSentence = dictPopupContextSentence,
                            bookLanguage = bookLanguage,
                            perLanguageDefaults = settings.perLanguageDefaultDict
                        )
                    }
                },
                onSaveToVocabulary = { word, lemma, pos, def, example ->
                    dictionaryViewModel.saveVocabularyWord(
                        word = word,
                        lemma = lemma,
                        partOfSpeech = pos,
                        definition = def,
                        exampleSentence = example,
                        contextSentence = dictPopupContextSentence,
                        bookId = book.id,
                        bookTitle = book.title
                    )
                },
                onOpenDictionaryManager = {
                    showDictLookupPopup = false
                    showDictManagerSheet = true
                },
                onShowMessage = onShowMessage,
                onDismiss = { showDictLookupPopup = false }
            )
        }

        if (showDictManagerSheet) {
            DictionaryManagerSheet(
                dictionaries = dictState.dictionaries,
                perLanguageDefaults = settings.perLanguageDefaultDict,
                isImporting = dictState.isImporting,
                onToggleEnabled = dictionaryViewModel::toggleDictionaryEnabled,
                onMoveOrder = dictionaryViewModel::moveDictionaryOrder,
                onUpdateLanguage = dictionaryViewModel::updateDictionaryLanguage,
                onDeleteCustom = dictionaryViewModel::deleteCustomDictionary,
                onSetPerLanguageDefault = { lang, dictId ->
                    onUpdateSettings { s ->
                        val updated = s.perLanguageDefaultDict.toMutableMap()
                        if (dictId == null) updated.remove(lang) else updated[lang] = dictId
                        s.copy(perLanguageDefaultDict = updated)
                    }
                },
                onImportSafTree = { uri ->
                    dictionaryViewModel.importFromSafTree(uri, onShowMessage)
                },
                onImportSafFile = { uri ->
                    dictionaryViewModel.importFromSafFile(uri, onShowMessage)
                },
                onDismiss = { showDictManagerSheet = false }
            )
        }

        if (showPdfThumbnails && isFixedPdf) {
            PdfThumbnailsSheet(
                bookUri = document.bookUri,
                pageCount = pdfPageCount,
                currentPageIndex = currentPageIdx,
                smartDarkInvert = settings.pdfSmartDarkInvert,
                onSelectPage = { p -> onUpdateFixedPdfPage(p, pdfPageCount) },
                onDismiss = { showPdfThumbnails = false }
            )
        }

        if (showStudioInspector) {
            val selectedMarkup = state.studioSession.selectedIds.firstOrNull()?.let { selId ->
                state.studioMarkups.find { it.id == selId }
            }
            StudioPropertiesSheet(
                selectedMarkup = selectedMarkup,
                activeStyle = state.studioSession.activeStyle,
                onUpdateActiveStyle = onUpdateStudioStyle,
                onUpdateSelectedMarkup = onUpdateStudioMarkup,
                onDismiss = { showStudioInspector = false }
            )
        }

        if (showStudioLayers) {
            StudioLayersSheet(
                layers = state.studioLayers,
                activeLayerId = state.studioSession.activeLayerId,
                onSelectLayer = onSelectStudioLayer,
                onCreateLayer = onCreateStudioLayer,
                onUpdateLayer = onUpdateStudioLayer,
                onDeleteLayer = onDeleteStudioLayer,
                onDismiss = { showStudioLayers = false }
            )
        }

        if (showStudioExport) {
            val safeBaseName = book.title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "mochi_doc" }
            StudioExportSheet(
                onExportAnnotatedPdf = {
                    if (isPdfDoc) {
                        exportAnnotatedPdfLauncher.launch("${safeBaseName}_annotated.pdf")
                    } else {
                        onShowMessage("Annotated PDF export is available for PDF documents")
                    }
                },
                onExportSvg = {
                    exportSvgLauncher.launch("${safeBaseName}_page_${currentPageIdx + 1}.svg")
                },
                onExportJson = {
                    exportStudioJsonLauncher.launch("${safeBaseName}_studio.json")
                },
                onImportJson = {
                    importStudioJsonLauncher.launch(arrayOf("application/json", "*/*"))
                },
                onDismiss = { showStudioExport = false }
            )
        }

        val calPts = calibrateMeasuredPts
        if (calPts != null) {
            val curScale = state.studioScales.firstOrNull { it.pageIndex == currentPageIdx }
            val curUnit = curScale?.let { s -> MeasurementUnit.entries.firstOrNull { it.name == s.unit } } ?: MeasurementUnit.MM
            CalibrateScaleDialog(
                measuredSegmentPts = calPts,
                currentUnit = curUnit,
                onConfirm = { ptsPerUnit, unit ->
                    onSavePageScale(currentPageIdx, ptsPerUnit, unit)
                    calibrateMeasuredPts = null
                    onShowMessage("Saved scale for page ${currentPageIdx + 1}")
                },
                onDismiss = { calibrateMeasuredPts = null }
            )
        }

        val noteDraft = pendingTextNoteDraft
        if (noteDraft != null) {
            val (notePageIdx, noteGeo) = noteDraft
            val toolForDraft = if (noteGeo.stampKind == StudioTool.CALLOUT.name) StudioTool.CALLOUT else StudioTool.TEXT_NOTE
            StudioTextPromptDialog(
                title = if (toolForDraft == StudioTool.CALLOUT) "Callout Label" else "Pinned Text Note",
                initialText = noteGeo.text,
                onConfirm = { label ->
                    onCreateStudioMarkup(notePageIdx, toolForDraft, noteGeo.copy(text = label), label)
                    pendingTextNoteDraft = null
                },
                onDismiss = { pendingTextNoteDraft = null }
            )
        }
    }
}

private val MOCHI_WORD_REGEX = Regex("[\\p{L}][\\p{L}'’-]*")

private data class DecoratedHighlightRange(
    val start: Int,
    val end: Int,
    val style: HighlightStyle,
    val color: Color
)

private fun buildDecoratedSliceText(
    base: AnnotatedString,
    annotations: List<AnnotationEntity>,
    highlightVocabWords: List<VocabularyWordEntity>,
    palette: MochiThemePalette,
    colorBlindSafe: Boolean
): Pair<AnnotatedString, List<DecoratedHighlightRange>> {
    if ((annotations.isEmpty() && highlightVocabWords.isEmpty()) || base.isEmpty()) return base to emptyList()
    val plain = base.text
    val lowerPlain = plain.lowercase()
    val squigglyAndWashi = mutableListOf<DecoratedHighlightRange>()
    val vocabTerms = if (highlightVocabWords.isNotEmpty()) {
        highlightVocabWords.flatMap { listOf(it.word.lowercase(), it.lemma.lowercase()) }
            .filter { it.length >= 2 }
            .toHashSet()
    } else emptySet()

    val decorated = buildAnnotatedString {
        append(base)
        for (ann in annotations) {
            if (AnnotationType.fromRaw(ann.type) == AnnotationType.BOOKMARK) continue
            val quote = ann.quoteExact.trim()
            if (quote.length < 2) continue
            val idx = lowerPlain.indexOf(quote.lowercase())
            if (idx < 0) continue
            val end = (idx + quote.length).coerceAtMost(plain.length)
            if (idx >= end) continue

            val color = HighlightColorHelper.colorForRole(palette, ann.colorRole, colorBlindSafe)
            val style = HighlightStyle.fromRaw(ann.style)
            addStringAnnotation("mochi_ann", ann.id.toString(), idx, end)

            when (style) {
                HighlightStyle.HIGHLIGHT -> addStyle(SpanStyle(background = color.copy(alpha = 0.45f)), idx, end)
                HighlightStyle.UNDERLINE -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, background = color.copy(alpha = 0.18f)),
                    idx, end
                )
                HighlightStyle.STRIKETHROUGH -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough, background = color.copy(alpha = 0.14f)),
                    idx, end
                )
                HighlightStyle.WASHI_TAPE -> {
                    addStyle(SpanStyle(background = color.copy(alpha = 0.58f)), idx, end)
                    squigglyAndWashi.add(DecoratedHighlightRange(idx, end, style, color))
                }
                HighlightStyle.SQUIGGLY -> {
                    addStyle(SpanStyle(background = color.copy(alpha = 0.12f)), idx, end)
                    squigglyAndWashi.add(DecoratedHighlightRange(idx, end, style, color))
                }
            }
        }

        if (vocabTerms.isNotEmpty()) {
            MOCHI_WORD_REGEX.findAll(plain).forEach { match ->
                val norm = EnglishLemmatizer.normalizeToken(match.value)
                val lemmas = EnglishLemmatizer.candidateLemmas(norm)
                val matchedTerm = lemmas.firstOrNull { it in vocabTerms }
                if (matchedTerm != null) {
                    val s = match.range.first
                    val e = match.range.last + 1
                    addStringAnnotation("mochi_vocab", match.value, s, e)
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            background = Color(palette.accentContainer).copy(alpha = 0.25f)
                        ),
                        s,
                        e
                    )
                }
            }
        }
    }
    return decorated to squigglyAndWashi
}

private fun extractWordAndSentenceAt(text: String, offset: Int): Pair<String, String>? {
    if (text.isBlank() || offset < 0 || offset >= text.length) return null
    val match = MOCHI_WORD_REGEX.findAll(text).firstOrNull { offset in it.range.first..(it.range.last + 1) }
        ?: return null
    val sentStart = text.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'), (match.range.first - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val sentEnd = text.indexOfAny(charArrayOf('.', '!', '?', '\n'), (match.range.last + 1).coerceAtMost(text.length))
        .let { if (it < 0) text.length else it + 1 }
    val sentence = text.substring(sentStart.coerceIn(0, text.length), sentEnd.coerceIn(0, text.length)).trim()
    return match.value to sentence
}

@Composable
private fun AnnotatedReaderText(
    text: AnnotatedString,
    style: TextStyle,
    annotations: List<AnnotationEntity>,
    highlightVocabWords: List<VocabularyWordEntity>,
    tapToDefineEnabled: Boolean,
    readerAreaWidthPx: Float,
    palette: MochiThemePalette,
    colorBlindSafe: Boolean,
    onFootnoteClick: (String) -> Unit,
    onWikilinkClick: (String) -> Unit,
    onAnnotationTap: (AnnotationEntity) -> Unit,
    onWordTapDefine: (String, String) -> Unit,
    onTapFallback: (Float) -> Unit,
    onLongPressOffset: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val (decoratedText, extraDecorations) = remember(text, annotations, highlightVocabWords, palette.id, colorBlindSafe) {
        buildDecoratedSliceText(text, annotations, highlightVocabWords, palette, colorBlindSafe)
    }

    Text(
        text = decoratedText,
        style = style,
        onTextLayout = { layoutResult = it },
        modifier = modifier
            .onGloballyPositioned { textCoords = it }
            .drawBehind {
                val layout = layoutResult ?: return@drawBehind
                for (dec in extraDecorations) {
                    val s = dec.start.coerceIn(0, decoratedText.length)
                    val e = dec.end.coerceIn(s, decoratedText.length)
                    if (s >= e) continue
                    val startLine = layout.getLineForOffset(s)
                    val endLine = layout.getLineForOffset((e - 1).coerceAtLeast(s))
                    for (line in startLine..endLine) {
                        val lineStartOff = if (line == startLine) s else layout.getLineStart(line)
                        val lineEndOff = if (line == endLine) e else layout.getLineEnd(line, visibleEnd = true)
                        if (lineStartOff >= lineEndOff) continue
                        val left = layout.getHorizontalPosition(lineStartOff, usePrimaryDirection = true)
                        val right = layout.getHorizontalPosition(lineEndOff, usePrimaryDirection = true)
                            .coerceAtLeast(left + 4f)
                        val bottom = layout.getLineBottom(line)
                        val top = layout.getLineTop(line)

                        if (dec.style == HighlightStyle.SQUIGGLY) {
                            val wavePath = Path()
                            val yBase = bottom - 2.dp.toPx()
                            val amp = 2.2.dp.toPx()
                            val period = 8.dp.toPx()
                            var x = left
                            wavePath.moveTo(x, yBase)
                            while (x < right) {
                                val phase = ((x - left) / period) * 2f * PI.toFloat()
                                wavePath.lineTo(x, yBase + sin(phase) * amp)
                                x += 2.5f
                            }
                            drawPath(
                                path = wavePath,
                                color = dec.color.copy(alpha = 0.95f),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        } else if (dec.style == HighlightStyle.WASHI_TAPE) {
                            val strokeW = 1.4.dp.toPx()
                            drawLine(
                                color = dec.color.copy(alpha = 0.9f),
                                start = Offset(left, top + 1.dp.toPx()),
                                end = Offset(right, top + 1.dp.toPx()),
                                strokeWidth = strokeW
                            )
                            drawLine(
                                color = dec.color.copy(alpha = 0.9f),
                                start = Offset(left, bottom - 1.dp.toPx()),
                                end = Offset(right, bottom - 1.dp.toPx()),
                                strokeWidth = strokeW
                            )
                        }
                    }
                }
            }
            .pointerInput(decoratedText, annotations, tapToDefineEnabled, readerAreaWidthPx) {
                detectTapGestures(
                    onTap = { pos ->
                        val offset = layoutResult?.getOffsetForPosition(pos)
                        val rootX = (textCoords?.positionInWindow()?.x ?: 0f) + pos.x
                        if (offset != null) {
                            decoratedText.getStringAnnotations("mochi_ann", offset, offset).firstOrNull()?.let { tag ->
                                val hit = annotations.find { it.id.toString() == tag.item }
                                if (hit != null) {
                                    onAnnotationTap(hit)
                                    return@detectTapGestures
                                }
                            }
                            decoratedText.getStringAnnotations("mochi_vocab", offset, offset).firstOrNull()?.let { tag ->
                                val pair = extractWordAndSentenceAt(decoratedText.text, offset)
                                onWordTapDefine(pair?.first ?: tag.item, pair?.second.orEmpty())
                                return@detectTapGestures
                            }
                            decoratedText.getStringAnnotations("footnote", offset, offset).firstOrNull()?.let {
                                onFootnoteClick(it.item)
                                return@detectTapGestures
                            }
                            decoratedText.getStringAnnotations("wikilink", offset, offset).firstOrNull()?.let {
                                onWikilinkClick(it.item)
                                return@detectTapGestures
                            }
                            if (tapToDefineEnabled) {
                                val w = readerAreaWidthPx.coerceAtLeast(1f)
                                if (rootX >= w * 0.22f && rootX <= w * 0.78f) {
                                    val pair = extractWordAndSentenceAt(decoratedText.text, offset)
                                    if (pair != null) {
                                        onWordTapDefine(pair.first, pair.second)
                                        return@detectTapGestures
                                    }
                                }
                            }
                        }
                        onTapFallback(rootX)
                    },
                    onLongPress = { pos ->
                        val offset = layoutResult?.getOffsetForPosition(pos) ?: 0
                        onLongPressOffset(decoratedText.text, offset)
                    }
                )
            }
    )
}

@Composable
private fun SinglePageCanvas(
    page: ReaderPage,
    chapterAnnotations: List<AnnotationEntity>,
    highlightVocabWords: List<VocabularyWordEntity>,
    readerAreaWidthPx: Float,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    bookUri: String,
    images: Map<String, ByteArray>,
    isScrollingOrAnimating: Boolean,
    onFootnoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onWikilinkClick: (String) -> Unit,
    onAnnotationTap: (AnnotationEntity) -> Unit,
    onWordTapDefine: (String, String) -> Unit,
    onTextTapFallback: (Float) -> Unit,
    onSliceLongPress: (String, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pageAnnotations = chapterAnnotations

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
    ) {
        page.slices.forEach { slice ->
            when (slice) {
                is PageSlice.TextBlockSlice -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (slice.isBlockquote) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(22.dp)
                                    .background(Color(palette.accent), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        AnnotatedReaderText(
                            text = slice.text,
                            style = slice.style,
                            annotations = pageAnnotations,
                            highlightVocabWords = highlightVocabWords,
                            tapToDefineEnabled = settings.tapToDefineEnabled,
                            readerAreaWidthPx = readerAreaWidthPx,
                            palette = palette,
                            colorBlindSafe = settings.colorBlindPaletteEnabled,
                            onFootnoteClick = onFootnoteClick,
                            onWikilinkClick = onWikilinkClick,
                            onAnnotationTap = onAnnotationTap,
                            onWordTapDefine = onWordTapDefine,
                            onTapFallback = onTextTapFallback,
                            onLongPressOffset = { txt, off ->
                                onSliceLongPress(txt, off, slice.chapterCharStart)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is PageSlice.RichBlockSlice -> {
                    RenderRichOrTextBlock(
                        block = slice.block,
                        chapterIndex = page.chapterIndex,
                        chapterTitle = page.chapterTitle,
                        chapterAnnotations = pageAnnotations,
                        highlightVocabWords = highlightVocabWords,
                        readerAreaWidthPx = readerAreaWidthPx,
                        settings = settings,
                        palette = palette,
                        bookUri = bookUri,
                        images = images,
                        isScrollingOrAnimating = isScrollingOrAnimating,
                        onFootnoteClick = onFootnoteClick,
                        onImageClick = onImageClick,
                        onWikilinkClick = onWikilinkClick,
                        onAnnotationTap = onAnnotationTap,
                        onWordTapDefine = onWordTapDefine,
                        onTextTapFallback = onTextTapFallback,
                        onTextLongPress = { txt, off ->
                            onSliceLongPress(txt, off, slice.chapterCharStart)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderRichOrTextBlock(
    block: ContentBlock,
    chapterIndex: Int,
    chapterTitle: String,
    chapterAnnotations: List<AnnotationEntity>,
    highlightVocabWords: List<VocabularyWordEntity>,
    readerAreaWidthPx: Float,
    settings: ReaderSettings,
    palette: MochiThemePalette,
    bookUri: String,
    images: Map<String, ByteArray>,
    isScrollingOrAnimating: Boolean = false,
    onFootnoteClick: (String) -> Unit,
    onImageClick: (String) -> Unit,
    onWikilinkClick: (String) -> Unit,
    onAnnotationTap: (AnnotationEntity) -> Unit,
    onWordTapDefine: (String, String) -> Unit,
    onTextTapFallback: (Float) -> Unit,
    onTextLongPress: (String, Int) -> Unit
) {
    val fontFamily = ReaderFontResolver.resolve(settings.fontId, settings.importedFonts)
    val baseStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = settings.fontSizeSp.sp,
        lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
        color = Color(palette.text)
    )

    when (block) {
        is ContentBlock.FrontMatterCard -> {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(palette.surfaceVariant).copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DOCUMENT METADATA", style = MaterialTheme.typography.labelSmall, color = Color(palette.link))
                    block.entries.forEach { (k, v) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$k:", style = MaterialTheme.typography.labelMedium, color = Color(palette.textSecondary))
                            Text(v, style = MaterialTheme.typography.bodySmall, color = Color(palette.text))
                        }
                    }
                }
            }
        }
        is ContentBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.48f
                2 -> 1.28f
                else -> 1.15f
            }
            AnnotatedReaderText(
                text = ReaderPaginator.toAnnotatedString(block.content, palette, false),
                style = baseStyle.copy(
                    fontSize = (settings.fontSizeSp * scale).sp,
                    lineHeight = (settings.fontSizeSp * scale * 1.25f).sp,
                    fontWeight = FontWeight.Bold
                ),
                annotations = chapterAnnotations,
                highlightVocabWords = highlightVocabWords,
                tapToDefineEnabled = settings.tapToDefineEnabled,
                readerAreaWidthPx = readerAreaWidthPx,
                palette = palette,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onFootnoteClick = onFootnoteClick,
                onWikilinkClick = onWikilinkClick,
                onAnnotationTap = onAnnotationTap,
                onWordTapDefine = onWordTapDefine,
                onTapFallback = onTextTapFallback,
                onLongPressOffset = onTextLongPress
            )
        }
        is ContentBlock.Paragraph -> {
            val annotated = ReaderPaginator.toAnnotatedString(block.content, palette, settings.hyphenationEnabled)
            AnnotatedReaderText(
                text = annotated,
                style = baseStyle,
                annotations = chapterAnnotations,
                highlightVocabWords = highlightVocabWords,
                tapToDefineEnabled = settings.tapToDefineEnabled,
                readerAreaWidthPx = readerAreaWidthPx,
                palette = palette,
                colorBlindSafe = settings.colorBlindPaletteEnabled,
                onFootnoteClick = onFootnoteClick,
                onWikilinkClick = onWikilinkClick,
                onAnnotationTap = onAnnotationTap,
                onWordTapDefine = onWordTapDefine,
                onTapFallback = onTextTapFallback,
                onLongPressOffset = onTextLongPress
            )
        }
        is ContentBlock.Blockquote -> {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(26.dp)
                        .background(Color(palette.accent), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                AnnotatedReaderText(
                    text = ReaderPaginator.toAnnotatedString(block.content, palette, false),
                    style = baseStyle.copy(fontStyle = FontStyle.Italic, color = Color(palette.textSecondary)),
                    annotations = chapterAnnotations,
                    highlightVocabWords = highlightVocabWords,
                    tapToDefineEnabled = settings.tapToDefineEnabled,
                    readerAreaWidthPx = readerAreaWidthPx,
                    palette = palette,
                    colorBlindSafe = settings.colorBlindPaletteEnabled,
                    onFootnoteClick = onFootnoteClick,
                    onWikilinkClick = onWikilinkClick,
                    onAnnotationTap = onAnnotationTap,
                    onWordTapDefine = onWordTapDefine,
                    onTapFallback = onTextTapFallback,
                    onLongPressOffset = onTextLongPress,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is ContentBlock.CodeBlock -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(palette.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!block.language.isNullOrBlank()) {
                        Text(block.language, style = MaterialTheme.typography.labelSmall, color = Color(palette.link))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = Color(palette.text),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
        is ContentBlock.TableBlock -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(palette.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            ) {
                Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .background(Color(palette.accentContainer).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        block.headers.forEach { h ->
                            Text(h, style = MaterialTheme.typography.labelMedium, color = Color(palette.text), modifier = Modifier.widthIn(min = 96.dp))
                        }
                    }
                    block.rows.forEach { row ->
                        Row(
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            row.forEach { cell ->
                                Text(cell, style = MaterialTheme.typography.bodySmall, color = Color(palette.text), modifier = Modifier.widthIn(min = 96.dp))
                            }
                        }
                    }
                }
            }
        }
        is ContentBlock.ListBlock -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEachIndexed { _, item ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (item.checked) {
                            true -> Icon(Icons.Rounded.CheckBox, contentDescription = "Checked", tint = Color(palette.accent), modifier = Modifier.size(18.dp))
                            false -> Icon(Icons.Rounded.CheckBoxOutlineBlank, contentDescription = "Unchecked", tint = Color(palette.textSecondary), modifier = Modifier.size(18.dp))
                            null -> Text(
                                text = if (item.orderedNumber != null) "${item.orderedNumber}." else "•",
                                style = baseStyle,
                                color = Color(palette.accent)
                            )
                        }
                        AnnotatedReaderText(
                            text = ReaderPaginator.toAnnotatedString(item.text, palette, false),
                            style = baseStyle,
                            annotations = chapterAnnotations,
                            highlightVocabWords = highlightVocabWords,
                            tapToDefineEnabled = settings.tapToDefineEnabled,
                            readerAreaWidthPx = readerAreaWidthPx,
                            palette = palette,
                            colorBlindSafe = settings.colorBlindPaletteEnabled,
                            onFootnoteClick = onFootnoteClick,
                            onWikilinkClick = onWikilinkClick,
                            onAnnotationTap = onAnnotationTap,
                            onWordTapDefine = onWordTapDefine,
                            onTapFallback = onTextTapFallback,
                            onLongPressOffset = onTextLongPress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        is ContentBlock.ImageBlock -> {
            val context = LocalContext.current
            val maxDecodeDim = if (isScrollingOrAnimating) 960 else 1280
            val imgCacheKey = ReaderDocumentCache.imageCacheKey(bookUri, block.imageKey, maxDecodeDim)
            val decodedBitmap by produceState(
                initialValue = ReaderDocumentCache.getCachedImageBlock(imgCacheKey),
                key1 = imgCacheKey,
                key2 = bookUri
            ) {
                if (value == null) {
                    value = ReaderDocumentCache.loadAndDecodeImageBlock(
                        context = context,
                        bookUri = bookUri,
                        inlineImages = images,
                        imageKey = block.imageKey,
                        maxDimensionPx = maxDecodeDim
                    )
                }
            }
            val bitmap = remember(decodedBitmap) { decodedBitmap?.takeIf { !it.isRecycled }?.asImageBitmap() }
            if (bitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onImageClick(block.imageKey) }
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = block.caption ?: "Book illustration (tap to zoom)",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    if (!block.caption.isNullOrBlank()) {
                        Text(block.caption, style = MaterialTheme.typography.labelSmall, color = Color(palette.textSecondary))
                    }
                }
            }
        }
        ContentBlock.Divider -> {
            HorizontalDivider(color = Color(palette.textSecondary).copy(alpha = 0.25f))
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Mochi Highlight", text))
}
