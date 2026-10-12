# Scientific Touch Grid Tracker (Android Utility)
**Target Specification & 2-Week Background Deployment Plan**

---

## 1. Project Goal & Critical Constraints
- **Core Goal:** Split the phone screen into a matrix of **5 cm × 5 cm cells**, numbered sequentially from $1$ to $N$.
- **Transparent Non-Intrusive Tracking:** Must run continuously in the background for **up to 2 weeks** without affecting normal phone usage (calling, messaging, social media, browsing, gaming).
- **Pass-Through Touches:** Touches must seamlessly register with underlying apps while the background utility logs the cell ID.
- **Output File:** Real-time log file (`touch_log.txt`) formatted as:
  ```text
  75, 72, 14, 88, 32
  ```
- **Remote Git Deployment & Updates:** Automatic compilation and over-the-air (OTA) updates on the phone triggered by Git repository pushes.

---

## 2. System Architecture: Transparent 2-Week Background Service

```
+-------------------------------------------------------------------------+
|                              Android Device                             |
|                                                                         |
|   +-----------------------------------------------------------------+   |
|   |                       Active User Applications                  |   |
|   |          (WhatsApp, Chrome, YouTube, Dialler, Games, etc.)      |   |
|   +-----------------------------------------------------------------+   |
|                                    ▲                                    |
|             Normal Touch Delivered | (Unblocked & Zero Latency)         |
|                                    |                                    |
|   +-----------------------------------------------------------------+   |
|   |                       Android Input Subsystem                   |   |
|   |             Linux Kernel Touch Driver: /dev/input/event*        |   |
|   +-----------------------------------------------------------------+   |
|                                    │                                    |
|               Global Touch Events  ▼                                    |
|   +-----------------------------------------------------------------+   |
|   |                  Background Logger Daemon / Service             |   |
|   |                                                                 |   |
|   |  1. Intercepts Raw Hardware Touches (ABS_MT_POSITION_X / Y)     |   |
|   |  2. Converts (X, Y) to 5cm x 5cm Grid Cell Number (1 to N)      |   |
|   |  3. Deduplicates / Debounces (Logs on initial contact)          |   |
|   |  4. Appends to Persistent Storage (touch_log.txt)               |   |
|   |  5. Battery Optimization (Wakelock-free, event-driven)          |   |
|   +-----------------------------------------------------------------+   |
|                                    │                                    |
|                                    ▼                                    |
|   +-----------------------------------------------------------------+   |
|   |                   Companion UI & Updater App                    |   |
|   |  - Visual grid inspection & DPI calibration                     |   |
|   |  - Log viewer & Export to CSV/Text/Google Drive                 |   |
|   |  - GitHub Releases OTA Auto-Update Checker                      |   |
|   +-----------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
```

---

## 3. How to Capture Touches Without Blocking the Phone

Due to Android security sandboxing (anti-tapjacking), ordinary overlay apps cannot see touches that pass through to other apps. To achieve **transparent background tracking** without rooting:

### Recommended Approach: Shizuku / Wireless ADB Background Service
1. **Mechanism:** Uses Android's native `Shizuku` API or a local wireless ADB daemon running directly on the device (paired once via Android Developer Settings -> Wireless Debugging).
2. **Advantages:**
   - **100% Pass-Through:** Does not intercept or delay touch events to underlying apps.
   - **No Root Required:** Standard Android permission model for scientific and developer tools.
   - **System-Wide:** Works across all apps, system keyboards, lock screen, and home screen.
   - **Low Battery Footprint:** Purely event-driven; sleeps when screen is off.
3. **Data Captured:**
   - Raw touch event `ABS_MT_POSITION_X` and `ABS_MT_POSITION_Y`.
   - Screen coordinate conversion using device `xdpi` and `ydpi`.

### Alternative Approach: Accessibility Service + Bounding Tracker
- Uses Android `AccessibilityService`.
- No ADB required, but only captures accessibility window interactions and click bounding boxes rather than raw millisecond touch points.

---

## 4. Mathematical Mapping (5cm × 5cm Grid)

### 4.1 Pixel Calculations
- Device Physical DPI: $xdpi$, $ydpi$
- Physical target: $5.0\text{ cm}$
- Metric conversion: $1\text{ in} = 2.54\text{ cm}$

