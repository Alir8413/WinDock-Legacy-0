# Build WinDock Legacy APK in GitHub Actions

1. Upload the contents of this ZIP to the root of your `WinDock-Legacy` repository. Keep `.github/workflows/build-legacy-apk.yml` at that exact path and `android-legacy/` at the repository root.
2. Open **Actions** in GitHub and select **Build WinDock Legacy APK**.
3. Select **Run workflow**. You can also push a change under `android-legacy/` to trigger a build.
4. When the workflow succeeds, open the run and download the `WinDock-Legacy-debug-apk` artifact. Extract it to get `app-debug.apk`.

This workflow avoids `android-actions/setup-android@v3`, which failed in the previous run while trying to install the obsolete SDK package `tools`. It uses the Android SDK command-line tools preinstalled on the GitHub-hosted runner and pins the runner to Ubuntu 24.04 to avoid the announced `ubuntu-latest` migration.

The APK has not been tested on a physical Android 4.0 device. A successful CI build confirms compilation only, not full device compatibility or complete feature parity with the original WinDock app.
