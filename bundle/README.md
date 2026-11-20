# Bundling your own book files into the APK

Optional — you can also add files from inside the app on your phone.

Put files you own here, one folder per book id, then run `scripts/build-apk.sh`:

```
bundle/books/ikigai/
    book.pdf            # any *.pdf
    cover.jpg           # optional, any .jpg/.png/.webp
    audio/01 ....mp3    # chapters, sorted by name (mp3, m4a, m4b, aac, ogg, opus, wav, flac)
bundle/books/seven-secrets-of-shiva/
    book.pdf
    audio/...
```

Everything under `bundle/books/` is git-ignored, so book files never end up in the repository.
