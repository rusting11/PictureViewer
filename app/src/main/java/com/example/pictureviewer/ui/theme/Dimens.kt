package com.example.pictureviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class AppDimens(
    val horizontalPadding: Dp,
    val cardPadding: Dp,
    val verticalSpacing: Dp,
    val cardCornerRadius: Dp,
    val iconSize: Dp,
    val titleIconSize: Dp,
    val coverAspectRatio: Float,
    val gridMinSize: Dp
)

@Composable
fun rememberAppDimens(): AppDimens {
    val config = LocalConfiguration.current
    val w = config.screenWidthDp.toFloat()

    val wScale = (w / 360f).coerceIn(0.85f, 1.3f)

    return remember(w) {
        AppDimens(
            horizontalPadding = (16 * wScale).dp,
            cardPadding = (12 * wScale).dp,
            verticalSpacing = (10 * wScale).dp,
            cardCornerRadius = (20 * wScale).dp,
            iconSize = (22 * wScale).dp,
            titleIconSize = (28 * wScale).dp,
            coverAspectRatio = 0.7f,
            gridMinSize = (150 * wScale).dp
        )
    }
}
