# Doc Converter (Android)

A native Android app (Kotlin + Jetpack Compose) that converts documents between formats **entirely on-device**. No uploads, no network permission.

## Supported conversions (v0.1)

| From | To |
|------|----|
| JPG / PNG / WEBP | the other image formats, PDF |
| PDF | PNG, JPG, WEBP (one image per page) |
| Text (.txt) | PDF, Markdown |
| Markdown (.md) | PDF (headings and lists rendered), Text |

Results are saved to `Downloads/DocConverter` and can be opened or shared. Files can also be sent to the app from the share sheet.

## Build

Requires JDK 17 and the Android SDK (API 34).

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. GitHub Actions builds it on every push (see the `Build` workflow artifacts).

## Roadmap

- DOCX to PDF/text, and text to DOCX
- Merge/split PDFs, page range selection
- Batch conversion, quality settings
