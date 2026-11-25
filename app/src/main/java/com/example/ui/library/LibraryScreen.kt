package com.example.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BookEntity
import com.example.data.db.BookFormat
import com.example.data.db.ReadingStatus
import com.example.data.preferences.UserPreferencesState
import com.example.theme.ContrastChecker
import com.example.theme.ShelfStyle
import com.example.ui.designsystem.GlassSwitchItem
import com.example.ui.designsystem.LiquidGlassPill
import com.example.ui.designsystem.LiquidGlassSurface
import com.example.ui.designsystem.LiquidGlassViewSwitcher
import com.example.ui.designsystem.LocalMochiWidthClass
import com.example.ui.designsystem.MochiBlobMascot
import com.example.ui.designsystem.MochiBookCover
import com.example.ui.designsystem.MochiConnectedSegments
import com.example.ui.designsystem.MochiWavyProgressBar
import com.example.ui.designsystem.MochiWidthClass
import com.example.ui.designsystem.mochiSharedBookBounds
import com.example.ui.theme.LocalMochiPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    prefs: UserPreferencesState,
    onOpenReader: (BookEntity, String) -> Unit,
    onSetTab: (LibraryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onSetSort: (SortOption) -> Unit,
    onSetFormatFilter: (BookFormat?) -> Unit,
    onSetStatusFilter: (ReadingStatus?) -> Unit,
    onSetTagFilter: (Long?) -> Unit,
    onToggleDuplicatesOnly: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onSetShelfStyle: (ShelfStyle) -> Unit,
    onNavigateFolder: (String?, String) -> Unit,
    onAddSafFolder: () -> Unit,
    onImportFile: () -> Unit,
    onRescanAll: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSwapBooks: (Long, Long) -> Unit,
    onStackBooks: (Long, Long) -> Unit,
    onUnstackBook: (Long) -> Unit,
    onUpdateStatus: (List<Long>, ReadingStatus) -> Unit,
    onHideBooks: (List<Long>, Boolean) -> Unit,
    onRemoveFromLibrary: (List<Long>) -> Unit,
    onDeleteFilesFromDisk: (List<Long>) -> Unit,
    onCreateCollection: (String, List<Long>) -> Unit,
    onAddBooksToCollection: (List<Long>, Long) -> Unit,
    onRemoveBookFromCollection: (Long, Long) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onAddTag: (Long, String) -> Unit,
    onRemoveTag: (Long, Long) -> Unit,
    onBottomBarVisibilityChange: (Boolean) -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenGutenbergCatalog: () -> Unit = {}
) {
    val palette = LocalMochiPalette.current
    val widthClass = LocalMochiWidthClass.current
    val isExpandedTablet = widthClass == MochiWidthClass.EXPANDED
    val listState = rememberLazyListState()

    // Collapse bottom bar when scrolling down; show when scrolling up or at top
    var prevIndex by remember { mutableIntStateOf(0) }
    var prevOffset by remember { mutableIntStateOf(0) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val idx = listState.firstVisibleItemIndex
        val off = listState.firstVisibleItemScrollOffset
        if (idx == 0 && off < 40) {
            onBottomBarVisibilityChange(true)
        } else if (idx > prevIndex || (idx == prevIndex && off > prevOffset + 24)) {
            onBottomBarVisibilityChange(false)
        } else if (idx < prevIndex || (idx == prevIndex && off < prevOffset - 24)) {
            onBottomBarVisibilityChange(true)
        }
        prevIndex = idx
        prevOffset = off
    }

    val isCollapsed by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 72 }
    }

    var selectedBook by remember { mutableStateOf<BookEntity?>(null) }
    var selectedStack by remember { mutableStateOf<BookStackGroup?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchCollectionDialog by remember { mutableStateOf(false) }

    val activeBook = selectedBook?.let { current ->
        state.allBooks.find { it.id == current.id } ?: current
    }
    val activeStack = selectedStack?.let { current ->
        state.shelfItems.find { it.stackId == current.stackId }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            PullToRefreshBox(
                isRefreshing = state.filter.isScanning,
                onRefresh = onRescanAll,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (widthClass == MochiWidthClass.COMPACT) 120.dp else 44.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp)
                        .align(Alignment.TopCenter)
                ) {
                // Collapsing large-title header + actions
                item(key = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Mochi",
                                    style = if (isCollapsed) {
                                        MaterialTheme.typography.headlineMedium
                                    } else {
                                        MaterialTheme.typography.displayLarge
                                    },
                                    color = Color(palette.text)
                                )
                                if (!isCollapsed) {
                                    Text(
                                        text = "${state.visibleBooks.size} book${if (state.visibleBooks.size == 1) "" else "s"} · Local & Classics",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(palette.textSecondary)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val shelfTargetStyle = if (prefs.skeuomorphicShelfEnabled) {
                                    ShelfStyle.WOODEN_SHELF
                                } else {
                                    ShelfStyle.GLASS_SHELF
                                }
                                val selectedGroupStyle = when (prefs.defaultShelfStyle) {
                                    ShelfStyle.GLASS_SHELF, ShelfStyle.WOODEN_SHELF -> shelfTargetStyle
                                    else -> prefs.defaultShelfStyle
                                }
                                val switcherItems = remember(shelfTargetStyle) {
                                    listOf(
                                        GlassSwitchItem(
                                            value = ShelfStyle.FLAT_GRID,
                                            label = "Grid",
                                            icon = Icons.Rounded.GridView,
                                            testTag = "shelf_style_grid_button"
                                        ),
                                        GlassSwitchItem(
                                            value = ShelfStyle.LIST,
                                            label = "List",
                                            icon = Icons.Rounded.ViewList,
                                            testTag = "shelf_style_list_button"
                                        ),
                                        GlassSwitchItem(
                                            value = shelfTargetStyle,
                                            label = "Shelf",
                                            icon = Icons.Rounded.Weekend,
                                            testTag = "shelf_style_shelf_button"
                                        )
                                    )
                                }
                                LiquidGlassViewSwitcher(
                                    items = switcherItems,
                                    selected = selectedGroupStyle,
                                    onSelect = onSetShelfStyle,
                                    compact = true
                                )

                                IconButton(
                                    onClick = onOpenStats,
                                    modifier = Modifier.testTag("library_stats_button")
                                ) {
                                    Icon(
                                        Icons.Rounded.BarChart,
                                        contentDescription = "Reading insights",
                                        tint = Color(palette.text)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showAddMenu = true },
                                        modifier = Modifier.testTag("add_books_menu_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Add folder or file", tint = Color(palette.text))
                                    }
                                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Browse Project Gutenberg (Free)") },
                                            leadingIcon = { Icon(Icons.Rounded.Public, contentDescription = null) },
                                            onClick = {
                                                showAddMenu = false
                                                onOpenGutenbergCatalog()
                                            },
                                            modifier = Modifier.testTag("add_menu_gutenberg")
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add Folder (SAF)") },
                                            leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                showAddMenu = false
                                                onAddSafFolder()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Import Single File") },
                                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                                            onClick = {
                                                showAddMenu = false
                                                onImportFile()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rescan Folders") },
                                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                            onClick = {
                                                showAddMenu = false
                                                onRescanAll()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Search Pill + Sort & Filter buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LiquidGlassSurface(
                                shape = CircleShape,
                                elevation = 4.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = Color(palette.textSecondary),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (state.filter.query.isEmpty()) {
                                            Text(
                                                text = "Search title, author, series…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color(palette.textSecondary),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        BasicTextField(
                                            value = state.filter.query,
                                            onValueChange = onQueryChange,
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(palette.text)
                                            ),
                                            cursorBrush = SolidColor(Color(palette.accent)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("library_search_input")
                                        )
                                    }
                                    if (state.filter.query.isNotEmpty()) {
                                        IconButton(
                                            onClick = { onQueryChange("") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Clear search",
                                                tint = Color(palette.textSecondary),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Box {
                                LiquidGlassPill(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort",
                                        tint = Color(palette.text),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    SortOption.entries.forEach { opt ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (state.filter.sortOption == opt) "✓ ${opt.label}" else opt.label,
                                                    fontWeight = if (state.filter.sortOption == opt) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                onSetSort(opt)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            LiquidGlassPill(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.testTag("filter_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = "Filters",
                                    tint = if (state.filter.formatFilter != null ||
                                        state.filter.statusFilter != null ||
                                        state.filter.showDuplicatesOnly
                                    ) Color(palette.accent) else Color(palette.text),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (state.filter.isScanning) {
                            LinearProgressIndicator(
                                color = Color(palette.accent),
                                trackColor = Color(palette.surfaceVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                // Segmented View Switch: Shelf | Folders | Collections
                item(key = "segmented_control") {
                    MochiConnectedSegments(
                        items = LibraryTab.entries,
                        selected = state.filter.tab,
                        labelProvider = { it.label },
                        testTagProvider = { "library_tab_${it.name.lowercase()}" },
                        onSelect = onSetTab,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Duplicate notice chip if duplicates exist
                if (state.duplicateHashes.isNotEmpty()) {
                    item(key = "duplicate_banner") {
                        Surface(
                            shape = CircleShape,
                            color = Color(palette.accentContainer).copy(alpha = 0.7f),
                            onClick = onToggleDuplicatesOnly,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (state.filter.showDuplicatesOnly) {
                                        "Showing duplicates · Tap to show all"
                                    } else {
                                        "${state.duplicateHashes.size} duplicate file hash found · Tap to review"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ContrastChecker.bestOnColor(Color(palette.accentContainer))
                                )
                            }
                        }
                    }
                }

                // Reading Now + Continue + Want to Read (calm hierarchy; hide horizontal cover rows in List mode so All Books appears right away)
                val showShelvesHero = state.filter.tab == LibraryTab.SHELF &&
                    state.filter.query.isBlank() &&
                    state.filter.formatFilter == null &&
                    state.filter.statusFilter == null &&
                    !state.filter.showDuplicatesOnly
                val isListView = prefs.defaultShelfStyle == ShelfStyle.LIST

                if (showShelvesHero && state.readingNowBook != null) {
                    item(key = "reading_now") {
                        ReadingNowHeroCard(
                            book = state.readingNowBook,
                            morphKey = "hero_${state.readingNowBook.id}",
                            onClick = { onOpenReader(state.readingNowBook, "hero_${state.readingNowBook.id}") },
                            onInspect = { selectedBook = state.readingNowBook },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (showShelvesHero && !isListView && state.continueShelf.isNotEmpty()) {
                    item(key = "continue_shelf") {
                        HorizontalBookRow(
                            title = "Continue",
                            rowKeyPrefix = "continue",
                            books = state.continueShelf,
                            onBookClick = { book, key -> onOpenReader(book, key) }
                        )
                    }
                }

                if (showShelvesHero && !isListView && state.wantToReadShelf.isNotEmpty()) {
                    item(key = "want_to_read_shelf") {
                        HorizontalBookRow(
                            title = "Want to Read",
                            rowKeyPrefix = "want",
                            books = state.wantToReadShelf,
                            onBookClick = { book, key -> onOpenReader(book, key) }
                        )
                    }
                }

                // Main tab content
                when (state.filter.tab) {
                    LibraryTab.SHELF -> {
                        item(key = "shelf_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "All Books",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color(palette.text)
                                    )
                                    if (!isListView) {
                                        Text(
                                            text = "${prefs.defaultShelfStyle.label} · Long-press to stack or select",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(palette.textSecondary)
                                        )
                                    }
                                }
                                Text(
                                    text = prefs.defaultShelfStyle.label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(palette.link)
                                )
                            }
                        }

                        if (state.shelfItems.isEmpty()) {
                            item(key = "empty_shelf") {
                                EmptyLibraryState(
                                    mascotEnabled = prefs.mascotEnabled,
                                    hasQuery = state.filter.query.isNotBlank(),
                                    onAddFolder = onAddSafFolder,
                                    onImportFile = onImportFile,
                                    onBrowseGutenberg = onOpenGutenbergCatalog
                                )
                            }
                        } else {
                            item(key = "shelf_grid") {
                                ShelfViewContainer(
                                    shelfStyle = prefs.defaultShelfStyle,
                                    items = state.shelfItems,
                                    selectedBookIds = state.filter.selectedBookIds,
                                    spineThicknessByPages = prefs.spineThicknessByPages,
                                    shelfBackgroundUri = prefs.shelfBackgroundUri,
                                    shelfBackgroundDim = prefs.shelfBackgroundDim,
                                    shelfBackgroundBlur = prefs.shelfBackgroundBlur,
                                    onBookClick = onOpenReader,
                                    onInspectBook = { selectedBook = it },
                                    onStackClick = { selectedStack = it },
                                    onToggleSelect = onToggleSelect,
                                    onSwapBooks = onSwapBooks,
                                    onStackBooks = onStackBooks
                                )
                            }
                        }
                    }

                    LibraryTab.FOLDERS -> {
                        item(key = "folders_view") {
                            FoldersTreeView(
                                folders = state.folders,
                                books = state.visibleBooks,
                                currentFolderUri = state.filter.currentFolderUri,
                                currentSubPath = state.filter.currentSubPath,
                                onNavigateFolder = onNavigateFolder,
                                onAddSafFolder = onAddSafFolder,
                                onImportFile = onImportFile,
                                onRemoveFolder = onRemoveFolder,
                                onBookClick = onOpenReader,
                                onInspectBook = { selectedBook = it }
                            )
                        }
                    }

                    LibraryTab.COLLECTIONS -> {
                        item(key = "collections_view") {
                            CollectionsAndSeriesView(
                                state = state,
                                onCreateCollection = { name -> onCreateCollection(name, emptyList()) },
                                onDeleteCollection = onDeleteCollection,
                                onSelectTagFilter = onSetTagFilter,
                                onBookClick = onOpenReader
                            )
                        }
                    }
                }
            }
        }

        // Batch selection floating glass bar
        androidx.compose.animation.AnimatedVisibility(
            visible = state.filter.selectedBookIds.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            val selectedIds = state.filter.selectedBookIds.toList()
            LiquidGlassSurface(shape = CircleShape, elevation = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${selectedIds.size} selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(palette.text),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                    Row {
                        if (selectedIds.size == 1) {
                            IconButton(onClick = {
                                selectedBook = state.allBooks.find { it.id == selectedIds.first() }
                                onClearSelection()
                            }) {
                                Icon(Icons.Rounded.Info, contentDescription = "Book details", tint = Color(palette.text))
                            }
                        }
                        IconButton(onClick = { showBatchCollectionDialog = true }) {
                            Icon(Icons.Rounded.CollectionsBookmark, contentDescription = "Add to collection", tint = Color(palette.text))
                        }
                        IconButton(onClick = { onUpdateStatus(selectedIds, ReadingStatus.FINISHED) }) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = "Mark finished", tint = Color(palette.text))
                        }
                        IconButton(onClick = { onHideBooks(selectedIds, true) }) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide", tint = Color(palette.text))
                        }
                        IconButton(onClick = { onRemoveFromLibrary(selectedIds) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove from library", tint = Color(palette.text))
                        }
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel selection", tint = Color(palette.text))
                        }
                    }
                }
            }
        }
        }

        // Tablet Supporting Pane (right-hand book inspector)
        if (isExpandedTablet) {
            val inspected = activeBook ?: state.readingNowBook ?: state.visibleBooks.firstOrNull()
            BookInspectorPane(
                book = inspected,
                allBooks = state.allBooks,
                collections = state.collections,
                collectionRefs = state.collectionRefs,
                tags = state.tags,
                tagRefs = state.tagRefs,
                duplicateHashes = state.duplicateHashes,
                onOpenReader = { book -> onOpenReader(book, "") },
                onUpdateStatus = { bookId, status -> onUpdateStatus(listOf(bookId), status) },
                onToggleCollection = { bookId, colId, add ->
                    if (add) onAddBooksToCollection(listOf(bookId), colId)
                    else onRemoveBookFromCollection(bookId, colId)
                },
                onAddTag = onAddTag,
                onRemoveTag = onRemoveTag,
                onStackOnto = onStackBooks,
                onUnstack = onUnstackBook,
                onHideBook = { b ->
                    onHideBooks(listOf(b.id), !b.hidden)
                    if (selectedBook?.id == b.id) selectedBook = null
                },
                onRemoveFromLibrary = { id ->
                    onRemoveFromLibrary(listOf(id))
                    if (selectedBook?.id == id) selectedBook = null
                },
                onDeleteFileFromDisk = { id ->
                    onDeleteFilesFromDisk(listOf(id))
                    if (selectedBook?.id == id) selectedBook = null
                },
                modifier = Modifier.width(340.dp)
            )
        }
    }

    if (!isExpandedTablet && activeBook != null) {
        BookDetailSheet(
            book = activeBook,
            allBooks = state.allBooks,
            collections = state.collections,
            collectionRefs = state.collectionRefs,
            tags = state.tags,
            tagRefs = state.tagRefs,
            isDuplicate = activeBook.contentHash in state.duplicateHashes,
            onDismiss = { selectedBook = null },
            onOpenReader = { onOpenReader(activeBook, "") },
            onUpdateStatus = { status -> onUpdateStatus(listOf(activeBook.id), status) },
            onToggleCollection = { colId, add ->
                if (add) onAddBooksToCollection(listOf(activeBook.id), colId)
                else onRemoveBookFromCollection(activeBook.id, colId)
            },
            onAddTag = { tag -> onAddTag(activeBook.id, tag) },
            onRemoveTag = { tagId -> onRemoveTag(activeBook.id, tagId) },
            onStackOnto = { targetId -> onStackBooks(activeBook.id, targetId) },
            onUnstack = { onUnstackBook(activeBook.id) },
            onHideBook = {
                onHideBooks(listOf(activeBook.id), !activeBook.hidden)
                selectedBook = null
            },
            onRemoveFromLibrary = {
                onRemoveFromLibrary(listOf(activeBook.id))
                selectedBook = null
            },
            onDeleteFileFromDisk = { onDeleteFilesFromDisk(listOf(activeBook.id)) }
        )
    }

    if (activeStack != null) {
        StackDetailSheet(
            stack = activeStack,
            onDismiss = { selectedStack = null },
            onOpenBook = onOpenReader,
            onInspectBook = { selectedBook = it },
            onUnstackBook = onUnstackBook
        )
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = Color(palette.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Filter Library", style = MaterialTheme.typography.headlineMedium, color = Color(palette.text))
                Text("Format", style = MaterialTheme.typography.titleSmall, color = Color(palette.textSecondary))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter.formatFilter == null,
                        onClick = { onSetFormatFilter(null) },
                        label = { Text("All") }
                    )
                    listOf(BookFormat.EPUB, BookFormat.PDF, BookFormat.CBZ, BookFormat.MD, BookFormat.TXT).forEach { fmt ->
                        FilterChip(
                            selected = state.filter.formatFilter == fmt,
                            onClick = { onSetFormatFilter(if (state.filter.formatFilter == fmt) null else fmt) },
                            label = { Text(fmt.displayName) }
                        )
                    }
                }
                Text("Reading Status", style = MaterialTheme.typography.titleSmall, color = Color(palette.textSecondary))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.filter.statusFilter == null,
                        onClick = { onSetStatusFilter(null) },
                        label = { Text("All") }
                    )
                    ReadingStatus.entries.forEach { st ->
                        FilterChip(
                            selected = state.filter.statusFilter == st,
                            onClick = { onSetStatusFilter(if (state.filter.statusFilter == st) null else st) },
                            label = { Text(st.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.filter.showDuplicatesOnly,
                        onClick = onToggleDuplicatesOnly,
                        label = { Text("Duplicates only") }
                    )
                    FilterChip(
                        selected = state.filter.showHidden,
                        onClick = onToggleShowHidden,
                        label = { Text("Include hidden") }
                    )
                }
            }
        }
    }

    if (showBatchCollectionDialog) {
        val selectedIds = state.filter.selectedBookIds.toList()
        AlertDialog(
            onDismissRequest = { showBatchCollectionDialog = false },
            title = { Text("Add ${selectedIds.size} books to collection") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.collections.isEmpty()) {
                        Text("Create a collection in the Collections tab first.")
                    } else {
                        state.collections.forEach { col ->
                            TextButton(
                                onClick = {
                                    onAddBooksToCollection(selectedIds, col.id)
                                    showBatchCollectionDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(col.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBatchCollectionDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ReadingNowHeroCard(
    book: BookEntity,
    morphKey: String,
    onClick: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMochiPalette.current
    val progressPct = (book.progress * 100).roundToInt()

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(palette.surface),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .testTag("reading_now_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(palette.accentContainer).copy(alpha = 0.35f),
                            Color(palette.surface)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MochiBookCover(
                    book = book,
                    cornerRadius = 14.dp,
                    modifier = Modifier
                        .width(92.dp)
                        .aspectRatio(0.68f)
                        .mochiSharedBookBounds(morphKey)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "READING NOW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(palette.link)
                    )
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(palette.text),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.authors,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    MochiWavyProgressBar(progress = book.progress)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (progressPct > 0) "$progressPct% complete" else "Ready to begin",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(palette.textSecondary)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(palette.surfaceVariant),
                                onClick = onInspect,
                                modifier = Modifier.testTag("reading_now_details_button")
                            ) {
                                Text(
                                    text = "Details",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(palette.text),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = Color(palette.accent),
                                onClick = onClick
                            ) {
                                Text(
                                    text = if (progressPct > 0) "Continue" else "Read",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ContrastChecker.bestOnColor(Color(palette.accent)),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
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
private fun HorizontalBookRow(
    title: String,
    rowKeyPrefix: String,
    books: List<BookEntity>,
    onBookClick: (BookEntity, String) -> Unit
) {
    val palette = LocalMochiPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color(palette.text),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(books, key = { it.id }) { book ->
                val morphKey = "${rowKeyPrefix}_${book.id}"
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onBookClick(book, morphKey) }
                ) {
                    MochiBookCover(
                        book = book,
                        cornerRadius = 12.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.68f)
                            .mochiSharedBookBounds(morphKey)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = book.authors,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(palette.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    mascotEnabled: Boolean,
    hasQuery: Boolean,
    onAddFolder: () -> Unit,
    onImportFile: () -> Unit,
    onBrowseGutenberg: () -> Unit
) {
    val palette = LocalMochiPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (mascotEnabled) {
            MochiBlobMascot(size = 96.dp)
        }
        Text(
            text = if (hasQuery) "No matching books" else "Your shelf is ready",
            style = MaterialTheme.typography.titleLarge,
            color = Color(palette.text)
        )
        Text(
            text = if (hasQuery) "Try clearing a filter or searching by author."
            else "Import your local EPUB, PDF, CBZ, or Markdown files — or download free public-domain classics from Project Gutenberg.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(palette.textSecondary)
        )
        if (!hasQuery) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onBrowseGutenberg,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(palette.accent),
                    contentColor = ContrastChecker.bestOnColor(Color(palette.accent))
                ),
                modifier = Modifier.testTag("empty_browse_gutenberg_button")
            ) {
                Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Free Classics (Project Gutenberg)")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onAddFolder,
                    shape = CircleShape,
                    modifier = Modifier.testTag("empty_add_folder_button")
                ) {
                    Text("Add Books Folder")
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = onImportFile,
                    shape = CircleShape,
                    modifier = Modifier.testTag("empty_import_file_button")
                ) {
                    Text("Import File")
                }
            }
        }
    }
}
