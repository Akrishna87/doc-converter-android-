# Doc Converter (Android)

A native Android app (Kotlin + Jetpack Compose) that converts documents between formats **entirely on-device**. No uploads, no network permission.

## Install

1. Open the [latest release](../../releases/latest) on your phone and download `DocConverter-*.apk`.
2. Open it and allow "Install unknown apps" for your browser when asked.
3. Launch **Doc Converter**. Needs Android 10 or newer.

## Features

| From | To |
|------|----|
| JPG / PNG / WEBP | the other image formats, PDF |
| PDF | PNG / JPG / WEBP (one per page), TXT, Markdown, DOCX (text extraction), PDF (extract pages or split) |
| Word (.docx) | PDF, TXT, Markdown (headings, bullets) |
| Text (.txt) | PDF, Markdown, DOCX |
| Markdown (.md) | PDF (headings and lists rendered), TXT, DOCX |

- **Batch:** pick many files at once and convert them all; failures are reported per file.
- **Combine:** merge images, PDFs, Word, text or Markdown files into a single PDF.
- **PDF pages:** keep a range such as `1-3,5,8-` or split into one file per page (real PDF pages, not rasterised).
- **Settings:** JPG/WEBP quality, PDF render resolution, A4/Letter page size, font size.
- Results are saved to `Downloads/DocConverter`; open or share them. Files can also be sent in from the share sheet.
- Scanned (image-only) PDFs have no text to extract, so PDF to TXT/MD/DOCX reports an error for them. OCR is not included.

## Build

Release signing reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` from the environment. The release workflow fills these from the GitHub secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. Without them the APK is signed with the debug key, which still installs.

Requires JDK 17 and the Android SDK (API 34).

```
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

The APK lands in `app/build/outputs/apk/debug/`. GitHub Actions builds it on every push (see the `Build` workflow artifacts).

## Roadmap

- OCR for scanned PDFs
- Password-protected PDFs
- Rich formatting (tables, images) in DOCX conversion
