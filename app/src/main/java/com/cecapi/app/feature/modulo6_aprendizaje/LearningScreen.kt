package com.cecapi.app.feature.modulo6_aprendizaje

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSuccess
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.voice.VoiceState

@Composable
fun LearningScreen(
    viewModel: LearningViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("ACTIVIDADES", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                Text(
                    "Mejora auditiva",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CecapiSuccess.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Nivel ${uiState.nivel.nivelActual}", color = CecapiSuccess, style = MaterialTheme.typography.bodyMedium)
            }
        }

        LinearProgressIndicator(
            progress = { (uiState.nivel.puntosTotales % 50) / 50f },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            color = CecapiAccent,
            trackColor = CecapiSurface,
        )

        val ejercicio = uiState.ejercicioActual
        if (ejercicio == null) {
            Text(
                "No hay ejercicios disponibles para este nivel todavía.",
                style = MaterialTheme.typography.bodyLarge,
                color = CecapiTextMuted,
                modifier = Modifier.padding(top = 32.dp),
            )
        } else {
            VoiceCaptionBubble(
                text = (voiceState as? VoiceState.Speaking)?.text ?: ejercicio.instruccion,
                modifier = Modifier.padding(top = 24.dp),
            )

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                VoiceMicButton(
                    isListening = voiceState is VoiceState.Listening,
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }

            uiState.feedback?.let { feedback ->
                Text(
                    feedback,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (feedback.startsWith("¡Correcto")) CecapiSuccess else MaterialTheme.colorScheme.error,
                )
                Text(
                    "\"Siguiente ejercicio\"",
                    color = CecapiAccent,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = viewModel::onSiguienteEjercicio)
                        .padding(top = 12.dp),
                )
            }
        }
    }
}
