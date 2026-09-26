package com.cecapi.app.feature.modulo4_lectordocumentos

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.VoiceCaptionBubble
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DocumentReaderScreen(
    viewModel: DocumentReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // El texto reconocido nunca se muestra: solo se narra por voz. Aquí solo
    // reflejamos el estado de la lectura para la burbuja de estado.
    val statusText = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.isProcessing -> "Procesando la imagen."
        uiState.estaLeyendo -> "Leyendo el documento en voz alta."
        uiState.capturaArmada -> "Toca otra vez para tomar la foto."
        uiState.parrafos.isNotEmpty() -> "Documento leído."
        else -> "Apunta la cámara a un documento y toca el botón para capturarlo."
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { p ->
                            p.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        } catch (_: Exception) {
                            // Camera unavailable (emulator without a virtual camera, etc.)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(CecapiSurfaceElevated), contentAlignment = Alignment.Center) {
                Text(
                    "Se necesita permiso de cámara.",
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(CecapiSurfaceElevated.copy(alpha = 0.85f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text("CÁMARA INTELIGENTE", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                Text(
                    "Leer documentos y texto",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(CecapiAccent)
                    .clickable(enabled = imageCapture != null && !uiState.isProcessing) {
                        if (!viewModel.onBotonCapturaPresionado()) return@clickable
                        val capture = imageCapture ?: return@clickable
                        val photoFile = File(
                            context.filesDir,
                            "cecapi_docs/scan_${System.currentTimeMillis()}.jpg",
                        ).apply { parentFile?.mkdirs() }
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        capture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    val uri = output.savedUri ?: Uri.fromFile(photoFile)
                                    coroutineScope.launch {
                                        viewModel.onPhotoCaptured(uri, photoFile.absolutePath)
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // Surfaced to the user via the ViewModel's own error path on failure.
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                } else {
                    Icon(
                        Icons.Filled.Camera,
                        contentDescription = "Capturar foto",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            VoiceCaptionBubble(
                text = statusText ?: "",
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}