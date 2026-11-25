package com.example.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MochiApplication
import com.example.data.annotations.CozySticker
import com.example.data.annotations.HighlightStyle
import com.example.data.db.AnnotationEntity
import com.example.data.db.AnnotationLayerEntity
import com.example.data.db.BookEntity
import com.example.data.db.ExportTemplateEntity
import com.example.data.db.MarkupObjectEntity
import com.example.data.db.PageScaleEntity
import com.example.data.reader.FootnoteEntry
import com.example.data.reader.PaginatedBook
import com.example.data.reader.ParsedDocument
import com.example.data.reader.PdfReflowExtractor
import com.example.data.reader.ReaderChapter
import com.example.data.reader.ReaderLocator
import com.example.data.reader.ReaderPaginator
import com.example.data.reader.ReaderSearchMatch
import com.example.data.studio.MeasurementUnit
import com.example.data.studio.StudioCommand
import com.example.data.studio.StudioExportBundle
import com.example.data.studio.StudioGeometry
import com.example.data.studio.StudioGeometryJson
import com.example.data.studio.StudioSessionState
import com.example.data.studio.StudioStyleJson
import com.example.data.studio.StudioTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val book: BookEntity? = null,
    val document: ParsedDocument? = null,
    val errorMessage: String? = null,
    val currentLocator: ReaderLocator = ReaderLocator(),
    val undoJumpLocator: ReaderLocator? = null,
    val chromeVisible: Boolean = false,
    val bookThemeOverrideId: String? = null,
    val searchQuery: String = "",
    val searchResults: List<ReaderSearchMatch> = emptyList(),
    val activeFootnote: FootnoteEntry? = null,
    val zoomImageKey: String? = null,
    val autoScrollActive: Boolean = false,
    val annotations: List<AnnotationEntity> = emptyList(),
    val exportTemplates: List<ExportTemplateEntity> = emptyList(),
    // Milestone E: Fixed-layout PDF & CAD Annotation Studio state
    val studioActive: Boolean = false,
    val studioLayers: List<AnnotationLayerEntity> = emptyList(),
    val studioMarkups: List<MarkupObjectEntity> = emptyList(),
    val studioScales: List<PageScaleEntity> = emptyList(),
    val studioSession: StudioSessionState = StudioSessionState(),
    // Milestone G: On-demand non-destructive PDF Text Reflow
    val pdfReflowChapters: List<ReaderChapter>? = null,
    val pdfScannedPages: Set<Int> = emptySet(),
    val pdfExtractedPageCount: Int = 0,
    val isExtractingPdfReflow: Boolean = false
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MochiApplication).container
    private val readerRepository = container.readerRepository
    private val annotationRepository = container.annotationRepository
    private val studioRepository = container.annotationStudioRepository
    private val readingStatsRepository = container.readingStatsRepository
    private val bookDao = container.database.bookDao()

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var loadedBookId: Long = -1L
    private var observerJob: Job? = null
    private var sessionTickerJob: Job? = null
    private var lastHeartbeatMillis: Long = 0L
    private var pendingPagesTurned: Int = 0
    private var activeBookTitle: String = ""

    fun openBook(bookId: Long, targetLocator: ReaderLocator? = null) {
        if (loadedBookId == bookId && _uiState.value.document != null) {
            if (targetLocator != null) {
                jumpWithUndo(targetLocator)
            }
            return
        }
        if (loadedBookId > 0L && loadedBookId != bookId) {
            flushSessionStatsSyncAsync()
        }
        loadedBookId = bookId
        observerJob?.cancel()
        sessionTickerJob?.cancel()
        lastHeartbeatMillis = System.currentTimeMillis()
        pendingPagesTurned = 0
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                undoJumpLocator = null,
                chromeVisible = false,
                annotations = emptyList(),
                studioActive = false,
                studioLayers = emptyList(),
                studioMarkups = emptyList(),
                studioScales = emptyList(),
                studioSession = StudioSessionState(),
                pdfReflowChapters = null,
                pdfScannedPages = emptySet(),
                pdfExtractedPageCount = 0,
                isExtractingPdfReflow = false
            )
        }

        observerJob = viewModelScope.launch {
            val book = readerRepository.getBook(bookId)
            if (book == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Book not found.") }
                return@launch
            }
            val doc = runCatching { readerRepository.loadDocument(book) }.getOrElse { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        book = book,
                        errorMessage = "Could not open file: ${err.localizedMessage ?: "Unsupported or corrupt file"}"
                    )
                }
                return@launch
            }
            val savedLocator = targetLocator ?: readerRepository.getSavedLocator(bookId) ?: ReaderLocator(
                chapterIndex = 0,
                chapterTitle = doc.chapters.firstOrNull()?.title ?: book.title,
                charOffset = 0,
                progression = book.progress
            )
            val layers = studioRepository.ensureDefaultLayers(bookId)
            activeBookTitle = book.title
            lastHeartbeatMillis = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    book = book,
                    document = doc,
                    currentLocator = savedLocator,
                    studioLayers = layers,
                    studioSession = it.studioSession.copy(
                        activeLayerId = layers.firstOrNull()?.id ?: 0L,
                        layers = layers
                    )
                )
            }
            startSessionHeartbeat()
            launch {
                readerRepository.observeBookSettings(bookId).collect { settings ->
                    _uiState.update { it.copy(bookThemeOverrideId = settings?.themeId) }
                }
            }
            launch {
                annotationRepository.observeBookAnnotations(bookId).collect { list ->
                    _uiState.update { it.copy(annotations = list) }
                }
            }
            launch {
                annotationRepository.observeTemplates().collect { templates ->
                    _uiState.update { it.copy(exportTemplates = templates) }
                }
            }
            launch {
                studioRepository.observeLayers(bookId).collect { layerList ->
                    _uiState.update { st ->
                        val activeId = st.studioSession.activeLayerId.takeIf { id -> layerList.any { it.id == id } }
                            ?: layerList.firstOrNull()?.id ?: 0L
                        st.copy(
                            studioLayers = layerList,
                            studioSession = st.studioSession.copy(
                                activeLayerId = activeId,
                                layers = layerList
                            )
                        )
                    }
                }
            }
            launch {
                studioRepository.observeBookMarkups(bookId).collect { markups ->
                    _uiState.update { st ->
                        val validIds = markups.map { it.id }.toHashSet()
                        st.copy(
                            studioMarkups = markups,
                            studioSession = st.studioSession.copy(
                                selectedIds = st.studioSession.selectedIds.filterTo(HashSet()) { it in validIds }
                            )
                        )
                    }
                }
            }
            launch {
                studioRepository.observeScales(bookId).collect { scales ->
                    _uiState.update { it.copy(studioScales = scales) }
                }
            }
        }
    }

    fun toggleChrome(force: Boolean? = null) {
        _uiState.update { it.copy(chromeVisible = force ?: !it.chromeVisible) }
    }

    fun updateCurrentPage(pageIndex: Int, paginated: PaginatedBook) {
        updateLocator(paginated.locatorForPage(pageIndex))
    }

    fun updateLocator(newLocator: ReaderLocator) {
        val bookId = _uiState.value.book?.id ?: return
        if (newLocator.chapterIndex != _uiState.value.currentLocator.chapterIndex ||
            kotlin.math.abs(newLocator.progression - _uiState.value.currentLocator.progression) > 0.001f
        ) {
            pendingPagesTurned++
        }
        _uiState.update { it.copy(currentLocator = newLocator) }
        viewModelScope.launch {
            readerRepository.savePosition(bookId, newLocator)
        }
    }

    fun updateFixedPdfPage(pageIndex: Int, totalPages: Int) {
        val bookId = _uiState.value.book?.id ?: return
        val safeTotal = totalPages.coerceAtLeast(1)
        val safeIdx = pageIndex.coerceIn(0, safeTotal - 1)
        if (safeIdx != _uiState.value.currentLocator.chapterIndex) {
            pendingPagesTurned++
        }
        val prog = if (safeTotal <= 1) 1f else safeIdx.toFloat() / (safeTotal - 1).toFloat()
        val newLocator = ReaderLocator(
            chapterIndex = safeIdx,
            chapterTitle = "Page ${safeIdx + 1}",
            charOffset = 0,
            progression = prog.coerceIn(0f, 1f)
        )
        _uiState.update { it.copy(currentLocator = newLocator) }
        viewModelScope.launch {
            readerRepository.savePosition(bookId, newLocator)
        }
    }

    fun ensurePdfReflowLoaded() {
        val st = _uiState.value
        val doc = st.document ?: return
        if (!doc.isFixedLayoutPdf || st.pdfReflowChapters != null || st.isExtractingPdfReflow) return
        _uiState.update { it.copy(isExtractingPdfReflow = true) }
        viewModelScope.launch {
            val result = runCatching {
                PdfReflowExtractor.extractFromUri(
                    context = getApplication(),
                    uriString = doc.bookUri,
                    expectedPageCount = doc.pdfPageCount
                )
            }.getOrNull()
            _uiState.update { cur ->
                if (result == null) {
                    cur.copy(isExtractingPdfReflow = false)
                } else {
                    cur.copy(
                        isExtractingPdfReflow = false,
                        pdfReflowChapters = result.chapters,
                        pdfScannedPages = result.scannedPageIndices,
                        pdfExtractedPageCount = result.extractedPageCount
                    )
                }
            }
        }
    }

    private fun startSessionHeartbeat() {
        sessionTickerJob?.cancel()
        sessionTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                flushSessionHeartbeat()
            }
        }
    }

    private suspend fun flushSessionHeartbeat() {
        val bookId = loadedBookId.takeIf { it > 0L } ?: return
        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastHeartbeatMillis) / 1000L).coerceIn(0L, 120L)
        lastHeartbeatMillis = now
        val pages = pendingPagesTurned
        pendingPagesTurned = 0
        if (deltaSec > 0L || pages > 0) {
            readingStatsRepository.recordReadingDelta(
                bookId = bookId,
                bookTitle = activeBookTitle.ifBlank { "Untitled" },
                durationDeltaSeconds = deltaSec,
                pagesTurnedDelta = pages
            )
        }
    }

    private fun flushSessionStatsSyncAsync() {
        val bookId = loadedBookId.takeIf { it > 0L } ?: return
        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastHeartbeatMillis) / 1000L).coerceIn(0L, 120L)
        lastHeartbeatMillis = now
        val pages = pendingPagesTurned
        pendingPagesTurned = 0
        val title = activeBookTitle.ifBlank { "Untitled" }
        if (deltaSec > 0L || pages > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                readingStatsRepository.recordReadingDelta(
                    bookId = bookId,
                    bookTitle = title,
                    durationDeltaSeconds = deltaSec,
                    pagesTurnedDelta = pages
                )
            }
        }
    }

    override fun onCleared() {
        sessionTickerJob?.cancel()
        flushSessionStatsSyncAsync()
        super.onCleared()
    }

    fun updateScrollProgression(chapterIndex: Int, progression: Float, chapterTitle: String) {
        val bookId = _uiState.value.book?.id ?: return
        val newLocator = ReaderLocator(
            chapterIndex = chapterIndex,
            chapterTitle = chapterTitle,
            charOffset = 0,
            progression = progression.coerceIn(0f, 1f)
        )
        _uiState.update { it.copy(currentLocator = newLocator) }
        viewModelScope.launch {
            readerRepository.savePosition(bookId, newLocator)
        }
    }

    fun jumpWithUndo(targetLocator: ReaderLocator) {
        val previous = _uiState.value.currentLocator
        _uiState.update {
            it.copy(
                undoJumpLocator = previous,
                currentLocator = targetLocator
            )
        }
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            readerRepository.savePosition(bookId, targetLocator)
        }
    }

    fun undoJump(): ReaderLocator? {
        val backTo = _uiState.value.undoJumpLocator ?: return null
        _uiState.update {
            it.copy(
                currentLocator = backTo,
                undoJumpLocator = null
            )
        }
        val bookId = _uiState.value.book?.id ?: return backTo
        viewModelScope.launch {
            readerRepository.savePosition(bookId, backTo)
        }
        return backTo
    }

    fun dismissUndoJump() {
        _uiState.update { it.copy(undoJumpLocator = null) }
    }

    fun updateSearchQuery(query: String) {
        val doc = _uiState.value.document
        val matches = if (doc != null) ReaderPaginator.search(doc, query) else emptyList()
        _uiState.update { it.copy(searchQuery = query, searchResults = matches) }
    }

    fun openFootnote(footnoteId: String) {
        val entry = _uiState.value.document?.footnotes?.get(footnoteId)
            ?: FootnoteEntry(footnoteId, "Footnote [$footnoteId]", "Footnote text not found in this section.")
        _uiState.update { it.copy(activeFootnote = entry) }
    }

    fun closeFootnote() {
        _uiState.update { it.copy(activeFootnote = null) }
    }

    fun openImageZoom(imageKey: String) {
        _uiState.update { it.copy(zoomImageKey = imageKey) }
    }

    fun closeImageZoom() {
        _uiState.update { it.copy(zoomImageKey = null) }
    }

    fun toggleAutoScroll(force: Boolean? = null) {
        _uiState.update { it.copy(autoScrollActive = force ?: !it.autoScrollActive) }
    }

    fun setBookThemeOverride(themeId: String?) {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            readerRepository.setBookThemeOverride(bookId, themeId)
        }
    }

    // ---- Standard Highlights / Bookmarks ----

    fun createHighlight(
        locator: ReaderLocator,
        quoteExact: String,
        quotePrefix: String = "",
        quoteSuffix: String = "",
        colorRole: Int = 0,
        style: HighlightStyle = HighlightStyle.HIGHLIGHT,
        noteMarkdown: String = "",
        tags: String = "",
        sticker: CozySticker = CozySticker.NONE,
        onCreated: ((AnnotationEntity) -> Unit)? = null
    ) {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            val saved = annotationRepository.createHighlight(
                bookId = bookId,
                locator = locator,
                quoteExact = quoteExact,
                quotePrefix = quotePrefix,
                quoteSuffix = quoteSuffix,
                colorRole = colorRole,
                style = style,
                noteMarkdown = noteMarkdown,
                tags = tags,
                sticker = sticker
            )
            onCreated?.invoke(saved)
        }
    }

    fun updateAnnotation(
        entity: AnnotationEntity,
        noteMarkdown: String = entity.noteMarkdown,
        tags: String = entity.tags,
        colorRole: Int = entity.colorRole,
        style: HighlightStyle = HighlightStyle.fromRaw(entity.style),
        sticker: CozySticker = CozySticker.fromRaw(entity.sticker)
    ) = viewModelScope.launch {
        annotationRepository.upsertAnnotation(
            entity.copy(
                noteMarkdown = noteMarkdown.trim(),
                tags = tags,
                colorRole = colorRole,
                style = style.name,
                sticker = sticker.takeIf { it != CozySticker.NONE }?.name
            )
        )
    }

    fun createOrUpdateBookmark(
        existing: AnnotationEntity?,
        locator: ReaderLocator,
        label: String,
        snippet: String,
        colorRole: Int
    ) = viewModelScope.launch {
        val bookId = _uiState.value.book?.id ?: return@launch
        if (existing != null) {
            annotationRepository.upsertAnnotation(
                existing.copy(
                    noteMarkdown = label.trim(),
                    colorRole = colorRole
                )
            )
        } else {
            annotationRepository.createBookmark(
                bookId = bookId,
                locator = locator,
                label = label,
                snippet = snippet,
                colorRole = colorRole
            )
        }
    }

    fun deleteAnnotation(id: Long) = viewModelScope.launch {
        annotationRepository.deleteAnnotation(id)
    }

    fun saveTemplate(name: String, body: String, existingId: Long) = viewModelScope.launch {
        annotationRepository.saveTemplate(name, body, existingId)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch {
        annotationRepository.deleteTemplate(id)
    }

    suspend fun findWikilinkBookId(targetTitle: String): Long? {
        val clean = targetTitle.trim().lowercase()
        return bookDao.getAllBooksOnce()
            .firstOrNull { it.title.lowercase() == clean || it.title.lowercase().contains(clean) }
            ?.id
    }

    // ---- Milestone E: CAD Annotation Studio Actions & Unlimited Undo/Redo ----

    fun toggleStudioMode(force: Boolean? = null) {
        _uiState.update { st ->
            val nextActive = force ?: !st.studioActive
            st.copy(
                studioActive = nextActive,
                chromeVisible = if (nextActive) false else st.chromeVisible
            )
        }
    }

    fun setStudioTool(tool: StudioTool) {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(activeTool = tool))
        }
    }

    fun toggleStudioSnap() {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(snapEnabled = !it.studioSession.snapEnabled))
        }
    }

    fun toggleStudioGridSnap() {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(gridSnapEnabled = !it.studioSession.gridSnapEnabled))
        }
    }

    fun toggleStudioOrtho() {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(orthoLockEnabled = !it.studioSession.orthoLockEnabled))
        }
    }

    fun updateStudioActiveStyle(style: StudioStyleJson) {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(activeStyle = style))
        }
    }

    fun selectStudioLayer(layerId: Long) {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(activeLayerId = layerId))
        }
    }

    fun createStudioLayer(name: String) = viewModelScope.launch {
        val bookId = _uiState.value.book?.id ?: return@launch
        val newId = studioRepository.createLayer(bookId, name)
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(activeLayerId = newId))
        }
    }

    fun updateStudioLayer(layer: AnnotationLayerEntity) = viewModelScope.launch {
        studioRepository.updateLayer(layer)
    }

    fun deleteStudioLayer(layerId: Long) = viewModelScope.launch {
        studioRepository.deleteLayer(layerId)
    }

    fun selectStudioMarkups(ids: Set<Long>) {
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(selectedIds = ids))
        }
    }

    fun createStudioMarkup(
        pageIndex: Int,
        tool: StudioTool,
        geometry: StudioGeometryJson,
        label: String = geometry.text
    ) {
        val bookId = _uiState.value.book?.id ?: return
        val session = _uiState.value.studioSession
        val layerId = session.activeLayerId.takeIf { it > 0L }
            ?: _uiState.value.studioLayers.firstOrNull()?.id ?: return

        viewModelScope.launch {
            val saved = studioRepository.upsertMarkup(
                MarkupObjectEntity(
                    bookId = bookId,
                    pageIndex = pageIndex,
                    layerId = layerId,
                    kind = tool.name,
                    geometryJson = StudioGeometryJson.toJson(geometry),
                    styleJson = StudioStyleJson.toJson(session.activeStyle),
                    label = label,
                    zOrder = (_uiState.value.studioMarkups.maxOfOrNull { it.zOrder } ?: 0) + 1
                )
            )
            _uiState.update { st ->
                st.copy(
                    studioSession = st.studioSession.copy(
                        selectedIds = setOf(saved.id),
                        undoStack = st.studioSession.undoStack + StudioCommand.AddMarkup(saved),
                        redoStack = emptyList()
                    )
                )
            }
        }
    }

    fun updateStudioMarkup(updated: MarkupObjectEntity, recordUndo: Boolean = true) {
        val before = _uiState.value.studioMarkups.find { it.id == updated.id }
        viewModelScope.launch {
            val saved = studioRepository.upsertMarkup(updated)
            if (recordUndo && before != null) {
                _uiState.update { st ->
                    st.copy(
                        studioSession = st.studioSession.copy(
                            undoStack = st.studioSession.undoStack + StudioCommand.UpdateMarkup(before, saved),
                            redoStack = emptyList()
                        )
                    )
                }
            }
        }
    }

    fun moveSelectedStudioMarkups(dx: Float, dy: Float) {
        val st = _uiState.value
        val selected = st.studioMarkups.filter { it.id in st.studioSession.selectedIds }
        if (selected.isEmpty()) return
        val moved = selected.map { StudioGeometry.translateMarkup(it, dx, dy) }
        viewModelScope.launch {
            studioRepository.upsertMarkups(moved)
            _uiState.update { cur ->
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        undoStack = cur.studioSession.undoStack + StudioCommand.BatchUpdate(selected, moved),
                        redoStack = emptyList()
                    )
                )
            }
        }
    }

    fun alignSelectedStudioMarkups(mode: StudioGeometry.AlignMode) {
        val st = _uiState.value
        val selected = st.studioMarkups.filter { it.id in st.studioSession.selectedIds }
        if (selected.size < 2) return
        val aligned = StudioGeometry.alignMarkups(selected, mode)
        viewModelScope.launch {
            studioRepository.upsertMarkups(aligned)
            _uiState.update { cur ->
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        undoStack = cur.studioSession.undoStack + StudioCommand.BatchUpdate(selected, aligned),
                        redoStack = emptyList()
                    )
                )
            }
        }
    }

    fun copySelectedStudioMarkups() {
        val st = _uiState.value
        val selected = st.studioMarkups.filter { it.id in st.studioSession.selectedIds }
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(studioSession = it.studioSession.copy(clipboard = selected))
        }
    }

    fun pasteStudioMarkups(targetPageIndex: Int) {
        val st = _uiState.value
        val clip = st.studioSession.clipboard
        if (clip.isEmpty()) return
        val activeLayer = st.studioSession.activeLayerId
        viewModelScope.launch {
            val createdList = mutableListOf<MarkupObjectEntity>()
            for (item in clip) {
                val offsetItem = StudioGeometry.translateMarkup(item, 0.03f, 0.03f)
                    .copy(id = 0L, pageIndex = targetPageIndex, layerId = if (activeLayer > 0L) activeLayer else item.layerId)
                createdList.add(studioRepository.upsertMarkup(offsetItem))
            }
            _uiState.update { cur ->
                val cmds = createdList.map { StudioCommand.AddMarkup(it) }
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        selectedIds = createdList.map { it.id }.toSet(),
                        undoStack = cur.studioSession.undoStack + cmds,
                        redoStack = emptyList()
                    )
                )
            }
        }
    }

    fun deleteSelectedStudioMarkups() {
        val st = _uiState.value
        val toDelete = st.studioMarkups.filter { it.id in st.studioSession.selectedIds }
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            studioRepository.deleteMarkups(toDelete.map { it.id })
            _uiState.update { cur ->
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        selectedIds = emptySet(),
                        undoStack = cur.studioSession.undoStack + StudioCommand.DeleteMarkups(toDelete),
                        redoStack = emptyList()
                    )
                )
            }
        }
    }

    fun undoStudio() {
        val st = _uiState.value
        val cmd = st.studioSession.undoStack.lastOrNull() ?: return
        viewModelScope.launch {
            when (cmd) {
                is StudioCommand.AddMarkup -> studioRepository.deleteMarkup(cmd.created.id)
                is StudioCommand.UpdateMarkup -> studioRepository.upsertMarkup(cmd.before)
                is StudioCommand.DeleteMarkups -> studioRepository.upsertMarkups(cmd.deleted)
                is StudioCommand.BatchUpdate -> studioRepository.upsertMarkups(cmd.before)
            }
            _uiState.update { cur ->
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        undoStack = cur.studioSession.undoStack.dropLast(1),
                        redoStack = cur.studioSession.redoStack + cmd
                    )
                )
            }
        }
    }

    fun redoStudio() {
        val st = _uiState.value
        val cmd = st.studioSession.redoStack.lastOrNull() ?: return
        viewModelScope.launch {
            when (cmd) {
                is StudioCommand.AddMarkup -> studioRepository.upsertMarkup(cmd.created)
                is StudioCommand.UpdateMarkup -> studioRepository.upsertMarkup(cmd.after)
                is StudioCommand.DeleteMarkups -> studioRepository.deleteMarkups(cmd.deleted.map { it.id })
                is StudioCommand.BatchUpdate -> studioRepository.upsertMarkups(cmd.after)
            }
            _uiState.update { cur ->
                cur.copy(
                    studioSession = cur.studioSession.copy(
                        redoStack = cur.studioSession.redoStack.dropLast(1),
                        undoStack = cur.studioSession.undoStack + cmd
                    )
                )
            }
        }
    }

    fun savePageScale(pageIndex: Int, pagePtsPerUnit: Float, unit: MeasurementUnit) {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            studioRepository.savePageScale(bookId, pageIndex, pagePtsPerUnit, unit)
        }
    }

    fun importStudioBundle(bundle: StudioExportBundle) {
        val bookId = _uiState.value.book?.id ?: return
        viewModelScope.launch {
            val layerMap = mutableMapOf<Long, Long>()
            for (l in bundle.layers) {
                val createdId = studioRepository.createLayer(bookId, l.name)
                layerMap[l.id] = createdId
            }
            val fallbackLayer = _uiState.value.studioLayers.firstOrNull()?.id ?: layerMap.values.firstOrNull() ?: return@launch
            val toInsert = bundle.markups.map { m ->
                MarkupObjectEntity(
                    bookId = bookId,
                    pageIndex = m.pageIndex,
                    layerId = layerMap[m.layerId] ?: fallbackLayer,
                    kind = m.kind,
                    geometryJson = m.geometryJson,
                    styleJson = m.styleJson,
                    label = m.label
                )
            }
            studioRepository.upsertMarkups(toInsert)
        }
    }
}
