# Finger Spark

An offline Android hand-tracking app for kids. It turns the front camera into a simple, playful interaction space powered by CameraX and MediaPipe.

The app currently ships with two modes:

- `Paint` for fingertip drawing
- `Game` for spark-target play

## At A Glance

- Front camera hand tracking
- Small fingertip dot that follows the index finger
- Bangla and English language switch
- Camera switch, screen capture, sound, back-home, and exit controls
- Offline-first native Android build

## How To Use

1. Open the app and wait for the launch animation.
2. Enter your name.
3. Allow camera permission.
4. Choose `Paint` or `Game`.
5. Tap the screen in camera mode to reveal the controls.
6. Use back-home to return, or exit to close the app.

## Mode Overview

| Mode | Preview | What It Does |
| --- | --- | --- |
| `Game` | <img src="docs/images/game-mode.jpg" alt="Game mode screenshot" width="220" /> | A spark-target challenge built around fingertip tracking. The target reacts to hand movement, score is tracked, and sound feedback makes the interaction feel alive. |
| `Paint` | <img src="docs/images/paint-mode.jpg" alt="Paint mode screenshot" width="220" /> | A free-draw canvas for fingertip painting. The palette changes brush color, drawings stay responsive, and capture saves the result to the gallery. |

### Paint

- Fingertip drawing on the camera feed
- Color palette selection
- Screen capture support
- Responsive live brush feedback

### Game

- Spark target gameplay
- Score tracking
- Hand-driven interaction
- Sound feedback for actions

## Build

Open `android-app/` in Android Studio, or build from PowerShell:

```powershell
cd android-app
.\gradlew.bat :app:assembleDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install On Phone

Enable Developer Options and USB Debugging on the Android phone, connect it by USB, then run:

```powershell
.\gradlew.bat :app:installDebug
```

## Tech Stack

- Kotlin
- CameraX
- MediaPipe Hand Landmarker
- Lottie animations
- Native Android views

## Notes

- The app is designed to stay offline.
- The web app in the parent project is unchanged.
- The Android app is a separate native project, not a WebView wrapper.
- Camera-related controls appear only after you tap the screen in camera mode.
