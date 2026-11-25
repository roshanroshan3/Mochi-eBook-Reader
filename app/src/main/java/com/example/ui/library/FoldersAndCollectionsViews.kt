package com.example.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMosaic
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.BookEntity
import com.example.data.db.FolderEntity
import com.example.theme.ContrastChecker
import com.example.ui.designsystem.MochiBookCover
import com.example.ui.designsystem.mochiSharedBookBounds
import com.example.ui.theme.LocalMochiPalette

@Composable
fun FoldersTreeView(
    folders: List<FolderEntity>,
    books: List<BookEntity>,
    currentFolderUri: String?,
    currentSubPath: String,
    onNavigateFolder: (String?, String) -> Unit,
    onAddSafFolder: () -> Unit,
    onImportFile: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onBookClick: (BookEntity, String) -> Unit,
    onInspectBook: (BookEntity) -> Unit
) {
    val palette = LocalMochiPalette.current
    val currentFolder = folders.find { it.treeUri == currentFolderUri }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick SAF actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onAddSafFolder,
                modifier = Modifier
                    .weight(1f)
                    .testTag("add_saf_folder_button")
            ) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Folder")
            }
            OutlinedButton(
                onClick = onImportFile,
                modifier = Modifier
                    .weight(1f)
                    .testTag("import_single_file_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import File")
            }
        }

        // Breadcrumbs bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(14.dp))
                .background(Color(palette.surfaceVariant).copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Root",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (currentFolderUri == null) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (currentFolderUri == null) Color(palette.link) else Color(palette.textSecondary),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onNavigateFolder(null, "") }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            if (currentFolder != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(palette.textSecondary),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = currentFolder.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (currentSubPath.isEmpty()) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (currentSubPath.isEmpty()) Color(palette.link) else Color(palette.textSecondary),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onNavigateFolder(currentFolder.treeUri, "") }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (currentSubPath.isNotEmpty()) {
                    val parts = currentSubPath.split("/").filter { it.isNotBlank() }
                    var running = ""
                    parts.forEachIndexed { idx, segment ->
                        running = if (running.isEmpty()) segment else "$running/$segment"
                        val targetPath = running
                        val isLast = idx == parts.lastIndex
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(palette.textSecondary),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = segment,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isLast) Color(palette.link) else Color(palette.textSecondary),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onNavigateFolder(currentFolder.treeUri, targetPath) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (currentFolderUri == null) {
            // Root SAF folders list
            folders.forEach { folder ->
                val count = books.count { it.folderTreeUri == folder.treeUri }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onNavigateFolder(folder.treeUri, "") }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(palette.accentContainer)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = ContrastChecker.bestOnColor(Color(palette.accentContainer))
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(palette.text)
                            )
                            Text(
                                text = "$count book${if (count == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(palette.textSecondary)
                            )
                        }
                        IconButton(onClick = { onRemoveFolder(folder.treeUri) }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Remove folder",
                                tint = Color(palette.textSecondary)
                            )
                        }
                    }
                }
            }

            // Show individually imported books if any
            val importedSingles = books.filter { it.folderTreeUri == "imported://documents" }
            if (importedSingles.isNotEmpty()) {
                Text(
                    text = "Imported Files",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(palette.textSecondary)
                )
                importedSingles.forEach { book ->
                    val morphKey = "folder_imp_${book.id}"
                    FolderBookRow(
                        book = book,
                        morphKey = morphKey,
                        onClick = { onBookClick(book, morphKey) },
                        onInspect = { onInspectBook(book) }
                    )
                }
            }
        } else {
            // Inside a folder: show immediate child subfolders + immediate files
            val folderBooks = books.filter { it.folderTreeUri == currentFolderUri }
            val prefix = if (currentSubPath.isEmpty()) "" else "$currentSubPath/"
            val childFolders = folderBooks
                .mapNotNull { book ->
                    val rel = book.relativeFolderPath
                    if (rel.startsWith(prefix) && rel.length > prefix.length) {
                        rel.removePrefix(prefix).substringBefore('/')
                    } else null
                }
                .distinct()
                .sorted()

            val directBooks = folderBooks.filter { it.relativeFolderPath == currentSubPath }

            childFolders.forEach { subName ->
                val nextPath = if (currentSubPath.isEmpty()) subName else "$currentSubPath/$subName"
                val nestedCount = folderBooks.count {
                    it.relativeFolderPath == nextPath || it.relativeFolderPath.startsWith("$nextPath/")
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onNavigateFolder(currentFolderUri, nextPath) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = Color(palette.accent),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subName, style = MaterialTheme.typography.titleSmall, color = Color(palette.text))
                            Text("$nestedCount item(s)", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(palette.textSecondary)
                        )
                    }
                }
            }

            directBooks.forEach { book ->
                val morphKey = "folder_${book.id}"
                FolderBookRow(
                    book = book,
                    morphKey = morphKey,
                    onClick = { onBookClick(book, morphKey) },
                    onInspect = { onInspectBook(book) }
                )
            }
        }
    }
}

