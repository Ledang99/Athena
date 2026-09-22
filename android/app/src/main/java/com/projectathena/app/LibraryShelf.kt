package com.projectathena.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectathena.app.data.Book
import com.projectathena.app.data.BookCategory
import com.projectathena.app.data.DuplicateKind
import com.projectathena.app.data.EbookRepository
import com.projectathena.app.data.LibraryCollection
import com.projectathena.app.data.LibrarySort
import com.projectathena.app.data.ReadingStatus
import com.projectathena.app.data.collectionLabels
import com.projectathena.app.data.sortBooks
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class FileFilter(val label: String) {
    ALL("All formats"),
    PDF("PDF"),
    EPUB("EPUB"),
}

private sealed class ShelfSection {
    data object AllBooks : ShelfSection()
    data class Folder(val uri: String, val label: String) : ShelfSection()
    data class Status(val status: ReadingStatus) : ShelfSection()
    data class Collection(val id: String, val label: String) : ShelfSection()

    val title: String
        get() = when (this) {
            AllBooks -> "All Books"
            is Folder -> label
            is Status -> status.label
            is Collection -> label
        }
}

@Composable
fun LibraryShelfScreen(
    state: AthenaUiState,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onRescanAll: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onOpenBook: (Book) -> Unit,
    onOpenDossier: (Book) -> Unit,
    onUpdateOrganization: (Book, String, String?, List<String>, List<String>, ReadingStatus) -> Unit,
    onUpdateReadingStatus: (Book, ReadingStatus) -> Unit,
    onSetViewMode: (LibraryViewMode) -> Unit,
    onToggleTheme: () -> Unit,
    onAddCategory: (String) -> Unit = {},
    onUpdateCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCategory: (String) -> Unit = {},
) {
    var sectionKey by rememberSaveable { mutableStateOf("all") }
    var search by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var fileFilter by rememberSaveable { mutableStateOf(FileFilter.ALL.name) }
    var sort by rememberSaveable { mutableStateOf(LibrarySort.RECENTLY_OPENED.name) }
    var filterOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var sectionMenuOpen by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<Book?>(null) }

    val selectedFileFilter = FileFilter.entries.firstOrNull { it.name == fileFilter } ?: FileFilter.ALL
    val librarySort = LibrarySort.entries.firstOrNull { it.name == sort } ?: LibrarySort.RECENTLY_OPENED

    val section = remember(sectionKey, state.libraryFolders, state.books) {
        resolveSection(sectionKey, state)
    }

    val duplicateKinds = remember(state.duplicateGroups) {
        state.duplicateGroups
            .flatMap { group -> group.copies.map { book -> book.id to group.kind } }
            .toMap()
    }

    val visibleBooks = remember(
        state.books,
        section,
        search,
        selectedFileFilter,
        librarySort,
    ) {
        val query = search.trim()
        val filtered = state.books.filter { book ->
            val matchesSection = when (section) {
                ShelfSection.AllBooks -> true
                is ShelfSection.Folder -> book.sourceFolder == section.uri
                is ShelfSection.Status -> book.readingStatus == section.status
                is ShelfSection.Collection -> section.id in book.collections
            }
            val matchesSearch = query.isBlank() ||
                book.title.contains(query, ignoreCase = true) ||
                book.author.orEmpty().contains(query, ignoreCase = true) ||
                book.tags.any { it.contains(query, ignoreCase = true) }
            val matchesType = when (selectedFileFilter) {
                FileFilter.ALL -> true
                FileFilter.PDF -> book.mimeType == EbookRepository.PDF_MIME
                FileFilter.EPUB -> book.mimeType == EbookRepository.EPUB_MIME
            }
            matchesSection && matchesSearch && matchesType
        }
        sortBooks(filtered, librarySort)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LibraryTopLiner(
            sectionTitle = section.title,
            bookCount = visibleBooks.size,
            viewMode = state.viewMode,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            sectionMenuOpen = sectionMenuOpen,
            onSectionMenuOpenChange = { sectionMenuOpen = it },
            overflowOpen = overflowOpen,
            onOverflowOpenChange = { overflowOpen = it },
            folders = state.libraryFolders,
            categories = state.categories,
            onSelectSection = { key ->
                sectionKey = key
                sectionMenuOpen = false
            },
            onAddFolder = {
                menuOpen = false
                onAddFolder()
            },
            onImportFiles = {
                menuOpen = false
                onImportFiles()
            },
            onRescanAll = {
                menuOpen = false
                onRescanAll()
            },
            onRemoveFolder = { uri ->
                menuOpen = false
                onRemoveFolder(uri)
            },
            onSearch = { searchOpen = true },
            onFilter = { filterOpen = true },
            onSetViewMode = onSetViewMode,
            onToggleTheme = onToggleTheme,
        )

        if (searchOpen || search.isNotBlank()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                label = { Text("Search") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    TextButton(
                        onClick = {
                            search = ""
                            searchOpen = false
                        },
                    ) {
                        Text("Clear")
                    }
                },
            )
        }

        if (visibleBooks.isEmpty()) {
            EmptyShelf(
                hasBooks = state.books.isNotEmpty(),
                hasFolders = state.libraryFolders.isNotEmpty(),
                onAddFolder = onAddFolder,
                onImportFiles = onImportFiles,
            )
        } else when (state.viewMode) {
            LibraryViewMode.TILES -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visibleBooks, key = { it.id }) { book ->
                        BookTile(
                            book = book,
                            duplicateKind = duplicateKinds[book.id],
                            onOpenBook = onOpenBook,
                            onOpenDossier = onOpenDossier,
                            onEdit = { editingBook = book },
                            onUpdateReadingStatus = onUpdateReadingStatus,
                        )
                    }
                }
            }
            LibraryViewMode.DETAILS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleBooks, key = { it.id }) { book ->
                        BookDetailRow(
                            book = book,
                            duplicateKind = duplicateKinds[book.id],
                            onOpenBook = onOpenBook,
                            onOpenDossier = onOpenDossier,
                            onEdit = { editingBook = book },
                            onUpdateReadingStatus = onUpdateReadingStatus,
                        )
                    }
                }
            }
        }
    }

    if (filterOpen) {
        FilterDialog(
            fileFilter = selectedFileFilter,
            sort = librarySort,
            onDismiss = { filterOpen = false },
            onApply = { nextFilter, nextSort ->
                fileFilter = nextFilter.name
                sort = nextSort.name
                filterOpen = false
            },
        )
    }

    editingBook?.let { book ->
        EditBookDialog(
            book = book,
            categories = state.categories,
            onAddCategory = onAddCategory,
            onUpdateCategory = onUpdateCategory,
            onDeleteCategory = onDeleteCategory,
            onDismiss = { editingBook = null },
            onSave = { title, author, tags, collections, status ->
                onUpdateOrganization(book, title, author, tags, collections, status)
                editingBook = null
            },
        )
    }
}

