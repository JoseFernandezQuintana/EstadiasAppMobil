# Implementation Plan - Fix ML Kit Object Detection Dependency Resolution

The project is failing to sync because the dependency `com.google.android.gms:play-services-mlkit-object-detection:17.0.2` cannot be resolved. This is due to a version mismatch: the unbundled version (Play Services) is currently at `17.0.0`, while the bundled version (`com.google.mlkit`) is at `17.0.2`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Lector de entorno/EstadiasAppMobil/gradle/libs.versions.toml)

Update the `mlkit-object-detection` library definition to use the correct group and artifact name that corresponds to version `17.0.2`.

- **Current (Incorrect):** `group = "com.google.android.gms"`, `name = "play-services-mlkit-object-detection"`
- **New (Correct):** `group = "com.google.mlkit"`, `name = "object-detection"`

This aligns with the existing `mlkit-text-recognition` dependency which also uses the `com.google.mlkit` group (bundled version).

## Verification Plan

### Automated Tests
- Run Gradle Sync in Android Studio to ensure the dependency resolves correctly.
- Execute a build of the `:app` module to verify that the ML Kit classes are accessible and the application compiles.

### Manual Verification
- None required as this is a build configuration fix.
