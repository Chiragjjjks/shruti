# Shruti

A personal audiobook player and PDF reader for Android, styled after the Audible app: dark navy UI, a cover carousel, a mini player above the tab bar, and a full-screen player.

It comes set up with two books — **Ikigai** (Héctor García & Francesc Miralles) and **7 Secrets of Shiva** (Devdutt Pattanaik). It includes their details but **not the book files**. You add a PDF and audiobook files you own, either from the app or bundled in at build time.

## Features

- **Home**: swipeable cover carousel with Listen / Resume, "Because you recently listened to", and your library row.
- **Book page**: cover, badge, rating, author / narrator, Play and Read PDF buttons, summary and chapter list.
- **Player**: plays in the background with lock-screen and notification controls. It has a scrubber, ±30 s, previous/next chapter, speed 0.75–2×, a sleep timer (minutes or end of chapter) and a chapter sheet. It remembers your place in every book.
- **Read along**: open the PDF while the audiobook plays. The player stays docked under the pages.
- **Read aloud**: no audiobook? The phone's text-to-speech voice reads the PDF page by page and turns the pages for you. You can change the speed.
- **PDF reader**: smooth scrolling, pinch zoom (double-tap to reset) and go-to-page. It remembers your last page.
- **Library**: filters for audiobooks, PDFs and finished books, with progress bars.

## Adding your books (on the phone)

1. Copy each book into its own folder on your phone: the audio files (`01 ….mp3`, `02 ….mp3`, or a single `.m4b`), the PDF and, if you like, a cover image.
2. In Shruti, open the book, tap **Add audiobook & PDF**, then **Choose book folder**.
3. The audio files become chapters in name order. The PDF and cover are picked up too.

You can also pick audio files or a PDF on their own, from the book page's **⋯** menu.

> DRM-protected files (Audible `.aax`/`.aaxc`, Kindle) can't be played. Use DRM-free files you're entitled to.

## Building

Requirements: Android SDK (platform 35, build-tools 35) and JDK 17.

```bash
scripts/build-apk.sh          # → dist/Shruti.apk
```

To pack files into the APK, see [bundle/README.md](bundle/README.md).

Install on your phone: copy `dist/Shruti.apk` over (or send it to yourself), open it, and allow "Install unknown apps" when asked.

## Tech

Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer + MediaSessionService, Android `PdfRenderer`, Android `TextToSpeech`, PDFBox-Android (text extraction). No network access, no accounts and no tracking.
