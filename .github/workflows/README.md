# Guardian Pet Android project

## Build an APK with GitHub Actions

1. Upload the contents of this folder to a GitHub repository (keep `.github/workflows/android.yml`).
2. Open the repository's **Actions** tab.
3. Run **Build Guardian Pet APK**, or push a commit to trigger it.
4. Open the completed workflow run and download the `GuardianPet-debug-apk` artifact.

The workflow builds this repository directly. It does not expect a separate `project.zip` inside the repository.

## App behavior

- The Android activity starts and stops the floating pet service.
- The pet is rendered in `app/src/main/assets/pet.html`.
- The service uses an Android overlay, so the user must grant **Display over other apps** permission.
- The service notification includes a Stop action.

## Build requirements

- JDK 17
- Android SDK platform 34 and build-tools 34.0.0
- Gradle 8.7 (Android Gradle Plugin 8.5.2)

This source archive was structurally inspected, but an APK build has not been run in this environment because a local Gradle/Android SDK build toolchain is unavailable.
