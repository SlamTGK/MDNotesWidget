<div align="center">
  <h1>MD Notes Widget v2.0</h1>
  <p><b>Minimalist, beautiful, and battery-friendly Android widget for your Markdown notes.</b></p>

  <p>
    <a href="README.md">Русская версия (Russian)</a>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Android-12%2B-green?logo=android" alt="Android 12+" />
    <img src="https://img.shields.io/badge/version-2.0-blue" alt="Version 2.0" />
    <img src="https://img.shields.io/badge/Kotlin-2.1-purple?logo=kotlin" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Material%20You-Dynamic%20Colors-orange" alt="Material You" />
  </p>
</div>

---

**MD Notes Widget** is an Android application that brings your "Second Brain" to your home screen. It randomly selects and beautifully displays Markdown (`.md`) files from a folder on your device. Designed specifically to work seamlessly with local Obsidian Vaults and other offline Markdown editors.

## What's New in v2.0

- **Full-screen note viewer** with swipe between random notes, Markdown rendering, and image previews
- **Pin notes** to the widget — pin button next to the title
- **List widget** — new widget type with swipeable note cards (StackView)
- **Material You / Monet** — system dynamic colors on Android 12+
- **Custom widget colors** — pick background, text, and title colors
- **Quiet hours** — configurable period when the widget won't update
- **Multiple tags + AND/OR logic** — advanced note filtering
- **Note history** — last 50 viewed notes with the ability to revisit
- **Localization** — full Russian and English support
- **Built-in viewer** — new option for opening notes (alongside Obsidian and system chooser)
- Numerous bug fixes and performance optimizations

## Key Features

### Widgets
- **Random note** — classic widget that periodically shows a new note
- **List widget** — note cards with swipe navigation right on the home screen
- **Pin** — tap the pin icon to keep a note on the widget
- **Themes** — Purple, Dark, Transparent, or custom colors
- **Font size** — Small / Medium / Large
- **Auto-adaptation** — widget automatically adjusts line count to fit its size

### Note Viewer
- **Full-screen viewer** with Markdown rendering (headings, lists, checkboxes)
- **Swipe** between random notes
- **Image previews** from notes
- **History** — feed of last 50 viewed notes
- **Open in** Obsidian or any other app

### Settings
- **SAF folder** — secure access to a specific folder via Storage Access Framework
- **Update interval** — from 30 minutes to 24 hours, or disable
- **Tag filtering** — multiple tags separated by `;`, AND/OR logic
- **Folder blacklist** — exclude directories (Templates, Archive, etc.)
- **Quiet hours** — widget won't update at night (configurable period)
- **Material You** — dynamic colors on Android 12+ (Monet)

### Technical Highlights
- **Kotlin + Coroutines** — asynchronous processing without blocking UI
- **Material 3** — modern design with Dynamic Colors
- **Battery efficiency** — AlarmManager with JSON caching
- **Full privacy** — no internet, no tracking, local files only
- **R8 minification** — optimized APK size

## Requirements

- **Android 12.0+** (API 31) and above
- Optimized for MIUI / HyperOS

## Installation

1. Go to the [Releases](https://github.com/SlamTGK/MDNotesWidget/releases) page
2. Download `app-release.apk`
3. Install the APK on your device

> **Xiaomi / HyperOS**: For stable scheduled updates, enable "Autostart" and set Battery Saver to "No Restrictions" for this app.

## Build from Source

```bash
# Clone the repository
git clone https://github.com/SlamTGK/MDNotesWidget.git
cd MDNotesWidget

# Build debug APK (requires Android SDK and JDK 17)
./gradlew assembleDebug

# Or use GitHub Actions — APK is built automatically
```

## Project Structure

```
app/src/main/java/com/mdnotes/widget/
├── MDNotesApp.kt              # Application — Material You Dynamic Colors
├── MainActivity.kt            # Settings screen
├── NoteViewerActivity.kt      # Full-screen note viewer
├── HistoryActivity.kt         # Viewed notes history
├── NoteWidgetProvider.kt      # Main widget provider
├── NoteListWidgetProvider.kt  # List widget provider
├── NoteListWidgetService.kt   # Data service for StackView
├── PreferencesManager.kt      # Settings and state storage
├── MarkdownFileScanner.kt     # .md file scanning and parsing
└── FileOpener.kt              # File opening (Obsidian/System/Viewer)
```

## Author

Made with care by [aristocrat](https://github.com/SlamTGK)
Telegram: [@slamtgk](https://t.me/slamtgk)

## License

This project is distributed as-is for personal use.
