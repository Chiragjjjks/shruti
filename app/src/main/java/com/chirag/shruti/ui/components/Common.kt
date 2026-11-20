package com.chirag.shruti.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.data.Book
import com.chirag.shruti.ui.theme.Palette

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Library("Library", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Filled.LibraryBooks),
    Discover("Discover", Icons.Outlined.Explore, Icons.Filled.Explore),
    Profile("Profile", Icons.Outlined.Person, Icons.Filled.Person),
}

@Composable
fun BottomNav(selected: Tab?, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Palette.Bg).navigationBarsPadding()) {
        HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Tab.entries.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (on) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = if (on) Palette.Text else Palette.Nav, modifier = Modifier.size(26.dp))
                    Text(tab.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Palette.Text else Palette.Nav)
                }
            }
        }
    }
}

/** The bar above the tabs: what's loaded, time left, rewind 30 and play. */
@Composable
fun MiniPlayer(
    book: Book,
    chapterIndex: Int,
    positionMs: Long,
    playing: Boolean,
    buffering: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onReplay: () -> Unit,
) {
    val chapter = book.chapters.getOrNull(chapterIndex)
    val left = (book.totalMs - book.listenedMs(chapterIndex, positionMs)).coerceAtLeast(0)
    Column(Modifier.background(Palette.Bg)) {
        LinearProgressIndicator(
            progress = { book.progress(chapterIndex, positionMs) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Palette.Accent,
            trackColor = Palette.Divider,
            drawStopIndicator = {},
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(book, Modifier.size(48.dp), corner = 4.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(book.title)
                        if (chapter != null) append(" | ").append(chapter.title)
                    },
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (book.totalMs > 0) com.chirag.shruti.data.formatLeft(left) else "Tap play to listen",
                    color = Palette.TextDim, fontSize = 13.sp,
                )
            }
            IconButton(onClick = onReplay) {
                Icon(Icons.Filled.Replay30, contentDescription = "Back 30 seconds", tint = Palette.Text, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(4.dp))
            PlayCircle(playing, buffering, 46.dp, onToggle)
        }
    }
}

@Composable
fun PlayCircle(playing: Boolean, buffering: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (buffering && playing) {
            CircularProgressIndicator(Modifier.size(size * 0.45f), color = Palette.Bg, strokeWidth = 2.dp)
        } else {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Palette.Bg,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** The outlined "▶ Preview"-style pill. */
@Composable
fun ListenPill(label: String, onClick: () -> Unit, icon: ImageVector = Icons.Filled.PlayArrow) {
    Row(
        Modifier
            .border(1.5.dp, Palette.PillBorder, RoundedCornerShape(50))
            .background(Palette.PillFill, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
fun Stars(rating: Float, size: Dp = 20.dp) {
    Row {
        for (i in 0 until 5) {
            val fill = (rating - i).coerceIn(0f, 1f)
            Box(Modifier.size(size).clipToBounds()) {
                Icon(Icons.Filled.Star, null, tint = Palette.TextDim.copy(alpha = 0.35f), modifier = Modifier.size(size))
                if (fill > 0f) {
                    Icon(
                        Icons.Filled.Star, null, tint = Palette.Text,
                        modifier = Modifier
                            .size(size)
                            .drawWithContent {
                                clipRect(right = size.toPx() * fill) { this@drawWithContent.drawContent() }
                            },
                    )
                }
            }
        }
    }
}

@Composable
fun Badge(text: String) {
    Text(
        text,
        color = Palette.BadgeText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .background(Palette.Badge, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun SectionLabel(kicker: String, title: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(kicker.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.TextDim, letterSpacing = 0.6.sp)
        if (title != null) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun BookCard(book: Book, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        BookCover(book, Modifier.fillMaxWidth().aspectRatio(1f), corner = 10.dp)
        if (book.progress() > 0f) {
            LinearProgressIndicator(
                progress = { book.progress() },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                color = Palette.Accent, trackColor = Palette.Divider, drawStopIndicator = {},
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(book.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("By ${book.authors}", color = Palette.Link, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

