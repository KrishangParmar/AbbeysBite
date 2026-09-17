package com.abbeysbite.app.features.improve

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.abbeysbite.app.core.config.Brand
import com.abbeysbite.app.core.designsystem.Dimens
import com.abbeysbite.app.core.designsystem.PrimaryButton
import com.abbeysbite.app.data.model.MealAnalysis
import com.abbeysbite.app.platform.ShareService
import com.abbeysbite.app.platform.encodeToPng
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * "Two tiny additions" share card — a beautiful exportable image showing the
 * meal plus the additions the user is making. Rendered offscreen via
 * GraphicsLayer and exported as PNG to the native share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet(
    analysis: MealAnalysis,
    imageBytes: ByteArray?,
    onDismiss: () -> Unit,
    shareService: ShareService = koinInject(),
) {
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var sharing by remember { mutableStateOf(false) }
    val additions = analysis.suggestedAdditions.take(2)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Dimens.screenPadding).padding(bottom = Dimens.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Share your upgrade", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Dimens.md))

            // The card itself — recorded into the graphics layer for export.
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(graphicsLayer)
                    },
            ) {
                ShareCardContent(analysis, imageBytes, additions)
            }

            Spacer(Modifier.height(Dimens.lg))
            PrimaryButton(
                text = "Share as image",
                loading = sharing,
                onClick = {
                    sharing = true
                    scope.launch {
                        runCatching {
                            val bitmap = graphicsLayer.toImageBitmap()
                            val png = bitmap.encodeToPng()
                            if (png.isNotEmpty()) {
                                shareService.shareImage(
                                    png,
                                    "${analysis.detectedMealName} — upgraded with ${Brand.appName}",
                                )
                            }
                        }
                        sharing = false
                    }
                },
            )
        }
    }
}

@Composable
private fun ShareCardContent(
    analysis: MealAnalysis,
    imageBytes: ByteArray?,
    additions: List<com.abbeysbite.app.data.model.SuggestedAddition>,
) {
    // Fixed light palette — share cards should look identical regardless of theme.
    val cardBg = Color(0xFFFAF9F7)
    val ink = Color(0xFF1B1B18)
    val subtle = Color(0xFF6E6C66)
    val accent = Brand.accent

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .padding(20.dp),
    ) {
        Text(
            when (additions.size) {
                0 -> "Today’s plate."
                1 -> "One tiny addition."
                else -> "Two tiny additions."
            },
            style = MaterialTheme.typography.headlineMedium,
            color = ink,
        )
        Spacer(Modifier.height(14.dp))

        if (imageBytes != null) {
            Image(
                painter = rememberAsyncImagePainter(imageBytes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brand.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    analysis.detectedMealName,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            analysis.detectedMealName,
            style = MaterialTheme.typography.titleMedium,
            color = ink,
        )
        Spacer(Modifier.height(10.dp))
        additions.forEach { addition ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Brand.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    addition.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ink,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Brand.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = subtle,
            )
            Text(
                Brand.shareFooter,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
    }
}
