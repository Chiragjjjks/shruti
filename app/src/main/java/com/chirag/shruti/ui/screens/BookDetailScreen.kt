package com.chirag.shruti.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.ShrutiApp
import com.chirag.shruti.data.Book
import com.chirag.shruti.data.Importer
import com.chirag.shruti.data.formatLeft
import com.chirag.shruti.data.formatLength
import com.chirag.shruti.playback.PlayerUiState
import com.chirag.shruti.ui.components.Badge
import com.chirag.shruti.ui.components.BookCover
import com.chirag.shruti.ui.components.ListenPill
import com.chirag.shruti.ui.components.Stars
import com.chirag.shruti.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun BookDetailScreen(
    book: Book,
    player: PlayerUiState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onRead: (readAloud: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = ShrutiApp.repo
    var busy by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }

    fun run(label: String, block: suspend () -> String) {
        busy = label
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "Couldn't import: ${it.message ?: "unknown error"}" }
            busy = null
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        run("Copying PDF…") { "Added ${Importer.importPdf(context, repo, book.id, uri)}" }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        ShrutiApp.player.unload(book.id)
        run("Reading audio files…") { "Added ${Importer.importAudioFiles(context, repo, book.id, uris)} audio file(s)" }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        ShrutiApp.player.unload(book.id)
        run("Scanning folder…") {
            val r = Importer.importFolder(context, repo, book.id, uri)
            listOfNotNull(
                if (r.chapters > 0) "${r.chapters} audio file(s)" else null,
                r.pdf?.let { "PDF" },
                if (r.cover) "cover" else null,
            ).joinToString(", ").ifEmpty { "No audio, PDF or image found in that folder" }
                .let { if (it.startsWith("No")) it else "Added $it" }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        run("Setting cover…") { Importer.importCover(context, repo, book.id, uri); "Cover updated" }
    }

    val isThis = player.bookId == book.id
    val chapterIdx = if (isThis) player.chapterIndex else book.chapterIndex
    val pos = if (isThis) player.positionMs else book.positionMs
    val playing = isThis && player.playWhenReady
    val tint = Color(book.tint)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.35f), Palette.Bg))),
            ) {
                Column(Modifier.statusBarsPadding().fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundIcon(Icons.Filled.ChevronLeft, "Back", onBack)
                        Spacer(Modifier.weight(1f))
                        Box {
                            RoundIcon(Icons.Filled.MoreHoriz, "More") { menu = true }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                MenuItem(Icons.Filled.Folder, "Choose book folder…") { menu = false; folderPicker.launch(null) }
                                MenuItem(Icons.Filled.AudioFile, if (book.hasAudio) "Replace audio files…" else "Add audio files…") {
                                    menu = false; audioPicker.launch(arrayOf("audio/*"))
                                }
                                MenuItem(Icons.Filled.PictureAsPdf, if (book.hasPdf) "Replace PDF…" else "Add PDF…") {
                                    menu = false; pdfPicker.launch(arrayOf("application/pdf"))
                                }
                                MenuItem(Icons.Filled.Image, "Set cover image…") { menu = false; coverPicker.launch(arrayOf("image/*")) }
                                if (book.hasAudio) MenuItem(Icons.Filled.Refresh, "Restart from beginning") {
                                    menu = false
                                    if (isThis) ShrutiApp.player.play(book, 0, 0) else
                                        repo.update(book.id) { it.copy(chapterIndex = 0, positionMs = 0, finished = false) }
                                }
                                if (book.hasAudio) MenuItem(Icons.Filled.AudioFile, "Remove audio from app") {
                                    menu = false; ShrutiApp.player.unload(book.id); Importer.removeAudio(context, repo, book.id)
                                }
                                if (book.hasPdf) MenuItem(Icons.Filled.PictureAsPdf, "Remove PDF from app") {
                                    menu = false; Importer.removePdf(context, repo, book.id)
                                }
                            }
                        }
                    }
                    BookCover(book, Modifier.size(250.dp).shadow(20.dp, RoundedCornerShape(10.dp)), corner = 10.dp)
                    Spacer(Modifier.height(22.dp))
                    when {
                        book.hasAudio -> ListenPill(if (playing) "Pause" else book.listenLabel, {
                            if (playing) ShrutiApp.player.pause() else ShrutiApp.player.play(book)
                        }, if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow)
                        book.hasPdf -> ListenPill("Read aloud", { onRead(true) }, Icons.Filled.RecordVoiceOver)
                        else -> ListenPill("Add files", { addMenu = true }, Icons.Filled.Add)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                book.badge?.let { Badge(it); Spacer(Modifier.height(8.dp)) }
                Text(book.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                if (book.subtitle.isNotBlank()) {
                    Text(book.subtitle, fontSize = 20.sp, textAlign = TextAlign.Center, lineHeight = 26.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        if (book.hasAudio || book.lengthLabel != null) "Audiobook" else null,
                        book.displayLength,
                        if (book.hasPdf) "PDF" else null,
                    ).joinToString("  ·  ").ifEmpty { "Book" },
                    fontSize = 17.sp,
                )
                if (book.rating != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%.1f".format(book.rating), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(Modifier.width(6.dp))
                        Stars(book.rating)
                        Spacer(Modifier.width(8.dp))
                        book.ratingsLabel?.let { Text(it, color = Palette.Link, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.Center) {
                    Credit("BY", book.authors, Modifier.weight(1f))
                    if (book.narrator != null) {
                        VerticalDivider(color = Palette.Divider, modifier = Modifier.padding(vertical = 6.dp))
                        Credit("NARRATED BY", book.narrator, Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(24.dp))
                // Primary actions
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (book.hasAudio) {
                        BigButton(
                            if (playing) "Pause" else book.listenLabel,
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, filled = true, modifier = Modifier.weight(1f),
                        ) { if (playing) ShrutiApp.player.pause() else { ShrutiApp.player.play(book); onOpenPlayer() } }
                    }
                    if (book.hasPdf) {
                        BigButton("Read PDF", Icons.AutoMirrored.Filled.MenuBook, filled = !book.hasAudio, modifier = Modifier.weight(1f)) { onRead(false) }
                    }
                }
                if (!book.hasAudio || !book.hasPdf) {
                    Spacer(Modifier.height(12.dp))
                    Box {
                        BigButton(
                            when {
                                !book.hasAudio && !book.hasPdf -> "Add audiobook & PDF"
                                !book.hasAudio -> "Add audiobook"
                                else -> "Add PDF"
                            },
                            Icons.Filled.Add, filled = false, modifier = Modifier.fillMaxWidth(),
                        ) { addMenu = true }
                        DropdownMenu(addMenu, onDismissRequest = { addMenu = false }) {
                            MenuItem(Icons.Filled.Folder, "Choose book folder (audio + PDF)") { addMenu = false; folderPicker.launch(null) }
                            MenuItem(Icons.Filled.AudioFile, "Pick audio files") { addMenu = false; audioPicker.launch(arrayOf("audio/*")) }
                            MenuItem(Icons.Filled.PictureAsPdf, "Pick PDF") { addMenu = false; pdfPicker.launch(arrayOf("application/pdf")) }
                        }
                    }
                }
                if (book.hasAudio && book.hasPdf) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tip: open the PDF while it plays to read along.",
                        color = Palette.TextDim, fontSize = 13.sp,
                    )
                }
                if (book.hasAudio && book.totalMs > 0) {
                    Spacer(Modifier.height(18.dp))
                    LinearProgressIndicator(
                        progress = { book.progress(chapterIdx, pos) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Palette.Accent, trackColor = Palette.Divider, drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (book.finished && !isThis) "Finished" else
                            "${formatLeft(book.totalMs - book.listenedMs(chapterIdx, pos))} · Part ${chapterIdx + 1} of ${book.chapters.size}",
                        color = Palette.TextDim, fontSize = 13.sp,
                    )
                }
            }

            Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
                Text("Summary", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(book.description, color = Palette.TextDim, fontSize = 15.sp, lineHeight = 22.sp)

                if (book.hasAudio) {
                    Spacer(Modifier.height(24.dp))
                    Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(4.dp))
                    book.chapters.forEachIndexed { i, ch ->
                        val current = i == chapterIdx && (isThis || book.progress() > 0f)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { ShrutiApp.player.play(book, i, 0) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (current && playing) {
                                Icon(Icons.Filled.Equalizer, null, tint = Palette.Accent, modifier = Modifier.width(32.dp))
                            } else {
                                Text("${i + 1}", color = if (current) Palette.Accent else Palette.TextDim, modifier = Modifier.width(32.dp))
                            }
                            Text(
                                ch.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = if (current) Palette.Accent else Palette.Text, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (ch.durationMs > 0) Text(formatLength(ch.durationMs), color = Palette.TextDim, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
                    }
                }
                if (book.hasPdf) {
                    Spacer(Modifier.height(20.dp))
                    Text("PDF: ${book.pdfName ?: "attached"}", color = Palette.TextDim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (busy != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(enabled = true) {}, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Palette.Accent)
                    Spacer(Modifier.height(12.dp))
                    Text(busy ?: "")
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .background(Color(0xFF1B2A4A).copy(alpha = 0.85f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Palette.Text, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = onClick, leadingIcon = { Icon(icon, null) })
}

@Composable
private fun Credit(label: String, value: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(value, color = Palette.Link, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 23.sp)
    }
}

@Composable
private fun BigButton(label: String, icon: ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .then(
                if (filled) Modifier.background(Color.White, RoundedCornerShape(26.dp))
                else Modifier.border(1.5.dp, Palette.PillBorder, RoundedCornerShape(26.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val c = if (filled) Palette.Bg else Palette.Text
        Icon(icon, null, tint = c, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = c, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
