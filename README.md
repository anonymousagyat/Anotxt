# Anotxt - Android Text Recording & Accessibility Service

A lightweight Android utility developed with Kotlin that uses the Android **AccessibilityService API** to monitor and record text change events locally on your device, with built-in background persistence and an in-app log viewer.

---

## 🌟 Key Features

- **Text Recording Service**: Captures text input events (`TYPE_VIEW_TEXT_CHANGED`) and logs the timestamp, source application package, and text content.
- **Persistent Background Execution**: Implements an ongoing foreground service (`startForeground`) with `specialUse` foreground service type (Android 14+ compatible) to prevent termination by aggressive OEM battery cleaners.
- **Storage & USB Compatibility**: Automatically writes daily log files (`dd MMM yyyy.txt`) to `Internal storage > Download > Anotxt > txt files` and notifies `MediaScannerConnection` so files are immediately visible over USB MTP on your PC.
- **In-App File Explorer & Viewer**:
  - View all recorded text files with real-time file size and modification timestamps.
  - Built-in scrollable, selectable monospace reader with a one-tap **"Copy All"** button.
  - Share files directly to other apps (WhatsApp, Drive, Gmail) using `FileProvider`.
  - Delete old or unwanted records directly from the UI.
- **Battery & Autostart Management**: Integrated helpers to request battery optimization exemptions (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and quick access to device app settings.

---

## 📱 Permissions Used

| Permission | Purpose |
| :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Required to monitor text input changes across active applications. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the background service active and perceptible to the system. |
| `POST_NOTIFICATIONS` | Displays the persistent foreground notification on Android 13+. |
| `MANAGE_EXTERNAL_STORAGE` | Grants access to create and append files directly in `Download/Anotxt/txt files`. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevents the system from freezing the service in Doze mode. |

---

## 🛠️ Building & Installation

### Prerequisites
- Android Studio Ladybug / Koala or newer
- JDK 17 or JDK 21
- Android SDK with platform tools (compileSdk 37, minSdk 23)

### Clone & Build
```bash
git clone https://github.com/anonymousagyat/Anotxt.git
cd Anotxt
./gradlew assembleDebug
```
The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚠️ Disclaimer

This project is created strictly for **personal text backup, typing recovery, accessibility assistance, and educational research into Android system internals**. 

- Do not use this software for unauthorized surveillance, data gathering, or tracking on devices without explicit, informed user consent.
- The author is not responsible for any misuse or violation of privacy policies.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
