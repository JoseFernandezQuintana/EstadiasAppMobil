# CECAPI — Sistema de Asistencia Auditiva

App Android nativa (Kotlin + Jetpack Compose) para usuarios de CECAPI con discapacidad visual. Implementa los 7 módulos de la versión 1.0 descritos en la propuesta de estadías, con voz como interfaz principal en todas las pantallas.

## Cómo abrir el proyecto

1. Instala **Android Studio** (versión Ladybug/2024.2 o más reciente).
2. Abre la carpeta `CECAPI-App/` completa como proyecto (`File → Open`).
3. Deja que Android Studio descargue el Gradle wrapper y sincronice (la primera vez tarda varios minutos porque baja Kotlin, Compose, Room, Hilt, CameraX y ML Kit).
4. Conecta un dispositivo físico o crea un emulador con **Android 8.0 (API 26) o superior** y con Google Play Services (los emuladores "sin Google APIs" no traen ML Kit ni el reconocedor de voz).
5. Ejecuta con el botón ▶ (Run 'app').

No se puede compilar ni ejecutar este proyecto desde este entorno de conversación — aquí no hay SDK de Android ni emulador. Todo el código fue escrito y revisado manualmente línea por línea, pero **la primera compilación en Android Studio es el momento en que se detectarán errores reales de sintaxis o de versiones de dependencias**; es normal necesitar 1-2 ajustes menores.

## Cuenta de prueba

- Usuario: `cecapi`
- Contraseña: `1234`

Se crea automáticamente la primera vez que la app abre su base de datos (ver `DatabaseModule.kt`).

## Qué está implementado (versión 1.0)

**Cada equipo trabaja únicamente dentro de su carpeta numerada** en
`app/src/main/java/com/cecapi/app/feature/`. El número de carpeta es el mismo
número de módulo de la propuesta — no hay que adivinar ni cruzar referencias.

| # | Módulo | Carpeta (`feature/...`) | Pantallas | Tablas Room |
|---|--------|--------------------------|-----------|-------------|
| 1 | Aplicación Principal Accesible | `modulo1_aplicacionprincipal/` | Home, Login, **Crear cuenta**, Dashboard, **Configuración de voz** | `usuarios`, `configuracion_usuario`, `permisos_modulos` |
| 2 | Asistente de Voz | `modulo2_asistentevoz/` | Comando por voz + historial | `comandos_voz`, `historial_comandos`, `respuestas_auditivas` |
| 3 | Asistente Inteligente | `modulo3_asistenteinteligente/` | Chat por voz con IA (vía proxy backend) | `consultas_ia`, `respuestas_ia`, `contextos_conversacion` |
| 4 | Lector Inteligente de Documentos | `modulo4_lectordocumentos/` | Cámara + OCR con ML Kit | `documentos_escaneados`, `textos_extraidos`, `historial_lecturas` |
| 5 | Centro de Solicitudes | `modulo5_solicitudes/` | Plantillas + generador guiado por voz | `plantillas_solicitud`, `solicitudes_generadas`, `datos_solicitud` |
| 6 | Centro de Aprendizaje | `modulo6_aprendizaje/` | Ejercicios auditivos + niveles | `ejercicios`, `resultados_ejercicios`, `niveles_aprendizaje` |
| 7 | Asistente del Entorno | `modulo7_entorno/` | Cámara + detección de objetos | `escaneos_entorno`, `objetos_detectados`, `descripciones_entorno` |

**Módulo 1 — qué se agregó después de la revisión inicial:** al revisar qué tan completo
estaba este módulo encontramos dos huecos reales — solo existía el usuario demo fijo
(no había forma de registrar gente nueva), y la tabla `configuracion_usuario` estaba
definida pero ninguna pantalla la usaba. Se agregaron:
- **Crear cuenta** (`RegisterScreen.kt`): registro de usuario nuevo, guiado por voz igual que el login. Funciona con el comando de voz "crear cuenta" desde la pantalla de login.
- **Configuración de voz** (`SettingsScreen.kt`, ícono de engranaje en el Dashboard o el comando de voz "configuración"): controla velocidad y volumen de la voz y el modo simple, y ahora sí se conecta de verdad al `VoiceEngine` (antes la velocidad estaba fija en el código).

