package com.chirag.shruti.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.ShrutiApp
import com.chirag.shruti.data.Book
import com.chirag.shruti.pdf.PdfPages
import com.chirag.shruti.playback.PlayerUiState
import com.chirag.shruti.ui.components.MiniPlayer
import com.chirag.shruti.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@Composable
fun ReaderScreen(
    book: Book,
    player: PlayerUiState,
    startReadAloud: Boolean,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(book.pdfPath) { book.pdfPath?.let(::File) }
    var error by remember { mutableStateOf<String?>(null) }
    val tts by ShrutiApp.readAloud.state.collectAsState()
    val ttsHere = tts.bookId == book.id

    // Opened off the main thread; always closed when the screen goes away, even mid-open.
    val pdf by produceState<PdfPages?>(null, file) {
        if (file == null) { error = "No PDF attached to this book yet."; return@produceState }
        val opened = runCatching { withContext(NonCancellable + Dispatchers.IO) { PdfPages.open(file) } }
        opened.exceptionOrNull()?.let { e ->
            error = if (e is SecurityException) "This PDF is password-protected and can't be opened."
            else "Couldn't open this PDF (${e.message ?: "unknown error"})."
        }
        val p = opened.getOrNull() ?: return@produceState
        try {
            value = p
            awaitCancellation()
        } finally {
            p.close()
        }
    }

    // Keep the screen awake while the voice is reading, and stop it when leaving the reader.
    val view = LocalView.current
    DisposableEffect(ttsHere && tts.speaking) {
        view.keepScreenOn = ttsHere && tts.speaking
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(book.id) {
        onDispose { if (ShrutiApp.readAloud.state.value.bookId == book.id) ShrutiApp.readAloud.stop() }
    }

    val list = rememberLazyListState(initialFirstVisibleItemIndex = book.pdfPage)
    val currentPage by remember {
        derivedStateOf {
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull()
            if (first != null && list.firstVisibleItemScrollOffset > first.size / 2) list.firstVisibleItemIndex + 1 else list.firstVisibleItemIndex
        }
    }
    LaunchedEffect(currentPage) {
        delay(500)
        ShrutiApp.repo.update(book.id) { it.copy(pdfPage = currentPage, lastRead = System.currentTimeMillis()) }
    }

    var autoStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pdf) {
        if (pdf != null && startReadAloud && !autoStarted && file != null) {
            autoStarted = true
            ShrutiApp.readAloud.start(book.id, file, book.pdfPage)
        }
    }
    LaunchedEffect(tts.page, ttsHere) {
        if (ttsHere && pdf != null && tts.page != currentPage) list.animateScrollToItem(tts.page)
    }
    LaunchedEffect(tts.message) {
        tts.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            ShrutiApp.readAloud.clearMessage()
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var goTo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color(0xFF1B2029))) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Palette.Bg)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Palette.Text) }
            Column(Modifier.weight(1f)) {
                Text(book.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                pdf?.let {
                    Text(
                        "Page ${currentPage + 1} of ${it.pageCount}",
                        color = Palette.Link, fontSize = 13.sp,
                        modifier = Modifier.clickable { goTo = true },
                    )
                }
            }
            if (pdf != null && file != null) {
                IconButton(onClick = {
                    if (ttsHere) ShrutiApp.readAloud.stop() else ShrutiApp.readAloud.start(book.id, file, currentPage)
                }) {
                    Icon(Icons.Filled.RecordVoiceOver, if (ttsHere) "Stop reading aloud" else "Read aloud", tint = if (ttsHere) Palette.Accent else Palette.Text)
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.count { it.pressed }
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            if (pressed >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 4f)
                                val pan = event.calculatePan()
                                val mx = size.width * (scale - 1f) / 2f
                                val my = size.height * (scale - 1f) / 2f
                                offsetX = (offsetX + pan.x).coerceIn(-mx, mx)
                                offsetY = (offsetY + pan.y).coerceIn(-my, my)
                                event.changes.forEach { it.consume() }
                            } else if (pressed == 1 && scale > 1.01f) {
                                val pan = event.calculatePan()
                                if (abs(pan.x) > abs(pan.y)) {
                                    offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1.01f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f
                    })
                },
        ) {
            val density = LocalDensity.current
            val baseWidth = with(density) { maxWidth.roundToPx() }
            val renderWidth = if (scale > 1.5f) (baseWidth * 2).coerceAtMost(2400) else baseWidth
            val doc = pdf
            when {
                error != null -> Text(error!!, color = Palette.TextDim, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                doc == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Palette.Accent)
                else -> LazyColumn(
                    state = list,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationX = offsetX; translationY = offsetY
                        },
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(doc.pageCount, key = { it }) { index ->
                        PageView(doc, index, baseWidth, renderWidth, highlight = ttsHere && tts.page == index)
                    }
                }
            }
        }

        // Bottom: read-aloud controls, or the audiobook player docked under the PDF.
        Column(Modifier.background(Palette.Bg).navigationBarsPadding()) {
            when {
                ttsHere -> ReadAloudBar(
                    page = tts.page, pageCount = tts.pageCount, speaking = tts.speaking, preparing = tts.preparing, rate = tts.rate,
                    onToggle = { if (tts.speaking) ShrutiApp.readAloud.pause() else ShrutiApp.readAloud.resume() },
                    onPrev = { ShrutiApp.readAloud.jumpTo(tts.page - 1) },
                    onNext = { ShrutiApp.readAloud.jumpTo(tts.page + 1) },
                    onRate = {
                        val rates = listOf(0.8f, 1f, 1.2f, 1.4f, 1.6f, 2f)
                        val i = rates.indexOfFirst { it >= tts.rate - 0.001f }
                        ShrutiApp.readAloud.setRate(rates[if (i < 0 || i + 1 >= rates.size) 0 else i + 1])
                    },
                    onStop = { ShrutiApp.readAloud.stop() },
                )
                book.hasAudio -> {
                    val isThis = player.bookId == book.id
                    MiniPlayer(
                        book = book,
                        chapterIndex = if (isThis) player.chapterIndex else book.chapterIndex,
                        positionMs = if (isThis) player.positionMs else book.positionMs,
                        playing = isThis && player.playWhenReady,
                        buffering = isThis && player.buffering,
                        onOpen = onOpenPlayer,
                        onToggle = { if (isThis) ShrutiApp.player.toggle(book) else ShrutiApp.player.play(book) },
                        onReplay = { if (isThis) ShrutiApp.player.seekBy(-30_000) else ShrutiApp.player.play(book) },
                    )
                }
            }
        }
    }

    val doc = pdf
    if (goTo && doc != null) {
        var text by remember { mutableStateOf("${currentPage + 1}") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text("Go to page") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    singleLine = true, label = { Text("1 – ${doc.pageCount}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = (text.toIntOrNull() ?: 1).coerceIn(1, doc.pageCount) - 1
                    goTo = false
                    scope.launch { list.scrollToItem(p) }
                    if (ttsHere) ShrutiApp.readAloud.jumpTo(p)
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PageView(doc: PdfPages, index: Int, baseWidth: Int, renderWidth: Int, highlight: Boolean) {
    var bmp by remember(index) { mutableStateOf(doc.cached(index, renderWidth) ?: doc.cached(index, baseWidth)) }
    LaunchedEffect(index, renderWidth) { doc.render(index, renderWidth)?.let { bmp = it } }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / doc.ratios[index].coerceIn(0.1f, 10f))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = "Page ${index + 1}", modifier = Modifier.fillMaxSize())
        } else {
            Text("${index + 1}", color = Color.LightGray)
        }
        if (highlight) {
            Box(Modifier.align(Alignment.CenterStart).width(4.dp).fillMaxSize().background(Palette.Accent))
        }
    }
}

