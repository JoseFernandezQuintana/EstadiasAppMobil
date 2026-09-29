package com.cecapi.app.feature.modulo4_lectordocumentos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextPrimary
import com.cecapi.app.core.theme.ModuleCameraAccent
import com.cecapi.app.core.theme.ModuleLearningAccent
import com.cecapi.app.core.ui.CameraViewfinder
import com.cecapi.app.core.ui.CaptureButton
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.capturePhoto

@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    viewModel: DocumentReaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isPreview = LocalInspectionMode.current

    var hasCameraPermission by remember {
        mutableStateOf(
            isPreview || ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
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
        if (!isPreview && !hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val takePhoto: () -> Unit = {
        val capture = imageCapture
        if (capture == null) {
            viewModel.onCaptureFailed()
        } else {
            capture.capturePhoto(
                context = context,
                folder = "cecapi_docs",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    val hayDocumento = uiState.parrafos.isNotEmpty()

    if (hayDocumento && !uiState.isProcessing) {
        // Pantalla 2: Interfaz futurista CECAPI de control de lectura con botón VOLVER AL MENÚ arriba de todo
        DocumentReadoutControlScreen(
            uiState = uiState,
            onBack = onBack,
            onPause = viewModel::pausarLectura,
            onResume = viewModel::continuarLectura,
            onRepeat = viewModel::repetirLectura,
            onPrevious = viewModel::anteriorParrafo,
            onNext = viewModel::siguienteParrafo,
            onRetakePhoto = viewModel::onResetToCameraRequested,
        )
    } else {
        // Pantalla 1: Vista previa de cámara limpia a pantalla completa
        DocumentCameraScreen(
            uiState = uiState,
            hasCameraPermission = hasCameraPermission,
            onReadyCamera = { imageCapture = it },
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
            onCaptureClick = { if (viewModel.onBotonCapturaPresionado()) takePhoto() },
            onPedirCaptura = viewModel::pedirCaptura,
        )
    }
}

/**
 * Pantalla 1: Vista previa de cámara limpia a pantalla completa con guía de encuadre y obturador.
 */
@Composable
private fun DocumentCameraScreen(
    uiState: DocumentReaderUiState,
    hasCameraPermission: Boolean,
    onReadyCamera: (ImageCapture?) -> Unit,
    onBack: () -> Unit,
    onCommands: () -> Unit,
    onCaptureClick: () -> Unit,
    onPedirCaptura: () -> Unit,
) {
    val statusText = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.isProcessing -> "Procesando la imagen..."
        uiState.capturaArmada -> "Toca otra vez para tomar la foto."
        else -> "Apunta la cámara al papel y di toma la foto."
    }

    Box(modifier = Modifier.fillMaxSize().background(CecapiBackground)) {
        CameraViewfinder(
            hasPermission = hasCameraPermission,
            onReady = onReadyCamera,
            modifier = Modifier.fillMaxSize(),
        )

        if (hasCameraPermission) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(3f / 4f),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenTopBar(
                eyebrow = "CÁMARA",
                title = "Leer texto",
                onBack = onBack,
                onCommands = onCommands,
            )

            Spacer(modifier = Modifier.weight(1f))

            VoiceCaptionBubble(text = statusText)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                SuggestionChip(label = "toma la foto", onClick = onPedirCaptura)
            }

            CaptureButton(
                processing = uiState.isProcessing,
                enabled = !uiState.isProcessing,
                help = "Botón de captura. Tócalo para tomar la foto del documento o di toma la foto.",
                onClick = onCaptureClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Pantalla 2: Interfaz futurista CECAPI de lectura. El botón VOLVER AL MENÚ PRINCIPAL está directamente
 * sobre el botón de PAUSAR LECTURA en un tamaño GIGANTE.
 */
@Composable
private fun DocumentReadoutControlScreen(
    uiState: DocumentReaderUiState,
    onBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRepeat: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRetakePhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CecapiBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Botón Superior Principal: VOLVER AL MENÚ PRINCIPAL (Tamaño GIGANTE de 82.dp)
        val menuTint = Color(0xFFEF4444)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.5.dp, menuTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Volver al menú principal"
                }
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(menuTint.copy(alpha = 0.2f))
                        .border(2.dp, menuTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = menuTint,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.size(14.dp))
                Text(
                    text = "VOLVER AL MENÚ PRINCIPAL",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 1 Futurista: PAUSAR / REANUDAR
        val pauseTint = CecapiAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, pauseTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = if (uiState.estaLeyendo) "Pausar lectura" else "Reanudar lectura"
                }
                .clickable { if (uiState.estaLeyendo) onPause() else onResume() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(pauseTint.copy(alpha = 0.2f))
                        .border(2.dp, pauseTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (uiState.estaLeyendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = pauseTint,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = if (uiState.estaLeyendo) "PAUSAR LECTURA" else "REANUDAR LECTURA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 2 Futurista: REPETIR DESDE EL INICIO
        val repeatTint = ModuleLearningAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, repeatTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Repetir lectura desde el inicio"
                }
                .clickable { onRepeat() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(repeatTint.copy(alpha = 0.2f))
                        .border(2.dp, repeatTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = null,
                        tint = repeatTint,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "REPETIR LECTURA",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }

        // Tarjeta 3 Futurista: NAVEGACIÓN DE PÁRRAFOS (ANTERIOR / SIGUIENTE)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val navBorder = CecapiBorder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CecapiSurfaceElevated)
                    .border(2.dp, navBorder, RoundedCornerShape(24.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ir al párrafo anterior"
                    }
                    .clickable { onPrevious() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = CecapiAccent,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "ANTERIOR",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CecapiTextPrimary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CecapiSurfaceElevated)
                    .border(2.dp, navBorder, RoundedCornerShape(24.dp))
                    .semantics {
                        role = Role.Button
                        contentDescription = "Ir al párrafo siguiente"
                    }
                    .clickable { onNext() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Text(
                        text = "SIGUIENTE",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = CecapiTextPrimary,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = CecapiAccent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Tarjeta 4 Futurista: TOMAR OTRA FOTO
        val cameraTint = ModuleCameraAccent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(CecapiSurfaceElevated)
                .border(2.dp, cameraTint, RoundedCornerShape(24.dp))
                .semantics {
                    role = Role.Button
                    contentDescription = "Tomar otra foto"
                }
                .clickable { onRetakePhoto() },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(cameraTint.copy(alpha = 0.2f))
                        .border(2.dp, cameraTint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = cameraTint,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = "TOMAR OTRA FOTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = CecapiTextPrimary,
                )
            }
        }
    }
}

@ComposePreview(showBackground = true, name = "Futuristic Document Readout Control Screen")
@Composable
private fun DocumentReadoutControlScreenPreview() {
    MaterialTheme {
        DocumentReadoutControlScreen(
            uiState = DocumentReaderUiState(
                recognizedText = "Texto de ejemplo para la vista previa.",
                parrafos = listOf("Este es el primer párrafo del documento leído.", "Este es el segundo párrafo del documento."),
                parrafoActual = 0,
                estaLeyendo = true,
            ),
            onBack = {},
            onPause = {},
            onResume = {},
            onRepeat = {},
            onPrevious = {},
            onNext = {},
            onRetakePhoto = {},
        )
    }
}
