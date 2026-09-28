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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.capturePhoto
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit = {},
    onNavigateToMenu: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    viewModel: DocumentReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    if (uiState.recognizedText != null && !uiState.isProcessing) {
        AudioControlScreen(
            uiState = uiState,
            onPauseResume = viewModel::onPauseResumeToggle,
            onRepeatFromStart = viewModel::onRepeatFromStartRequested,
            onRetakePhoto = viewModel::onResetToCameraRequested,
            onNavigateToMenu = { viewModel.onNavigateToMenuRequested(onNavigateToMenu) },
        )
    } else {
        CameraCaptureScreen(
            uiState = uiState,
            onPhotoCaptured = viewModel::onPhotoCaptured,
            onNavigateToMenu = { viewModel.onNavigateToMenuRequested(onNavigateToMenu) },
        )
    }
}

/**
 * Pantalla 1 del Prototipo Beta: Vista de Cámara a Pantalla Completa con Botón Volver al Menú Rojo
 */
@Composable
private fun CameraCaptureScreen(
    uiState: DocumentReaderUiState,
    onPhotoCaptured: (imageUri: Uri, rutaImagen: String) -> Unit,
    onNavigateToMenu: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val isPreview = LocalInspectionMode.current

    var hasCameraPermission by remember {
        mutableStateOf(
            isPreview || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!isPreview && !hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val backgroundColor = Color(0xFF070B1E)
    val targetBlue = Color(0xFF00B2FF)
    val menuRed = Color(0xFF900000)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        // Vista de Cámara a Pantalla Completa (Sin marco blanco)
        if (isPreview) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.DarkGray,
                    modifier = Modifier.size(80.dp),
                )
            }
        } else if (hasCameraPermission) {
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
                            // Sin cámara disponible (ej. emulador)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Se necesita permiso de cámara.",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        // Botón superior izquierdo: Cuadro Rojo con símbolo '<' para volver al menú
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
                .size(64.dp)
                .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(menuRed)
                .semantics {
                    role = Role.Button
                    contentDescription = "Volver al menú principal"
                }
                .clickable { onNavigateToMenu() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "<",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
            )
        }

        // Panel inferior flotante: Botón Objetivo Azul (Diana) y Leyenda de Voz
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Botón objetivo Diana Azul
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(targetBlue)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Tomar foto del documento"
                    }
                    .clickable(
                        enabled = isPreview || (imageCapture != null && !uiState.isProcessing),
                    ) {
                        if (isPreview) return@clickable
                        val capture = imageCapture ?: return@clickable
                        capture.capturePhoto(
                            context = context,
                            folder = "cecapi_docs",
                            onFailed = {
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
                                                onPhotoCaptured(uri, photoFile.absolutePath)
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            // Error manejado en ViewModel
                                        }
                                    },
                                )
                            },
                        ) { uri, path ->
                            coroutineScope.launch { onPhotoCaptured(uri, path) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(backgroundColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(targetBlue),
                        )
                    }
                }
            }

            // Subtítulo / Leyenda de accesibilidad
            VoiceCaptionBubble(
                text = uiState.errorMessage
                    ?: if (uiState.isProcessing) "Procesando la imagen..." else "Apunta la cámara al documento y toca el botón azul.",
            )
        }
    }
}

/**
 * Pantalla 2 del Prototipo Beta: 4 Grandes Bloques Táctiles de Alta Accesibilidad
 * 1. PAUSA (|| / ▶) - Verde Oliva (#586232)
 * 2. REPETIR DESDE EL INICIO (|<) - Rojo Carmesí (#6C1024)
 * 3. VOLVER A TOMAR FOTO (<) - Verde Esmeralda (#14723B)
 * 4. MENU (MENU) - Gris Claro (#D1D5DB)
 */
@Composable
private fun AudioControlScreen(
    uiState: DocumentReaderUiState,
    onPauseResume: () -> Unit,
    onRepeatFromStart: () -> Unit,
    onRetakePhoto: () -> Unit,
    onNavigateToMenu: () -> Unit,
) {
    val backgroundColor = Color(0xFF070B1E)

    val oliveColor = Color(0xFF586232)
    val maroonColor = Color(0xFF6C1024)
    val emeraldColor = Color(0xFF14723B)
    val silverColor = Color(0xFFD1D5DB)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(12.dp),
    ) {
        // Bloque 1: PAUSAR / REANUDAR (Símbolo || o ▶)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(oliveColor)
                .semantics {
                    role = Role.Button
                    contentDescription = if (uiState.estaPausado) "Reanudar lectura" else "Pausar lectura"
                }
                .clickable { onPauseResume() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (uiState.estaPausado) "▶" else "||",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bloque 2: REPETIR DESDE EL INICIO (Símbolo |<)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(maroonColor)
                .semantics {
                    role = Role.Button
                    contentDescription = "Repetir lectura desde el inicio"
                }
                .clickable { onRepeatFromStart() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "|<",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bloque 3: VOLVER A TOMAR FOTO (Símbolo <)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(emeraldColor)
                .semantics {
                    role = Role.Button
                    contentDescription = "Volver a tomar foto"
                }
                .clickable { onRetakePhoto() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "<",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bloque 4: MENU (Texto MENU)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(silverColor)
                .semantics {
                    role = Role.Button
                    contentDescription = "Volver al menú principal"
                }
                .clickable { onNavigateToMenu() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "MENU",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@ComposePreview(showBackground = true, name = "Camera View Full Screen Beta")
@Composable
private fun CameraCaptureScreenPreview() {
    MaterialTheme {
        CameraCaptureScreen(
            uiState = DocumentReaderUiState(),
            onPhotoCaptured = { _, _ -> },
            onNavigateToMenu = {},
        )
    }
}

@ComposePreview(showBackground = true, name = "Audio Control 4 Buttons Beta")
@Composable
private fun AudioControlScreenPreview() {
    MaterialTheme {
        AudioControlScreen(
            uiState = DocumentReaderUiState(recognizedText = "Texto de prueba"),
            onPauseResume = {},
            onRepeatFromStart = {},
            onRetakePhoto = {},
            onNavigateToMenu = {},
        )
    }
}
