package com.cecapi.app.feature.modulo1_aplicacionprincipal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiBorder
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.BigBtn
import com.cecapi.app.core.ui.BigBtnVariant
import com.cecapi.app.core.ui.MicPad
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@Composable
fun EditAccountScreen(
    onBack: () -> Unit,
    viewModel: EditAccountViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsState()
    var nombre by remember(user?.nombreCompleto) { mutableStateOf(user?.nombreCompleto.orEmpty()) }
    var apodo by remember(user?.apodo) { mutableStateOf(user?.apodo.orEmpty()) }
    var usarApodo by remember(user?.usarApodoRanking) { mutableStateOf(user?.usarApodoRanking ?: false) }
    var contrasenaActual by remember { mutableStateOf("") }
    var contrasenaNueva by remember { mutableStateOf("") }
    var contrasenaVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val voiceState by viewModel.voiceState.collectAsState()
    val listening = voiceState is VoiceState.Listening

    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.guardado.collect { onBack() } }
    LaunchedEffect(Unit) { viewModel.errorMensaje.collect { error = it } }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .padding(bottom = 230.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "CUENTA",
            title = "Editar mi cuenta",
            onBack = viewModel::onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        NoticeBanner("Cambia lo que quieras y confirma con tu contraseña actual.")

        // Mismo tono gris que los bloques de Configuración (CecapiSurface), con el mismo borde
        // exterior que usan las demás tarjetas (BigBtn) en vez de quedar sin borde.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CecapiSurface)
                .border(1.dp, CecapiBorder, RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            modifier = Modifier.fillMaxWidth().voiceHint("Tu nombre completo."),
            label = { Text("Tu nombre completo") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
        )

        OutlinedTextField(
            value = apodo,
            onValueChange = { apodo = it },
            modifier = Modifier.fillMaxWidth().voiceHint(
                "Tu apodo. Es opcional, y solo se usa en Clasificación si eliges mostrarlo en vez de tu nombre.",
            ),
            label = { Text("Apodo (opcional)") },
            placeholder = { Text("Cómo quieres que te digan") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
        )

        ToggleNombreApodo(usarApodo = usarApodo, onChange = { usarApodo = it })

        OutlinedTextField(
            value = contrasenaNueva,
            onValueChange = { contrasenaNueva = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth().voiceHint("Nueva contraseña, si quieres cambiarla. Déjalo vacío para no cambiarla."),
            label = { Text("Nueva contraseña (opcional)") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { contrasenaVisible = !contrasenaVisible }) {
                    Icon(
                        imageVector = if (contrasenaVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (contrasenaVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
        )

        OutlinedTextField(
            value = contrasenaActual,
            onValueChange = { contrasenaActual = it.filter(Char::isDigit) },
            modifier = Modifier.fillMaxWidth().voiceHint("Tu contraseña actual, para confirmar los cambios."),
            label = { Text("Tu contraseña actual, para confirmar") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = { viewModel.onGuardarPerfil(nombre, apodo, usarApodo, contrasenaActual, contrasenaNueva) },
            ),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent, unfocusedBorderColor = CecapiTextMuted),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigBtn(
                icon = Icons.Filled.Check,
                label = "Guardar",
                variant = BigBtnVariant.Accent,
                half = true,
                onClick = { viewModel.onGuardarPerfil(nombre, apodo, usarApodo, contrasenaActual, contrasenaNueva) },
                modifier = Modifier.weight(1f),
            )
            BigBtn(
                icon = Icons.Filled.Close,
                label = "Cancelar",
                half = true,
                onClick = viewModel::onBack,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }

        MicPad(
            listening = listening,
            onDoubleTap = viewModel::onMicDoubleTap,
            hint = if (listening) "Escuchando…" else "Toca aquí para hablar",
            onClick = viewModel::onMicTapped,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ToggleNombreApodo(usarApodo: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Text(
            "EN CLASIFICACIÓN, MUÉSTRAME COMO:",
            style = MaterialTheme.typography.labelLarge,
            color = CecapiTextMuted,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BigBtn(
                icon = Icons.Filled.Badge,
                label = "Mi nombre",
                variant = if (!usarApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { onChange(false) },
                modifier = Modifier.weight(1f),
            )
            BigBtn(
                icon = Icons.Filled.Face,
                label = "Mi apodo",
                variant = if (usarApodo) BigBtnVariant.Accent else BigBtnVariant.Neutral,
                half = true,
                onClick = { onChange(true) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
