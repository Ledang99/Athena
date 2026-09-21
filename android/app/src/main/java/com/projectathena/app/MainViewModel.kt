package com.projectathena.app

import android.app.Application
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.projectathena.app.data.Book
import com.projectathena.app.data.CapturedNote
import com.projectathena.app.data.DuplicateKind
import com.projectathena.app.data.EbookRepository
import com.projectathena.app.data.ScanProgress
import com.projectathena.app.data.duplicateKinds
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AthenaUiState(
    val books: List<Book> = emptyList(),
    val notes: List<CapturedNote> = emptyList(),
    val duplicateKinds: Map<Long, DuplicateKind> = emptyMap(),
    val scanning: Boolean = false,
    val scanProgress: ScanProgress = ScanProgress(),
    val libraryFolder: String? = null,
    val darkMode: Boolean = false,
    val preferredViewerPackage: String? = null,
    val availableViewers: List<ViewerApp> = emptyList(),
    val message: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EbookRepository(application)
    private val preferences = application.getSharedPreferences(PREFERENCES, 0)
    private val operationActive = AtomicBoolean(false)
    private val _uiState = MutableStateFlow(
        AthenaUiState(
            libraryFolder = preferences.getString(LIBRARY_FOLDER, null),
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

    fun scanFolder(uri: Uri) {
        if (!operationActive.compareAndSet(false, true)) return
        preferences.edit { putString(LIBRARY_FOLDER, uri.toString()) }
        _uiState.update {
            it.copy(
                scanning = true,
                scanProgress = ScanProgress(phase = "Preparing"),
                libraryFolder = uri.toString(),
            )
        }
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    repository.scanFolder(uri) { progress ->
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
                        message = result.message
                            ?: "Indexed ${result.indexed} ebook${if (result.indexed == 1) "" else "s"}" +
                            if (result.errors > 0) " · ${result.errors} skipped" else "",
                    )
                },
                onFailure = { error ->
                    loadCatalog(message = error.message ?: "Folder scan failed")
                },
            )
        }
    }

    fun rescan() {
        _uiState.value.libraryFolder?.let { scanFolder(it.toUri()) }
    }

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

    fun markOpened(book: Book) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.markOpened(book.id) }
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

    private fun refresh() {
        viewModelScope.launch { loadCatalog() }
    }

    private suspend fun loadCatalog(message: String? = null) {
        val (books, notes) = withContext(Dispatchers.IO) {
            repository.books() to repository.notes()
        }
        _uiState.update {
            it.copy(
                books = books,
                notes = notes,
                duplicateKinds = duplicateKinds(books),
                scanning = false,
                scanProgress = ScanProgress(),
                message = message,
            )
        }
    }

    companion object {
        private const val PREFERENCES = "athena_preferences"
        private const val LIBRARY_FOLDER = "library_folder"
        private const val DARK_MODE = "dark_mode"
        private const val PREFERRED_VIEWER = "preferred_viewer_package"
    }
}
