# PolyPic: Open Source Android File-to-Image Packer

**PolyPic** is an open-source Android utility written in Kotlin & Jetpack Compose that bundles multiple arbitrary files (PDFs, spreadsheets, text documents, archives) into a standalone PNG image file.

The output image features an automatically generated **visual index card** listing the archive title, timestamps, file names, file types, and sizes directly on the graphic, allowing you to instantly identify the stored contents from any standard photo viewer or gallery.

---

## Key Features

- **Automated Visual Index Card**: Dynamically generates an image card listing all included files, badges for each file format, sizes, and total count.
- **Polyglot PNG + ZIP Format**: The resulting image is 100% valid PNG format for image viewers, while simultaneously remaining a valid ZIP archive extractable with `unzip` or the app's extraction tab.
- **Direct Gallery & MediaStore Integration**: Exports directly to `Pictures/PolyPic` so Android photo apps and cloud backup services detect it immediately.
- **Built-in Extractor**: Pick any polyglot image, preview the embedded files, and restore them directly to your `Downloads` directory.
- **Modern Jetpack Compose UI**: Built with Material Design 3 and dark theme support.
- **Automated GitHub Actions CI**: Compiles and releases installable APK artifacts on every commit or release tag.

---

## Technical Specifications

### How It Works

1. **PNG Termination**: Standard PNG files terminate with the 12-byte `IEND` chunk (`00 00 00 00 49 45 4E 44 AE 42 60 82`). Image renderers stop processing once this marker is encountered.
2. **ZIP Tail Reading**: The ZIP archive format stores its index (End of Central Directory) at the tail end of the byte stream and reads backwards.
3. **Polyglot Structure**: Appending a compressed ZIP stream after the PNG's `IEND` chunk produces a file readable by standard image decoders as a picture, and by zip utilities as an archive.

### Google Photos & Cloud Storage Notes

- **Backup Quality**: When uploading to Google Photos, ensure backup is set to **Original Quality**. The **Storage Saver** mode compresses images on the server, which strips extra trailing bytes after the image container.
- **Extraction**: To extract, ensure you download the original image rather than a downsampled web preview.

---

## Building the Project

### Requirements
- Android SDK 34
- JDK 17
- Gradle 8.7+

### Command Line
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## License

This project is open-source under the [MIT License](LICENSE).
