<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="App Icon" width="128"/>
  <h1>MD Notes Widget</h1>
  <p><b>Minimalist, beautiful, and battery-friendly Android widget for your Markdown notes.</b></p>

  <p>
    <a href="README.md">🇷🇺 Русская версия (Russian)</a>
  </p>
</div>

---

**MD Notes Widget** is an Android application that brings your "Second Brain" to your home screen. It randomly selects and beautifully displays Markdown (`.md`) files from a selected folder on your device. Designed specifically to work seamlessly with local Obsidian Vaults and other local Markdown editors, right out of the box.

## ✨ Key Features

* 🎲 **Smart Randomization**: Periodically updates the widget with a random note from your folder. Built-in repetition protection logic ensures you see fresh content.
* 🎨 **Adaptive & Beautiful UI**: Three gorgeous themes out of the box (Purple Signature, Pitch Black, Transparent). The widget dynamically scales and flawlessly truncates long text ensuring it looks great on any grid size.
* ⚡ **Obsidian Integration**: Deeply respects Obsidian paths. Tapping a widget opens the exact note directly directly inside the Obsidian Android app.
* 🏷️ **Tag Filtering**: Want to see only specific notes? Set a tag filter (e.g., `#quote`), and the widget will only display notes containing that tag.
* 🚫 **Folder Blacklisting**: Easily ignore specific sub-folders like `Templates`, `Daily Notes`, or `Archive` during scanning.
* 🔋 **Absolute Zero Battery Drain**: Operates locally using Android's modern `WorkManager`. The app sleeps completely between cycles, using absolutely zero battery or background CPU points. Updates are lightning fast due to an optimized JSON internal caching system.
* 🔒 **Privacy First**: Completely offline. No internet access required. Uses Android SAF (Storage Access Framework) to securely access only the specific folder you grant it.

## 🛠️ Requirements & Compatibility
* **Android 12.0+ (API 31)** to **Android 16+**.
* Highly tested and optimized for strict systems like MIUI / HyperOS.

## 📥 Installation

1. Go to the [Releases](https://github.com/SlamTGK/MDNotesWidget/releases) page.
2. Download the latest `app-release.apk`.
3. Install the APK on your Android device.

## ⚙️ How to Setup

1. Long press on your home screen and add the **MD Notes Widget**.
2. Tap the widget to open the Settings screen.
3. Tap **Select Folder** and use the Android system picker to select your Main Markdown Directory / Obsidian Vault.
4. Optional: Configure the update interval, widget theme, font sizes, tags, or folder blacklist.
5. Tap **Refresh all widgets** at the bottom.

## 👨‍💻 Author and Contributions

Made with ❤️ by [aristocrat](https://github.com/SlamTGK) 
Telegram: [@slamtgk](https://t.me/slamtgk)

Feel free to open an issue or submit a pull request if you want to contribute!
