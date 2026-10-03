package com.cecapi.app.feature.modulo4_lectordocumentos

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.Dp
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
import com.cecapi.app.core.ui.CapturedPhotoPreview
import com.cecapi.app.core.ui.FramingGuide
import com.cecapi.app.core.ui.TopAction
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.capturePhoto
import com.cecapi.app.core.ui.copyPickedImage
import com.cecapi.app.core.ui.voiceHint
import kotlinx.coroutines.launch

@Composable
fun DocumentReaderScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit = {},
    viewModel: DocumentReaderViewModel = hiltViewModel(),
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
                folder = "cecapi_docs",
                onFailed = viewModel::onCaptureFailed,
            ) { uri, path -> viewModel.onPhotoCaptured(uri, path) }
        }
    }

    LaunchedEffect(Unit) { viewModel.captureRequests.collect { takePhoto() } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.routes.collect(onOpen) }

    // The system picture picker: the person chooses one picture and the app sees only that one.
    val coroutineScope = rememberCoroutineScope()
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) {
            viewModel.onPickCancelled()
        } else {
            coroutineScope.launch {
                val copy = copyPickedImage(context, uri, "cecapi_docs")
                if (copy == null) viewModel.onPickFailed() else viewModel.onPhotoCaptured(copy.first, copy.second)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.pickRequests.collect {
            pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    val hayDocumento = uiState.parrafos.isNotEmpty()
    val onVolverClick: () -> Unit = { if (viewModel.onBotonVolverPresionado()) onBack() }

    if (hayDocumento && !uiState.isProcessing) {
        // Pantalla 2: control de lectura con botones gigantes.
        DocumentReadoutControlScreen(
            uiState = uiState,
            onBack = onVolverClick,
            onPause = viewModel::pausarLectura,
            onResume = viewModel::continuarLectura,
            onRepeat = viewModel::repetirLectura,
            onPrevious = viewModel::anteriorParrafo,
            onNext = viewModel::siguienteParrafo,
            onRetakePhoto = viewModel::onResetToCameraRequested,
        )
    } else {
        // Pantalla 1: cámara a pantalla completa.
        DocumentCameraScreen(
            uiState = uiState,
            hasCameraPermission = hasCameraPermission,
            onReadyCamera = { imageCapture = it },
            onBack = onVolverClick,
            onCaptureClick = { if (viewModel.onBotonCapturaPresionado()) takePhoto() },
            onGalleryClick = viewModel::pedirGaleria,
            onFramingHint = viewModel::onFramingHint,
        )
    }
}

/**
 * Pantalla 1: cámara a pantalla completa con el botón VOLVER AL MENÚ, la guía de encuadre,
 * el acceso a "Mis fotos" y un obturador grande.
 */
@Composable
private fun DocumentCameraScreen(
    uiState: DocumentReaderUiState,
    hasCameraPermission: Boolean,
    onReadyCamera: (ImageCapture?) -> Unit,
    onBack: () -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onFramingHint: (FramingHint) -> Unit = {},
) {
    // Analiza la vista previa para guiar por voz hacia dónde mover el teléfono; se apaga al salir de la cámara.
    val framingAnalyzer = remember { TextFramingAnalyzer(onFramingHint) }
    DisposableEffect(framingAnalyzer) { onDispose { framingAnalyzer.close() } }

    Box(modifier = Modifier.fillMaxSize().background(CecapiBackground)) {
        // Con una foto ya tomada (o elegida) ella ocupa el lugar de la cámara en vivo mientras se procesa.
        val photoPath = uiState.photoPath
        if (photoPath != null) {
            CapturedPhotoPreview(path = photoPath, modifier = Modifier.fillMaxSize())
        } else {
            CameraViewfinder(
                hasPermission = hasCameraPermission,
                onReady = onReadyCamera,
                modifier = Modifier.fillMaxSize(),
                analyzer = framingAnalyzer,
            )
        }

        if (hasCameraPermission && photoPath == null) {
            FramingGuide(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .aspectRatio(3f / 4f),
            )
        }

        // Las instrucciones ("apunta la cámara", "toma la foto") se dan por voz desde el ViewModel.
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VolverAlMenuButton(onClick = onBack)

            Spacer(modifier = Modifier.weight(1f))

            // Un fallo (foto oscura, sin texto...) también se ve en pantalla, no solo se oye.
            uiState.errorMessage?.let { VoiceCaptionBubble(text = it) }

            // "Mis fotos" abajo a la izquierda, el obturador al centro (el Spacer de la derecha lo equilibra).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Mis fotos",
                    help = "Mis fotos. Abre el selector de fotos de tu teléfono para que elijas una imagen que ya tienes. " +
                        "La aplicación solo ve la foto que tú elijas. También puedes decir: elige una foto.",
                    onClick = onGalleryClick,
                )
                CaptureButton(
                    processing = uiState.isProcessing,
                    enabled = !uiState.isProcessing,
                    help = "Botón de captura. Tócalo para prepararte y tócalo otra vez para tomar la foto del documento. " +
                        "También puedes decir: toma la foto.",
                    onClick = onCaptureClick,
                    size = 136.dp,
                )
                // Same width as the button on the left, so the shutter stays centered.
                Spacer(modifier = Modifier.width(76.dp))
            }
        }
    }
}

