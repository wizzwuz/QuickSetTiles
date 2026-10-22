# QuickSetTiles

<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_foreground.xml" width="96" height="96" alt="QuickSetTiles Logo" />
</p>

<p align="center">
  <b>A powerful, modern Android Quick Settings tile creator and manager built with Jetpack Compose & Material 3.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg" alt="Platform" />
  <img src="https://img.shields.io/badge/MinSdk-24-blue.svg" alt="MinSdk" />
  <img src="https://img.shields.io/badge/TargetSdk-35-orange.svg" alt="TargetSdk" />
  <img src="https://img.shields.io/badge/Material-3-purple.svg" alt="Material 3" />
  <img src="https://img.shields.io/badge/Shizuku-Enabled-red.svg" alt="Shizuku" />
</p>

---

## ✨ Features

- **Intuitive Bottom Navigation**:
  - **Active Tiles Tab** (Left): Displays currently active quick settings tiles.
    - *Tap*: Inactivates the tile and moves it to the Inactive tab.
    - *Long press*: Opens the tile editor.
  - **Floating Plus (+) Button** (Center): Elevated Material 3 FAB to create new quick setting tiles.
  - **Inactive Tiles Tab** (Right): Displays inactive tiles.
    - *Tap*: Activates the tile and assigns it to an active quick settings slot.
    - *Long press*: Opens the tile editor or delete options.

- **8 Versatile Tile Types**:
  1. 📱 **Apps**: Floating menu with real-time search across all apps (including system apps).
  2. 🌐 **Website**: Add a custom label and URL to launch directly in your default browser.
  3. ⚡ **Shortcut**: Select from inbuilt application shortcuts.
  4. 🧩 **Activity**: Full-page explorer of installed apps with accordion drop-downs of all activities.
     - *Tap*: Selects the activity for your tile.
     - *Long press*: Live test launch to verify execution before saving!
  5. 📨 **Intent**: Custom broadcast or activity intents with package name, action, and URI.
  6. 🐚 **Shell**: Execute shell commands or scripts using **Shizuku API** or Superuser root (`su`) with live output feedback.
  7. 📄 **Files**: Select any file to open instantly from your Quick Settings panel.
  8. 📁 **Folders**: Select any directory to open in the system file manager.

- **🎨 Advanced Icon Customization**:
  - Live icon preview reflecting all adjustments in real time.
  - Choose custom images from device storage via file picker.
  - Choose from built-in preset vector icons (Web, Terminal, Folder, File, Settings, Star, Flashlight, etc.).
  - Default App Icon automatically applied when picking an App or Activity.
  - Sliders for **Outline Thickness** (0 dp to 10 dp), **Outline Threshold** (0% to 100%), and **Icon Size** (16 dp to 64 dp).

- **⚡ Quick Settings Tile Integration**:
  - 15 pre-configured `TileService` slots dynamically mapped to your active tiles.
  - Direct prompt to add tiles to the status bar on modern Android devices.

- **🌙 Material 3 Design**:
  - Dynamic Color theming.
  - Full Dark Mode and Light Mode support.

---

## 🚀 Building & Installation

Automated builds are executed via **GitHub Actions**. Every push to `main` compiles the debug APK and uploads it to GitHub Workflow Artifacts:

1. Navigate to the **Actions** tab on GitHub: [QuickSetTiles Actions](https://github.com/wizzwuz/QuickSetTiles/actions).
2. Click on the latest workflow run.
3. Download the `QuickSetTiles-debug-apk` artifact.
4. Install the `.apk` on your Android device.

To build locally:
```bash
./gradlew assembleDebug
```
