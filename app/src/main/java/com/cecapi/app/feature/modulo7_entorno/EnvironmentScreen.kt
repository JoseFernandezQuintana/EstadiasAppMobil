package com.cecapi.app.feature.modulo7_entorno

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.capturePhoto

@Composable
fun EnvironmentScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: EnvironmentViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) viewModel.onCameraPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            capture.capturePhoto(
                context = context,
                folder = "cecapi_entorno",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    val statusText = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.isProcessing -> "Analizando el entorno."
        uiState.descripcion != null -> uiState.descripcion
        else -> "Apunta la cámara al frente y di qué hay enfrente."
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraViewfinder(
            hasPermission = hasCameraPermission,
            onReady = { imageCapture = it },
            modifier = Modifier.fillMaxSize(),
        )

        if (hasCameraPermission) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f)
                    .aspectRatio(3f / 4f),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTopBar(
                eyebrow = "CÁMARA",
                title = "Qué hay enfrente",
                onBack = onBack,
                onCommands = viewModel::onCommandsRequested,
            )

            Spacer(modifier = Modifier.weight(1f))

            // A long description scrolls inside its own box so the shutter button never leaves the screen.
            VoiceCaptionBubble(
                text = statusText ?: "",
                modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SuggestionChip(label = "qué hay enfrente", onClick = viewModel::pedirCaptura)
            }

            if (uiState.descripcion != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CecapiBackground.copy(alpha = 0.82f))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TopAction(
                        icon = Icons.Filled.Replay,
                        label = "Repetir",
                        help = "Repetir. Vuelve a decir la última descripción.",
                        onClick = viewModel::repetir,
                    )
                }
            }

            CaptureButton(
                processing = uiState.isProcessing,
                enabled = imageCapture != null && !uiState.isProcessing,
                help = "Botón de captura. Toca dos veces para describir lo que hay enfrente. " +
                    "También puedes decir: qué hay enfrente.",
                onClick = viewModel::pedirCaptura,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
