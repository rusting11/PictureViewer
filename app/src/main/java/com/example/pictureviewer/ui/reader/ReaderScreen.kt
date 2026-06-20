package com.example.pictureviewer.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.pictureviewer.ui.components.ZoomableImage
import com.example.pictureviewer.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    comicUri: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    LaunchedEffect(comicUri) {
        viewModel.loadComic(comicUri)
    }

    val images by viewModel.images.collectAsState()
    val readMode by viewModel.readMode.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val c = AppTheme.colors

    var showControls by remember { mutableStateOf(false) }
    var zoomImageIndex by remember { mutableIntStateOf(-1) }
    var isNavigatingBack by remember { mutableStateOf(false) }

    if (zoomImageIndex >= 0 && zoomImageIndex < images.size) {
        ZoomDialog(
            images = images,
            initialIndex = zoomImageIndex,
            onDismiss = { zoomImageIndex = -1 },
            onIndexChanged = { zoomImageIndex = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val progressShape = RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .shadow(4.dp, progressShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                            .clip(progressShape)
                            .background(c.glassSurface)
                            .border(0.5.dp, c.glassBorder, progressShape)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${currentIndex + 1} / ${images.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            fontSize = 14.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isNavigatingBack) {
                            isNavigatingBack = true
                            viewModel.saveProgress()
                            onBack()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = c.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showControls = !showControls }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "菜单",
                            tint = c.accent
                        )
                    }
                    IconButton(onClick = { viewModel.toggleReadMode() }) {
                        Icon(
                            imageVector = when (readMode) {
                                ReadMode.VERTICAL -> Icons.Rounded.ViewWeek
                                ReadMode.HORIZONTAL -> Icons.Rounded.GridView
                                ReadMode.GRID -> Icons.Rounded.ViewDay
                            },
                            contentDescription = "切换模式",
                            tint = c.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bgGradient)
                .padding(paddingValues)
        ) {
            if (images.isEmpty()) {
                EmptyReaderView()
            } else {
                when (readMode) {
                    ReadMode.VERTICAL -> VerticalReaderWithScrollbar(
                        images = images,
                        initialIndex = currentIndex,
                        onIndexChanged = { viewModel.setCurrentIndex(it) },
                        showControls = showControls,
                        onGoToPage = { page -> }
                    )
                    ReadMode.HORIZONTAL -> HorizontalReader(
                        images = images,
                        initialIndex = currentIndex,
                        onIndexChanged = { viewModel.setCurrentIndex(it) },
                        showControls = showControls,
                        onGoToPage = { page -> }
                    )
                    ReadMode.GRID -> GridReader(
                        images = images,
                        currentIndex = currentIndex,
                        onImageClick = { index -> zoomImageIndex = index },
                        showControls = showControls,
                        onGoToPage = { page -> viewModel.goToPage(page) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomDialog(
    images: List<Any>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onIndexChanged: (Int) -> Unit
) {
    val c = AppTheme.colors
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }

    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImage(
                        model = images[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun EmptyReaderView() {
    val c = AppTheme.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未找到图片",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textSecondary
        )
    }
}

@Composable
private fun GridReader(
    images: List<Any>,
    currentIndex: Int,
    onImageClick: (Int) -> Unit,
    showControls: Boolean,
    onGoToPage: (Int) -> Unit
) {
    val c = AppTheme.colors
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(images, key = { _, uri -> uri.toString() }) { index, imageUri ->
                val isCurrentPage = index == currentIndex

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isCurrentPage) c.accent.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isCurrentPage) 2.dp else 0.dp,
                            color = if (isCurrentPage) c.accent else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onImageClick(index) }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        if (showControls) {
            BottomControlBar(
                currentIndex = currentIndex,
                totalItems = images.size,
                onGoToPage = onGoToPage,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun VerticalReaderWithScrollbar(
    images: List<Any>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    showControls: Boolean,
    onGoToPage: (Int) -> Unit
) {
    val c = AppTheme.colors
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val coroutineScope = rememberCoroutineScope()

    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }

    val totalItems = images.size

    LaunchedEffect(isScrolling) {
        if (!isScrolling) {
            onIndexChanged(firstVisibleIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(images, key = { it.toString() }) { imageUri ->
                ZoomableImage(
                    model = imageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    enableZoom = false
                )
            }
        }

        if (showControls) {
            BottomControlBar(
                currentIndex = firstVisibleIndex,
                totalItems = totalItems,
                onGoToPage = { targetIndex ->
                    coroutineScope.launch {
                        listState.scrollToItem(targetIndex)
                        onIndexChanged(targetIndex)
                    }
                    onGoToPage(targetIndex)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomControlBar(
    currentIndex: Int,
    totalItems: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = AppTheme.colors
    var sliderPosition by remember { mutableFloatStateOf(currentIndex.toFloat()) }
    var isUserDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex) {
        if (!isUserDragging) {
            sliderPosition = currentIndex.toFloat()
        }
    }

    val barShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, barShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
            .clip(barShape)
            .background(c.glassSurface)
            .border(0.5.dp, c.glassBorder, barShape)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            sliderPosition = 0f
                            onGoToPage(0)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.FirstPage,
                        contentDescription = "首页",
                        tint = c.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("首页", color = c.accent, fontSize = 13.sp)
                }

                Text(
                    text = "${(sliderPosition.toInt() + 1).coerceIn(1, totalItems)} / $totalItems",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            sliderPosition = (totalItems - 1).toFloat()
                            onGoToPage(totalItems - 1)
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("尾页", color = c.accent, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.LastPage,
                        contentDescription = "尾页",
                        tint = c.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            var trackWidthPx by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            if (trackWidthPx > 0) {
                                val progress = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                                sliderPosition = (progress * (totalItems - 1)).coerceIn(0f, (totalItems - 1).toFloat())
                                onGoToPage(sliderPosition.toInt())
                            }
                        }
                    }
            ) {
                LinearProgressIndicator(
                    progress = { (sliderPosition + 1) / totalItems.toFloat().coerceAtLeast(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = c.accent,
                    trackColor = c.neuShadow.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun HorizontalReader(
    images: List<Any>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    showControls: Boolean,
    onGoToPage: (Int) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val c = AppTheme.colors
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableImage(
                        model = images[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val progressShape = RoundedCornerShape(12.dp)
            var containerWidthPx by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .shadow(4.dp, progressShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                    .clip(progressShape)
                    .background(c.glassSurface)
                    .border(0.5.dp, c.glassBorder, progressShape)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onSizeChanged { containerWidthPx = it.width.toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            if (containerWidthPx > 0) {
                                val progress = (change.position.x / containerWidthPx).coerceIn(0f, 1f)
                                val targetPage = (progress * (images.size - 1)).toInt().coerceIn(0, images.size - 1)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        }
                    }
            ) {
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1).toFloat() / images.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = c.accent,
                    trackColor = c.neuShadow.copy(alpha = 0.3f),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        if (showControls) {
            BottomControlBar(
                currentIndex = pagerState.currentPage,
                totalItems = images.size,
                onGoToPage = { page ->
                    coroutineScope.launch { pagerState.animateScrollToPage(page) }
                    onGoToPage(page)
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
