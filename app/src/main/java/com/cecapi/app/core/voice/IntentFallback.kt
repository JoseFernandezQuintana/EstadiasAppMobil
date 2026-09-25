package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the AI team plugs in as a "phrase translator". When the assistant does not understand what was
 * said and there is internet, [resolver] gets the phrase and may answer with a command the assistant
 * already knows ("qué hora tengo" -> "hora"), so the offline command list does not have to grow to cover
 * every way people talk. Returning null means "I could not map it either". No resolver is registered yet.
 */
@Singleton
class IntentFallback @Inject constructor() {
    @Volatile
    var resolver: (suspend (String) -> String?)? = null
}
