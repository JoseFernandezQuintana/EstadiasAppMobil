package com.cecapi.app.feature.modulo1_aplicacionprincipal

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cecapi.app.core.theme.CecapiAccent
import com.cecapi.app.core.theme.CecapiError
import com.cecapi.app.core.theme.CecapiEyebrowStyle
import com.cecapi.app.core.theme.CecapiSurface
import com.cecapi.app.core.theme.CecapiSurfaceElevated
import com.cecapi.app.core.theme.CecapiTextMuted
import com.cecapi.app.core.ui.NoticeBanner
import com.cecapi.app.core.ui.ScreenTopBar
import com.cecapi.app.core.ui.SuggestionChip
import com.cecapi.app.core.ui.voiceHint
import kotlin.math.roundToInt

/**
 * Configuración, in blocks so each thing has one obvious place: Volumen, Notificaciones,
 * Escuchar fuera de la aplicación, Accesibilidad and (only when signed in) Cuenta.
 * The assistant's voice and names are in Personalización.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val simpleMode by viewModel.simpleMode.collectAsState()
    val announceNotifications by viewModel.announceNotifications.collectAsState()
    val listenOutsideApp by viewModel.listenOutsideApp.collectAsState()
    val user by viewModel.currentUser.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val pendingClean by viewModel.pendingClean.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val pendingDeleteAccount by viewModel.pendingDeleteAccount.collectAsState()

    var volume by remember { mutableFloatStateOf(viewModel.currentVolumePercent().toFloat()) }

    LaunchedEffect(Unit) { viewModel.loggedOut.collect { onLoggedOut() } }

    // The permission is granted in Android's own settings, so check again every time we come back.
    var notificationAccess by remember { mutableStateOf(viewModel.isNotificationAccessEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationAccess = viewModel.isNotificationAccessEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ asks before showing the always-visible "Asistente activo" notice; the service works either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onListenOutsideAppChanged(true)
    }

    // The switch and the voice command ("activa escuchar fuera de la aplicación") do the same thing.
    val changeListenOutside: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onListenOutsideAppChanged(enabled)
        }
    }
    LaunchedEffect(Unit) { viewModel.listenOutsideRequests.collect { changeListenOutside(it) } }
    LaunchedEffect(Unit) { viewModel.back.collect { onBack() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenTopBar(
            eyebrow = "MENÚ",
            title = "Configuración",
            onBack = onBack,
            onCommands = viewModel::onCommandsRequested,
        )

        Section("VOLUMEN", "Qué tan fuerte se oye todo en el teléfono") {
            SliderRow(
                label = "Volumen",
                valueText = "${volume.roundToInt()}%",
                value = volume,
                range = 1f..100f,
                steps = 0,
                onChange = { volume = it },
                onFinished = { viewModel.onVolumeChanged(volume.roundToInt()) },
                help = "Volumen. Sube o baja el volumen de todo el teléfono, igual que los botones laterales. " +
                    "No baja hasta silencio para que siempre me escuches.",
            )
        }

        Section("NOTIFICACIONES", "Que te lea los mensajes y avisos del teléfono cuando se lo pidas") {
            Text(
                text = if (notificationAccess) "Acceso concedido" else "Falta dar acceso",
                style = MaterialTheme.typography.titleMedium,
                color = if (notificationAccess) CecapiAccent else CecapiError,
            )
            if (!notificationAccess) {
                Text(
                    "Android pide que lo actives tú, una sola vez, en sus ajustes. Busca CECAPI y enciéndelo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
                ActionButton(
                    "Dar acceso a notificaciones",
                    "Dar acceso a notificaciones. Abre los ajustes de Android, donde debes buscar CECAPI y activarlo.",
                    viewModel::onOpenNotificationSettings,
                )
            } else {
                Text(
                    "Di: qué notificaciones tengo, léeme la última, o léelas todas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
            }
            SwitchRow(
                label = "Avisar cuando llegan",
                description = "Dice solo \"nueva notificación de\" y la app, nunca el contenido",
                checked = announceNotifications,
                onChange = viewModel::onAnnounceNotificationsChanged,
                help = "Avisar cuando llegan. Cuando entra una notificación digo de qué aplicación es, " +
                    "pero nunca leo lo que dice si no me lo pides. No aviso durante llamadas ni con No molestar.",
            )
        }

        Section("ALMACENAMIENTO", "Cuánto espacio queda en el teléfono y cuánto ocupa la aplicación") {
            val usage = storage
            if (usage == null) {
                Text("Calculando…", style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
            } else {
                if (usage.lowSpace) NoticeBanner("Queda poco espacio en el teléfono. Conviene liberar espacio.")
                Text(
                    text = viewModel.describeStorage(usage),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (usage.oldPhotoCount > 0) {
                    Text(
                        "${usage.oldPhotoCount} fotos tienen más de un mes y se pueden borrar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CecapiTextMuted,
                    )
                }
            }
            if (pendingClean) {
                NoticeBanner("¿Borrar las fotos de más de un mes? Di sí o no.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip(label = "sí", onClick = viewModel::confirmClean)
                    SuggestionChip(label = "no", onClick = viewModel::cancelClean)
                }
            } else {
                ActionButton(
                    "Liberar espacio",
                    "Liberar espacio. Borra las fotos que la aplicación guardó hace más de un mes. " +
                        "Te pido confirmación antes de borrar y el texto que leí sigue guardado. También puedes decir: libera espacio.",
                    viewModel::askClean,
                )
                ActionButton(
                    "Vaciar memoria temporal",
                    "Vaciar memoria temporal. Borra archivos temporales que la aplicación y sus librerías guardan para ir más rápido. " +
                        "No borra tus chats ni nada que hayas hecho. También puedes decir: limpia la caché.",
                    viewModel::clearCache,
                )
                ActionButton(
                    "Escuchar el reporte",
                    "Escuchar el reporte. Te digo cuánto espacio libre queda y cuánto ocupa la aplicación. " +
                        "También puedes decir: cuánto espacio tengo.",
                    viewModel::speakStorage,
                )
            }
        }

        Section("ESCUCHAR FUERA DE LA APLICACIÓN", "Seguir atento a \"hola\" aunque cierres la aplicación") {
            SwitchRow(
                label = "Escuchar fuera de la aplicación",
                description = "Muestra un aviso fijo mientras escucha. Di \"para\" para detenerlo",
                checked = listenOutsideApp,
                onChange = changeListenOutside,
                help = "Escuchar fuera de la aplicación. Sigo atento a la palabra hola o a mi nombre aunque cierres " +
                    "la aplicación. Mientras tanto verás un aviso fijo, y puedes detenerme diciendo para. " +
                    "Gasta más batería.",
            )
        }

        Section("PRIVACIDAD", "Qué sale de tu teléfono: por defecto, nada") {
            SwitchRow(
                label = "Inteligencia artificial en internet",
                description = "Todavía no está conectada. No la actives si quien usa la aplicación es menor de edad",
                checked = aiEnabled,
                onChange = viewModel::onAiEnabledChanged,
                help = "Inteligencia artificial en internet. Apagada, nada de lo que dices se envía a ningún servidor. " +
                    "Encendida, cuando no entienda una frase y haya internet, podrá enviarla a un servidor para interpretarla. " +
                    "Los proveedores de inteligencia artificial no permiten su uso con menores de edad. " +
                    "También puedes decir: activa la inteligencia artificial, o desactívala.",
            )
        }

        Section("ACCESIBILIDAD", "Para que todo sea más fácil de usar") {
            SwitchRow(
                label = "Modo simple",
                description = "Menús más grandes y con menos opciones a la vez",
                checked = simpleMode,
                onChange = viewModel::onSimpleModeChanged,
                help = "Modo simple. Muestra los menús más grandes y con menos opciones a la vez.",
            )
        }

        user?.let { current ->
            Section("CUENTA", "Con quién iniciaste sesión") {
                Text(current.nombreCompleto, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    buildString {
                        append("Usuario ${current.nombreUsuario} · rol ${current.rol}")
                        if (current.origen.isNotBlank()) append(" · ${current.origen}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CecapiTextMuted,
                )
                Button(
                    onClick = viewModel::onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .semantics { contentDescription = "Cerrar sesión. Toca dos veces para salir de tu cuenta." }
                        .voiceHint("Cerrar sesión. Sale de tu cuenta y vuelve al inicio. Tus datos se conservan."),
                    colors = ButtonDefaults.buttonColors(containerColor = CecapiError, contentColor = Color.White),
                ) {
                    Text("Cerrar sesión", style = MaterialTheme.typography.titleMedium)
                }
                if (pendingDeleteAccount) {
                    NoticeBanner("¿Borrar tu cuenta y todo lo que guardaste? No se puede deshacer. Di sí, borrar, o no.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(label = "sí, borrar", onClick = viewModel::confirmDeleteAccount)
                        SuggestionChip(label = "no", onClick = viewModel::cancelDeleteAccount)
                    }
                } else {
                    ActionButton(
                        "Borrar mi cuenta y mis datos",
                        "Borrar mi cuenta y mis datos. Elimina tu cuenta y todo lo que guardaste: chats, resultados, documentos y fotos. " +
                            "Te pido confirmación y no se puede deshacer. También puedes decir: borra mi cuenta.",
                        viewModel::askDeleteAccount,
                    )
                }
            }
        }
    }
}

/** One block of the screen: a title, one line saying what it is for, then its controls. */
@Composable
internal fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CecapiSurface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(title, style = CecapiEyebrowStyle, color = CecapiAccent)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted, modifier = Modifier.padding(top = 2.dp))
        }
        content()
    }
}

@Composable
internal fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    help: String,
) {
    Column(modifier = Modifier.voiceHint(help)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(valueText, style = MaterialTheme.typography.titleMedium, color = CecapiAccent)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = CecapiAccent, activeTrackColor = CecapiAccent),
            modifier = Modifier.semantics { contentDescription = "$label, $valueText" },
        )
    }
}

@Composable
internal fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    help: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .heightIn(min = 56.dp)
            .voiceHint(help),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = CecapiTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CecapiAccent, checkedTrackColor = CecapiAccent.copy(alpha = 0.5f)),
        )
    }
}

@Composable
internal fun ActionButton(text: String, help: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .voiceHint(help),
        colors = ButtonDefaults.buttonColors(containerColor = CecapiAccent),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun SmallButton(text: String, help: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).voiceHint(help),
        colors = ButtonDefaults.buttonColors(containerColor = CecapiSurfaceElevated, contentColor = CecapiAccent),
    ) {
        Text(text)
    }
}
