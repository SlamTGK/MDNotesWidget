<div align="center">
  <h1>MD Notes Widget</h1>
  <p><b>A Markdown note widget for your Android home screen.</b></p>

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

**MD Notes Widget** is an Android app for people who store notes in Markdown. It randomly displays `.md` files from a chosen folder directly on your home screen. Works great with **Obsidian**, Logseq, and any other local Markdown vault.

## Widgets

| Widget | Description |
|--------|-------------|
| **Random Note** | Displays one note. Adapts line count to widget size |
| **Extended Widget** | Shows the full note with maximum content |

Common widget features:
- 📌 **Pin** — keep the current note on screen
- ➕ **Create** — quickly create a `.md` file right from the widget
- 🔄 **Refresh** — show a different random note
- 🏷️ **Tag status** — shown inline with date and folder: `Apr 24 2026, Psychology, #favorites`
- 🎨 **Themes** — Purple, Dark, Transparent, Custom
- 🔡 **Font size** — Small / Medium / Large

## Note Viewer

- **Markdown rendering** — headings, **bold**, _italic_, ~~strikethrough~~, ☑ checkboxes, lists
- **Editor** — ✏️ button toggles edit mode with monospace font and auto-save
- **Images** — horizontal scroll with image previews from the note
- **Swipe** — swipe between random notes
- **Search** — live search by note title and content
- **Favorites** — add with one tap, swipe to remove
- **History** — last 50 opened notes with swipe actions
- **On This Day** — notes from this day in past years
- **Menu ⋮** — share, open in Obsidian, open externally, delete

## Filtering & Settings

- **Tags** — enter tags separated by commas (`favorites, daily`); the widget shows only matching notes
- **Tag search** — finds `#tags` in YAML frontmatter and inline in note text
- **Auto-suggestions** — ChipGroup shows tags from your actual notes
- **Folder blacklist** — exclude folders from scanning (`.trash`, `templates`, etc.)
- **Anti-repeat** — last 20 shown notes are excluded from random selection
- **Update interval** — off / 15 min / 30 min / 1–24 h
- **Quiet hours** — widget won't update at night
- **Click action** — built-in viewer / Obsidian / system chooser

## Installation

1. Download the APK from [Releases](../../releases/latest)
2. Allow installation from unknown sources
3. Install the APK
4. Long-press on your home screen and add the **MD Notes Widget**
5. In widget settings, select your folder with `.md` files

> **Xiaomi / HyperOS:** For reliable scheduled updates, enable "Autostart" and set Battery Saver to "No Restrictions" for this app.

## Requirements

- Android 12+ (API 31)
- Folder access permission (via system dialog, no root required)

## Tech Stack

- **Kotlin** + Android SDK
- **Material You** — dynamic system colors
- **SAF** (Storage Access Framework) — secure file access
- **AlarmManager** — reliable periodic widget updates
- **Coroutines** — async operations

## Project Structure

```
app/src/main/java/com/mdnotes/widget/
├── MainActivity.kt            # Settings screen
├── NoteViewerActivity.kt      # Full-screen viewer + editor + search
├── HistoryActivity.kt         # Note history with swipe actions
├── FavoritesActivity.kt       # Favorites with swipe actions
├── NoteWidgetProvider.kt      # Main widget
├── NoteListWidgetProvider.kt  # Extended widget
├── WidgetThemeHelper.kt       # Centralized widget theming
├── PreferencesManager.kt      # Settings & state storage
├── MarkdownFileScanner.kt     # Scanning, rendering, tag extraction
├── TagIndexManager.kt         # Tag → URI index with JSON persistence
├── NoteListAdapter.kt         # Universal RecyclerView adapter
└── FileOpener.kt              # File opening (Obsidian / System / Viewer)
```

---

<div align="center">
  <p>Made with ❤️ by <a href="https://t.me/slamtgk">@slamtgk</a></p>
</div>
