# Scientific Touch Grid Tracker (Android Utility)
**Target Specification & 2-Week Always-On Background Deployment Plan**

---

## 1. Project Goal & Critical Constraints
- **Core Goal:** Split the phone screen into a fine matrix of **5 mm × 5 mm cells**, numbered sequentially from $1$ to $N$.
- **Transparent Non-Intrusive Always-On Tracking:** Runs continuously in the background 24/7 for **up to 2 weeks** without affecting normal phone usage (calling, WhatsApp, YouTube, browsing, typing, gaming).
- **Pass-Through Touches:** All touches pass through with zero latency to whatever app is running underneath.
- **Output File:** Real-time persistent log file (`touch_log.txt`) formatted as:
  ```text
  75, 72, 14, 88, 32
  ```
- **Remote Git Deployment & Updates:** Automatic cloud compilation via GitHub Actions and over-the-air (OTA) 1-tap updates.

---

## 2. System Architecture: 24/7 Always-On Background Layer

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
|   |                   Android System & Input Subsystem              |   |
|   +-----------------------------------------------------------------+   |
|                                    │                                    |
|               Global Touch Events  ▼                                    |
|   +-----------------------------------------------------------------+   |
|   |           Dual Background Tracking Engine (Always-On)           |   |
|   |                                                                 |   |
|   |  A. GlobalTouchAccessibilityService (24/7, No Root/ADB needed)  |   |
|   |  B. TouchTrackingService (Foreground Service with Auto-Boot)    |   |
|   |  C. Shizuku / Kernel getevent Reader (Raw digitizer listener)   |   |
|   |                                                                 |   |
|   |  1. Converts (X, Y) to 5mm x 5mm Grid Cell Number (1 to N)      |   |
|   |  2. Appends to Persistent Storage (touch_log.txt)               |   |
|   |  3. Battery Optimization (Wakelock-free, event-driven)          |   |
|   +-----------------------------------------------------------------+   |
|                                    │                                    |
|                                    ▼                                    |
|   +-----------------------------------------------------------------+   |
|   |                   Companion UI & Updater App                    |   |
|   |  - Visual 5mm grid inspection & ruler calibration               |   |
|   |  - Log viewer & Export to CSV/Text/Google Drive                 |   |
|   |  - GitHub Releases OTA Auto-Update Checker                      |   |
|   +-----------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
```

---

## 3. Mathematical Mapping (5 mm × 5 mm Grid)

### 3.1 Physical Calculations
- Device Physical DPI: $xdpi$, $ydpi$
- Physical target: $5.0\text{ mm}$
- Metric conversion: $1\text{ in} = 25.4\text{ mm}$

$$\text{Cell Width (px)} = \left(\frac{5.0\text{ mm}}{25.4\text{ mm/in}}\right) \times xdpi \approx 0.19685 \times xdpi$$

$$\text{Cell Height (px)} = \left(\frac{5.0\text{ mm}}{25.4\text{ mm/in}}\right) \times ydpi \approx 0.19685 \times ydpi$$

### 3.2 Matrix Dimensions & Cell Count Example
For a typical 1080 × 2400 display with ~400 DPI:
- Cell Size: $\approx 78.7\text{ px} \times 78.7\text{ px}$
- Columns ($C$): $\approx 14$
- Rows ($R$): $\approx 31$
- Total Cells ($N$): $\approx 434$ cells (numbered 1 to 434)

### 3.3 Coordinate to Cell ID Conversion
For any recorded touch $(X, Y)$:
$$\text{col} = \min\left(\left\lfloor \frac{X}{\text{Cell Width}} \right\rfloor, C - 1\right)$$
$$\text{row} = \min\left(\left\lfloor \frac{Y}{\text{Cell Height}} \right\rfloor, R - 1\right)$$
$$\mathbf{\text{Cell ID}} = (\text{row} \times C) + \text{col} + 1$$
