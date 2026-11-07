# Doc Converter (Android)

A native Android app (Kotlin + Jetpack Compose) that converts documents between formats **entirely on-device**. No uploads, no network permission.

## Features (v0.2)

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

Requires JDK 17 and the Android SDK (API 34).

```
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. GitHub Actions builds it on every push (see the `Build` workflow artifacts).

## Roadmap

- OCR for scanned PDFs
- Password-protected PDFs
- Rich formatting (tables, images) in DOCX conversion