@Composable
private fun LibraryTopLiner(
    sectionTitle: String,
    bookCount: Int,
    viewMode: LibraryViewMode,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    sectionMenuOpen: Boolean,
    onSectionMenuOpenChange: (Boolean) -> Unit,
    overflowOpen: Boolean,
    onOverflowOpenChange: (Boolean) -> Unit,
    folders: List<LibraryFolder>,
    categories: List<BookCategory> = emptyList(),
    onSelectSection: (String) -> Unit,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onRescanAll: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onSearch: () -> Unit,
    onFilter: () -> Unit,
    onSetViewMode: (LibraryViewMode) -> Unit,
    onToggleTheme: () -> Unit,
) {
    Surface(
        color = Color(0xFF121212),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                LinerIconButton(label = "≡", onClick = { onMenuOpenChange(true) })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { onMenuOpenChange(false) }) {
                    DropdownMenuItem(
                        text = { Text("Add folder") },
                        onClick = onAddFolder,
                    )
                    DropdownMenuItem(
                        text = { Text("Import files") },
                        onClick = onImportFiles,
                    )
                    DropdownMenuItem(
                        text = { Text("Rescan all folders") },
                        onClick = onRescanAll,
                    )
                    if (folders.isNotEmpty()) {
                        HorizontalDivider()
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text("Remove “${folder.label}”") },
                                onClick = { onRemoveFolder(folder.uri) },
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSectionMenuOpenChange(true) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            sectionTitle,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "$bookCount",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                        )
                    }
                    Text(" ▾", color = Color.White.copy(alpha = 0.8f))
                }
                DropdownMenu(
                    expanded = sectionMenuOpen,
                    onDismissRequest = { onSectionMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("All Books") },
                        onClick = { onSelectSection("all") },
                    )
                    DropdownMenuItem(
                        text = { Text("Unread") },
                        onClick = { onSelectSection("status:unread") },
                    )
                    DropdownMenuItem(
                        text = { Text("Reading") },
                        onClick = { onSelectSection("status:reading") },
                    )
                    DropdownMenuItem(
                        text = { Text("Finished") },
                        onClick = { onSelectSection("status:finished") },
                    )
                    if (folders.isNotEmpty()) {
                        HorizontalDivider()
                        folders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder.label) },
                                onClick = { onSelectSection("folder:${folder.uri}") },
                            )
                        }
                    }
                    HorizontalDivider()
                    val catItems = if (categories.isNotEmpty()) {
                        categories
                    } else {
                        LibraryCollection.entries.map { BookCategory(it.id, it.label) }
                    }
                    catItems.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.label) },
                            onClick = { onSelectSection("collection:${cat.id}") },
                        )
                    }
                }
            }

            LinerIconButton(label = "⌕", onClick = onSearch)
            LinerIconButton(label = "▤", onClick = onFilter)

            Box {
                LinerIconButton(label = "⋮", onClick = { onOverflowOpenChange(true) })
                DropdownMenu(
                    expanded = overflowOpen,
                    onDismissRequest = { onOverflowOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (viewMode == LibraryViewMode.TILES) {
                                    "Tiles view ✓"
                                } else {
                                    "Tiles view"
                                },
                            )
                        },
                        onClick = {
                            onSetViewMode(LibraryViewMode.TILES)
                            onOverflowOpenChange(false)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (viewMode == LibraryViewMode.DETAILS) {
                                    "Details view ✓"
                                } else {
                                    "Details view"
                                },
                            )
                        },
                        onClick = {
                            onSetViewMode(LibraryViewMode.DETAILS)
                            onOverflowOpenChange(false)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Toggle day / dark") },
                        onClick = {
                            onToggleTheme()
                            onOverflowOpenChange(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinerIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BookTile(
    book: Book,
    duplicateKind: DuplicateKind?,
    onOpenBook: (Book) -> Unit,
    onOpenDossier: (Book) -> Unit,
    onEdit: () -> Unit,
    onUpdateReadingStatus: (Book, ReadingStatus) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onOpenDossier(book) },
        ) {
            ShelfCover(book = book, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                        ),
                    ),
            )
            Text(
                book.readingStatus.label.take(1),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Box(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    "⋮",
                    modifier = Modifier
                        .clickable { menuOpen = true }
                        .padding(6.dp),
                    color = Color.White,
                    fontSize = 16.sp,
                )
                BookOverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    book = book,
                    onOpenBook = onOpenBook,
                    onOpenDossier = onOpenDossier,
                    onEdit = onEdit,
                    onUpdateReadingStatus = onUpdateReadingStatus,
                )
            }
            if (duplicateKind != null) {
                Text(
                    if (duplicateKind == DuplicateKind.EXACT) "Dup" else "≈",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 9.sp,
                )
            }
        }
        Text(
            book.title,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookDetailRow(
    book: Book,
    duplicateKind: DuplicateKind?,
    onOpenBook: (Book) -> Unit,
    onOpenDossier: (Book) -> Unit,
    onEdit: () -> Unit,
    onUpdateReadingStatus: (Book, ReadingStatus) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { onOpenDossier(book) },
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShelfCover(
                book = book,
                modifier = Modifier
                    .width(52.dp)
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    book.author ?: "Unknown author",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(
                        book.readingStatus.label,
                        book.folderName,
                        book.collectionLabels().firstOrNull(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (duplicateKind != null) {
                    Text(
                        if (duplicateKind == DuplicateKind.EXACT) "Exact duplicate" else "Likely match",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                BookOverflowMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    book = book,
                    onOpenBook = onOpenBook,
                    onOpenDossier = onOpenDossier,
                    onEdit = onEdit,
                    onUpdateReadingStatus = onUpdateReadingStatus,
                )
            }
        }
    }
}

@Composable
private fun BookOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    book: Book,
    onOpenBook: (Book) -> Unit,
    onOpenDossier: (Book) -> Unit,
    onEdit: () -> Unit,
    onUpdateReadingStatus: (Book, ReadingStatus) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Open reader") },
            onClick = {
                onDismiss()
                onOpenBook(book)
            },
        )
        DropdownMenuItem(
            text = { Text("Mind maps & notes") },
            onClick = {
                onDismiss()
                onOpenDossier(book)
            },
        )
        DropdownMenuItem(
            text = { Text("Edit details") },
            onClick = {
                onDismiss()
                onEdit()
            },
        )
        HorizontalDivider()
        ReadingStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = {
                    Text(
                        if (book.readingStatus == status) {
                            "${status.label} ✓"
                        } else {
                            status.label
                        },
                    )
                },
                onClick = {
                    onDismiss()
                    onUpdateReadingStatus(book, status)
                },
            )
        }
    }
}

