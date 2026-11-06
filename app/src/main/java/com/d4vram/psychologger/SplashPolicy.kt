package com.d4vram.psychologger

/**
 * Regla del splash: se muestra en las primeras [AUTO_LAUNCHES] aperturas (instalación + 3);
 * en la siguiente se desactiva solo, salvo que el usuario ya tocara el interruptor (autoDone).
 */
object SplashPolicy {
    const val AUTO_LAUNCHES = 4

    data class State(val launches: Int, val autoDone: Boolean, val disableNow: Boolean)

    fun onLaunch(launches: Int, autoDone: Boolean): State = when {
        autoDone -> State(launches, true, false)
        launches >= AUTO_LAUNCHES -> State(launches, true, true)
        else -> State(launches + 1, false, false)
    }
}
