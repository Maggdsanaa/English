# Third-party components and license compatibility

| Component | Use | License |
|---|---|---|
| Tesseract OCR 5.x + Leptonica (via Tesseract4Android 4.9.0, cz.adaptech) | OCR engine, JNI wrapper | Apache-2.0 (Leptonica: BSD-2; libjpeg/libpng: permissive) |
| tessdata_fast `ara`, `eng` | Bundled language models | Apache-2.0 |
| PdfBox-Android 2.0.27.0 (com.tom-roush) | Read PDF, detect text, write invisible text layer, recompress images | Apache-2.0 |
| Amiri-Regular.ttf | Embedded font for the invisible text layer (Arabic + Latin) | SIL OFL-1.1 (`app/src/main/assets/fonts/OFL-Amiri.txt`); embedding in PDFs is permitted |
| AndroidX, Compose, Room, WorkManager, kotlinx | App framework | Apache-2.0 |
| OCRmyPDF | **Design inspiration only; no code copied.** | MPL-2.0 (file-level copyleft; not triggered because no files are used) |

All licenses are compatible with distributing this app under Apache-2.0. Keep the Apache/OFL notices when redistributing.
Avoided on purpose: MuPDF/iText (AGPL), Ghostscript (AGPL), commercial PDF SDKs.
