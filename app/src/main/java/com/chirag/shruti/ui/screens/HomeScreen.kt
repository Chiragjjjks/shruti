package com.chirag.shruti.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.data.Book
import com.chirag.shruti.ui.components.BookCard
import com.chirag.shruti.ui.components.BookCover
import com.chirag.shruti.ui.components.ListenPill
import com.chirag.shruti.ui.components.SectionLabel
import com.chirag.shruti.ui.theme.Palette

@Composable
fun Logo() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("shruti", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, letterSpacing = (-0.5).sp)
        Spacer(Modifier.width(3.dp))
        Canvas(Modifier.size(width = 20.dp, height = 20.dp).padding(bottom = 8.dp)) {
            val s = size.width
            for (i in 0 until 3) {
                val r = s * (0.35f + i * 0.3f)
                drawArc(
                    Palette.Accent, startAngle = -85f, sweepAngle = 75f, useCenter = false,
                    topLeft = Offset(-r, size.height - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = s * 0.12f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    books: List<Book>,
    playingBookId: String?,
    onOpenBook: (String) -> Unit,
    onListen: (Book) -> Unit,
    onSearch: () -> Unit,
) {
    val pager = rememberPagerState { books.size }
    val current = books.getOrNull(pager.currentPage)
    val tint by animateColorAsState(Color(current?.tint ?: 0xFF1A2333), label = "tint")
    val inProgress = books.filter { (it.progress() > 0f && !it.finished) || it.pdfPage > 0 }
        .sortedByDescending { maxOf(it.lastPlayed, it.lastRead) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.75f), tint.copy(alpha = 0.25f), Palette.Bg))),
            ) {
                Column(Modifier.statusBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Logo()
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Search", tint = Palette.Text) }
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 56.dp), pageSpacing = 16.dp) { page ->
                        val b = books[page]
                        BookCover(
                            b,
                            Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .shadow(18.dp, RoundedCornerShape(10.dp))
                                .clickable { onOpenBook(b.id) },
                            corner = 10.dp,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    if (current != null) {
                        Text(
                            when {
                                current.id == playingBookId -> "NOW PLAYING"
                                current.progress() > 0f && !current.finished -> "CONTINUE LISTENING"
                                current.hasAudio -> "READY TO LISTEN"
                                current.hasPdf -> "READY TO READ"
                                else -> "IN YOUR LIBRARY"
                            },
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.6.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        if (!current.hasAudio && current.hasPdf) {
                            ListenPill("Read aloud", { onListen(current) }, Icons.AutoMirrored.Filled.MenuBook)
                        } else {
                            val isPlaying = current.id == playingBookId
                            ListenPill(
                                when { isPlaying -> "Pause"; current.hasAudio -> current.listenLabel; else -> "Add files" },
                                { onListen(current) },
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        books.indices.forEach { i ->
                            Box(
                                Modifier
                                    .width(if (i == pager.currentPage) 44.dp else 30.dp)
                                    .height(4.dp)
                                    .background(if (i == pager.currentPage) Palette.Text else Palette.PillBorder, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }

        if (inProgress.isNotEmpty()) {
            val b = inProgress.first()
            item {
                SectionLabel("Because you recently listened to", b.title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                ContinueRow(b, onOpenBook, onListen)
                Spacer(Modifier.height(20.dp))
            }
        }

        item {
            SectionLabel("Your library", "Ready when you are", Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(books, key = { it.id }) { b -> BookCard(b, 170.dp) { onOpenBook(b.id) } }
            }
        }
    }
}

@Composable
private fun ContinueRow(book: Book, onOpenBook: (String) -> Unit, onListen: (Book) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(Palette.Surface, RoundedCornerShape(12.dp))
            .clickable { onOpenBook(book.id) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(book, Modifier.size(72.dp), corner = 6.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val ch = book.chapters.getOrNull(book.chapterIndex)
            Text(
                when {
                    book.hasAudio && ch != null -> "Chapter ${book.chapterIndex + 1} of ${book.chapters.size} · ${ch.title}"
                    book.hasPdf -> "Page ${book.pdfPage + 1}"
                    else -> book.authors
                },
                color = Palette.TextDim, fontSize = 13.sp, maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.LinearProgressIndicator(
                progress = { book.progress() },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Palette.Accent, trackColor = Palette.Divider, drawStopIndicator = {},
            )
        }
        Spacer(Modifier.width(8.dp))
        ListenPill(if (book.hasAudio) "Resume" else "Read", { onListen(book) })
    }
}
