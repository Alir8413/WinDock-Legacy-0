# Build WinDock Legacy without installing Android Studio locally

This package includes a GitHub Actions workflow to build a debug APK on a GitHub-hosted runner. It has **not** been run yet, so the workflow and APK build are not verified.

## Quick path

1. Create a new repository on GitHub.
2. Upload the contents of this ZIP to the repository (keep both `android-legacy/` and `.github/` at the repository root).
3. Open the repository's **Actions** tab and allow workflows if GitHub asks.
4. Select **Build WinDock Legacy APK** and click **Run workflow**. A push that changes the included paths also triggers it.
5. When the run finishes successfully, open the run and download the `WinDock-Legacy-debug-apk` artifact. Extract it to get `app-debug.apk`.

The first build downloads Gradle, Android SDK packages, and Maven dependencies, so the cloud runner needs network access. This is a prototype touchpad-only port; it is not the complete WinDock app, and compatibility with a physical Android 4.0 device still needs testing.
