package com.abbeysbite.app

import androidx.compose.runtime.Composable
import com.abbeysbite.app.core.designsystem.AppTheme
import com.abbeysbite.app.navigation.RootNavigation

/** Root of the shared Compose application — hosted by each platform's entry point. */
@Composable
fun App() {
    AppTheme {
        RootNavigation()
    }
}
