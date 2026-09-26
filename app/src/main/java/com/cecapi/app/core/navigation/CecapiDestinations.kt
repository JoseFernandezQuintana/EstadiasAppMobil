package com.cecapi.app.core.navigation

/** Central route registry so every module refers to the same route strings. */
object CecapiDestinations {
    const val HOME = "home"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val PERSONALIZATION = "personalization"
    const val CHATS = "chats"

    /** The "Cámara" card: one entry that leads to reading text or describing what is in front. */
    const val CAMERA_HUB = "camera_hub"

    const val VOICE_ASSISTANT = "voice_assistant"
    const val AI_ASSISTANT = "ai_assistant"
    const val DOCUMENT_READER = "document_reader"
    const val REQUESTS = "requests"
    const val LEARNING = "learning"
    const val ENVIRONMENT = "environment"
}
