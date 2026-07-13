package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val configuracion by viewModel.configuracion.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = CecapiTextMuted)
            }
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text("APLICACIÓN PRINCIPAL", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                Text(
                    "Configuración de voz",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CecapiSurface)
                .padding(16.dp),
        ) {
            Text(
                "VELOCIDAD DE VOZ",
                style = CecapiEyebrowStyle,
                color = CecapiTextMuted,
            )
            Slider(
                value = configuracion.velocidadVoz,
                onValueChange = viewModel::onVelocidadChange,
                valueRange = 0.5f..2.0f,
                steps = 5,
                colors = SliderDefaults.colors(thumbColor = CecapiAccent, activeTrackColor = CecapiAccent),
            )
            Text(
                "${String.format(Locale.US, "%.1f", configuracion.velocidadVoz)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CecapiSurface)
                .padding(16.dp),
        ) {
            Text(
                "VOLUMEN",
                style = CecapiEyebrowStyle,
                color = CecapiTextMuted,
            )
            Slider(
                value = configuracion.volumen,
                onValueChange = viewModel::onVolumenChange,
                valueRange = 0.1f..1.0f,
                colors = SliderDefaults.colors(thumbColor = CecapiAccent, activeTrackColor = CecapiAccent),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CecapiSurface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Modo simple",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Menús más grandes y con menos opciones a la vez.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            }
            Switch(
                checked = configuracion.modoSimple,
                onCheckedChange = viewModel::onModoSimpleChange,
                colors = SwitchDefaults.colors(checkedThumbColor = CecapiAccent, checkedTrackColor = CecapiAccent.copy(alpha = 0.5f)),
            )
        }

        Button(
            onClick = viewModel::onProbarVoz,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(50)),
            colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
        ) {
            Text("Probar voz", color = MaterialTheme.colorScheme.onPrimary)
        }

        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text(
                "Estos ajustes aplican a toda la aplicación, no solo a esta pantalla.",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        }
    }
}
