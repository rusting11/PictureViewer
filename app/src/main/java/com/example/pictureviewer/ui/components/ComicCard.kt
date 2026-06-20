package com.example.pictureviewer.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pictureviewer.data.LibraryEntry
import com.example.pictureviewer.ui.theme.AppTheme
import com.example.pictureviewer.ui.theme.rememberAppDimens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(
    entry: LibraryEntry,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = AppTheme.colors
    val d = rememberAppDimens()
    val shape = RoundedCornerShape(d.cardCornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
            .clip(shape)
            .background(c.glassSurface)
            .border(1.dp, c.glassBorder, shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(d.coverAspectRatio)
                    .clip(RoundedCornerShape(topStart = d.cardCornerRadius, topEnd = d.cardCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (entry.coverImagePath != null) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(entry.coverImagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = entry.name.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textTertiary
                    )
                }
            }
            Text(
                text = entry.name,
                modifier = Modifier.padding(horizontal = d.cardPadding, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