@Composable
private fun ShelfCover(book: Book, modifier: Modifier = Modifier) {
    val cover by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.asImageBitmap()
        }
    }
    if (cover != null) {
        Image(
            bitmap = checkNotNull(cover),
            contentDescription = book.title,
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                book.title.take(1).uppercase(),
                color = Color(0xFFD6F06F),
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
        }
    }
}

@Composable
private fun EmptyShelf(
    hasBooks: Boolean,
    hasFolders: Boolean,
    onAddFolder: () -> Unit,
    onImportFiles: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                hasBooks -> "No books in this section"
                hasFolders -> "No ebooks indexed yet"
                else -> "Add your first library folder"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            when {
                hasBooks -> "Try another section, clear search, or change filters."
                hasFolders -> "Use Rescan all folders from the menu."
                else -> "Link one or more ebook folders, or import PDF/EPUB files."
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!hasFolders) {
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = onAddFolder) { Text("Add folder") }
                TextButton(onClick = onImportFiles) { Text("Import files") }
            }
        }
    }
}

@Composable
private fun FilterDialog(
    fileFilter: FileFilter,
    sort: LibrarySort,
    onDismiss: () -> Unit,
    onApply: (FileFilter, LibrarySort) -> Unit,
) {
    var nextFilter by remember { mutableStateOf(fileFilter) }
    var nextSort by remember { mutableStateOf(sort) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter & sort") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Format", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FileFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = nextFilter == filter,
                            onClick = { nextFilter = filter },
                            label = { Text(filter.label) },
                        )
                    }
                }
                Text("Sort", fontWeight = FontWeight.SemiBold)
                LibrarySort.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = nextSort == option,
                                onClick = { nextSort = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(nextFilter, nextSort) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditBookDialog(
    book: Book,
    categories: List<BookCategory> = emptyList(),
    onAddCategory: (String) -> Unit = {},
    onUpdateCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCategory: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (String, String?, List<String>, List<String>, ReadingStatus) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    var tagsText by remember(book.id) { mutableStateOf(book.tags.joinToString(", ")) }
    var selectedCollections by remember(book.id) { mutableStateOf(book.collections.toSet()) }
    var readingStatus by remember(book.id) { mutableStateOf(book.readingStatus) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var showAddCategory by remember { mutableStateOf(false) }
    var editingCategoryInDialog by remember { mutableStateOf<BookCategory?>(null) }
    var editCategoryInDialogText by remember { mutableStateOf("") }

    val allCategories = remember(categories) {
        if (categories.isNotEmpty()) {
            categories
        } else {
            LibraryCollection.entries.map { BookCategory(it.id, it.label) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book details") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Title") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Author") },
                        singleLine = true,
                    )
                }
                item {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Tags (comma separated)") },
                    )
                }
                item {
                    Text("Reading status", fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.padding(top = 4.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = statusDropdownExpanded,
                            onExpandedChange = { statusDropdownExpanded = it },
                        ) {
                            OutlinedTextField(
                                value = readingStatus.label,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                label = { Text("Status") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = statusDropdownExpanded,
                                    )
                                },
                                singleLine = true,
                            )
                            ExposedDropdownMenu(
                                expanded = statusDropdownExpanded,
                                onDismissRequest = { statusDropdownExpanded = false },
                            ) {
                                ReadingStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (readingStatus == status) {
                                                    "${status.label} ✓"
                                                } else {
                                                    status.label
                                                },
                                            )
                                        },
                                        onClick = {
                                            readingStatus = status
                                            statusDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Categories", fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showAddCategory = !showAddCategory }) {
                            Text(if (showAddCategory) "Done" else "+ Add Category")
                        }
                    }
                    if (showAddCategory) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newCategoryText,
                                onValueChange = { newCategoryText = it },
                                placeholder = { Text("New category name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    if (newCategoryText.isNotBlank()) {
                                        onAddCategory(newCategoryText.trim())
                                        newCategoryText = ""
                                    }
                                },
                                enabled = newCategoryText.isNotBlank(),
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
                items(allCategories.chunked(2)) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { category ->
                            FilterChip(
                                selected = category.id in selectedCollections,
                                onClick = {
                                    selectedCollections = if (category.id in selectedCollections) {
                                        selectedCollections - category.id
                                    } else {
                                        selectedCollections + category.id
                                    }
                                },
                                label = { Text(category.label) },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "✎",
                                            modifier = Modifier
                                                .padding(start = 2.dp, end = 2.dp)
                                                .clickable {
                                                    editingCategoryInDialog = category
                                                    editCategoryInDialogText = category.label
                                                },
                                            fontSize = 12.sp,
                                        )
                                        Text(
                                            "×",
                                            modifier = Modifier
                                                .padding(start = 2.dp)
                                                .clickable {
                                                    onDeleteCategory(category.id)
                                                    selectedCollections = selectedCollections - category.id
                                                },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagsText.split(',', ';')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    onSave(
                        title,
                        author.ifBlank { null },
                        tags,
                        selectedCollections.toList(),
                        readingStatus,
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    editingCategoryInDialog?.let { cat ->
        AlertDialog(
            onDismissRequest = { editingCategoryInDialog = null },
            title = { Text("Edit category") },
            text = {
                OutlinedTextField(
                    value = editCategoryInDialogText,
                    onValueChange = { editCategoryInDialogText = it },
                    label = { Text("Category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editCategoryInDialogText.isNotBlank()) {
                            onUpdateCategory(cat.id, editCategoryInDialogText.trim())
                            editingCategoryInDialog = null
                        }
                    },
                    enabled = editCategoryInDialogText.isNotBlank(),
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCategoryInDialog = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun resolveSection(key: String, state: AthenaUiState): ShelfSection = when {
    key == "all" -> ShelfSection.AllBooks
    key == "status:unread" -> ShelfSection.Status(ReadingStatus.UNREAD)
    key == "status:reading" -> ShelfSection.Status(ReadingStatus.READING)
    key == "status:finished" -> ShelfSection.Status(ReadingStatus.FINISHED)
    key.startsWith("folder:") -> {
        val uri = key.removePrefix("folder:")
        val folder = state.libraryFolders.firstOrNull { it.uri == uri }
        if (folder != null) {
            ShelfSection.Folder(folder.uri, folder.label)
        } else {
            ShelfSection.AllBooks
        }
    }
    key.startsWith("collection:") -> {
        val id = key.removePrefix("collection:")
        val cat = state.categories.firstOrNull { it.id == id }
        val label = cat?.label ?: LibraryCollection.fromId(id)?.label
        if (label != null) {
            ShelfSection.Collection(id, label)
        } else {
            ShelfSection.AllBooks
        }
    }
    else -> ShelfSection.AllBooks
}
