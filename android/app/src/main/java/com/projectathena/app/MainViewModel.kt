package com.projectathena.app

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectathena.app.data.Book
import com.projectathena.app.data.BookCategory
import com.projectathena.app.data.BookSummaryImage
import com.projectathena.app.data.CapturedNote
import com.projectathena.app.data.CatalogStats
import com.projectathena.app.data.DuplicateGroup
import com.projectathena.app.data.EbookRepository
import com.projectathena.app.data.ReadingStatus
import com.projectathena.app.data.ScanProgress
import com.projectathena.app.data.buildDuplicateGroups
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryFolder(
    val uri: String,
    val label: String,
)

enum class LibraryViewMode {
    TILES,
    DETAILS,
}

data class AthenaUiState(
    val books: List<Book> = emptyList(),
    val notes: List<CapturedNote> = emptyList(),
    val catalogStats: CatalogStats = CatalogStats(),
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val scanning: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val libraryFolders: List<LibraryFolder> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.TILES,
    val selectedDossierBookId: Long? = null,
    val dossierImages: List<BookSummaryImage> = emptyList(),
    val dossierNotes: List<CapturedNote> = emptyList(),
    val categories: List<BookCategory> = emptyList(),
    val darkMode: Boolean = false,
    val preferredViewerPackage: String? = null,
    val availableViewers: List<ViewerApp> = emptyList(),
    val message: String? = null,
) {
    @Deprecated("Use libraryFolders", ReplaceWith("libraryFolders.isNotEmpty()"))
    val libraryFolder: String? get() = libraryFolders.firstOrNull()?.uri
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EbookRepository(application)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val operationActive = AtomicBoolean(false)
    private val _uiState = MutableStateFlow(
        AthenaUiState(
            libraryFolders = loadFolders(),
            viewMode = if (preferences.getString(VIEW_MODE, LibraryViewMode.TILES.name) == LibraryViewMode.DETAILS.name) {
                LibraryViewMode.DETAILS
            } else {
                LibraryViewMode.TILES
            },
            darkMode = preferences.getBoolean(DARK_MODE, false),
            preferredViewerPackage = preferences.getString(PREFERRED_VIEWER, null),
            availableViewers = application.packageManager.installedEbookViewers(),
        ),
    )
    val uiState: StateFlow<AthenaUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refreshViewers() {
        _uiState.update {
            it.copy(availableViewers = getApplication<Application>().packageManager.installedEbookViewers())
        }
    }

    fun setPreferredViewer(packageName: String?) {
        preferences.edit {
            if (packageName.isNullOrBlank()) {
                remove(PREFERRED_VIEWER)
            } else {
                putString(PREFERRED_VIEWER, packageName)
            }
        }
        _uiState.update { it.copy(preferredViewerPackage = packageName) }
    }

    fun setViewMode(mode: LibraryViewMode) {
        preferences.edit { putString(VIEW_MODE, mode.name) }
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun addFolder(uri: Uri) {
        val folders = loadFolders().toMutableList()
        if (folders.any { it.uri == uri.toString() }) {
            scanFolders(listOf(uri.toString()), messagePrefix = "Folder already linked · ")
            return
        }
        val label = folderLabel(uri)
        folders += LibraryFolder(uri = uri.toString(), label = label)
        saveFolders(folders)
        _uiState.update { it.copy(libraryFolders = folders) }
        scanFolders(listOf(uri.toString()))
    }

    fun removeFolder(uri: String) {
        val folders = loadFolders().filterNot { it.uri == uri }
        saveFolders(folders)
        _uiState.update { it.copy(libraryFolders = folders) }
    }

    /** Prefer addFolder for multi-folder; kept for callers that still pass a single tree. */
    fun scanFolder(uri: Uri) = addFolder(uri)

    fun rescan() = rescanAll()

    fun rescanAll() {
        val uris = _uiState.value.libraryFolders.map { it.uri }
        if (uris.isEmpty()) {
            _uiState.update { it.copy(message = "Add a library folder first") }
            return
        }
        scanFolders(uris)
    }

    fun rescanFolder(uri: String) = scanFolders(listOf(uri))

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty() || !operationActive.compareAndSet(false, true)) return
        _uiState.update {
            it.copy(
                scanning = true,
                scanProgress = ScanProgress(phase = "Preparing import", total = uris.size),
            )
        }
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    repository.importFiles(uris) { progress ->
                        _uiState.update { state ->
                            state.copy(scanning = true, scanProgress = progress)
                        }
                    }
                }
            }
            operationActive.set(false)
            outcome.fold(
                onSuccess = { result ->
                    loadCatalog(
                        message = "Imported ${result.indexed} ebook${if (result.indexed == 1) "" else "s"}" +
                            if (result.errors > 0) " · ${result.errors} skipped" else "",
                    )
                },
                onFailure = { error ->
                    loadCatalog(message = error.message ?: "File import failed")
                },
            )
        }
    }

    fun captureSharedText(text: String, sourcePackage: String?) {
        if (text.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.captureNote(text, sourcePackage) }
            loadCatalog(message = "Passage saved to Athena notes")
        }
    }

    fun updateMetadata(book: Book, title: String, author: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateMetadata(book.id, title, author)
            }
            loadCatalog(message = "Book details updated")
        }
    }

    fun updateOrganization(
        book: Book,
        title: String,
        author: String?,
        tags: List<String>,
        collections: List<String>,
        readingStatus: ReadingStatus,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateOrganization(
                    bookId = book.id,
                    title = title,
                    author = author,
                    tags = tags,
                    collections = collections,
                    readingStatus = readingStatus,
                )
            }
            loadCatalog(message = "Book organization updated")
        }
    }

    fun updateReadingStatus(book: Book, status: ReadingStatus) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateReadingStatus(book.id, status)
            }
            loadCatalog()
        }
    }

    fun updateNoteCollections(note: CapturedNote, collections: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateNoteCollections(note.id, collections)
            }
            loadCatalog(message = "Note collections updated")
        }
    }

    fun openDossier(bookId: Long) {
        _uiState.update { it.copy(selectedDossierBookId = bookId) }
        loadDossierData(bookId)
    }

    fun closeDossier() {
        _uiState.update {
            it.copy(
                selectedDossierBookId = null,
                dossierImages = emptyList(),
                dossierNotes = emptyList(),
            )
        }
    }

    fun addSummaryImageToBook(bookId: Long, sourceUri: Uri, caption: String? = null) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.addSummaryImage(bookId, sourceUri, caption)
            }
            if (result != null) {
                loadDossierData(bookId)
                _uiState.update { it.copy(message = "Summary image added") }
            } else {
                _uiState.update { it.copy(message = "Failed to import image") }
            }
        }
    }

    fun deleteSummaryImage(imageId: Long, bookId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteSummaryImage(imageId)
            }
            loadDossierData(bookId)
            _uiState.update { it.copy(message = "Summary image removed") }
        }
    }

    fun addNoteToBook(bookId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.captureNote(text = text, sourcePackage = "Athena Dossier", bookId = bookId)
            }
            loadDossierData(bookId)
            loadCatalog(message = "Note saved")
        }
    }

    fun deleteNote(noteId: Long, bookId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteNote(noteId)
            }
            if (bookId != null) {
                loadDossierData(bookId)
            }
            loadCatalog(message = "Note deleted")
        }
    }

    private fun loadDossierData(bookId: Long) {
        viewModelScope.launch {
            val (images, notes) = withContext(Dispatchers.IO) {
                Pair(repository.bookImages(bookId), repository.notesForBook(bookId))
            }
            _uiState.update {
                it.copy(dossierImages = images, dossierNotes = notes)
            }
        }
    }

    fun markOpened(book: Book) {
        viewModelScope.launch {
            withContext<Unit>(Dispatchers.IO) { repository.markOpened(book.id) }
            loadCatalog()
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun toggleTheme() {
        val darkMode = !_uiState.value.darkMode
        preferences.edit { putBoolean(DARK_MODE, darkMode) }
        _uiState.update { it.copy(darkMode = darkMode) }
    }

    private fun scanFolders(uris: List<String>, messagePrefix: String = "") {
        if (uris.isEmpty() || !operationActive.compareAndSet(false, true)) return
        _uiState.update {
            it.copy(
                scanning = true,
                scanProgress = ScanProgress(phase = "Preparing"),
            )
        }
        viewModelScope.launch {
            var indexed = 0
            var errors = 0
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    uris.forEachIndexed { folderIndex, uriString ->
                        val uri = uriString.toUri()
                        val folderName = _uiState.value.libraryFolders
                            .firstOrNull { it.uri == uriString }
                            ?.label
                            ?: folderLabel(uri)
                        _uiState.update { state ->
                            state.copy(
                                scanProgress = ScanProgress(
                                    phase = "Scanning ${folderIndex + 1}/${uris.size}: $folderName",
                                ),
                            )
                        }
                        val result = repository.scanFolder(uri) { progress ->
                            _uiState.update { state ->
                                state.copy(
                                    scanning = true,
                                    scanProgress = progress.copy(
                                        phase = "${progress.phase} · $folderName",
                                    ),
                                )
                            }
                        }
                        indexed += result.indexed
                        errors += result.errors
                    }
                }
            }
            operationActive.set(false)
            outcome.fold(
                onSuccess = {
                    loadCatalog(
                        message = messagePrefix +
                            "Indexed $indexed ebook${if (indexed == 1) "" else "s"}" +
                            if (errors > 0) " · $errors skipped" else "",
                    )
                },
                onFailure = { error ->
                    loadCatalog(message = error.message ?: "Folder scan failed")
                },
            )
        }
    }

    private fun folderLabel(uri: Uri): String {
        val name = DocumentFile.fromTreeUri(getApplication(), uri)?.name
        return name?.takeIf { it.isNotBlank() } ?: "Library folder"
    }

    private fun loadFolders(): List<LibraryFolder> {
        val stored = preferences.getStringSet(LIBRARY_FOLDERS, null).orEmpty()
        val legacy = preferences.getString(LIBRARY_FOLDER, null)
        val uris = (stored + listOfNotNull(legacy)).distinct()
        if (uris.isEmpty()) return emptyList()
        val folders = uris.map { uriString ->
            LibraryFolder(
                uri = uriString,
                label = runCatching { folderLabel(uriString.toUri()) }
                    .getOrDefault("Library folder"),
            )
        }
        if (stored.isEmpty() && legacy != null) {
            saveFolders(folders)
            preferences.edit { remove(LIBRARY_FOLDER) }
        }
        return folders.sortedBy { it.label.lowercase() }
    }

    private fun saveFolders(folders: List<LibraryFolder>) {
        preferences.edit {
            putStringSet(LIBRARY_FOLDERS, folders.map { it.uri }.toSet())
            remove(LIBRARY_FOLDER)
        }
    }

    private fun refresh() {
        viewModelScope.launch { loadCatalog() }
    }

    fun addCategory(label: String) {
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { repository.addCategory(label) }
            if (added != null) {
                loadCatalog(message = "Category added: ${added.label}")
            } else {
                _uiState.update { it.copy(message = "Category already exists or invalid") }
            }
        }
    }

    fun updateCategory(id: String, newLabel: String) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) { repository.updateCategory(id, newLabel) }
            if (success) {
                loadCatalog(message = "Category updated")
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            withContext<Unit>(Dispatchers.IO) { repository.deleteCategory(id) }
            loadCatalog(message = "Category deleted")
        }
    }

    private suspend fun loadCatalog(message: String? = null) {
        val snapshot = withContext(Dispatchers.IO) {
            val books = repository.books()
            val notes = repository.notes()
            val stats = repository.catalogStats()
            val cats = repository.categories()
            Tuple4(books, notes, stats, cats)
        }
        val (books, notes, stats, cats) = snapshot
        _uiState.update {
            it.copy(
                books = books,
                notes = notes,
                catalogStats = stats,
                categories = cats,
                duplicateGroups = buildDuplicateGroups(books),
                libraryFolders = loadFolders(),
                scanning = false,
                scanProgress = ScanProgress(),
                message = message,
            )
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        private const val PREFERENCES = "athena_preferences"
        private const val LIBRARY_FOLDER = "library_folder"
        private const val LIBRARY_FOLDERS = "library_folders"
        private const val VIEW_MODE = "library_view_mode"
        private const val DARK_MODE = "dark_mode"
        private const val PREFERRED_VIEWER = "preferred_viewer_package"
    }
}
