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
import com.projectathena.app.data.LibraryCollection
import com.projectathena.app.data.ReadingStatus
import com.projectathena.app.data.ScanProgress
import com.projectathena.app.data.collectionLabels
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
            viewModel.addFolder(uri)
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

    val showBrandBar = screen != Screen.LIBRARY

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (showBrandBar) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    title = {
                        Text(
                            when (screen) {
                                Screen.DUPLICATES -> "Duplicates"
                                Screen.NOTES -> "Notes"
                                Screen.SETTINGS -> "Settings"
                                Screen.LIBRARY -> "Library"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    actions = {
                        TextButton(onClick = viewModel::toggleTheme) {
                            Text(if (state.darkMode) "Day" else "Dark")
                        }
                    },
                )
            }
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
                Screen.LIBRARY -> LibraryShelfScreen(
                    state = state,
                    onAddFolder = { folderLauncher.launch(null) },
                    onImportFiles = {
                        filesLauncher.launch(
                            arrayOf(
                                EbookRepository.PDF_MIME,
                                EbookRepository.EPUB_MIME,
                                "application/octet-stream",
                            ),
                        )
                    },
                    onRescanAll = viewModel::rescanAll,
                    onRemoveFolder = viewModel::removeFolder,
                    onOpenBook = onOpenBook,
                    onUpdateOrganization = viewModel::updateOrganization,
                    onUpdateReadingStatus = viewModel::updateReadingStatus,
                    onSetViewMode = viewModel::setViewMode,
                    onToggleTheme = viewModel::toggleTheme,
                )

                Screen.DUPLICATES -> DuplicatesScreen(
                    groups = state.duplicateGroups,
                    onOpenBook = onOpenBook,
                )

                Screen.NOTES -> NotesScreen(
                    notes = state.notes,
                    onUpdateCollections = viewModel::updateNoteCollections,
                )

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
private fun NotesScreen(
    notes: List<CapturedNote>,
    onUpdateCollections: (CapturedNote, List<String>) -> Unit,
) {
    var collectionFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var editingNoteId by remember { mutableStateOf<Long?>(null) }
    val activeCollections = remember(notes) {
        LibraryCollection.entries.filter { collection ->
            notes.any { collection.id in it.collections }
        }
    }
    val visibleNotes = remember(notes, collectionFilter) {
        notes.filter { note ->
            collectionFilter == null || collectionFilter in note.collections
        }
    }
    val editingNote = notes.firstOrNull { it.id == editingNoteId }

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
                "Share text from Moon+ into Athena, then file notes into standard collections.",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        item {
            Text(
                "Collection",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = collectionFilter == null,
                    onClick = { collectionFilter = null },
                    label = { Text("All") },
                )
            }
            activeCollections.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { collection ->
                        val count = notes.count { collection.id in it.collections }
                        FilterChip(
                            selected = collectionFilter == collection.id,
                            onClick = {
                                collectionFilter =
                                    if (collectionFilter == collection.id) null else collection.id
                            },
                            label = { Text("${collection.label} $count") },
                        )
                    }
                }
            }
        }

        if (visibleNotes.isEmpty()) {
            item {
                Card {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            if (notes.isEmpty()) "No passages yet" else "No notes in this collection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (notes.isEmpty()) {
                                "In Moon+, select text and choose Share → Project Athena."
                            } else {
                                "Try another collection filter."
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(visibleNotes, key = { it.id }) { note ->
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(note.text, style = MaterialTheme.typography.bodyLarge)
                        val labels = note.collectionLabels()
                        if (labels.isNotEmpty()) {
                            Text(
                                labels.joinToString(" · "),
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            buildString {
                                append(DateFormat.getDateTimeInstance().format(Date(note.createdAt)))
                                note.sourcePackage?.let { append(" · "); append(it) }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { editingNoteId = note.id },
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text("Collections")
                        }
                    }
                }
            }
        }
    }

    if (editingNote != null) {
        NoteCollectionsDialog(
            note = editingNote,
            onDismiss = { editingNoteId = null },
            onSave = { collections ->
                onUpdateCollections(editingNote, collections)
                editingNoteId = null
            },
        )
    }
}

@Composable
private fun NoteCollectionsDialog(
    note: CapturedNote,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var selectedCollections by remember(note.id) { mutableStateOf(note.collections.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note collections") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryCollection.entries.chunked(2)) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { collection ->
                            FilterChip(
                                selected = collection.id in selectedCollections,
                                onClick = {
                                    selectedCollections = if (collection.id in selectedCollections) {
                                        selectedCollections - collection.id
                                    } else {
                                        selectedCollections + collection.id
                                    }
                                },
                                label = { Text(collection.label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedCollections.toList()) }) {
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
