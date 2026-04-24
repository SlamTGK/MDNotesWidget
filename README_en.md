<div align="center">
  <h1>MD Notes Widget v3.1</h1>
  <p><b>Minimalist, beautiful, and battery-friendly Android widget for your Markdown notes.</b></p>

  <p>
    <a href="README.md">Русская версия (Russian)</a>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Android-12%2B-green?logo=android" alt="Android 12+" />
    <img src="https://img.shields.io/badge/version-3.1-blue" alt="Version 3.1" />
    <img src="https://img.shields.io/badge/Kotlin-2.1-purple?logo=kotlin" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Material%20You-Dynamic%20Colors-orange" alt="Material You" />
  </p>
</div>

---

**MD Notes Widget** is an Android application that brings your "Second Brain" to your home screen. It randomly selects and beautifully displays Markdown (`.md`) files from a folder on your device. Designed specifically to work seamlessly with local Obsidian Vaults and other offline Markdown editors.

## What's New in v3.1

### 🏷️ Tags Merged into Meta Line
- Tags now appear on one line with date and folder on the widget
- Example: `January 1, 2026, Psychology, #favorites`
- Removed the separate tag row — everything is compact in one place

### ✏️ Edit Button (FAB) Improved
- Icon changed from `+` to a pencil
- Button is 2x smaller (mini size, 36dp)
- Slightly raised for better usability

### 🏷️ Simplified Tag Filter
- Removed AND/OR logic — now always OR (any matching tag)
- Tags separated by comma: `favorites, daily`

## What's New in v3.0

### 🏷️ Tag Filtering — Completely Redesigned
- **Tag Index** — builds a `tag → files` index during scan for instant filtering (no re-reading files)
- **ChipGroup UI** — convenient chips with delete button and autocomplete from real tags
- **Correct badge** — shows "X of Y files (#tag)" instead of total count
- **Widget tag status** — widget displays a `🏷️ #tag` chip when filter is active
- **Inline #hashtag support** — finds tags in YAML frontmatter AND in note body

### ✏️ Built-in Markdown Editor
- **FAB toggle** — switches between view and edit mode
- **Monospace font** for code editing
- **Auto-save** — changes are written back to file via SAF
- **Instant re-render** — note re-renders after saving

### 📅 On This Day
- New tab in bottom navigation
- Shows notes created/modified on this day in previous years
- Nostalgic feature for reviewing old entries

### 🔍 Improved Search
- **Debounced search** (300ms) — doesn't overload device on fast typing
- **Match highlighting** — found text highlighted in yellow in title and preview
- **Unified adapter** — search, history, and favorites share NoteListAdapter

### 👆 Swipe Actions
- **← Swipe left** in history/favorites → remove from list (red background)
- **→ Swipe right** → open in Obsidian (purple background)

### ⚡ Performance
- **SAF ContentResolver.query()** — file scanning 10-50x faster (replaces DocumentFile.listFiles())
- **LRU cache** for rendered SpannableStringBuilder — smooth scrolling
- **Bitmap downsampling** — images loaded with inSampleSize, no OOM
- **requestLayout() fix** — spannable rendering wrapped in `post {}`, no flickering
- **Thread-safe** SimpleDateFormat via ThreadLocal

### 🏗️ Architecture
- **WidgetThemeHelper** — unified theme logic for both widgets
- **TagIndexManager** — tag index manager with JSON persistence
- **NoteListAdapter** — universal adapter replacing 3 duplicates
- **FileOpener** — centralized Obsidian URI building

## Key Features

### Widgets
- **Random note** — periodically shows a new note, adapts line count to widget size
- **List widget** — displays full note with scrolling
- **Pin** — tap 📌 to keep a note on the widget
- **Tag status** — active tag chip displayed on widget
- **Themes** — Purple, Dark, Transparent, or custom colors
- **Font size** — Small / Medium / Large
- **Auto-adaptation** — widget automatically adjusts line count to fit its size

### Note Viewer
- **Markdown rendering** — headings, **bold**, _italic_, ~~strikethrough~~, ☑ checkboxes, • lists
- **Markdown editor** — edit notes with monospace font and auto-save
- **Image previews** from notes (with downsampling)
- **Swipe** between random notes
- **Search** — live debounced search with match highlighting
- **Favorites** — add/remove with one tap + swipe to remove
- **History** — last 50 notes + swipe actions
- **On This Day** — notes from this day in past years
- **Create** — create new `.md` note
- **Menu ⋮** — share, open in Obsidian, open externally, delete

### Filtering
- **Tags (ChipGroup)** — input via chips with autocomplete suggestions
- **Tag index** — instant filtering without reading files
- **Frontmatter + inline** — supports YAML `tags:` and inline `#hashtags`
- **OR logic** — shows notes matching any of the specified tags
- **Folder blacklist** — exclude directories from scanning
- **Anti-repeat** — last 20 shown notes excluded from random selection

### Settings
- Update interval: off / 15 min / 30 min / 1–24 hours
- Quiet hours — widget won't update during specified period
- Open action: built-in viewer / Obsidian / system chooser

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
├── MainActivity.kt            # Settings screen (ChipGroup for tags)
├── NoteViewerActivity.kt      # Full-screen viewer + editor + search
├── HistoryActivity.kt         # History with swipe actions
├── FavoritesActivity.kt       # Favorites with swipe actions
├── NoteWidgetProvider.kt      # Main widget provider
├── NoteListWidgetProvider.kt  # List widget provider
├── WidgetThemeHelper.kt       # Centralized widget theming
├── PreferencesManager.kt      # Settings and state storage
├── MarkdownFileScanner.kt     # .md scanning + rendering + tag extraction
├── TagIndexManager.kt         # Tag → URI index with JSON persistence
├── NoteListAdapter.kt         # Universal RecyclerView adapter
└── FileOpener.kt              # File opening (Obsidian/System/Viewer)
```

## Author

Made with care by [aristocrat](https://github.com/SlamTGK)
Telegram: [@slamtgk](https://t.me/slamtgk)

## License

This project is distributed as-is for personal use.
