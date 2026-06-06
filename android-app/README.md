# Finger Spark Android

Native Android version of the Finger Spark hand tracker. This app keeps the first milestone intentionally small: front camera, MediaPipe hand tracking, and one filled circle following the index fingertip.

## Build

Open `android-app/` in Android Studio, or build from PowerShell after Android Studio/JDK are installed:

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install On Phone

Enable Developer Options and USB Debugging on the Android phone, connect it by USB, then run:

```powershell
.\gradlew.bat :app:installDebug
```
