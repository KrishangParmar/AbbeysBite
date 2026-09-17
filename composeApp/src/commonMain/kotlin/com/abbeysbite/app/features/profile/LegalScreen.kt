package com.abbeysbite.app.features.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.abbeysbite.app.core.config.LegalContent
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.platform.UrlOpener
import org.koin.compose.koinInject

/**
 * In-app legal pages rendering the canonical bundled content — always real,
 * always available offline. When hosted URLs are configured, an open-in-
 * browser action appears too (store listings link to the hosted copies).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
    kindName: String,
    onBack: () -> Unit,
    urlOpener: UrlOpener = koinInject(),
) {
    val kind = LegalContent.Kind.entries.firstOrNull { it.name == kindName }
        ?: LegalContent.Kind.PRIVACY

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    LegalContent.hostedUrl(kind)?.let { url ->
                        IconButton(onClick = { urlOpener.openUrl(url) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open in browser")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.screenPadding),
        ) {
            Text(
                LegalContent.body(kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}
