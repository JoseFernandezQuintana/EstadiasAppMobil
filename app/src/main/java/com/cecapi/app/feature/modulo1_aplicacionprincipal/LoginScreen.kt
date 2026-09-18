package com.cecapi.app.feature.modulo1_aplicacionprincipal

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiTextMuted

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onMicTapped() }

    LaunchedEffect(Unit) {
        viewModel.loginSucceeded.collect { onLoginSuccess() }
    }
    LaunchedEffect(Unit) {
        viewModel.navigateToRegister.collect { onNavigateToRegister() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Di tu usuario y contraseña, o escríbelos abajo",
            style = MaterialTheme.typography.bodyLarge,
            color = CecapiTextMuted,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CecapiSurface)
                .clickable {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) viewModel.onMicTapped() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = CecapiAccent)
                Text(
                    text = "Para iniciar sesión en CECAPI, di tu usuario o escríbelo. Luego di tu contraseña.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        Text(
            text = "USUARIO",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tu nombre de usuario") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CecapiAccent,
                unfocusedBorderColor = CecapiTextMuted,
            ),
        )

        Text(
            text = "CONTRASEÑA",
            style = CecapiEyebrowStyle,
            color = CecapiTextMuted,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tu contraseña") },
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CecapiAccent,
                unfocusedBorderColor = CecapiTextMuted,
            ),
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
                .clip(RoundedCornerShape(50)),
            colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
        ) {
            if (uiState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Ingresar al sistema", color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Demo: usuario cecapi · contraseña 1234",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiTextMuted,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable(onClick = onNavigateToRegister),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "¿No tienes cuenta? Crear una",
                style = MaterialTheme.typography.bodyMedium,
                color = CecapiAccent,
            )
        }
    }
}
