## QuickSetTiles v1.0.1 🚀

A fast, customizable Android Quick Settings tile creator and manager.

### 🌟 What's Changed

- **📦 Full Custom Icons Backup & Restore (.bak)**
  - Backups now use a standard `.bak` format that bundles all custom icons picked from your file picker (PNG, SVG, WebP, JPG, GIF).
  - Restoring a backup on a new device or fresh install now restores all custom icons seamlessly and updates icon paths automatically.
  - Backwards-compatible: You can still restore older `.json` backups at any time.

- **🔍 Backup Content Preview**
  - Before restoring, the app now shows an informative summary dialog displaying the exact number of tiles and custom icons inside the backup file.

- **☕ Support Section & Buy Me A Coffee**
  - Added a dedicated Support section under Settings / About with a link to support project development.

- **⚡ Performance & Clean Architecture**
  - Package namespace consolidated to `com.wizzwuz.quicksettiles`.
  - Streamlined backup archive generation with streaming ZIP compression.

---

### 📋 Full Changelog (v1.0.0 vs v1.0.1)
Comparing changes between **v1.0.0** and **v1.0.1**:
https://github.com/wizzwuz/QuickSetTiles/compare/v1.0.0...v1.0.1

- `feat(backup)`: Support `.bak` ZIP container format bundling both `backup.json` and raw image assets in `icons/`
- `feat(backup)`: Automatic detection and extraction of custom icons from `.bak` archives into internal storage
- `feat(backup)`: Preview custom icon counts and tile counts in the restore confirmation dialog
- `refactor`: Remap custom icon file paths across single tiles, toggle tiles, collections, and multi-shell steps
- `chore`: Bump version to `1.0.1` (`versionCode = 2`)

---

### 📦 Release Assets
- **QuickSetTiles-1.0.1.apk**: Official signed release APK (optimized with ProGuard/R8)
- **QuickSetTiles-1.0.1.apk.asc**: Detached ASCII-armored PGP signature (.asc)
- **QuickSetTiles-public-key.asc**: Public PGP signing key for verifying authenticity
- **QuickSetTiles-1.0.1-debug.apk**: Debug build with diagnostics
- **SHA256SUMS.txt**: SHA256 checksums for verifying file integrity
- **Source code (.zip & .tar.gz)**: Complete source tree

---

### 🔐 PGP Signature Verification
To verify the authenticity of the release APK:
```bash
# 1. Import the public key
gpg --import QuickSetTiles-public-key.asc

# 2. Verify the release APK signature
gpg --verify QuickSetTiles-1.0.1.apk.asc QuickSetTiles-1.0.1.apk
```