/**
 * Pantalla 2: control de lectura. El botón VOLVER AL MENÚ PRINCIPAL queda arriba de todo y cada
 * acción (pausar, repetir, anterior/siguiente, otra foto) es una tarjeta gigante que reparte el alto.
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
        VolverAlMenuButton(onClick = onBack)

        // PAUSAR / REANUDAR
        BigActionCard(
            label = if (uiState.estaLeyendo) "PAUSAR LECTURA" else "REANUDAR LECTURA",
            description = if (uiState.estaLeyendo) {
                "Pausar lectura. Detiene la lectura hasta que lo reanudes."
            } else {
                "Reanudar lectura. Continúa leyendo el párrafo donde te quedaste."
            },
            icon = if (uiState.estaLeyendo) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            tint = CecapiAccent,
            onClick = { if (uiState.estaLeyendo) onPause() else onResume() },
            modifier = Modifier.weight(1f),
            iconSize = 34.dp,
        )

        // REPETIR DESDE EL INICIO
        BigActionCard(
            label = "REPETIR LECTURA",
            description = "Repetir lectura. Lee el documento otra vez desde el principio.",
            icon = Icons.Filled.Replay,
            tint = ModuleLearningAccent,
            onClick = onRepeat,
            modifier = Modifier.weight(1f),
        )

        // NAVEGACIÓN DE PÁRRAFOS (ANTERIOR / SIGUIENTE)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NavCard(
                label = "ANTERIOR",
                description = "Párrafo anterior. Vuelve un párrafo atrás.",
                icon = Icons.Filled.SkipPrevious,
                iconFirst = true,
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            NavCard(
                label = "SIGUIENTE",
                description = "Párrafo siguiente. Salta al siguiente párrafo.",
                icon = Icons.Filled.SkipNext,
                iconFirst = false,
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }

        // TOMAR OTRA FOTO
        BigActionCard(
            label = "TOMAR OTRA FOTO",
            description = "Tomar otra foto. Vuelve a la cámara para leer otro papel.",
            icon = Icons.Filled.CameraAlt,
            tint = ModuleCameraAccent,
            onClick = onRetakePhoto,
            modifier = Modifier.weight(1f),
            iconSize = 30.dp,
        )
    }
}

/**
 * Tarjeta gigante con borde de color, un círculo con el ícono y la acción en mayúsculas.
 * [description] es lo que lee TalkBack y lo que se oye al mantener presionado.
 */
@Composable
private fun BigActionCard(
    label: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(2.dp, tint, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .voiceHint(description),
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
                    .background(tint.copy(alpha = 0.2f))
                    .border(2.dp, tint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = label,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = CecapiTextPrimary,
            )
        }
    }
}

/** Tarjeta de navegación (media pantalla de ancho) para ANTERIOR y SIGUIENTE. */
@Composable
private fun NavCard(
    label: String,
    description: String,
    icon: ImageVector,
    iconFirst: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(2.dp, CecapiBorder, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .voiceHint(description),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp),
        ) {
            val iconContent: @Composable () -> Unit = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CecapiAccent,
                    modifier = Modifier.size(32.dp),
                )
            }
            if (iconFirst) {
                iconContent()
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = label,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = CecapiTextPrimary,
            )
            if (!iconFirst) {
                Spacer(modifier = Modifier.size(8.dp))
                iconContent()
            }
        }
    }
}

/** Botón GIGANTE para volver al menú principal, compartido por la cámara y la pantalla de lectura. */
@Composable
private fun VolverAlMenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val menuTint = Color(0xFFEF4444)
    val shape = RoundedCornerShape(24.dp)
    val description = "Volver al menú principal. Sales del lector y regresas al menú. También puedes decir: atrás."
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(shape)
            .background(CecapiSurfaceElevated)
            .border(2.5.dp, menuTint, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "Volver al menú principal"
            }
            .voiceHint(description),
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
                    imageVector = Icons.Filled.Home,
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
}

@ComposePreview(showBackground = true, name = "Control de lectura (botones gigantes)")
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
