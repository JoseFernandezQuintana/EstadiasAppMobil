package com.cecapi.app.core.theme

import androidx.compose.ui.graphics.Color

// Fixed dark, high-contrast palette taken from the CECAPI Figma Make prototype.
// The app intentionally does NOT follow system light/dark mode: for low-vision
// users, contrast consistency matters more than platform convention, so the
// palette is pinned regardless of device theme.
val CecapiBackground = Color(0xFF0B0B12)
val CecapiSurface = Color(0xFF14141F)
val CecapiSurfaceElevated = Color(0xFF1A1A26)
val CecapiBorder = Color(0xFF2A2A38)

val CecapiAccentStart = Color(0xFF22D3EE)
val CecapiAccentEnd = Color(0xFF38BDF8)
val CecapiAccent = Color(0xFF38BDF8)

val CecapiTextPrimary = Color(0xFFF5F6FA)
val CecapiTextMuted = Color(0xFF9AA0AE)
val CecapiTextLabel = Color(0xFF7E8494)

val CecapiSuccess = Color(0xFF22C55E)
val CecapiError = Color(0xFFEF4444)
val CecapiWarning = Color(0xFFF59E0B)

// Module accent colors used for dashboard cards (icons/borders), one per
// module so users who retain some vision can tell modules apart at a glance.
val ModuleVoiceAccent = Color(0xFF38BDF8)
val ModuleLearningAccent = Color(0xFFB794F6)
val ModuleDocumentsAccent = Color(0xFFF6C453)
val ModuleCameraAccent = Color(0xFF4ADE80)
val ModuleAiAccent = Color(0xFF60A5FA)
val ModuleEnvironmentAccent = Color(0xFFFB923C)
