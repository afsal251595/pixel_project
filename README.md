# Scientific Touch Grid Tracker (Android)

Android research utility designed to divide any smartphone screen into physical **5 mm × 5 mm cells**, number the cells from **1 to N**, and continuously log all user touch interactions to a comma-separated text file (`touch_log.txt`) for up to **2 weeks** in the background without disturbing normal phone usage.

---

## 📋 Features

- 📏 **Exact 5mm x 5mm Cells:** Automatically computes physical dimensions from device `xdpi` and `ydpi` ($5\text{ mm} = \frac{5.0}{25.4} \times \text{DPI}$).
- 🔢 **Sequential Cell Indexing:** Numbered $1$ to $N$ starting from top-left (Row 1: $1, 2, 3\dots$, Row 2: $C+1\dots$).
- 👻 **Always-On Background Layer:** Operates in the background across all apps (WhatsApp, YouTube, calls, keyboard, browsing).
- 🔋 **2-Week Endurance:** Starts on boot, wakelock-free background service with in-memory write buffering to protect flash storage and battery.
- 💾 **Target Output Format:** Outputs comma-separated integer sequence:
  ```text
  75, 72, 14, 88, 32
  ```
- 🚀 **Automated Remote Git Builds & OTA Updates:** Pushing to GitHub triggers automatic APK compilation; the app updates itself over-the-air with 1 tap.

---

## 📱 How to Run & Enable Always-On Tracking

1. Install the latest `app-debug.apk` on your phone.
2. Open the app:
   - The background service starts **automatically** on launch.
   - Tap **"Enable Always-On Tracking (Settings)"** $\to$ Turn on **Touch Grid Tracker** in Accessibility Settings.
3. Now, whenever you touch the phone in **any app** (WhatsApp, YouTube, Chrome, Home Screen, Keyboard), it registers the 5mm cell ID and logs it directly to `touch_log.txt`.
4. You can also tap **"Open Interactive Grid & Ruler Test"** to visually verify the 5mm grid with a ruler.
