package com.cecapi.app.core.ui

import android.content.Context
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBackground
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.theme.CecapiTextPrimary
import java.io.File

/**
 * The live camera picture shared by the text reader and the environment assistant. Calls [onReady]
 * with the capture use case once the camera is running, or with null if the camera cannot start.
 */
@Composable
fun CameraViewfinder(
    hasPermission: Boolean,
    onReady: (ImageCapture?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    Box(
        modifier = modifier.background(CecapiSurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (hasPermission) {
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
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                            onReady(capture)
                        } catch (_: Exception) {
                            // Camera unavailable (emulator without a virtual camera, etc.)
                            onReady(null)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )
        } else {
            Text(
                "Se necesita permiso de cámara. Actívalo en los ajustes de la aplicación.",
                color = CecapiTextMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}

/** Four corner marks that show where to point the phone. */
@Composable
fun FramingGuide(modifier: Modifier = Modifier, color: Color = CecapiAccent) {
    Canvas(modifier = modifier) {
        val length = size.minDimension * 0.14f
        val stroke = 6.dp.toPx()
        val width = size.width
        val height = size.height

        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * length, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * length), stroke, StrokeCap.Round)
        }

        corner(0f, 0f, 1f, 1f)
        corner(width, 0f, -1f, 1f)
        corner(0f, height, 1f, -1f)
        corner(width, height, -1f, -1f)
    }
}

/** Header over the camera: back, what this screen is, and a button that reads out its commands. */
@Composable
fun ScreenTopBar(
    eyebrow: String,
    title: String,
    onBack: () -> Unit,
    onCommands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CecapiBackground.copy(alpha = 0.82f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val backHelp = "Volver. Regresa a la pantalla anterior."
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = backHelp }
                .voiceHint(backHelp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = CecapiTextPrimary)
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(eyebrow, style = CecapiEyebrowStyle, color = CecapiTextMuted)
            Text(title, style = MaterialTheme.typography.titleLarge, color = CecapiTextPrimary)
        }
        val commandsHelp = "Comandos. Toca para escuchar todo lo que puedes decir en esta pantalla."
        IconButton(
            onClick = onCommands,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = commandsHelp }
                .voiceHint(commandsHelp),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = CecapiAccent)
        }
    }
}

/** The big round shutter button. [help] is also what a long press explains. */
@Composable
fun CaptureButton(
    processing: Boolean,
    enabled: Boolean,
    help: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(104.dp)
            .clip(CircleShape)
            .border(4.dp, CecapiTextPrimary, CircleShape)
            .padding(8.dp)
            .clip(CircleShape)
            .background(if (enabled) CecapiAccent else CecapiSurfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = help }
            .voiceHint(help),
        contentAlignment = Alignment.Center,
    ) {
        if (processing) {
            CircularProgressIndicator(color = CecapiBackground, modifier = Modifier.size(40.dp))
        } else {
            Icon(
                Icons.Filled.Camera,
                contentDescription = null,
                tint = CecapiBackground,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

/** Takes a picture into the app's private folder [folder] and reports where it was saved. */
fun ImageCapture.capturePhoto(
    context: Context,
    folder: String,
    onFailed: () -> Unit,
    onSaved: (uri: Uri, path: String) -> Unit,
) {
    val photoFile = File(context.filesDir, "$folder/scan_${System.currentTimeMillis()}.jpg")
        .apply { parentFile?.mkdirs() }
    takePicture(
        ImageCapture.OutputFileOptions.Builder(photoFile).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onSaved(output.savedUri ?: Uri.fromFile(photoFile), photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) = onFailed()
        },
    )
}
