# MD Notes Widget

Виджет для рабочего стола Android, который показывает случайные Markdown-заметки из выбранной папки.

## Возможности

- 📂 **Выбор папки** через системный диалог (SAF, не нужны опасные разрешения)
- 📝 **Рекурсивный поиск** `.md` файлов во всех подпапках
- 🎲 **Случайная заметка** отображается в виджете на рабочем столе
- ⏰ **Автообновление** с настраиваемым интервалом (1–24 ч) через WorkManager
- 🔄 **Кнопка обновить** прямо на виджете
- 📱 **Открытие в Obsidian** или системном выборе приложений
- 🌙 **Красивый тёмный дизайн** — градиент, скруглённые углы, акцентная полоска

## Минимальные требования

- Android 16 (API 36)
- Разрешения: только доступ к папке через системный диалог

## Установка и сборка

### Вариант 1: GitHub Actions (рекомендуется)

1. Создайте репозиторий на GitHub и загрузите код
2. Перейдите в **Actions** → запуск произойдёт автоматически при пуше
3. После завершения скачайте APK из раздела **Artifacts**:
   - `MDNotesWidget-debug` — для тестирования
   - `MDNotesWidget-release` — для установки

**Создание Release**: добавьте тег версии:
```bash
git tag v1.0.0
git push origin v1.0.0
```
GitHub Actions автоматически создаст Release с APK.

### Вариант 2: Android Studio

1. Откройте папку `MDNotesWidget/` в Android Studio (версия Meerkat или новее)
2. Дождитесь синхронизации Gradle (файлы обёртки создадутся автоматически)
3. **Build → Build Bundle(s)/APK(s) → Build APK(s)**
4. APK будет в `app/build/outputs/apk/debug/`

### Вариант 3: Командная строка (если установлен Gradle 8.11+)

```bash
cd MDNotesWidget
gradle assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

## Использование виджета

1. Установите APK на устройство
2. Откройте приложение и выберите папку с `.md` файлами
3. Настройте интервал обновления и приложение для открытия
4. Долгим нажатием на рабочий стол добавьте виджет **MD Notes Widget**
5. Разместите и измените размер по желанию

## Структура проекта

```
MDNotesWidget/
├── .github/workflows/build.yml      # GitHub Actions CI
├── app/src/main/
│   ├── java/com/mdnotes/widget/
│   │   ├── NoteWidgetProvider.kt    # Главный AppWidgetProvider
│   │   ├── WidgetUpdateWorker.kt    # WorkManager Worker
│   │   ├── MainActivity.kt          # Экран настроек
│   │   ├── MarkdownFileScanner.kt   # Сканирование папок через SAF
│   │   ├── FileOpener.kt            # Открытие в Obsidian/системном приложении
│   │   └── PreferencesManager.kt   # SharedPreferences
│   └── res/
│       ├── layout/                  # widget_note, activity_main, …
│       ├── drawable/                # Градиенты, иконки
│       └── xml/note_widget_info.xml # Метаданные виджета
└── build.gradle.kts
```

## Открытие в Obsidian

Виджет открывает файлы через URL-схему `obsidian://open?path=<путь>`.
Это работает для файлов во внешнем хранилище (типичное расположение хранилища Obsidian).
Если Obsidian не установлен или путь не определяется — автоматически используется системный выбор приложений.

## GitHub Actions: автоматическая сборка

Файл `.github/workflows/build.yml` настраивает:
- **Триггер**: при каждом пуше в `main`/`master`
- **JDK**: Temurin 17
- **Gradle**: 8.11 (загружается автоматически)
- **Android SDK**: API 36, Build Tools 36.0.0
- **Артефакты**: debug + release APK (хранятся 30 дней)
