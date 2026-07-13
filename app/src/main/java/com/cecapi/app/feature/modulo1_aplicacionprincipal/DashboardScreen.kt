package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.model.ModuloCecapi
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.voice.VoiceState

@Composable
fun DashboardScreen(
    onModuleClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val enabledModules by viewModel.enabledModules.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect(onModuleClick)
    }
    LaunchedEffect(currentUser) {
        if (currentUser == null) onLoggedOut()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("ASISTENTE CECAPI", style = CecapiEyebrowStyle, color = CecapiTextMuted)
                    Text(
                        text = "Hola, ${currentUser?.nombreCompleto ?: ""}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Row {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configuración de voz", tint = CecapiTextMuted)
                    }
                    IconButton(onClick = viewModel::onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión", tint = CecapiTextMuted)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CecapiSurface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = CecapiAccent)
                    Text(
                        text = (voiceState as? VoiceState.Speaking)?.text
                            ?: "Sesión iniciada correctamente. Di un comando para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Text(
                text = "MÓDULOS DISPONIBLES",
                style = CecapiEyebrowStyle,
                color = CecapiTextMuted,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )
        }

        val highlighted = enabledModules.firstOrNull { it == ModuloCecapi.ASISTENTE_VOZ }
        if (highlighted != null) {
            item {
                ModuleCard(
                    modulo = highlighted,
                    highlighted = true,
                    onClick = { viewModel.onModuleSelected(highlighted) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        val remaining = enabledModules.filterNot { it == ModuloCecapi.ASISTENTE_VOZ }
        items(remaining.chunked(2)) { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { modulo ->
                    Box(modifier = Modifier.weight(1f)) {
                        ModuleCard(modulo = modulo, highlighted = false, onClick = { viewModel.onModuleSelected(modulo) })
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ModuleCard(modulo: ModuloCecapi, highlighted: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) CecapiSurfaceElevated else CecapiSurface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(modulo.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(modulo.accent))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = modulo.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(text = modulo.subtitle, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
            }
            if (highlighted) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CecapiTextMuted)
            }
        }
    }
}
