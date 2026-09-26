# Fix ML Kit Object Detection Dependency Resolution Error

The project fails to build because it attempts to resolve `com.google.android.gms:play-services-mlkit-object-detection:17.0.2`. According to the official ML Kit documentation, the Object Detection and Tracking API is only available as a **bundled** library under the `com.google.mlkit` group. There is no "thin" (Google Play Services) version for this specific API.

## User Review Required

> [!IMPORTANT]
> This change will switch the Object Detection library from a (non-existent) Play Services version to the Bundled version. This will increase the APK size by approximately 5-10 MB, as the model will be included directly in the app. This is the only way to use the ML Kit Object Detection API on Android.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Lector de entorno/EstadiasAppMobil/gradle/libs.versions.toml)

Update the `mlkit-object-detection` library definition to use the correct group and artifact name.

- **Current (Incorrect):** `group = "com.google.android.gms"`, `name = "play-services-mlkit-object-detection"`
- **New (Correct):** `group = "com.google.mlkit"`, `name = "object-detection"`

## Verification Plan

### Automated Tests
- Run `gradlew :app:assembleDebug` (or sync the project in Android Studio) to verify that all dependencies are resolved correctly.

### Manual Verification
- None required beyond a successful build.
