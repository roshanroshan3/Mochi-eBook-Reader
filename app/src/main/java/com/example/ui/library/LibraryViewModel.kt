package com.example.ui.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MochiApplication
import com.example.data.db.BookCollectionCrossRef
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.BookTagCrossRef
import com.example.data.db.CollectionEntity
import com.example.data.db.FolderEntity
import com.example.data.db.ReadingStatus
import com.example.data.db.TagEntity
import com.example.data.library.GutenbergBook
import com.example.data.stats.ReadingInsights
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    SHELF("Shelf"),
    FOLDERS("Folders"),
    COLLECTIONS("Collections")
}

enum class SortOption(val label: String) {
    SHELF_ORDER("Custom Shelf"),
    LAST_READ("Last Read"),
    TITLE("Title"),
    AUTHOR("Author"),
    DATE_ADDED("Date Added"),
    PROGRESS("Progress"),
    SIZE("File Size")
}

data class LibraryFilterState(
    val query: String = "",
    val tab: LibraryTab = LibraryTab.SHELF,
    val sortOption: SortOption = SortOption.SHELF_ORDER,
    val formatFilter: BookFormat? = null,
    val statusFilter: ReadingStatus? = null,
    val tagIdFilter: Long? = null,
    val showDuplicatesOnly: Boolean = false,
    val showHidden: Boolean = false,
    val currentFolderUri: String? = null,
    val currentSubPath: String = "",
    val selectedBookIds: Set<Long> = emptySet(),
    val isScanning: Boolean = false
)

data class BookStackGroup(
    val stackId: String?,
    val stackTitle: String?,
    val books: List<BookEntity>
) {
    val leadBook: BookEntity get() = books.first()
    val isStack: Boolean get() = stackId != null && books.size > 1
}

data class LibraryUiState(
    val filter: LibraryFilterState = LibraryFilterState(),
    val visibleBooks: List<BookEntity> = emptyList(),
    val allBooks: List<BookEntity> = emptyList(),
    val readingNowBook: BookEntity? = null,
    val continueShelf: List<BookEntity> = emptyList(),
    val wantToReadShelf: List<BookEntity> = emptyList(),
    val shelfItems: List<BookStackGroup> = emptyList(),
    val duplicateHashes: Set<String> = emptySet(),
    val folders: List<FolderEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionRefs: List<BookCollectionCrossRef> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val tagRefs: List<BookTagCrossRef> = emptyList(),
    val readingInsights: ReadingInsights = ReadingInsights(),
    val eventMessage: String? = null
)

