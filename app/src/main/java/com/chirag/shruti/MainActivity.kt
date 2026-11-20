package com.chirag.shruti

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.chirag.shruti.data.Book
import com.chirag.shruti.ui.components.BottomNav
import com.chirag.shruti.ui.components.MiniPlayer
import com.chirag.shruti.ui.components.Tab
import com.chirag.shruti.ui.screens.BookDetailScreen
import com.chirag.shruti.ui.screens.DiscoverScreen
import com.chirag.shruti.ui.screens.HomeScreen
import com.chirag.shruti.ui.screens.LibraryScreen
import com.chirag.shruti.ui.screens.PlayerScreen
import com.chirag.shruti.ui.screens.ProfileScreen
import com.chirag.shruti.ui.screens.ReaderScreen
import com.chirag.shruti.ui.theme.Palette
import com.chirag.shruti.ui.theme.ShrutiTheme

sealed interface Route {
    data class Detail(val id: String) : Route
    data object Player : Route
    data class Reader(val id: String, val readAloud: Boolean) : Route
}

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ShrutiApp.player.connect()
        setContent { ShrutiTheme { ShrutiRoot() } }
    }
}

@Composable
private fun ShrutiRoot() {
    val context = LocalContext.current
    val books by ShrutiApp.repo.books.collectAsState()
    val player by ShrutiApp.player.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val stack = remember { mutableStateListOf<Route>() }

    fun open(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun book(id: String) = books.firstOrNull { it.id == id }

    BackHandler(stack.isNotEmpty()) { back() }
    BackHandler(stack.isEmpty() && tab != Tab.Home) { tab = Tab.Home }

    LaunchedEffect(player.error) {
        player.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            ShrutiApp.player.clearError()
        }
    }

    val miniBook: Book? = player.bookId?.let(::book)
        ?: books.filter { it.hasAudio && it.lastPlayed > 0 }.maxByOrNull { it.lastPlayed }

    fun listen(b: Book) = when {
        b.hasAudio && player.bookId == b.id && player.playWhenReady -> ShrutiApp.player.pause()
        b.hasAudio -> ShrutiApp.player.play(b)
        b.hasPdf -> open(Route.Reader(b.id, readAloud = true))
        else -> open(Route.Detail(b.id))
    }

    val top = stack.lastOrNull()
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = top ?: tab,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "nav",
            ) { target ->
                when (target) {
                    Tab.Home -> HomeScreen(books, player.bookId.takeIf { player.playWhenReady }, onOpenBook = { open(Route.Detail(it)) }, onListen = ::listen, onSearch = { tab = Tab.Library })
                    Tab.Library -> LibraryScreen(
                        books,
                        onOpenBook = { open(Route.Detail(it)) },
                        onPlay = { ShrutiApp.player.play(it); open(Route.Player) },
                        onRead = { open(Route.Reader(it.id, false)) },
                    )
                    Tab.Discover -> DiscoverScreen(books) { open(Route.Detail(it)) }
                    Tab.Profile -> ProfileScreen()
                    is Route.Detail -> book(target.id)?.let { b ->
                        BookDetailScreen(
                            b, player,
                            onBack = ::back,
                            onOpenPlayer = { open(Route.Player) },
                            onRead = { readAloud -> open(Route.Reader(b.id, readAloud)) },
                        )
                    }
                    is Route.Reader -> book(target.id)?.let { b ->
                        ReaderScreen(b, player, target.readAloud, onBack = ::back, onOpenPlayer = { open(Route.Player) })
                    }
                    Route.Player -> {
                        val b = miniBook
                        if (b == null) LaunchedEffect(Unit) { back() } else PlayerScreen(
                            b, player,
                            onClose = ::back,
                            onRead = { open(Route.Reader(b.id, false)) },
                        )
                    }
                    else -> Unit
                }
            }
        }
        val chrome = top == null || top is Route.Detail
        if (chrome) {
            miniBook?.let { b ->
                val isThis = player.bookId == b.id
                MiniPlayer(
                    book = b,
                    chapterIndex = if (isThis) player.chapterIndex else b.chapterIndex,
                    positionMs = if (isThis) player.positionMs else b.positionMs,
                    playing = isThis && player.playWhenReady,
                    buffering = isThis && player.buffering,
                    onOpen = { open(Route.Player) },
                    onToggle = { ShrutiApp.player.toggle(b) },
                    onReplay = { if (isThis) ShrutiApp.player.seekBy(-30_000) else ShrutiApp.player.play(b) },
                )
            }
            BottomNav(if (top == null) tab else null) { tab = it; stack.clear() }
        }
    }
}
