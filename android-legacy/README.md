# WinDock Legacy (Android 4.0 / API 14) — experimental port

This is a separate, intentionally small Java client intended to test the existing WinDock Windows host with an Android 4.0 device over the same Wi-Fi network. It does not modify the modern Android app.

## Current scope

- UDP discovery using the existing WinDock discovery protocol (`41234`, `windock:discover:v1`).
- Manual local IPv4 address entry (default host port `8620`).
- PIN pairing against `/api/auth`, retaining the returned session cookie in memory.
- A basic landscape touchpad sending the existing `/input` WebSocket protocol (`move`, `button`).
- Android manifest `minSdkVersion 14` and Java-only Activity to avoid Kotlin/AndroidX dependencies.

## Important limitations

- This is a prototype scaffold, not a verified release APK. It has not yet been built or tested on physical Android 4.0 hardware.
- The host must be running a WinDock version with the authenticated `/api/auth` and `/input` endpoints and a working input provider. Allow WinDock through Windows Firewall on the private Wi-Fi network.
- Only the touchpad is implemented here. The modern dock UI, app launching, media, capture and other controls are not ported yet.
- HTTP and WebSocket traffic use the existing local-network protocol. Do not expose the WinDock host port to the public internet.
- The Gradle setup uses Android Gradle Plugin 3.6.4 and Gradle 5.6.4, which generally require an older JDK (typically JDK 8). A current Android Studio/JDK setup may not build this legacy project without a separate compatible JDK.

## Build (once a compatible JDK and Android SDK are installed)

From this directory:

```bash
./gradlew assembleDebug
```

On Windows use `gradlew.bat assembleDebug`. The expected output is `app/build/outputs/apk/debug/app-debug.apk`.

## First test

1. Start WinDock on Windows 11 and keep the PC and phone on the same private Wi-Fi network.
2. Check Windows Firewall permits WinDock on the private network.
3. Install the debug APK, tap **Find PC on Wi-Fi**, or enter the PC's LAN IPv4 address and port.
4. Pair with the four-digit PIN shown by WinDock.
5. Use the touchpad area to move and click. If discovery fails, manually enter the host's local IP.