data class GutenbergCatalogState(
    val query: String = "",
    val selectedTopic: String = "Popular",
    val books: List<GutenbergBook> = emptyList(),
    val totalCount: Int = 0,
    val nextUrl: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val downloadProgress: Map<Int, Float> = emptyMap(),
    val downloadedBookIds: Map<Int, Long> = emptyMap()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MochiApplication).container
    private val repository = container.libraryRepository
    private val gutenbergRepository = container.gutenbergRepository
    private val statsRepository = container.readingStatsRepository

    private val _filter = MutableStateFlow(LibraryFilterState())
    private val _eventMessage = MutableStateFlow<String?>(null)
    private val _gutenbergRaw = MutableStateFlow(GutenbergCatalogState())
    private var gutenbergJob: Job? = null

    private data class RepoSnapshot(
        val books: List<BookEntity>,
        val folders: List<FolderEntity>,
        val collections: List<CollectionEntity>,
        val collectionRefs: List<BookCollectionCrossRef>,
        val tags: List<TagEntity>,
        val tagRefs: List<BookTagCrossRef>
    )

    private val repoFlow = combine(
        repository.allBooksIncludingHidden,
        repository.folders,
        repository.collections,
        repository.bookCollectionRefs,
        repository.tags
    ) { books, folders, collections, colRefs, tags ->
        RepoSnapshot(books, folders, collections, colRefs, tags, emptyList())
    }.combine(repository.bookTagRefs) { snap, tagRefs ->
        snap.copy(tagRefs = tagRefs)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _filter,
        repoFlow,
        statsRepository.observeInsights(),
        _eventMessage
    ) { filter, snap, insights, message ->
        val visiblePool = if (filter.showHidden) {
            snap.books
        } else {
            snap.books.filter { !it.hidden }
        }

        val duplicateHashes = snap.books
            .filter { !it.hidden && it.contentHash.isNotBlank() }
            .groupBy { it.contentHash }
            .filterValues { it.size > 1 }
            .keys

        val tagBookIds = filter.tagIdFilter?.let { tagId ->
            snap.tagRefs.filter { it.tagId == tagId }.map { it.bookId }.toSet()
        }

        val q = filter.query.trim().lowercase()
        val filtered = visiblePool.filter { book ->
            val matchesQuery = q.isEmpty() ||
                book.title.lowercase().contains(q) ||
                book.authors.lowercase().contains(q) ||
                (book.series?.lowercase()?.contains(q) == true)
            val matchesFormat = filter.formatFilter == null || book.format == filter.formatFilter
            val matchesStatus = filter.statusFilter == null || book.status == filter.statusFilter
            val matchesTag = tagBookIds == null || book.id in tagBookIds
            val matchesDup = !filter.showDuplicatesOnly || book.contentHash in duplicateHashes
            matchesQuery && matchesFormat && matchesStatus && matchesTag && matchesDup
        }

        val sorted = when (filter.sortOption) {
            SortOption.SHELF_ORDER -> filtered.sortedBy { it.shelfOrder }
            SortOption.LAST_READ -> filtered.sortedByDescending { it.lastOpenedAt ?: 0L }
            SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOption.AUTHOR -> filtered.sortedBy { it.authors.lowercase() }
            SortOption.DATE_ADDED -> filtered.sortedByDescending { it.addedAt }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progress }
            SortOption.SIZE -> filtered.sortedByDescending { it.fileSizeBytes }
        }

        // Build stacks when in Shelf tab
        val stackedGroups = buildShelfGroups(sorted)

        val readingNow = visiblePool
            .filter { it.status == ReadingStatus.READING || (it.lastOpenedAt != null && it.progress < 0.99f) }
            .maxByOrNull { it.lastOpenedAt ?: 0L }
            ?: visiblePool.firstOrNull()

        val continueShelf = visiblePool
            .filter { it.id != readingNow?.id && it.progress > 0f && it.progress < 0.99f }
            .sortedByDescending { it.lastOpenedAt ?: it.addedAt }

        val wantToRead = visiblePool
            .filter { it.status == ReadingStatus.UNREAD }
            .sortedByDescending { it.addedAt }

        LibraryUiState(
            filter = filter,
            visibleBooks = sorted,
            allBooks = snap.books,
            readingNowBook = readingNow,
            continueShelf = continueShelf,
            wantToReadShelf = wantToRead,
            shelfItems = stackedGroups,
            duplicateHashes = duplicateHashes,
            folders = snap.folders,
            collections = snap.collections,
            collectionRefs = snap.collectionRefs,
            tags = snap.tags,
            tagRefs = snap.tagRefs,
            readingInsights = insights,
            eventMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState()
    )

    val gutenbergState: StateFlow<GutenbergCatalogState> = combine(
        _gutenbergRaw,
        repository.allBooksIncludingHidden
    ) { catalog, allBooks ->
        val regex = Regex("pg_(\\d+)\\.")
        val downloadedMap = buildMap<Int, Long> {
            allBooks.forEach { book ->
                val match = regex.find(book.uri)
                val gid = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (gid != null) put(gid, book.id)
            }
        }
        catalog.copy(downloadedBookIds = downloadedMap)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GutenbergCatalogState()
    )

    private fun buildShelfGroups(books: List<BookEntity>): List<BookStackGroup> {
        val result = mutableListOf<BookStackGroup>()
        val seenStacks = mutableSetOf<String>()
        val byStack = books.filter { it.stackId != null }.groupBy { it.stackId!! }

        for (book in books) {
            val sid = book.stackId
            if (sid == null) {
                result.add(BookStackGroup(stackId = null, stackTitle = null, books = listOf(book)))
            } else if (seenStacks.add(sid)) {
                val members = byStack[sid].orEmpty()
                if (members.size == 1) {
                    result.add(BookStackGroup(stackId = null, stackTitle = null, books = members))
                } else {
                    result.add(
                        BookStackGroup(
                            stackId = sid,
                            stackTitle = book.stackTitle ?: "Book Stack",
                            books = members
                        )
                    )
                }
            }
        }
        return result
    }

    fun setTab(tab: LibraryTab) {
        _filter.update { it.copy(tab = tab) }
    }

    fun setQuery(query: String) {
        _filter.update { it.copy(query = query) }
    }

    fun setSort(sort: SortOption) {
        _filter.update { it.copy(sortOption = sort) }
    }

    fun setFormatFilter(format: BookFormat?) {
        _filter.update { it.copy(formatFilter = format) }
    }

    fun setStatusFilter(status: ReadingStatus?) {
        _filter.update { it.copy(statusFilter = status) }
    }

    fun setTagFilter(tagId: Long?) {
        _filter.update { it.copy(tagIdFilter = tagId) }
    }

    fun toggleDuplicatesOnly() {
        _filter.update { it.copy(showDuplicatesOnly = !it.showDuplicatesOnly) }
    }

    fun toggleShowHidden() {
        _filter.update { it.copy(showHidden = !it.showHidden) }
    }

    fun navigateFolder(treeUri: String?, subPath: String = "") {
        _filter.update { it.copy(currentFolderUri = treeUri, currentSubPath = subPath) }
    }

    fun toggleSelection(bookId: Long) {
        _filter.update { state ->
            val next = if (bookId in state.selectedBookIds) {
                state.selectedBookIds - bookId
            } else {
                state.selectedBookIds + bookId
            }
            state.copy(selectedBookIds = next)
        }
    }

    fun clearSelection() {
        _filter.update { it.copy(selectedBookIds = emptySet()) }
    }

    fun clearEventMessage() {
        _eventMessage.value = null
    }

    fun addSafFolder(treeUri: Uri) = viewModelScope.launch {
        _filter.update { it.copy(isScanning = true) }
        val count = repository.addSafFolder(treeUri)
        _filter.update { it.copy(isScanning = false) }
        _eventMessage.value = "Scanned folder: $count book${if (count == 1) "" else "s"} found"
    }

    fun importFile(uri: Uri) = viewModelScope.launch {
        _filter.update { it.copy(isScanning = true) }
        val book = repository.importSingleFile(uri)
        _filter.update { it.copy(isScanning = false) }
        _eventMessage.value = if (book != null) {
            "Added \"${book.title}\""
        } else {
            "File format not supported"
        }
    }

    fun rescanAll() = viewModelScope.launch {
        _filter.update { it.copy(isScanning = true) }
        repository.rescanAllFolders()
        _filter.update { it.copy(isScanning = false) }
        _eventMessage.value = "Library folders rescanned"
    }

    fun removeFolder(treeUri: String) = viewModelScope.launch {
        repository.removeFolder(treeUri)
        if (_filter.value.currentFolderUri == treeUri) {
            navigateFolder(null, "")
        }
        _eventMessage.value = "Folder removed from library"
    }

    fun swapBooks(firstId: Long, secondId: Long) = viewModelScope.launch {
        repository.swapBookOrder(firstId, secondId)
    }

    fun stackBookOnto(sourceId: Long, targetId: Long) = viewModelScope.launch {
        repository.stackBookOnto(sourceId, targetId)
        _eventMessage.value = "Created book stack"
    }

    fun unstackBook(bookId: Long) = viewModelScope.launch {
        repository.unstackBook(bookId)
    }

    fun markBooksStatus(ids: List<Long>, status: ReadingStatus) = viewModelScope.launch {
        repository.updateStatus(ids, status)
        clearSelection()
    }

    fun hideBooks(ids: List<Long>, hidden: Boolean) = viewModelScope.launch {
        repository.hideBooks(ids, hidden)
        clearSelection()
        _eventMessage.value = if (hidden) "Hidden ${ids.size} book(s)" else "Restored ${ids.size} book(s)"
    }

    fun removeBooksFromLibrary(ids: List<Long>) = viewModelScope.launch {
        repository.removeFromLibrary(ids)
        clearSelection()
        _eventMessage.value = "Removed from library (original files untouched)"
    }

    fun deleteBooksFromDisk(ids: List<Long>) = viewModelScope.launch {
        val count = repository.deleteBookFilesFromDisk(ids)
        clearSelection()
        _eventMessage.value = "Deleted $count file(s) from disk"
    }

    fun createCollection(name: String, addBookIds: List<Long> = emptyList()) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = repository.createCollection(name)
        if (addBookIds.isNotEmpty()) {
            repository.addBooksToCollection(addBookIds, id)
        }
        clearSelection()
        _eventMessage.value = "Created collection \"$name\""
    }

    fun addBooksToCollection(bookIds: List<Long>, collectionId: Long) = viewModelScope.launch {
        repository.addBooksToCollection(bookIds, collectionId)
        clearSelection()
        _eventMessage.value = "Added to collection"
    }

    fun removeBookFromCollection(bookId: Long, collectionId: Long) = viewModelScope.launch {
        repository.removeBookFromCollection(bookId, collectionId)
    }

    fun deleteCollection(id: Long) = viewModelScope.launch {
        repository.deleteCollection(id)
    }

    fun addTagToBook(bookId: Long, tagName: String) = viewModelScope.launch {
        repository.addTagToBook(bookId, tagName)
    }

    fun removeTagFromBook(bookId: Long, tagId: Long) = viewModelScope.launch {
        repository.removeTagFromBook(bookId, tagId)
    }

    fun openGutenbergCatalogIfNeeded() {
        val current = _gutenbergRaw.value
        if (current.books.isEmpty() && !current.isLoading) {
            searchGutenberg(current.query, current.selectedTopic)
        }
    }

    fun setGutenbergQuery(query: String) {
        _gutenbergRaw.update { it.copy(query = query) }
    }

    fun searchGutenberg(
        query: String = _gutenbergRaw.value.query,
        topic: String = _gutenbergRaw.value.selectedTopic
    ) {
        gutenbergJob?.cancel()
        _gutenbergRaw.update {
            it.copy(
                query = query,
                selectedTopic = topic,
                isLoading = true,
                errorMessage = null
            )
        }
        gutenbergJob = viewModelScope.launch {
            val result = gutenbergRepository.fetchCatalog(query = query, topic = topic)
            result.fold(
                onSuccess = { page ->
                    _gutenbergRaw.update {
                        it.copy(
                            books = page.results,
                            totalCount = page.count,
                            nextUrl = page.nextUrl,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { err ->
                    _gutenbergRaw.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Unable to reach Project Gutenberg"
                        )
                    }
                }
            )
        }
    }

    fun loadMoreGutenberg() {
        val current = _gutenbergRaw.value
        val next = current.nextUrl ?: return
        if (current.isLoading || current.isLoadingMore) return
        _gutenbergRaw.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = gutenbergRepository.fetchCatalog(pageUrl = next)
            result.fold(
                onSuccess = { page ->
                    _gutenbergRaw.update { state ->
                        val merged = (state.books + page.results).distinctBy { it.id }
                        state.copy(
                            books = merged,
                            totalCount = page.count,
                            nextUrl = page.nextUrl,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { err ->
                    _gutenbergRaw.update { it.copy(isLoadingMore = false) }
                    _eventMessage.value = err.message ?: "Failed to load more titles"
                }
            )
        }
    }

    fun downloadGutenbergBook(
        book: GutenbergBook,
        onReadyToOpen: ((BookEntity) -> Unit)? = null
    ) {
        if (book.id in _gutenbergRaw.value.downloadProgress) return
        _gutenbergRaw.update {
            it.copy(downloadProgress = it.downloadProgress + (book.id to 0.02f))
        }
        viewModelScope.launch {
            val result = gutenbergRepository.downloadBook(
                context = getApplication(),
                book = book,
                onProgress = { frac ->
                    _gutenbergRaw.update {
                        it.copy(downloadProgress = it.downloadProgress + (book.id to frac))
                    }
                }
            )
            result.fold(
                onSuccess = { downloaded ->
                    val imported = repository.importDownloadedGutenbergBook(
                        bookFile = downloaded.bookFile,
                        gutenbergBook = book,
                        coverCacheFile = downloaded.coverCacheFile
                    )
                    _gutenbergRaw.update {
                        it.copy(downloadProgress = it.downloadProgress - book.id)
                    }
                    _eventMessage.value = "Downloaded \"${imported.title}\""
                    onReadyToOpen?.invoke(imported)
                },
                onFailure = { err ->
                    _gutenbergRaw.update {
                        it.copy(downloadProgress = it.downloadProgress - book.id)
                    }
                    _eventMessage.value = err.message ?: "Failed to download ${book.title}"
                }
            )
        }
    }
}
