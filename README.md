# ABSOLUTE DRVCAM (starter project)

A no-subscription Android starter app with a gallery photo picker, image preview, and basic rotate/mirror/loop UI controls.

## Build an APK on GitHub
1. Upload the **contents** of this folder to the root of your `absolute-drv-cam` repository (not the ZIP file itself).
2. Open the repository's **Actions** tab.
3. Select **Build ABSOLUTE DRVCAM APK** and press **Run workflow**, or push/commit to `main`/`master`.
4. When the workflow finishes, open its run and download the `ABSOLUTE-DRVCAM-debug-apk` artifact.

## Current limitations
This starter version does **not** yet encode a selected photo into a 30-second MP4 and does not replace another app's camera feed. System-level virtual-camera replacement requires separate device/root/framework compatibility work. Use camera-source testing only with your own test apps and do not use it to misrepresent identity or bypass verification.
