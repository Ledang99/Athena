package com.projectathena.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectathena.app.data.Book
import com.projectathena.app.data.CapturedNote
import com.projectathena.app.data.DuplicateGroup
import com.projectathena.app.data.DuplicateKind
import com.projectathena.app.data.EbookRepository
import com.projectathena.app.data.ScanProgress
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Screen(val label: String, val shortLabel: String) {
    LIBRARY("Library", "L"),
    DUPLICATES("Duplicates", "D"),
    NOTES("Notes", "N"),
    SETTINGS("Settings", "S"),
}

private enum class FileFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    EPUB("EPUB"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthenaApp(
    viewModel: MainViewModel,
    onOpenBook: (Book) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    val snackbarHostState = remember { SnackbarHostState() }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.scanFolder(uri)
        }
    }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.importFiles(uris)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearMessage()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "A",
                                color = Color(0xFFD6F06F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Project Athena",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "ON-DEVICE LIBRARY",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleTheme) {
                        Text(if (state.darkMode) "Day mode" else "Dark mode")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Screen.entries.forEach { item ->
                    val badgeCount = when (item) {
                        Screen.LIBRARY -> state.catalogStats.total
                        Screen.DUPLICATES -> state.duplicateGroups.size
                        Screen.NOTES -> state.notes.size
                        Screen.SETTINGS -> 0
                    }
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (screen == item) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        RoundedCornerShape(9.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    item.shortLabel,
                                    color = if (screen == item) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                )
                            }
                        },
                        label = {
                            Text(
                                if (badgeCount > 0) "${item.label} $badgeCount" else item.label,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (screen) {
                Screen.LIBRARY -> LibraryScreen(
                    state = state,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onImportFiles = {
                        filesLauncher.launch(
                            arrayOf(
                                EbookRepository.PDF_MIME,
                                EbookRepository.EPUB_MIME,
                                "application/octet-stream",
                            ),
                        )
                    },
                    onRescan = viewModel::rescan,
                    onOpenBook = onOpenBook,
                    onUpdateMetadata = viewModel::updateMetadata,
                )

                Screen.DUPLICATES -> DuplicatesScreen(
                    groups = state.duplicateGroups,
                    onOpenBook = onOpenBook,
                )

                Screen.NOTES -> NotesScreen(state.notes)

                Screen.SETTINGS -> SettingsScreen(
                    state = state,
                    onPreferredViewer = viewModel::setPreferredViewer,
                    onRefreshViewers = viewModel::refreshViewers,
                    onToggleTheme = viewModel::toggleTheme,
                )
            }

            if (state.scanning) {
                ScanProgressBanner(
                    progress = state.scanProgress,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun ScanProgressBanner(
    progress: ScanProgress,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                progress.phase,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (progress.total > 0) {
                Text(
                    "${progress.current} of ${progress.total}",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (progress.isDeterminate) {
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            progress.currentFolder?.let { folder ->
                Text(
                    "Folder: $folder",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            progress.currentFile?.let { file ->
                Text(
                    "Ebook: $file",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: AthenaUiState,
    onChooseFolder: () -> Unit,
    onImportFiles: () -> Unit,
    onRescan: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onUpdateMetadata: (Book, String, String?) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var fileFilter by rememberSaveable { mutableStateOf(FileFilter.ALL) }
    val normalizedSearch = search.trim()
    val duplicateKinds = remember(state.duplicateGroups) {
        state.duplicateGroups
            .flatMap { group -> group.copies.map { book -> book.id to group.kind } }
            .toMap()
    }
    val visibleBooks = remember(state.books, normalizedSearch, fileFilter) {
        state.books.filter { book ->
            val matchesSearch = normalizedSearch.isBlank() ||
                book.title.contains(normalizedSearch, ignoreCase = true) ||
                book.author.orEmpty().contains(normalizedSearch, ignoreCase = true)
            val matchesType = when (fileFilter) {
                FileFilter.ALL -> true
                FileFilter.PDF -> book.mimeType == EbookRepository.PDF_MIME
                FileFilter.EPUB -> book.mimeType == EbookRepository.EPUB_MIME
            }
            matchesSearch && matchesType
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Your reading inbox",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Catalog downloads, check duplicates, then continue reading in Moon+.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onChooseFolder,
                    enabled = !state.scanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.libraryFolder == null) "Choose folder" else "Change folder")
                }
                OutlinedButton(
                    onClick = onImportFiles,
                    enabled = !state.scanning,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import files")
                }
            }
            if (state.libraryFolder != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Folder access granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    TextButton(onClick = onRescan, enabled = !state.scanning) {
                        Text("Rescan")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Downloads folder on Android 11+",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Android blocks access to the Downloads root. Select a subfolder such as Downloads/Athena, or use Import files to select existing downloads.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item {
            StatsRow(state)
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search title or author") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = fileFilter == filter,
                        onClick = { fileFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
            }
        }

        if (visibleBooks.isEmpty()) {
            item {
                EmptyLibrary(
                    hasBooks = state.books.isNotEmpty(),
                    onChooseFolder = onChooseFolder,
                )
            }
        } else {
            items(visibleBooks, key = { it.id }) { book ->
                BookCard(
                    book = book,
                    duplicateKind = duplicateKinds[book.id],
                    onOpenBook = onOpenBook,
                    onUpdateMetadata = onUpdateMetadata,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(state: AthenaUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard("In library", state.catalogStats.total.toString(), Modifier.weight(1f))
        StatCard("PDF", state.catalogStats.pdf.toString(), Modifier.weight(1f))
        StatCard("EPUB", state.catalogStats.epub.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyLibrary(hasBooks: Boolean, onChooseFolder: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                if (hasBooks) "No books match" else "Connect your ebook folder",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (hasBooks) {
                    "Try another title, author, or file format."
                } else {
                    "Choose Downloads or another folder. Athena receives read-only access and leaves every file in place."
                },
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!hasBooks) {
                Button(
                    onClick = onChooseFolder,
                    modifier = Modifier.padding(top = 18.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD6F06F),
                        contentColor = Color(0xFF12231F),
                    ),
                ) {
                    Text("Choose folder")
                }
            }
        }
    }
}

@Composable
private fun BookCover(book: Book) {
    val cover by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = book.coverPath,
    ) {
        value = withContext(Dispatchers.IO) {
            book.coverPath
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    if (cover != null) {
        Image(
            bitmap = checkNotNull(cover),
            contentDescription = "Cover of ${book.title}",
            modifier = Modifier
                .size(width = 72.dp, height = 96.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 96.dp)
                .background(
                    if (book.mimeType == EbookRepository.PDF_MIME) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (book.mimeType == EbookRepository.PDF_MIME) "PDF" else "EPUB",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BookCard(
    book: Book,
    duplicateKind: DuplicateKind?,
    onOpenBook: (Book) -> Unit,
    onUpdateMetadata: (Book, String, String?) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                BookCover(book)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.author ?: "Unknown author",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            formatBytes(book.sizeBytes),
                            book.folderName,
                            book.displayName,
                        ).joinToString(" · "),
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (duplicateKind != null) {
                AssistChip(
                    onClick = {},
                    modifier = Modifier.padding(top = 10.dp),
                    label = {
                        Text(
                            if (duplicateKind == DuplicateKind.EXACT) {
                                "Exact duplicate"
                            } else {
                                "Likely match"
                            },
                        )
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { editing = true }) {
                    Text("Edit details")
                }
                Button(onClick = { onOpenBook(book) }) {
                    Text("Open")
                }
            }
        }
    }

    if (editing) {
        EditBookDialog(
            book = book,
            onDismiss = { editing = false },
            onSave = { title, author ->
                onUpdateMetadata(book, title, author)
                editing = false
            },
        )
    }
}

@Composable
private fun EditBookDialog(
    book: Book,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var title by remember(book.id) { mutableStateOf(book.title) }
    var author by remember(book.id) { mutableStateOf(book.author.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit book details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Author") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, author.ifBlank { null }) },
                enabled = title.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    onOpenBook: (Book) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Duplicate check",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Exact copies share the same file hash. Likely matches share title, file size, and type with different hashes.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (groups.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            "No duplicate files found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Athena compares title, size, type, and SHA-256 hashes across your catalog.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(groups, key = { it.key }) { group ->
                DuplicateGroupCard(group = group, onOpenBook = onOpenBook)
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onOpenBook: (Book) -> Unit,
) {
    val formatLabel = if (group.mimeType == EbookRepository.PDF_MIME) "PDF" else "EPUB"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$formatLabel · ${formatBytes(group.sizeBytes)} · ${group.copyCount} copies",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (group.kind == DuplicateKind.EXACT) "Exact hash" else "Likely match",
                        )
                    },
                )
            }

            group.sha256?.let { hash ->
                Text(
                    "Hash: ${hash.take(12)}…",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            group.copies.forEachIndexed { index, book ->
                if (index > 0) {
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            book.folderName ?: "Imported file",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            book.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { onOpenBook(book) }) {
                        Text("Open")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesScreen(notes: List<CapturedNote>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Captured passages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "In Moon+ Reader, select text and choose Share → Project Athena.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (notes.isEmpty()) {
            item {
                Card {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            "No passages saved yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Shared text stays in Athena's private on-device database.",
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(notes, key = { it.id }) { note ->
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            note.text,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text(
                            DateFormat.getDateTimeInstance(
                                DateFormat.MEDIUM,
                                DateFormat.SHORT,
                            ).format(Date(note.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: AthenaUiState,
    onPreferredViewer: (String?) -> Unit,
    onRefreshViewers: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    var viewerMenuExpanded by remember { mutableStateOf(false) }
    val preferredLabel = state.availableViewers
        .firstOrNull { it.packageName == state.preferredViewerPackage }
        ?.label
        ?: "Ask every time"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Choose how Athena opens ebooks and how the app looks.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Preferred ebook viewer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Athena opens PDF and EPUB files with the app you choose here.",
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ExposedDropdownMenuBox(
                        expanded = viewerMenuExpanded,
                        onExpandedChange = { viewerMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = preferredLabel,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            label = { Text("Viewer") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = viewerMenuExpanded,
                                )
                            },
                        )
                        ExposedDropdownMenu(
                            expanded = viewerMenuExpanded,
                            onDismissRequest = { viewerMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ask every time") },
                                onClick = {
                                    onPreferredViewer(null)
                                    viewerMenuExpanded = false
                                },
                            )
                            state.availableViewers.forEach { viewer ->
                                DropdownMenuItem(
                                    text = { Text(viewer.label) },
                                    onClick = {
                                        onPreferredViewer(viewer.packageName)
                                        viewerMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    if (state.availableViewers.isEmpty()) {
                        Text(
                            "No ebook viewers were found. Install a reader such as Moon+ Reader, then refresh.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    OutlinedButton(
                        onClick = onRefreshViewers,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Refresh installed viewers")
                    }
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.darkMode) "Dark mode is on" else "Day mode is on",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onToggleTheme) {
                            Text(if (state.darkMode) "Use day mode" else "Use dark mode")
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }
    return "%.1f %s".format(value, units[unitIndex])
}
