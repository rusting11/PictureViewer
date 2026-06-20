package com.example.pictureviewer.ui.rename

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pictureviewer.data.model.FileCategory
import com.example.pictureviewer.data.model.FileRenameItem
import com.example.pictureviewer.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameScreen(
    initialFolderUri: String? = null,
    initialRootUri: String? = null,
    onBack: () -> Unit,
    viewModel: RenameViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }

    // 如果有初始文件夹 URI，自动加载
    LaunchedEffect(initialFolderUri) {
        if (initialFolderUri != null) {
            val rootUri = initialRootUri?.let { Uri.parse(it) }
            viewModel.onFolderSelected(Uri.parse(initialFolderUri), rootUri)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    uiState.errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("错误", color = c.textPrimary) },
            text = { Text(msg, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("确定", color = c.accent)
                }
            },
            containerColor = c.glassSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    uiState.resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearResult() },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF43E97B)) },
            title = { Text("完成", color = c.textPrimary) },
            text = { Text(msg, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearResult() }) {
                    Text("确定", color = c.accent)
                }
            },
            containerColor = c.glassSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (uiState.showRenameConfirm) {
        val validSelected = uiState.files.count {
            it.isSelected && it.category == FileCategory.VALID && !it.isRenamed
        }
        AlertDialog(
            onDismissRequest = { viewModel.onRenameConfirmDismissed() },
            title = { Text("确认重命名", color = c.textPrimary) },
            text = { Text("将重命名 $validSelected 个文件，是否继续？", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.renameAll() }) {
                    Text("确定", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onRenameConfirmDismissed() }) {
                    Text("取消", color = c.textSecondary)
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
                    Text(
                        text = "文件重命名",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = c.textPrimary
                        )
                    }
                },
                actions = {
                    if (uiState.selectedFolderUri != null) {
                        IconButton(onClick = { viewModel.refreshFiles() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "刷新",
                                tint = c.textPrimary
                            )
                        }
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
            if (uiState.selectedFolderUri == null) {
                EmptyFolderView { folderPickerLauncher.launch(null) }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            FolderInfoCard(
                                folderName = uiState.folderDisplayName,
                                fileCount = uiState.files.size,
                                includeSubfolders = uiState.includeSubfolders,
                                onSubfolderToggle = { viewModel.onIncludeSubfoldersChanged(it) },
                                onChangeFolder = { folderPickerLauncher.launch(null) }
                            )
                        }

                        item {
                            RenameRulesCard(
                                paddingWidth = uiState.paddingWidth,
                                prefix = uiState.prefix,
                                suffix = uiState.suffix,
                                findText = uiState.findText,
                                replaceText = uiState.replaceText,
                                onPaddingWidthChanged = { viewModel.onPaddingWidthChanged(it) },
                                onPrefixChanged = { viewModel.onPrefixChanged(it) },
                                onSuffixChanged = { viewModel.onSuffixChanged(it) },
                                onFindTextChanged = { viewModel.onFindTextChanged(it) },
                                onReplaceTextChanged = { viewModel.onReplaceTextChanged(it) }
                            )
                        }

                        item {
                            StatsCard(
                                validCount = uiState.validCount,
                                conflictCount = uiState.conflictCount,
                                unchangedCount = uiState.unchangedCount,
                                invalidCount = uiState.invalidCount,
                                selectedCategory = selectedCategory,
                                onCategoryClick = { category ->
                                    selectedCategory = if (selectedCategory == category) null else category
                                }
                            )
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "文件列表",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = c.textPrimary
                                )
                                Row {
                                    TextButton(onClick = { viewModel.onSelectAll() }) {
                                        Text("全选", color = c.accent)
                                    }
                                    TextButton(onClick = { viewModel.onDeselectAll() }) {
                                        Text("取消全选", color = c.textSecondary)
                                    }
                                }
                            }
                        }

                        items(
                            if (selectedCategory != null) {
                                uiState.files.filter { it.category == selectedCategory }
                            } else {
                                uiState.files
                            },
                            key = { it.documentUri }
                        ) { item ->
                            FileRenameItemCard(
                                item = item,
                                onToggle = { viewModel.onFileSelectionToggled(item.documentUri) }
                            )
                        }
                    }

                    BottomActionBar(
                        isRenaming = uiState.isRenaming,
                        progress = uiState.renameProgress,
                        completedCount = uiState.renameCompletedCount,
                        totalCount = uiState.renameTotalCount,
                        canUndo = uiState.canUndo,
                        onRename = { viewModel.onRenameConfirmRequested() },
                        onUndo = { viewModel.undoLastRename() }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = c.accent)
                        Text(
                            text = "加载中...",
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFolderView(onSelectFolder: () -> Unit) {
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
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = c.accent
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "选择文件夹",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "选择包含需要重命名文件的文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onSelectFolder,
                colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("选择文件夹")
            }
        }
    }
}

