package com.example.pictureviewer.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pictureviewer.data.LibraryEntry
import com.example.pictureviewer.ui.components.ComicCard
import com.example.pictureviewer.ui.components.ComicListItem
import com.example.pictureviewer.ui.theme.AppTheme
import com.example.pictureviewer.ui.theme.rememberAppDimens
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onComicClick: (String, String?) -> Unit,
    onRenameClick: () -> Unit = {},
    onRenameFolderClick: (String, String?) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = viewModel()
) {
    val comics by viewModel.comics.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanCount by viewModel.scanCount.collectAsState()
    val displayMode by viewModel.displayMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val c = AppTheme.colors
    val d = rememberAppDimens()
    var entryToDelete by remember { mutableStateOf<LibraryEntry?>(null) }
    var entryToRefresh by remember { mutableStateOf<LibraryEntry?>(null) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var longPressEntry by remember { mutableStateOf<LibraryEntry?>(null) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 从其他页面返回时重新加载数据
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.loadComics()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.importFolder(it) }
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.statusError) },
            title = { Text("删除漫画", color = c.textPrimary) },
            text = { Text("确定删除「${entry.name}」？", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComic(entry)
                    entryToDelete = null
                }) {
                    Text("删除", color = c.statusError)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("取消", color = c.textSecondary)
                }
            },
            containerColor = c.glassSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    entryToRefresh?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToRefresh = null },
            icon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.accent) },
            title = { Text("刷新漫画", color = c.textPrimary) },
            text = { Text("重新扫描「${entry.name}」？", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.refreshSingleComic(entry)
                    entryToRefresh = null
                }) {
                    Text("刷新", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToRefresh = null }) {
                    Text("取消", color = c.textSecondary)
                }
            },
            containerColor = c.glassSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showLongPressMenu && longPressEntry != null) {
        val entry = longPressEntry!!
        AlertDialog(
            onDismissRequest = { showLongPressMenu = false; longPressEntry = null },
            title = { Text(entry.name, color = c.textPrimary, maxLines = 1) },
            confirmButton = {},
            dismissButton = {},
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            // 传递 folderUri 和 rootUri
                            onRenameFolderClick(entry.uri, entry.rootUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("改名", color = c.textPrimary)
                    }
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            entryToRefresh = entry
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("刷新", color = c.textPrimary)
                    }
                    TextButton(
                        onClick = {
                            showLongPressMenu = false
                            entryToDelete = entry
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = c.statusError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("删除", color = c.statusError)
                    }
                }
            },
            containerColor = c.glassSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Collections,
                                contentDescription = null,
                                modifier = Modifier.size(d.titleIconSize),
                                tint = c.accent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "图片查看器",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = c.textPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(onClick = onRenameClick) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "重命名",
                            tint = c.textPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (comics.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                if (displayMode == DisplayMode.GRID) {
                                    gridState.scrollToItem(0)
                                } else {
                                    listState.scrollToItem(0)
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        containerColor = c.glassSurface,
                        contentColor = c.textSecondary,
                        shape = RoundedCornerShape(12.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "顶部", modifier = Modifier.size(20.dp))
                    }

                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                if (displayMode == DisplayMode.GRID) {
                                    gridState.scrollToItem(comics.size - 1)
                                } else {
                                    listState.scrollToItem(comics.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        containerColor = c.glassSurface,
                        contentColor = c.textSecondary,
                        shape = RoundedCornerShape(12.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "底部", modifier = Modifier.size(20.dp))
                    }
                }

                FloatingActionButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.size(40.dp),
                    containerColor = c.accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "导入文件夹", modifier = Modifier.size(20.dp))
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bgGradient)
                .padding(paddingValues)
        ) {
            if (comics.isEmpty() && !isScanning) {
                EmptyStateView()
            } else {
                Column {
                    // 扫描进度条
                    if (isScanning) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.glassSurface.copy(alpha = 0.9f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = c.accent,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "扫描中... 已发现 $scanCount 个文件夹",
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textPrimary
                            )
                        }
                    }

                    ToolbarRow(
                        displayMode = displayMode,
                        sortOrder = sortOrder,
                        onDisplayModeChange = { viewModel.setDisplayMode(it) },
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        onRefresh = { viewModel.refreshAllComics() }
                    )
                    
                    // 漫画统计
                    Text(
                        text = "共 ${comics.size} 本",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    when (displayMode) {
                        DisplayMode.GRID -> {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(d.gridMinSize),
                                contentPadding = PaddingValues(d.horizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(d.verticalSpacing),
                                verticalArrangement = Arrangement.spacedBy(d.verticalSpacing)
                            ) {
                                items(comics, key = { it.uri }) { entry ->
                                    ComicCard(
                                        entry = entry,
                                        onClick = { onComicClick(entry.uri, entry.rootUri) },
                                        onLongPress = {
                                            longPressEntry = entry
                                            showLongPressMenu = true
                                        }
                                    )
                                }
                            }
                        }
                        DisplayMode.LIST -> {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(d.horizontalPadding),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(comics, key = { it.uri }) { entry ->
                                    ComicListItem(
                                        entry = entry,
                                        onClick = { onComicClick(entry.uri, entry.rootUri) },
                                        onLongPress = {
                                            longPressEntry = entry
                                            showLongPressMenu = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarRow(
    displayMode: DisplayMode,
    sortOrder: SortOrder,
    onDisplayModeChange: (DisplayMode) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onRefresh: () -> Unit
) {
    val c = AppTheme.colors
    var showSortMenu by remember { mutableStateOf(false) }
    val toolbarShape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .shadow(4.dp, toolbarShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                .clip(toolbarShape)
                .background(c.glassSurface)
                .border(1.dp, c.glassBorder, toolbarShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val activeColor = c.accent
            val inactiveColor = c.textSecondary

            IconButton(
                onClick = { onDisplayModeChange(DisplayMode.GRID) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (displayMode == DisplayMode.GRID) c.accent.copy(alpha = 0.15f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GridView,
                    contentDescription = "网格",
                    modifier = Modifier.size(20.dp),
                    tint = if (displayMode == DisplayMode.GRID) activeColor else inactiveColor
                )
            }
            IconButton(
                onClick = { onDisplayModeChange(DisplayMode.LIST) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (displayMode == DisplayMode.LIST) c.accent.copy(alpha = 0.15f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.List,
                    contentDescription = "列表",
                    modifier = Modifier.size(20.dp),
                    tint = if (displayMode == DisplayMode.LIST) activeColor else inactiveColor
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .shadow(4.dp, toolbarShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                    .clip(toolbarShape)
                    .background(c.glassSurface)
                    .border(1.dp, c.glassBorder, toolbarShape)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(20.dp),
                    tint = c.textPrimary
                )
            }

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .shadow(4.dp, toolbarShape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                        .clip(toolbarShape)
                        .background(c.glassSurface)
                        .border(1.dp, c.glassBorder, toolbarShape)
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapVert,
                        contentDescription = "排序",
                        modifier = Modifier.size(20.dp),
                        tint = c.textPrimary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier
                        .background(c.glassSurface)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(12.dp))
                ) {
                    SortMenuItem("名称 A→Z", SortOrder.NAME_ASC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                    SortMenuItem("名称 Z→A", SortOrder.NAME_DESC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                    SortMenuItem("时间 旧→新", SortOrder.DATE_ASC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                    SortMenuItem("时间 新→旧", SortOrder.DATE_DESC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                    SortMenuItem("图片数 ↑", SortOrder.COUNT_ASC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                    SortMenuItem("图片数 ↓", SortOrder.COUNT_DESC, sortOrder) {
                        onSortOrderChange(it)
                        showSortMenu = false
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    order: SortOrder,
    currentOrder: SortOrder,
    onClick: (SortOrder) -> Unit
) {
    val c = AppTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (order == currentOrder) c.accent else c.textPrimary,
                fontWeight = if (order == currentOrder) FontWeight.Bold else FontWeight.Normal
            )
        },
        onClick = { onClick(order) }
    )
}

@Composable
private fun EmptyStateView() {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .shadow(8.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight)
                .clip(shape)
                .background(c.glassSurface)
                .border(1.dp, c.glassBorder, shape)
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = c.accent
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "暂无漫画",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击 + 导入文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
        }
    }
}