@Composable
private fun ReadAloudBar(
    page: Int, pageCount: Int, speaking: Boolean, preparing: Boolean, rate: Float,
    onToggle: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onRate: () -> Unit, onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.RecordVoiceOver, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Reading aloud", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(if (pageCount > 0) "Page ${page + 1} of $pageCount" else "Preparing…", color = Palette.TextDim, fontSize = 13.sp)
        }
        Text(
            "%sx".format(rate.toString().removeSuffix(".0")),
            modifier = Modifier
                .background(Palette.SurfaceHi, RoundedCornerShape(12.dp))
                .clickable(onClick = onRate)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 13.sp,
        )
        IconButton(onClick = onPrev) { Icon(Icons.Filled.SkipPrevious, "Previous page", tint = Palette.Text) }
        Box(
            Modifier
                .size(44.dp)
                .background(Color.White, RoundedCornerShape(50))
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (preparing && speaking) CircularProgressIndicator(Modifier.size(20.dp), color = Palette.Bg, strokeWidth = 2.dp)
            else Icon(if (speaking) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (speaking) "Pause" else "Play", tint = Palette.Bg)
        }
        IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, "Next page", tint = Palette.Text) }
        IconButton(onClick = onStop) { Icon(Icons.Filled.Close, "Stop", tint = Palette.TextDim) }
    }
}

