package com.chirag.shruti.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.data.Book
import com.chirag.shruti.data.formatLeft
import com.chirag.shruti.ui.components.BookCover
import com.chirag.shruti.ui.theme.Palette

@Composable
fun LibraryScreen(books: List<Book>, onOpenBook: (String) -> Unit, onPlay: (Book) -> Unit, onRead: (Book) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf("All", "Audiobooks", "PDFs", "Finished")
    val shown = books.filter {
        when (filter) {
            1 -> it.hasAudio
            2 -> it.hasPdf
            3 -> it.finished
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, f ->
                FilterChip(
                    selected = filter == i, onClick = { filter = i }, label = { Text(f) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Palette.Text, selectedLabelColor = Palette.Bg),
                )
            }
        }
        if (shown.isEmpty()) {
            Text("Nothing here yet.", color = Palette.TextDim, modifier = Modifier.padding(20.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.id }) { b ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBook(b.id) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(b, Modifier.size(84.dp), corner = 6.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(b.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("By ${b.authors}", color = Palette.TextDim, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            listOfNotNull(
                                when {
                                    b.finished -> "Finished"
                                    b.hasAudio && b.totalMs > 0 -> formatLeft(b.totalMs - b.listenedMs())
                                    b.hasAudio -> "${b.chapters.size} parts"
                                    else -> null
                                },
                                if (b.hasPdf) "PDF" else null,
                                if (!b.hasAudio && !b.hasPdf) "Tap to add your files" else null,
                            ).joinToString(" · "),
                            color = Palette.Link, fontSize = 13.sp,
                        )
                        if (b.progress() > 0f) {
                            LinearProgressIndicator(
                                progress = { b.progress() },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                                color = Palette.Accent, trackColor = Palette.Divider, drawStopIndicator = {},
                            )
                        }
                    }
                    if (b.hasPdf) IconButton(onClick = { onRead(b) }) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Read PDF", tint = Palette.Text) }
                    if (b.hasAudio) IconButton(onClick = { onPlay(b) }) { Icon(Icons.Filled.Headphones, "Listen", tint = Palette.Text) }
                }
            }
        }
    }
}
