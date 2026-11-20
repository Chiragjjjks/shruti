package com.chirag.shruti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.ShrutiApp
import com.chirag.shruti.data.Book
import com.chirag.shruti.data.formatClock
import com.chirag.shruti.data.formatLeft
import com.chirag.shruti.data.formatLength
import com.chirag.shruti.playback.PlayerUiState
import com.chirag.shruti.ui.components.BookCover
import com.chirag.shruti.ui.components.PlayCircle
import com.chirag.shruti.ui.theme.Palette
import kotlinx.coroutines.delay

private val speeds = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(book: Book, state: PlayerUiState, onClose: () -> Unit, onRead: () -> Unit) {
    val player = ShrutiApp.player
    val isThis = state.bookId == book.id
    val chapterIdx = if (isThis) state.chapterIndex else book.chapterIndex
    val position = if (isThis) state.positionMs else book.positionMs
    val chapter = book.chapters.getOrNull(chapterIdx)
    val duration = if (isThis && state.durationMs > 0) state.durationMs else chapter?.durationMs ?: 0L
    val playing = isThis && state.playWhenReady

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showChapters by remember { mutableStateOf(false) }
    var sleepMenu by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sleepEndsAt) {
        while (state.sleepEndsAt != null) { now = System.currentTimeMillis(); delay(1000) }
    }

    val tint = Color(book.tint)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.9f), tint.copy(alpha = 0.3f), Palette.Bg, Palette.Bg)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", tint = Palette.Text, modifier = Modifier.size(32.dp)) }
            Text("Now playing", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(48.dp))
        }
        Spacer(Modifier.weight(0.4f))
        BookCover(
            book,
            Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .shadow(24.dp, RoundedCornerShape(10.dp)),
            corner = 10.dp,
        )
        Spacer(Modifier.weight(0.4f))
        Text(book.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(chapter?.title ?: "", color = Palette.TextDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))

        val shown = if (dragging) dragValue.toLong() else position
        Slider(
            value = shown.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                if (isThis) player.seekTo(dragValue.toLong()) else player.play(book, chapterIdx, dragValue.toLong())
                dragging = false
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = Palette.Text, activeTrackColor = Palette.Text, inactiveTrackColor = Palette.Divider),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(shown), color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (book.totalMs > 0) formatLeft(book.totalMs - book.listenedMs(chapterIdx, shown)) else "",
                color = Palette.TextDim, fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            Text("-" + formatClock((duration - shown).coerceAtLeast(0)), color = Palette.TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (isThis) player.previousChapter() else player.play(book, (chapterIdx - 1).coerceAtLeast(0), 0) }) {
                Icon(Icons.Filled.SkipPrevious, "Previous chapter", tint = Palette.Text, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { if (isThis) player.seekBy(-30_000) else player.play(book) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay30, "Back 30 seconds", tint = Palette.Text, modifier = Modifier.size(40.dp))
            }
            PlayCircle(playing, state.buffering, 76.dp) { if (isThis) player.toggle(book) else player.play(book) }
            IconButton(onClick = { if (isThis) player.seekBy(30_000) else player.play(book) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Forward30, "Forward 30 seconds", tint = Palette.Text, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { if (isThis) player.nextChapter() else player.play(book, (chapterIdx + 1).coerceAtMost(book.chapters.lastIndex), 0) }) {
                Icon(Icons.Filled.SkipNext, "Next chapter", tint = Palette.Text, modifier = Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.weight(0.5f))

        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BarAction(Icons.Filled.Speed, "%sx".format(state.speed.toString().removeSuffix(".0"))) {
                val i = speeds.indexOfFirst { it >= state.speed - 0.001f }
                player.setSpeed(speeds[(i + 1).takeIf { it in speeds.indices && i >= 0 } ?: 0])
            }
            Box {
                BarAction(
                    Icons.Filled.Bedtime,
                    when {
                        state.sleepAtChapterEnd -> "End of ch."
                        state.sleepEndsAt != null -> formatClock(state.sleepEndsAt - now)
                        else -> "Sleep"
                    },
                    highlight = state.sleepEndsAt != null || state.sleepAtChapterEnd,
                ) { sleepMenu = true }
                DropdownMenu(sleepMenu, onDismissRequest = { sleepMenu = false }) {
                    listOf(5, 15, 30, 45, 60).forEach { m ->
                        DropdownMenuItem(text = { Text("$m minutes") }, onClick = { sleepMenu = false; player.sleepIn(m) })
                    }
                    DropdownMenuItem(text = { Text("End of chapter") }, onClick = { sleepMenu = false; player.sleepAtEndOfChapter() })
                    if (state.sleepEndsAt != null || state.sleepAtChapterEnd) {
                        DropdownMenuItem(text = { Text("Turn off timer") }, onClick = { sleepMenu = false; player.sleepIn(null) })
                    }
                }
            }
            BarAction(Icons.AutoMirrored.Filled.FormatListBulleted, "Chapters") { showChapters = true }
            if (book.hasPdf) BarAction(Icons.AutoMirrored.Filled.MenuBook, "Read PDF", onClick = onRead)
        }
    }

    if (showChapters) {
        ModalBottomSheet(onDismissRequest = { showChapters = false }, containerColor = Palette.Surface) {
            Text("Chapters", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(book.chapters) { i, ch ->
                    val current = i == chapterIdx
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { player.play(book, i, 0); showChapters = false }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text("${i + 1}", color = if (current) Palette.Accent else Palette.TextDim, modifier = Modifier.width(36.dp))
                        Text(ch.title, modifier = Modifier.weight(1f), color = if (current) Palette.Accent else Palette.Text, maxLines = 2)
                        if (ch.durationMs > 0) Text(formatLength(ch.durationMs), color = Palette.TextDim, fontSize = 13.sp)
                    }
                    HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, highlight: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (highlight) Palette.Accent else Palette.Text
        Icon(icon, label, tint = c, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = c, fontSize = 12.sp)
    }
}