@Composable
private fun FolderBookRow(
    book: BookEntity,
    morphKey: String,
    onClick: () -> Unit,
    onInspect: () -> Unit
) {
    val palette = LocalMochiPalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(palette.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MochiBookCover(
                book = book,
                cornerRadius = 8.dp,
                modifier = Modifier
                    .width(42.dp)
                    .aspectRatio(0.7f)
                    .mochiSharedBookBounds(morphKey)
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
                    text = "${book.format.displayName} · ${book.authors}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(palette.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onInspect) {
                Icon(Icons.Rounded.Info, contentDescription = "Book details", tint = Color(palette.textSecondary))
            }
        }
    }
}

@Composable
fun CollectionsAndSeriesView(
    state: LibraryUiState,
    onCreateCollection: (String) -> Unit,
    onDeleteCollection: (Long) -> Unit,
    onSelectTagFilter: (Long?) -> Unit,
    onBookClick: (BookEntity, String) -> Unit
) {
    val palette = LocalMochiPalette.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    val seriesGroups = remember(state.visibleBooks) {
        state.visibleBooks
            .filter { !it.series.isNullOrBlank() }
            .groupBy { it.series!! }
            .toSortedMap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Collections header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Collections",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )
            FilledTonalButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("new_collection_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
        }

        if (state.collections.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(palette.surfaceVariant).copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CollectionsBookmark,
                        contentDescription = null,
                        tint = Color(palette.accent)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No custom collections yet. Create one or long-press books to group them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(palette.textSecondary)
                    )
                }
            }
        } else {
            state.collections.forEach { col ->
                val memberIds = state.collectionRefs.filter { it.collectionId == col.id }.map { it.bookId }.toSet()
                val memberBooks = state.visibleBooks.filter { it.id in memberIds }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(col.name, style = MaterialTheme.typography.titleMedium, color = Color(palette.text))
                                Text("${memberBooks.size} book(s)", style = MaterialTheme.typography.bodySmall, color = Color(palette.textSecondary))
                            }
                            IconButton(onClick = { onDeleteCollection(col.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete collection", tint = Color(palette.textSecondary))
                            }
                        }
                        if (memberBooks.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                memberBooks.forEach { book ->
                                    val morphKey = "col_${col.id}_${book.id}"
                                    Column(
                                        modifier = Modifier
                                            .width(74.dp)
                                            .clickable { onBookClick(book, morphKey) }
                                    ) {
                                        MochiBookCover(
                                            book = book,
                                            cornerRadius = 10.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.68f)
                                                .mochiSharedBookBounds(morphKey)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = book.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(palette.text),
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
        }

        // Tags
        if (state.tags.isNotEmpty()) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.tags.forEach { tag ->
                    val selected = state.filter.tagIdFilter == tag.id
                    Surface(
                        shape = CircleShape,
                        color = if (selected) Color(palette.accentContainer) else Color(palette.surfaceVariant),
                        onClick = { onSelectTagFilter(if (selected) null else tag.id) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Tag, contentDescription = null, modifier = Modifier.size(14.dp))
                            Text(tag.name, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Automatic Series
        if (seriesGroups.isNotEmpty()) {
            Text(
                text = "Automatic Series",
                style = MaterialTheme.typography.titleLarge,
                color = Color(palette.text)
            )
            seriesGroups.forEach { (seriesName, booksInSeries) ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(palette.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(palette.textSecondary).copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesomeMosaic,
                                contentDescription = null,
                                tint = Color(palette.accent),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = seriesName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(palette.text)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            booksInSeries.sortedBy { it.seriesIndex ?: 0f }.forEach { book ->
                                val morphKey = "series_${book.id}"
                                Column(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .clickable { onBookClick(book, morphKey) }
                                ) {
                                    MochiBookCover(
                                        book = book,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.68f)
                                            .mochiSharedBookBounds(morphKey)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(palette.text),
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
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Collection") },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    label = { Text("Collection name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateCollection(newCollectionName)
                        newCollectionName = ""
                        showCreateDialog = false
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}
