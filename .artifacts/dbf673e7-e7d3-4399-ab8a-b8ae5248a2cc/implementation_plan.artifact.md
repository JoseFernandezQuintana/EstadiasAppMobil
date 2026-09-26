# Fix ML Kit Object Detection Dependency Resolution

The project sync is failing because the dependency `com.google.android.gms:play-services-mlkit-object-detection:17.0.2` cannot be found. This version number (`17.0.2`) corresponds to the **bundled** version of ML Kit's Object Detection library (`com.google.mlkit:object-detection`), whereas the **thin** (Play Services) version (`com.google.android.gms:play-services-mlkit-object-detection`) typically uses different versioning or does not have a 17.0.2 release.

## Proposed Changes

### Gradle Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Lector de entorno/EstadiasAppMobil/gradle/libs.versions.toml)

Update the `mlkit-object-detection` library definition to use the correct group and artifact name that matches the `17.0.2` version.

- **Current (Incorrect):** `group = "com.google.android.gms"`, `name = "play-services-mlkit-object-detection"`
- **Proposed (Correct):** `group = "com.google.mlkit"`, `name = "object-detection"`

This change will also make the object detection dependency consistent with the existing text recognition dependency (`com.google.mlkit:text-recognition`).

## Verification Plan

### Automated Tests
- Run Gradle sync to verify that all dependencies are resolved correctly.
- Execute a build of the `:app` module.

### Manual Verification
- Confirm that the project syncs without errors in Android Studio.