$$\text{Cell Width (px)} = \left(\frac{5.0}{2.54}\right) \times xdpi$$

$$\text{Cell Height (px)} = \left(\frac{5.0}{2.54}\right) \times ydpi$$

### 4.2 Grid Matrix & Cell Numbering
Given screen resolution $W \times H$:
- Number of Columns: $C = \lceil W / \text{Cell Width} \rceil$
- Number of Rows: $R = \lceil H / \text{Cell Height} \rceil$
- Total Cells: $N = C \times R$

For any recorded touch $(X, Y)$:
$$\text{col} = \min\left(\left\lfloor \frac{X}{\text{Cell Width}} \right\rfloor, C - 1\right)$$
$$\text{row} = \min\left(\left\lfloor \frac{Y}{\text{Cell Height}} \right\rfloor, R - 1\right)$$
$$\mathbf{\text{Cell ID}} = (\text{row} \times C) + \text{col} + 1$$

---

## 5. Continuous 2-Week Reliability Strategy

To ensure the utility does not get killed by Android battery optimization during the 2-week test:
1. **Foreground Service with Persistent Notification:** Shows tracking status ("Scientific Touch Tracker active").
2. **Exemption from Battery Optimization:** Prompt user to whitelist the app under `Settings -> Battery -> Unrestricted`.
3. **Chunked & Resilient File Writes:**
   - Touches are buffered in memory (e.g. flushed every 10 events or 30 seconds) to avoid excessive flash storage wear.
   - Automatic rollover / daily backup files (e.g., `touch_log_day1.txt`, `touch_log_day2.txt`).

---

## 6. Testing, Installing, and Remote Git Updates (OTA)

### 6.1 Initial Installation on Your Phone
1. Enable **Developer Options** on your phone (Tap *Build Number* 7 times in *About Phone*).
2. Enable **Install via USB** and **Wireless Debugging**.
3. Install the debug APK via ADB or by downloading the `.apk` file directly on the phone.

### 6.2 Updating the App via Remote Git (Is it possible?)
**Yes! Fully possible and standard practice:**

```
+-------------+         git push          +-------------------+
| Local Dev / | ------------------------> |  GitHub / GitLab  |
| IDE Code    |                           |    Repository     |
+-------------+                           +-------------------+
                                                    │
                                                    ▼ (Auto-trigger)
                                          +-------------------+
                                          |   GitHub Actions  |
                                          |   (Cloud Runner)  |
                                          +-------------------+
                                                    │
                                                    ▼ (Compiles APK)
                                          +-------------------+
                                          |  GitHub Releases  |
                                          |  (Latest .apk)    |
                                          +-------------------+
                                                    │
                                                    ▼ (OTA In-App Check)
                                          +-------------------+
                                          |   Android Phone   |
                                          | (App Auto-Update) |
                                          +-------------------+
```

1. **Automated Cloud Builds (GitHub Actions):**
   - Whenever you push new code to your Git repo (`git push`), a GitHub Action automatically compiles the APK.
   - It attaches the APK to a GitHub Release tagged `v1.0.1`, `v1.0.2`, etc.
2. **In-App Auto-Updater:**
   - The Android app includes an OTA updater module.
   - On launch (or scheduled daily), it checks `https://api.github.com/repos/<username>/<repo>/releases/latest`.
   - If a new version exists, it downloads the APK and triggers Android's package installer with a single tap.
   - **No PC connection needed for future updates!**

---

## 7. Next Steps & Implementation Roadmap

1. [ ] **Step 1:** Create project scaffolding (Android Studio / Gradle project with Kotlin).
2. [ ] **Step 2:** Implement the 5cm × 5cm Grid Calculator module & display metrics calibration.
3. [ ] **Step 3:** Implement the transparent touch listener service (Shizuku / Input daemon).
4. [ ] **Step 4:** Implement buffered persistent file logger (`touch_log.txt`).
5. [ ] **Step 5:** Configure GitHub Actions CI workflow (`.github/workflows/build-apk.yml`) for automated cloud APK builds.
6. [ ] **Step 6:** Add in-app GitHub OTA update receiver.
