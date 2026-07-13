package com.cecapi.app.feature.modulo5_solicitudes

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.VoiceCaptionBubble
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.voice.VoiceState

@Composable
fun RequestsScreen(
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsState()
    val plantillas by viewModel.plantillas.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("CENTRO DE SOLICITUDES", style = CecapiEyebrowStyle, color = CecapiTextMuted)
        Text(
            "Solicitudes CECAPI",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        when {
            uiState.textoGenerado != null -> {
                VoiceCaptionBubble(text = uiState.textoGenerado!!, modifier = Modifier.padding(top = 20.dp))
                Button(
                    onClick = viewModel::onReiniciar,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
                ) {
                    Text("Crear otra solicitud", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            uiState.plantillaSeleccionada != null -> {
                var respuestaTexto by remember(uiState.indiceActual) { mutableStateOf("") }

                VoiceCaptionBubble(
                    text = (voiceState as? VoiceState.Speaking)?.text ?: uiState.preguntaActual.orEmpty(),
                    modifier = Modifier.padding(top = 20.dp),
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

                OutlinedTextField(
                    value = respuestaTexto,
                    onValueChange = { respuestaTexto = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("O escribe tu respuesta aquí") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
                )
                Button(
                    onClick = {
                        if (respuestaTexto.isNotBlank()) {
                            viewModel.onAnswerProvided(respuestaTexto)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
                ) {
                    Text("Continuar", color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            else -> {
                Text(
                    "PLANTILLAS DISPONIBLES",
                    style = CecapiEyebrowStyle,
                    color = CecapiTextMuted,
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(plantillas, key = { it.id }) { plantilla ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CecapiSurface)
                                .clickable { viewModel.onPlantillaSelected(plantilla) }
                                .padding(16.dp),
                        ) {
                            Column {
                                Text(
                                    plantilla.titulo,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    plantilla.descripcion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CecapiTextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
