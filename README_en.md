# MD Notes Widget

Local Markdown notes and home-screen widgets for **Android 12+ · Kotlin · version 3.2**.

[Русский](README.md) · [Download APK](https://github.com/SlamTGK/MDNotesWidget/releases/latest)

## Features

- Compact and expanded widgets with random notes, pinning, creation and manual refresh.
- **Per-widget settings:** folder, tags, theme, font, interval and quiet hours. Tap a widget title to configure it. The launcher screen sets defaults for **new** widgets.
- Markdown viewer with tables, code blocks, strikethrough, task lists and links. Local images appear in a separate preview strip.
- Wiki links `[[note]]`, `[[path/note|label]]` and relative Markdown links. Ambiguous filenames are not opened arbitrarily.
- **Autosaving editor**, local recovery drafts and external-change detection. Paging is disabled while editing. Empty notes can be saved; failed writes are reported.
- Search across filenames and full content, favorites and the last 50 opened notes.
- YAML and inline tags with AND/OR, exclusions, nested tags and favorites-only filtering. `work` includes `work/project`, not `workshop`.
- **App trash:** a local copy is persisted before the original file is deleted. Restore from the viewer menu into the selected root folder, without overwriting an existing file.
- Daily notes named `YYYY-MM-DD`, a configurable creation subfolder and templates using `{{date}}` / `{{title}}`.
- On this day: past years only; use YAML `date`/`created`, filename dates or modification dates.
- Custom colors with `#AARRGGBB` transparency, preview and contrast estimation on black.

## Setup

1. Install the APK from Releases.
2. Add a home-screen widget and select a folder using Android's system picker.
3. Wait for scanning. Read-only folders can be viewed; editing requires write access.
4. For Obsidian, select the **vault root** and optionally enter its name. Nested vault locations are supported.

Use “Rescan files” after changing files externally. Periodic updates also scan the tree; tag indexing rereads changed files. Providers without reliable modification dates are reread.

## Behavior and limits

- Background updates use **WorkManager**, at approximate intervals of 15 minutes or longer. Doze and battery restrictions can delay updates. Quiet hours apply only to automatic refresh.
- Xiaomi/HyperOS devices may require autostart and unrestricted battery access.
- Widgets show text **previews**, up to 2000/4000 characters. Open the viewer for the complete note. The expanded widget is not a scrolling editor.
- Autosave writes the actual file. “Discard unsaved edits” removes a draft and reloads the saved file; it does not undo completed autosaves.
- External edits preserve the local draft instead of overwriting the changed file. Copy any needed draft text before discarding a conflict. SAF does not offer universal cross-app transactional writes.
- Trash and drafts live in app data. Uninstalling or clearing app data removes them. They are not a replacement for vault backups.
- Common YAML tag forms are supported: inline arrays, single values and block lists. Advanced YAML and all Obsidian extensions are not supported. Tags inside code blocks are ignored.

## Build and verify

Install **JDK 17**, Android SDK Platform 36 and Build Tools 36.0.0. Set `ANDROID_HOME` or `sdk.dir` in a local `local.properties`.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The Wrapper pins Gradle 8.11.1 and verifies its distribution SHA-256. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

For release builds, provide `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, then run `./gradlew assembleRelease`. Release builds never fall back to a debug key.

CI runs tests, Android Lint and a debug build on pushes/PRs. Tags matching `v*` publish a signed release after checks pass. Repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Architecture

- `EditorViewModel`: URI-bound editing session and save queue.
- `NoteRepository`: writes, conflicts, drafts and trash.
- `MarkdownFileScanner`, `NoteMetadata`, `TagIndexManager`: SAF scanning and incremental indexing.
- `WidgetUpdateCoordinator` / `WidgetUpdateWorker`: independent widget background jobs.
- `AtomicJsonStore`: atomic local JSON persistence; list mutations are serialized.
- Markwon rendering and Material 3 UI.

Regression unit tests in `app/src/test/` cover metadata parsing, filters, settings isolation and atomic storage. Device release checks should include editor rotation/backgrounding, revoked SAF access, read-only providers, both widget types with separate folders, reboot, Doze and trash restoration.

Made by [@slamtgk](https://t.me/slamtgk).