**Regla para cada equipo:** edita solo los archivos dentro de tu carpeta
`moduloN_...`. Si necesitas algo de otro módulo (por ejemplo, `UsuarioEntity`
del módulo 1), impórtalo — no lo copies ni lo muevas. Los únicos archivos
compartidos entre todos los módulos viven en `core/` (tema, motor de voz,
navegación, base de datos) y en `core/di/DatabaseModule.kt` — si tu módulo
necesita una tabla nueva, avísale a quien mantenga ese archivo para que
registre tu DAO ahí.

Los módulos 8-14 (versión 1.5) están **documentados en `database/schema.sql`** pero no tienen código Kotlin todavía — quedan listos para que un nuevo equipo los tome sin rediseñar la base de datos.

## Decisiones técnicas y por qué

- **Kotlin nativo + Jetpack Compose**, no React Native: SpeechRecognizer, TextToSpeech, CameraX y ML Kit tienen soporte directo en Android; en RN cada uno necesitaría un módulo puente nativo escrito a mano. Compose además expone `Modifier.semantics` para integrarse con TalkBack.
- **Room + Hilt**: una sola base de datos (`cecapi.db`) con las tablas de cada módulo, tal como recomienda la sección 9 de la propuesta ("estrategia mixta").
- **Un solo `VoiceEngine` compartido** (`core/voice/VoiceEngine.kt`): Android solo permite un `SpeechRecognizer`/`TextToSpeech` confiable por proceso, así que todos los módulos leen su estado en vez de crear el suyo.
- **El asistente inteligente NO llama a ninguna API de IA directamente desde el teléfono.** Llama a un backend propio (`AiAssistantApi.kt`, URL configurable en `BuildConfig.AI_PROXY_BASE_URL`). Poner una API key dentro de un APK la expone a cualquiera que lo descompile — no existe un almacenamiento "seguro" en el cliente que lo arregle.
  - **Ese backend está pendiente a propósito** — de los 7 módulos, es el único que necesita algo fuera de la app, así que le corresponde al equipo del Módulo 3 construirlo como parte de su propio módulo completo (regla de la sección 4 de la propuesta: nada de equipos exclusivos de backend). Los otros 6 equipos no necesitan esperar nada de esto para empezar a trabajar.
  - **Proveedor de IA recomendado: Gemini API** (Google AI Studio) — tiene nivel gratuito permanente, algo que Claude y OpenAI no ofrecen fuera de un crédito de prueba que se agota. Como la app solo habla con el backend propio (nunca con el proveedor directamente), cambiar de proveedor después es una decisión que se toma sin tocar la app.
  - El contrato ya está definido (ver el comentario en `AiAssistantApi.kt`): `POST {AI_PROXY_BASE_URL}` con `{"pregunta", "contexto"}` y responde `{"respuesta"}`. Mientras no exista, la app maneja el error de red con un mensaje hablado en vez de tronar.
- **Tema oscuro fijo**, no sigue el modo claro/oscuro del sistema: para usuarios con baja visión el contraste constante importa más que la convención de la plataforma (ver comentario en `core/theme/Theme.kt`).

## Limitaciones conocidas / próximos pasos

1. **Backend del asistente de IA (Módulo 3)**: pendiente a propósito, es tarea del equipo de ese módulo (ver arriba). Ningún otro módulo depende de esto.
2. **Sin pruebas automatizadas todavía** (unit tests / instrumented tests) — la propuesta pide "pruebas del módulo" como entregable; agregar JUnit + Compose UI Test por módulo es el siguiente paso lógico.
3. **Sin ícono final de la app**: se usa un ícono adaptativo vectorial simple (un micrófono) como placeholder.
4. **CameraX en emulador**: los emuladores sin cámara virtual configurada no muestran vista previa; usar un dispositivo físico para probar los módulos 4 y 7.
5. **No se integró un modo offline explícito** más allá de detectar conexión (`ConnectivityObserver`) y avisar por voz — el modo offline completo se dejó como "futura implementación" en la propuesta original.
