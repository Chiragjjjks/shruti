package com.chirag.shruti.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chirag.shruti.ShrutiApp
import com.chirag.shruti.data.Book
import com.chirag.shruti.ui.components.BookCover
import com.chirag.shruti.ui.theme.Palette

@Composable
fun DiscoverScreen(books: List<Book>, onOpenBook: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Discover", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card {
            Text("Add your books", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Steps(
                "Copy the audiobook files (MP3 / M4A / M4B) and the PDF you own to your phone — one folder per book.",
                "Open the book here, tap “Add files” → “Choose book folder”, and pick that folder.",
                "Audio files become chapters in name order (01, 02 …). A PDF and a cover image in the folder are picked up too.",
            )
        }
        Spacer(Modifier.height(16.dp))
        Card {
            Text("Read & listen together", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Open the PDF while the audiobook plays — the player stays docked at the bottom of the reader. " +
                    "No audiobook? Tap the voice button in the reader and your phone reads the PDF aloud, turning pages as it goes.",
                color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("In your library", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        books.forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBook(b.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(b, Modifier.size(56.dp), corner = 4.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.title, fontWeight = FontWeight.SemiBold)
                    Text(b.authors, color = Palette.TextDim, fontSize = 13.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.TextDim)
            }
        }
    }
}

@Composable
fun ProfileScreen() {
    val context = LocalContext.current
    val player by ShrutiApp.player.state.collectAsState()
    val tts by ShrutiApp.readAloud.state.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Profile", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card {
            SettingTitle(Icons.Filled.Speed, "Narration speed", "%.2fx".format(player.speed))
            Slider(
                value = player.speed, onValueChange = { ShrutiApp.player.setSpeed((it * 20).toInt() / 20f) },
                valueRange = 0.5f..3f, colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent),
            )
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SettingTitle(Icons.Filled.RecordVoiceOver, "Read-aloud voice speed", "%.2fx".format(tts.rate))
            Slider(
                value = tts.rate, onValueChange = { ShrutiApp.readAloud.setRate((it * 20).toInt() / 20f) },
                valueRange = 0.5f..2.5f, colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Open Settings → Accessibility → Text-to-speech", Toast.LENGTH_LONG).show()
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Change the voice (phone text-to-speech settings)", color = Palette.Link, modifier = Modifier.weight(1f), fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Link)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SettingTitle(Icons.Filled.Folder, "Your files", null)
            Text(
                "Books you add are read from your phone's storage. PDFs are copied into the app so the reader always opens them. " +
                    "If you move or delete an audio folder, add it again from the book page.",
                color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SettingTitle(Icons.Filled.Info, "About", null)
            Text("Shruti 1.0 — a personal audiobook player and PDF reader.", color = Palette.TextDim, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingTitle(icon: ImageVector, title: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.Accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Palette.TextDim)
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Palette.Surface, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Steps(vararg steps: String) {
    steps.forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 4.dp)) {
            Text("${i + 1}.", color = Palette.Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
            Text(s, color = Palette.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
