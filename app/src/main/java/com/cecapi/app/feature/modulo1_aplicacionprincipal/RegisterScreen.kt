package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.VoiceMicButton
import com.cecapi.app.core.ui.voiceHint
import com.cecapi.app.core.voice.VoiceState

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() else viewModel.onMicPermissionDenied() }

    LaunchedEffect(Unit) {
        viewModel.registerSucceeded.collect { onRegisterSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.back.collect { onBack() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("CREAR CUENTA", style = CecapiEyebrowStyle, color = CecapiTextMuted)
        Text(
            "Crea tu cuenta",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        // What the voice says is not repeated on screen; the assistant guides the steps by voice.
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
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

        Text(
            text = "NOMBRE COMPLETO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.nombreCompleto,
            onValueChange = viewModel::onNombreCompletoChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .voiceHint(
                    help = "Campo de nombre completo. Aquí va tu nombre y apellido, como quieres que te salude.",
                    focusLabel = "Campo de nombre completo. Escribe tu nombre y apellido.",
                ),
            placeholder = { Text("Tu nombre y apellido") },
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        Text(
            text = "USUARIO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.nombreUsuario,
            onValueChange = viewModel::onNombreUsuarioChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .voiceHint(
                    help = "Campo de usuario. Elige el nombre corto con el que vas a entrar. " +
                        "Se escribe en mayúsculas automáticamente.",
                    focusLabel = "Campo de usuario. Elige un nombre corto para entrar.",
                ),
            placeholder = { Text("Elige un nombre de usuario") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        Text(
            text = "CONTRASEÑA",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.contrasena,
            onValueChange = viewModel::onContrasenaChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .voiceHint(
                    help = "Campo de contraseña. Elige una clave secreta de al menos cuatro caracteres. " +
                        "Se ve oculta y nunca la leo en voz alta.",
                    focusLabel = "Campo de contraseña. Elige una clave de al menos cuatro caracteres.",
                ),
            placeholder = { Text("Mínimo cuatro caracteres") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                    )
                }
            },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CecapiAccent),
        )

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                color = CecapiError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = !uiState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(50))
                .voiceHint("Crear mi cuenta. Guarda tus datos y abre tu cuenta nueva."),
            colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Crear mi cuenta", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