@Composable
private fun FolderInfoCard(
    folderName: String,
    fileCount: Int,
    includeSubfolders: Boolean,
    onSubfolderToggle: (Boolean) -> Unit,
    onChangeFolder: () -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.glassSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.glassBorder, shape)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$fileCount 个文件",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
                TextButton(onClick = onChangeFolder) {
                    Text("更换", color = c.accent)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "包含子文件夹",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary
                )
                Switch(
                    checked = includeSubfolders,
                    onCheckedChange = onSubfolderToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.accent,
                        checkedTrackColor = c.accent.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun RenameRulesCard(
    paddingWidth: Int,
    prefix: String,
    suffix: String,
    findText: String,
    replaceText: String,
    onPaddingWidthChanged: (Int) -> Unit,
    onPrefixChanged: (String) -> Unit,
    onSuffixChanged: (String) -> Unit,
    onFindTextChanged: (String) -> Unit,
    onReplaceTextChanged: (String) -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.glassSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.glassBorder, shape)
                .padding(16.dp)
        ) {
            Text(
                text = "重命名规则",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "数字填充宽度",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3, 4, 5).forEach { width ->
                    val isSelected = paddingWidth == width
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) c.accent else c.glassSurface.copy(alpha = 0.5f))
                            .clickable { onPaddingWidthChanged(width) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = width.toString(),
                            color = if (isSelected) Color.White else c.textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = onPrefixChanged,
                    label = { Text("前缀", color = c.textSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = onSuffixChanged,
                    label = { Text("后缀", color = c.textSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = findText,
                    onValueChange = onFindTextChanged,
                    label = { Text("查找", color = c.textSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = onReplaceTextChanged,
                    label = { Text("替换", color = c.textSecondary) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.glassBorder,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        cursorColor = c.accent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    validCount: Int,
    conflictCount: Int,
    unchangedCount: Int,
    invalidCount: Int,
    selectedCategory: FileCategory?,
    onCategoryClick: (FileCategory) -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.glassSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.glassBorder, shape)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("有效", validCount, Color(0xFF43E97B), selectedCategory == FileCategory.VALID) {
                onCategoryClick(FileCategory.VALID)
            }
            StatItem("冲突", conflictCount, Color(0xFFFF9A44), selectedCategory == FileCategory.CONFLICT) {
                onCategoryClick(FileCategory.CONFLICT)
            }
            StatItem("未变", unchangedCount, c.textSecondary, selectedCategory == FileCategory.UNCHANGED) {
                onCategoryClick(FileCategory.UNCHANGED)
            }
            StatItem("无效", invalidCount, Color(0xFFFF4D4D), selectedCategory == FileCategory.INVALID) {
                onCategoryClick(FileCategory.INVALID)
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    count: Int,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) color else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = color.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun FileRenameItemCard(
    item: FileRenameItem,
    onToggle: () -> Unit
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)

    val statusColor = when (item.category) {
        FileCategory.VALID -> Color(0xFF43E97B)
        FileCategory.CONFLICT -> Color(0xFFFF9A44)
        FileCategory.INVALID -> Color(0xFFFF4D4D)
        FileCategory.UNCHANGED -> c.textSecondary
    }

    val statusIcon = when {
        item.hasError -> Icons.Rounded.Error
        item.isRenamed -> Icons.Rounded.CheckCircle
        item.category == FileCategory.VALID -> Icons.Rounded.CheckCircle
        else -> Icons.Rounded.Close
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = c.neuShadow, spotColor = c.neuHighlight),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.glassSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.glassBorder, shape)
                .clickable { onToggle() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = c.accent,
                    uncheckedColor = c.textSecondary
                )
            )

            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Column {
                    Text(
                        text = item.originalName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.originalName != item.renamedName) {
                        Text(
                            text = "→ ${item.renamedName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = TextDecoration.None
                        )
                    }
                    if (item.category == FileCategory.CONFLICT && item.conflictWith.isNotEmpty()) {
                        Text(
                            text = "冲突: ${item.conflictWith.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF9A44),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.relativePath.isNotEmpty()) {
                        Text(
                            text = item.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = if (item.hasError) Color(0xFFFF4D4D) else statusColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun BottomActionBar(
    isRenaming: Boolean,
    progress: Float,
    completedCount: Int,
    totalCount: Int,
    canUndo: Boolean,
    onRename: () -> Unit,
    onUndo: () -> Unit
) {
    val c = AppTheme.colors
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.glassSurface.copy(alpha = 0.95f))
            .border(1.dp, c.glassBorder)
            .padding(16.dp)
    ) {
        if (isRenaming) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = c.accent,
                trackColor = c.neuShadow.copy(alpha = 0.3f)
            )
            Text(
                text = "正在重命名... $completedCount/$totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (canUndo) {
                    Button(
                        onClick = onUndo,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = c.glassSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("撤销", color = c.textPrimary)
                    }
                }

                Button(
                    onClick = onRename,
                    modifier = Modifier.weight(if (canUndo) 2f else 1f),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isRenaming
                ) {
                    Text("重命名", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
