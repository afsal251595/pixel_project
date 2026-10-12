# Scientific Touch Grid Tracker (Android)

Android research utility designed to divide any smartphone screen into physical **5 cm × 5 cm cells**, number the cells from **1 to N**, and continuously log all user touch interactions to a comma-separated text file (`touch_log.txt`) for up to **2 weeks** in the background without disturbing normal phone usage.

---

## 📋 Features

- 📏 **Exact Physical Grid Mapping:** Automatically computes physical dimensions from device `xdpi` and `ydpi` ($5\text{ cm} = \frac{5.0}{2.54} \times \text{DPI}$).
- 🔢 **Sequential Cell Indexing:** Numbered $1$ to $N$ starting from top-left (Row 1: $1, 2, 3\dots$, Row 2: $C+1\dots$).
- 👻 **Non-Intrusive Pass-Through Layer:** Operates in the background without blocking or interfering with daily apps (WhatsApp, YouTube, calls, keyboard, browsing).
- 🔋 **2-Week Endurance:** Event-driven, wakelock-free background service with in-memory write buffering to protect flash storage and battery.
- 💾 **Target Output Format:** Outputs comma-separated integer sequence:
  ```text
  75, 72, 14, 88, 32
  ```
- 🚀 **Automated Remote Git Builds & OTA Updates:** Pushing to GitHub triggers automatic APK compilation; the app can update itself over-the-air with 1 tap.

---

## 🛠️ Project Structure

```text
pixel_project/
├── PROJECT_TARGET.md             # Technical target specification & math formulas
├── README.md                     # Installation, testing & Git update guide
├── .github/workflows/
│   └── build-apk.yml             # Cloud CI/CD: builds APK on every 'git push'
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Permissions (Foreground service, battery exemption)
│   │   ├── java/com/scientific/pixelgrid/
│   │   │   ├── MainActivity.kt           # Dashboard, status & log manager
│   │   │   ├── CalibrationActivity.kt    # Fullscreen 5cm grid & ruler visualizer
│   │   │   ├── model/
│   │   │   │   └── GridCalculator.kt     # Math engine for 5cm x 5cm mapping
│   │   │   ├── storage/
│   │   │   │   └── TouchFileLogger.kt    # Buffered file writer (touch_log.txt)
│   │   │   ├── service/
│   │   │   │   ├── TouchTrackingService.kt # Continuous 2-week Foreground Service
│   │   │   │   └── ShizukuInputReader.kt   # System-wide pass-through touch sniffer
│   │   │   ├── ui/
│   │   │   │   └── GridOverlayView.kt    # Visual canvas with numbered cells
│   │   │   └── updater/
│   │   │       └── AppUpdater.kt         # GitHub Releases OTA auto-installer
│   │   └── res/                          # Layouts, themes, and styles
│   └── build.gradle.kts
└── build.gradle.kts
```

---

## 📱 How to Install & Test on Your Phone

### Step 1: Initial Installation
You have two easy options to get the APK on your phone:

#### Option A: Direct Cloud Build via GitHub (Recommended - No Android Studio needed on PC)
1. Push this project to your GitHub repository:
   ```bash
   git init
   git add .
   git commit -m "Initial commit of Touch Grid Tracker"
   git remote add origin https://github.com/<YOUR_USERNAME>/<YOUR_REPO>.git
   git branch -M main
   git push -u origin main
   ```
2. Go to your GitHub repository -> **Actions** tab. GitHub will automatically compile the APK.
3. Once completed (takes ~2 minutes), open the repository on your phone's browser, download `app-debug.apk` from the latest Release/Artifacts, and tap **Install**.

#### Option B: Build locally via Gradle / Android Studio
Open the project in Android Studio and click **Run on Device** or:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Enabling 2-Week Transparent Pass-Through Tracking

To ensure the utility runs for 2 weeks without interrupting normal phone use:

1. **Exempt from Battery Optimization:**
   - In the app, tap **"Battery Exemption"**.
   - Select **"Allow / Unrestricted"** so Android does not kill the service when idle.
2. **System-Wide Pass-Through Touches (Shizuku):**
   - Install **Shizuku** from Google Play Store or GitHub on your phone.
   - Follow Shizuku's 1-minute wireless setup (`Settings -> Developer options -> Wireless debugging`).
   - Open Touch Grid Tracker and tap **"Start Background Service"**.
   - Touches across all phone apps will now be recorded silently to `touch_log.txt`.

---

## 🔄 Updating the App Over Remote Git (OTA)

Whenever you make improvements or changes to the code:
1. Commit and push from your computer:
   ```bash
   git commit -am "Update feature"
   git push origin main
   ```
2. GitHub Actions compiles the new `.apk` automatically in the cloud.
3. Open the app on your phone and tap **"Check for Remote Git Updates"**.
4. The app detects the new release, downloads the APK, and prompts you to update in one tap—**no cables or PC needed!**

---

## 📁 Accessing the Logged Data

- **Inside the App:** The dashboard displays the live count and a preview of the latest sequence (e.g., `75, 72, 14, 88...`).
- **Exporting:** Tap **"Export / Share Log"** to send `touch_log.txt` directly to Google Drive, Email, WhatsApp, or save to your Downloads folder.
- **File Location:** Stored in the app's external files directory:
  `Android/data/com.scientific.pixelgrid/files/touch_log.txt`
