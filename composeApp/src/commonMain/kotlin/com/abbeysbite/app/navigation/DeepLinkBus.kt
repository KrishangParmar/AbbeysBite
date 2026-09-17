package com.abbeysbite.app.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Routes notification taps (OneSignal deep links like
 * `abbeysbite://friends/requests`) into navigation. Platform entry points
 * parse URIs and emit; RootNavigation/MainShell consume.
 */
object DeepLinkBus {

    private val _routes = MutableSharedFlow<Route>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val routes: SharedFlow<Route> = _routes

    private val _tabRequest = MutableStateFlow<MainTab?>(null)
    val tabRequest: StateFlow<MainTab?> = _tabRequest

    fun consumeTabRequest() {
        _tabRequest.value = null
    }

    /** Maps an abbeysbite:// URI (host + path) to app navigation. */
    fun handleUri(host: String?, path: String?) {
        when (host) {
            "friends" -> _routes.tryEmit(Route.Friends)
            "journal" -> _tabRequest.value = MainTab.JOURNAL
            "community" -> when (path?.trim('/')) {
                "mine" -> _routes.tryEmit(Route.MyRecipes)
                else -> _tabRequest.value = MainTab.COMMUNITY
            }
        }
    }
}
