package com.projectathena.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.projectathena.app.data.Book
import com.projectathena.app.data.BookCategory
import com.projectathena.app.data.BookSummaryImage
import com.projectathena.app.data.CapturedNote
import com.projectathena.app.data.ReadingStatus
import com.projectathena.app.data.collectionLabels
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDossierScreen(
    book: Book,
    summaryImages: List<BookSummaryImage>,
    notes: List<CapturedNote>,
    categories: List<BookCategory> = emptyList(),
    onBack: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onAddSummaryImage: (Uri, String?) -> Unit,
    onDeleteSummaryImage: (Long) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onUpdateReadingStatus: (ReadingStatus) -> Unit,
    onEditDetails: () -> Unit,
) {
    var newNoteText by remember { mutableStateOf("") }
    var fullScreenImage by remember { mutableStateOf<BookSummaryImage?>(null) }
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            pendingImageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    Text(
                        book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(onClick = onEditDetails) {
                        Text("Edit")
                    }
                    Button(
                        onClick = { onOpenBook(book) },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Open")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Book overview card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        DossierCover(book = book)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                book.author ?: "Unknown author",
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOfNotNull(
                                    book.folderName,
                                    book.displayName,
                                ).joinToString(" · "),
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Reading status dropdown selection
                            Box(modifier = Modifier.padding(top = 10.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = statusDropdownExpanded,
                                    onExpandedChange = { statusDropdownExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = book.readingStatus.label,
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
                                                        if (book.readingStatus == status) {
                                                            "${status.label} ✓"
                                                        } else {
                                                            status.label
                                                        },
                                                    )
                                                },
                                                onClick = {
                                                    onUpdateReadingStatus(status)
                                                    statusDropdownExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            // Collections & tags
                            val labels = book.collectionLabels(categories) + book.tags
                            if (labels.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    labels.chunked(2).forEach { rowLabels ->
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            rowLabels.forEach { label ->
                                                AssistChip(
                                                    onClick = onEditDetails,
                                                    label = { Text(label, maxLines = 1) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mind Maps & Visual Summaries section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Mind Maps & Visual Summaries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Attach infographics, mind maps, or summary slides from Pinterest/Web",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(160.dp)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                                Text("Add Image", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    items(summaryImages, key = { it.id }) { summaryImage ->
                        SummaryImageThumbnail(
                            image = summaryImage,
                            onClick = { fullScreenImage = summaryImage },
                            onDelete = { onDeleteSummaryImage(summaryImage.id) },
                        )
                    }
                }
            }

            // Notes & Insights section
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    "My Notes & Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Key takeaways, thoughts, and book quotes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newNoteText,
                        onValueChange = { newNoteText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write a note or key takeaway…") },
                        maxLines = 3,
                    )
                    Button(
                        onClick = {
                            if (newNoteText.isNotBlank()) {
                                onAddNote(newNoteText.trim())
                                newNoteText = ""
                            }
                        },
                        enabled = newNoteText.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                }
            }

            if (notes.isEmpty()) {
                item {
                    Text(
                        "No notes for this book yet. Type above or share passages from Moon+ Reader.",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(note.text, style = MaterialTheme.typography.bodyMedium)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    DateFormat.getDateInstance().format(Date(note.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { onDeleteNote(note.id) }) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add caption dialog when image picked
    pendingImageUri?.let { uri ->
        AddSummaryCaptionDialog(
            uri = uri,
            onDismiss = { pendingImageUri = null },
            onConfirm = { caption ->
                onAddSummaryImage(uri, caption)
                pendingImageUri = null
            },
        )
    }

    // Full screen zoomable image viewer
    fullScreenImage?.let { img ->
        FullScreenImageViewer(
            image = img,
            onDismiss = { fullScreenImage = null },
            onDelete = {
                onDeleteSummaryImage(img.id)
                fullScreenImage = null
            },
        )
    }
}

@Composable
private fun SummaryImageThumbnail(
    image: BookSummaryImage,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = image.imagePath) {
        value = withContext(Dispatchers.IO) {
            val file = File(image.imagePath)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        }
    }
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (bitmap != null) {
                Image(
                    bitmap = checkNotNull(bitmap),
                    contentDescription = image.caption ?: "Summary image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Image", style = MaterialTheme.typography.labelSmall)
                }
            }
            image.caption?.let { cap ->
                Text(
                    cap,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AddSummaryCaptionDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var caption by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add mind map or summary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Give this visual summary a title or note (optional):",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    placeholder = { Text("e.g. Chapter 1 Mind Map, Framework Cheatsheet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(caption.ifBlank { null }) }) {
                Text("Add")
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
private fun FullScreenImageViewer(
    image: BookSummaryImage,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = image.imagePath) {
        value = withContext(Dispatchers.IO) {
            val file = File(image.imagePath)
            if (file.isFile) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 5f)
        offset += offsetChange
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = checkNotNull(bitmap),
                    contentDescription = image.caption ?: "Full screen summary",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .transformable(state = transformState),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading image…", color = Color.White)
                }
            }

            // Top control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Text("✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                image.caption?.let { cap ->
                    Text(
                        cap,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape),
                ) {
                    Text("🗑", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DossierCover(book: Book) {
    val cover by produceState<ImageBitmap?>(initialValue = null, key1 = book.coverPath) {
        value = withContext(Dispatchers.IO) {
            book.coverPath
                ?.let(::File)
                ?.takeIf { it.isFile }
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    if (cover != null) {
        Image(
            bitmap = checkNotNull(cover),
            contentDescription = book.title,
            modifier = Modifier
                .width(80.dp)
                .aspectRatio(0.68f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .width(80.dp)
                .aspectRatio(0.68f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                book.title.take(1).uppercase(),
                color = Color(0xFFD6F06F),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
        }
    }
}
