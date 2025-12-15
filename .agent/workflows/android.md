---
description: Android build, run, and debug commands
keybindings:
  - key: cmd+shift+b
    step: 1
  - key: cmd+shift+r
    step: 2
  - key: cmd+shift+c
    step: 3
  - key: cmd+shift+l
    step: 4
  - key: cmd+shift+t
    step: 5
---

# Android Development Workflow

Common commands for building, running, and debugging the WXYC Android app.

## 1. Build Debug APK
Build the debug version of the app.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew assembleDebug
```

## 2. Install and Run on Device
Install the debug APK on the connected device/emulator and launch the app.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew installDebug && adb shell am start -n org.wxyc.wxycapp/.MainActivity
```

## 3. Clean Build
Clean all build artifacts.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew clean
```

## 4. View Logs
View filtered logcat output for the WXYC app.
// turbo
```bash
adb logcat -v time | grep -E "WXYC|AndroidRuntime"
```

## 5. Run Unit Tests
Run all unit tests in the project.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew test
```

## 6. Run Lint Checks
Run Android lint to check for code quality issues.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew lint
```

## 7. Uninstall App
Uninstall the app from the connected device.
// turbo
```bash
adb uninstall org.wxyc.wxycapp
```

## 8. List Connected Devices
Show all connected Android devices and emulators.
// turbo
```bash
adb devices -l
```

## 9. Clear App Data
Clear all app data and cache on the device.
// turbo
```bash
adb shell pm clear org.wxyc.wxycapp
```

## 10. Build Release APK
Build a release version of the app (requires signing configuration).
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew assembleRelease
```

## 11. Take Screenshot
Capture a screenshot from the connected device.
// turbo
```bash
adb exec-out screencap -p > ~/Desktop/screenshot-$(date +%Y%m%d-%H%M%S).png
```

## 12. Full Rebuild
Clean and rebuild the entire project.
// turbo
```bash
cd /Users/jake/Developer/WXYC-Android && ./gradlew clean build
```
