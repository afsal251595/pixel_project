# Scientific Touch Grid Tracker (Android)

Android research utility designed to divide any smartphone screen into physical **7 mm (width) × 10 mm (height) cells**, number the cells from **1 to N**, and continuously log all user touch interactions to a comma-separated text file (`touch_log.txt`) for up to **2 weeks** in the background without disturbing normal phone usage.

---

## 📋 Features

- 📏 **Calibrated 7mm (W) × 10mm (H) Cells:** Automatically computes physical dimensions from device `xdpi` and `ydpi`.
- 🔢 **Sequential Cell Indexing:** Numbered $1$ to $N$ starting from top-left (Row 1: $1, 2, 3\dots$, Row 2: $C+1\dots$).
- 👻 **Always-On Background Layer:** Operates in the background across all apps (WhatsApp, YouTube, calls, keyboard, browsing).
- 🔋 **2-Week Endurance:** Starts on boot, wakelock-free background service with in-memory write buffering to protect flash storage and battery.
- 💾 **Target Output Format:** Outputs comma-separated integer sequence:
  ```text
  75, 72, 14, 88, 32
  ```
- 📋 **Complete Log Viewer:** In-app button to inspect the full file and copy all entries with 1 tap.
- 🚀 **Automated Remote Git Builds & OTA Updates:** Pushing to GitHub triggers automatic APK compilation; the app updates itself over-the-air with 1 tap.
