# Release-Readiness Review

## Review Brief

Audit Finger Spark for release readiness across lifecycle, camera concurrency,
memory ownership, drawing correctness, permissions, storage failures,
accessibility, and build configuration. Reproduce or substantiate each finding,
fix confirmed defects, and add focused regression coverage. Report device-only
checks separately from verified results.

## Confirmed Findings And Fixes

| Area | Finding | Correction |
| --- | --- | --- |
| UI responsiveness | PNG compression and storage writes blocked the main thread | Dedicated capture worker; UI thread only composes the snapshot |
| Capture ownership | A delayed capture could outlive its originating camera session | Capture snapshots a session token; pending composition is cancelled on stop |
| Capture cancellation | Encoding could continue writing after the user left play | Session-aware output stream rejects writes after session invalidation |
| Storage publication | A failed MediaStore publish could be reported as successful | Publish update must affect one row; failure removes the pending entry |
| Legacy storage | A null external directory could result in a relative output path | Explicit failure on unavailable storage and partial-file cleanup |
| Snapshot memory | An exception during composition could retain temporary bitmaps | Snapshot and preview ownership are released on failure paths |
| Preview mapping | Preview and analysis could use different sensor crop regions | CameraX UseCaseGroup shares PreviewView's viewport; analysis applies ImageProxy.cropRect |
| Startup responsiveness | Native model creation happened on the UI thread | Model setup runs on the analysis executor, with session checks before binding |
| Camera availability | Initial startup required the preferred lens | Startup falls back to the other lens if available |
| Device filtering | Camera permission can imply a required rear/autofocus camera | Those hardware features are explicitly optional; any camera remains required |
| Game accuracy | Normalized hit radius produced stretched, oversized hit areas | Hit distance now uses screen pixels and rejects off-screen/nonfinite coordinates |
| Splash teardown | Cancelling the splash during destruction could invoke navigation | Remove animator listeners and guard completion against finishing/destroyed activity |
| Permission recovery | Denial returned silently to home | Localized retry or Settings dialog, with cancel |
| Permission recreation | A restored permission result could be interrupted by the splash fallback | Restore the chosen mode and complete splash before handling the result |
| Error-state navigation | A camera-switch failure could reveal the camera toolbar over home | Toolbar reveal is permitted only during an active camera session |
| Long text | Long names or enlarged header text could clip silently | Explicit greeting ellipsis; single-line header with bounded autosizing |

## Automated Verification

- Debug APK assembly.
- Android lint: zero errors; pinned-version and portrait-orientation warnings remain.
- 17 JVM regression tests: 5 session, 8 geometry/hit-testing, 4 capture-stream tests.
- APK manifest inspection confirms no internet/network-state permission.
- APK ZIP alignment verified with Android SDK zipalign at 16 KB.
- Every bundled arm64-v8a and x86_64 ELF PT_LOAD segment has at least 16 KB alignment.
- JavaScript syntax checks for the existing browser prototype.

The alignment checks establish packaging and ELF properties. They do not certify
runtime behavior on a device using 16 KB pages.

## Device Acceptance Still Required

No authorized phone or emulator was available during this review. These are
remaining acceptance checks, not claimed passes:

- Deny camera permission once and permanently; retry and recover via Settings.
- Kill/recreate the app while its permission request is open.
- Exit during splash and during model initialization.
- Compare fingertip/preview alignment on both lenses and multiple phone ratios.
- Switch lenses repeatedly with a hand visible and while capture is pending.
- Verify Paint palette dwell, clear gesture, brush behavior, and saved PNG alignment.
- Verify Game hits at the edge of the visible ring and cooldown behavior.
- Interrupt a capture by returning home or backgrounding the app; check cleanup.
- Verify captures in Android 10+ gallery and Android 7-9 app-specific storage.
- Exercise name entry with keyboard, large fonts, long names, Bangla, and display cutouts.
- Confirm camera indicator shutdown and no continuing animation after app exit.

Release signing, store publication, and broad device qualification remain outside
the current development build.

## Implementation Reference

[CameraX configuration: shared viewport and use-case crop regions](https://developer.android.com/media/camera/camerax/configuration)
