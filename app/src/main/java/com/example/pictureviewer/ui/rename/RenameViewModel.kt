package com.example.pictureviewer.ui.rename

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pictureviewer.data.DataStore
import com.example.pictureviewer.data.model.FileCategory
import com.example.pictureviewer.data.model.FileRenameItem
import com.example.pictureviewer.data.model.RenameRecord
import com.example.pictureviewer.util.FolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class RenameUiState(
    val selectedFolderUri: Uri? = null,
    val rootUri: Uri? = null,
    val folderDisplayName: String = "",
    val files: List<FileRenameItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRenaming: Boolean = false,
    val renameProgress: Float = 0f,
    val renameCompletedCount: Int = 0,
    val renameTotalCount: Int = 0,
    val errorMessage: String? = null,
    val resultMessage: String? = null,
    val paddingWidth: Int = 3,
    val prefix: String = "",
    val suffix: String = "",
    val findText: String = "",
    val replaceText: String = "",
    val showRenameConfirm: Boolean = false,
    val includeSubfolders: Boolean = true,
    val undoHistory: List<RenameRecord> = emptyList(),
    val canUndo: Boolean = false
) {
    val validCount: Int get() = files.count { it.category == FileCategory.VALID }
    val conflictCount: Int get() = files.count { it.category == FileCategory.CONFLICT }
    val invalidCount: Int get() = files.count { it.category == FileCategory.INVALID }
    val unchangedCount: Int get() = files.count { it.category == FileCategory.UNCHANGED }
}

class RenameViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RenameUiState())
    val uiState: StateFlow<RenameUiState> = _uiState.asStateFlow()

    companion object {
        private val ANY_DIGITS_REGEX = "\\d+".toRegex()
    }

    fun onFolderSelected(uri: Uri, rootUri: Uri? = null) {
        // 对 rootUri 进行转换，确保是标准格式
        val normalizedRootUri = rootUri?.let { docUriToTreeUri(it) }
        android.util.Log.d("RenameViewModel", "onFolderSelected: uri=$uri, rootUri=$rootUri, normalizedRootUri=$normalizedRootUri")
        _uiState.value = _uiState.value.copy(
            selectedFolderUri = uri,
            rootUri = normalizedRootUri,
            isLoading = true,
            errorMessage = null
        )
        viewModelScope.launch {
            try {
                val displayName = getFolderDisplayName(uri, normalizedRootUri)
                val files = listFiles(uri, _uiState.value.includeSubfolders, normalizedRootUri)
                val categorized = categorizeFiles(files)
                _uiState.value = _uiState.value.copy(
                    folderDisplayName = displayName,
                    files = categorized,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "读取文件夹失败: ${e.message}"
                )
            }
        }
    }

    fun onIncludeSubfoldersChanged(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeSubfolders = include)
        val uri = _uiState.value.selectedFolderUri ?: return
        val rootUri = _uiState.value.rootUri
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val files = listFiles(uri, include, rootUri)
                val categorized = categorizeFiles(files)
                _uiState.value = _uiState.value.copy(
                    files = categorized,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "读取文件夹失败: ${e.message}"
                )
            }
        }
    }

    fun onPrefixChanged(prefix: String) {
        _uiState.value = _uiState.value.copy(prefix = prefix)
        recalcAllNames()
    }

    fun onSuffixChanged(suffix: String) {
        _uiState.value = _uiState.value.copy(suffix = suffix)
        recalcAllNames()
    }

    fun onFindTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(findText = text)
        recalcAllNames()
    }

    fun onReplaceTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(replaceText = text)
        recalcAllNames()
    }

    private fun recalcAllNames() {
        val state = _uiState.value
        val renamed = state.files.map { item ->
            val newRenamedName = computeRenamedName(item.originalName, state.paddingWidth, state.prefix, state.suffix, state.findText, state.replaceText)
            item.copy(renamedName = newRenamedName, isRenamed = false, hasError = false).also { it.documentFile = item.documentFile }
        }
        val categorized = categorizeFiles(renamed)
        _uiState.value = state.copy(files = categorized)
    }

    fun onPaddingWidthChanged(width: Int) {
        if (width == _uiState.value.paddingWidth) return
        _uiState.value = _uiState.value.copy(paddingWidth = width)
        recalcAllNames()
    }

    fun onFileSelectionToggled(uri: Uri) {
        val updatedFiles = _uiState.value.files.map { item ->
            if (item.documentUri == uri) {
                val newSelected = !item.isSelected
                item.copy(isSelected = newSelected, hasError = if (newSelected) false else item.hasError)
            } else item
        }
        _uiState.value = _uiState.value.copy(files = updatedFiles)
    }

    fun onSelectAll() {
        val updatedFiles = _uiState.value.files.map { item ->
            item.copy(isSelected = true, hasError = false)
        }
        val recategorized = categorizeFiles(updatedFiles)
        _uiState.value = _uiState.value.copy(files = recategorized)
    }

    fun onDeselectAll() {
        val updatedFiles = _uiState.value.files.map { item ->
            item.copy(isSelected = false)
        }
        _uiState.value = _uiState.value.copy(files = updatedFiles)
    }

    fun onRenameConfirmRequested() {
        val state = _uiState.value
        val validSelected = state.files.count {
            it.isSelected && it.category == FileCategory.VALID && !it.isRenamed
        }
        if (validSelected == 0) {
            _uiState.value = state.copy(
                errorMessage = if (state.files.isEmpty()) "未加载文件" else "没有需要重命名的文件"
            )
            return
        }
        _uiState.value = state.copy(showRenameConfirm = true)
    }

    fun onRenameConfirmDismissed() {
        _uiState.value = _uiState.value.copy(showRenameConfirm = false)
    }

    fun renameAll() {
        _uiState.value = _uiState.value.copy(showRenameConfirm = false)
        val currentState = _uiState.value
        val resetErrorFiles = currentState.files.map { item ->
            if (item.isSelected && item.category == FileCategory.VALID && item.hasError)
                item.copy(hasError = false).also { it.documentFile = item.documentFile }
            else item
        }
        val stateForRename = currentState.copy(files = resetErrorFiles)
        val filesToRename = resetErrorFiles.filter {
            it.isSelected && it.category == FileCategory.VALID && !it.isRenamed && !it.hasError
        }
        if (filesToRename.isEmpty()) return

        _uiState.value = currentState.copy(
            isRenaming = true,
            renameProgress = 0f,
            renameCompletedCount = 0,
            renameTotalCount = filesToRename.size,
            errorMessage = null
        )

        viewModelScope.launch {
            val records = mutableListOf<RenameRecord>()
            var successCount = 0
            var failCount = 0
            val total = filesToRename.size
            val updatedFiles = stateForRename.files.toMutableList()
            val indexMap = updatedFiles.indices.associateBy { updatedFiles[it].documentUri }
            val semaphore = Semaphore(15)
            val channel = Channel<Int>(Channel.UNLIMITED)

            val consumer = launch {
                var completed = 0
                for (msg in channel) {
                    completed++
                    _uiState.value = _uiState.value.copy(
                        renameProgress = completed.toFloat() / total,
                        renameCompletedCount = completed
                    )
                }
            }

            filesToRename.map { item ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        var newUri: Uri? = null
                        val success = try {
                            if (item.documentFile != null) {
                                item.documentFile!!.renameTo(item.renamedName)
                            } else {
                                newUri = DocumentsContract.renameDocument(
                                    getApplication<Application>().contentResolver,
                                    item.documentUri,
                                    item.renamedName
                                )
                                newUri != null
                            }
                        } catch (e: Exception) {
                            false
                        }

                        synchronized(updatedFiles) {
                            val idx = indexMap[item.documentUri] ?: -1
                            if (success) {
                                if (idx >= 0) {
                                    val newOriginal = item.renamedName
                                    val newRenamed = computeRenamedName(newOriginal, currentState.paddingWidth, currentState.prefix, currentState.suffix, currentState.findText, currentState.replaceText)
                                    updatedFiles[idx] = item.copy(
                                        documentUri = newUri ?: item.documentUri,
                                        originalName = newOriginal,
                                        renamedName = newRenamed,
                                        isRenamed = true
                                    ).also { it.documentFile = item.documentFile }
                                }
                                records.add(RenameRecord(item.documentUri, newUri ?: item.documentUri, item.originalName, item.renamedName))
                                successCount++
                            } else {
                                if (idx >= 0) {
                                    updatedFiles[idx] = item.copy(hasError = true).also { it.documentFile = item.documentFile }
                                }
                                failCount++
                            }
                        }

                        channel.send(1)
                    }
                }
            }.awaitAll()

            channel.close()
            consumer.join()

            val skipCount = currentState.files.count {
                it.isSelected && it.category != FileCategory.VALID
            }
            val finalFiles = categorizeFiles(updatedFiles)
            _uiState.value = _uiState.value.copy(
                isRenaming = false,
                renameProgress = 1f,
                files = finalFiles,
                undoHistory = records,
                canUndo = records.isNotEmpty(),
                resultMessage = "成功 $successCount，跳过 $skipCount，失败 $failCount"
            )

            // 同步更新 LibraryEntry 中的 imageUris
            syncLibraryEntryAfterRename()
        }
    }

    fun undoLastRename() {
        val records = _uiState.value.undoHistory
        if (records.isEmpty()) return

        _uiState.value = _uiState.value.copy(isRenaming = true, renameProgress = 0f, renameCompletedCount = 0, renameTotalCount = records.size)

        viewModelScope.launch {
            var successCount = 0
            val total = records.size
            val updatedFiles = _uiState.value.files.toMutableList()
            val indexMap = updatedFiles.indices.associateBy { updatedFiles[it].documentUri }

            records.forEachIndexed { index, record ->
                val success = withContext(Dispatchers.IO) {
                    try {
                        DocumentsContract.renameDocument(
                            getApplication<Application>().contentResolver,
                            record.newUri,
                            record.originalName
                        ) != null
                    } catch (_: Exception) { false }
                }

                if (success) {
                    val idx = indexMap[record.newUri] ?: -1
                    if (idx >= 0) {
                        updatedFiles[idx] = updatedFiles[idx].copy(
                            documentUri = record.oldUri,
                            renamedName = record.originalName,
                            isRenamed = false,
                            hasError = false
                        )
                    }
                    successCount++
                }

                val completed = index + 1
                _uiState.value = _uiState.value.copy(
                    renameProgress = completed.toFloat() / total,
                    renameCompletedCount = completed
                )
            }

            val uri = _uiState.value.selectedFolderUri
            if (uri != null) {
                val rootUri = _uiState.value.rootUri
                val files = listFiles(uri, _uiState.value.includeSubfolders, rootUri)
                val categorized = categorizeFiles(files)
                _uiState.value = _uiState.value.copy(
                    files = categorized,
                    isRenaming = false,
                    renameProgress = 0f,
                    undoHistory = emptyList(),
                    canUndo = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isRenaming = false,
                    renameProgress = 0f,
                    undoHistory = emptyList(),
                    canUndo = false
                )
            }

            // 同步更新 LibraryEntry 中的 imageUris
            syncLibraryEntryAfterRename()
        }
    }

    fun refreshFiles() {
        val uri = _uiState.value.selectedFolderUri ?: return
        val rootUri = _uiState.value.rootUri
        onFolderSelected(uri, rootUri)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(resultMessage = null)
    }

    private suspend fun getFolderDisplayName(uri: Uri, rootUri: Uri? = null): String {
        return withContext(Dispatchers.IO) {
            try {
                var docFile = DocumentFile.fromTreeUri(getApplication(), uri)
                // 如果失败，使用 rootUri 和相对路径导航
                if (docFile == null && rootUri != null) {
                    val relativePath = extractRelativePath(uri, rootUri)
                    if (relativePath != null) {
                        docFile = navigateToFolder(getApplication(), rootUri, relativePath)
                    }
                }
                docFile?.name ?: "未知文件夹"
            } catch (e: Exception) {
                "未知文件夹"
            }
        }
    }

    private suspend fun listFiles(folderUri: Uri, includeSubfolders: Boolean, rootUri: Uri? = null): List<FileRenameItem> {
        return withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val files = mutableListOf<FileRenameItem>()

            // 尝试用 DocumentFile.fromTreeUri 打开
            var doc = DocumentFile.fromTreeUri(context, folderUri)

            // 如果失败，使用 rootUri 和相对路径导航
            if (doc == null || !doc.isDirectory) {
                android.util.Log.d("RenameViewModel", "listFiles: fromTreeUri failed, trying with rootUri=$rootUri")
                if (rootUri != null) {
                    val relativePath = extractRelativePath(folderUri, rootUri)
                    if (relativePath != null) {
                        android.util.Log.d("RenameViewModel", "listFiles: navigating from rootUri via relativePath: $relativePath")
                        doc = navigateToFolder(context, rootUri, relativePath)
                    }
                }
            }

            if (doc != null && doc.isDirectory) {
                val paddingWidth = _uiState.value.paddingWidth
                val prefix = _uiState.value.prefix
                val suffix = _uiState.value.suffix
                val findText = _uiState.value.findText
                val replaceText = _uiState.value.replaceText

                val children = doc.listFiles()
                for (child in children) {
                    if (child.isDirectory && includeSubfolders) {
                        scanFolderCompat(context, child, child.name ?: "", files, true)
                    } else if (child.isFile) {
                        val name = child.name ?: continue
                        val renamedName = computeRenamedName(name, paddingWidth, prefix, suffix, findText, replaceText)
                        val item = FileRenameItem(
                            documentUri = child.uri,
                            originalName = name,
                            renamedName = renamedName,
                            relativePath = ""
                        )
                        item.documentFile = child
                        files.add(item)
                    }
                }
            } else {
                android.util.Log.e("RenameViewModel", "listFiles: cannot open folder $folderUri")
            }

            files
        }
    }

    private fun extractRelativePath(uri: Uri, rootUri: Uri): String? {
        // 先尝试原始格式比较
        val uriStr = uri.toString()
        val rootStr = rootUri.toString().trimEnd('/')
        android.util.Log.d("RenameViewModel", "extractRelativePath: uriStr=$uriStr")
        android.util.Log.d("RenameViewModel", "extractRelativePath: rootStr=$rootStr")
        if (uriStr.startsWith(rootStr)) {
            val relative = uriStr.removePrefix(rootStr).trimStart('/')
            android.util.Log.d("RenameViewModel", "extractRelativePath: found relative (raw)=$relative")
            return relative.ifEmpty { null }
        }

        // 如果失败，尝试解码后比较
        val decodedUriStr = Uri.decode(uriStr)
        val decodedRootStr = Uri.decode(rootStr)
        android.util.Log.d("RenameViewModel", "extractRelativePath: decodedUriStr=$decodedUriStr")
        android.util.Log.d("RenameViewModel", "extractRelativePath: decodedRootStr=$decodedRootStr")
        if (decodedUriStr.startsWith(decodedRootStr)) {
            val relative = decodedUriStr.removePrefix(decodedRootStr).trimStart('/')
            android.util.Log.d("RenameViewModel", "extractRelativePath: found relative (decoded)=$relative")
            // 重新编码相对路径中的特殊字符
            val encoded = Uri.encode(relative, "/")
            android.util.Log.d("RenameViewModel", "extractRelativePath: encoded=$encoded")
            return encoded.ifEmpty { null }
        }

        android.util.Log.e("RenameViewModel", "extractRelativePath: no match found")
        return null
    }

    private fun navigateToFolder(context: Application, rootUri: Uri, relativePath: String): DocumentFile? {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        // 解码相对路径，因为 DocumentFile.name 返回的是解码后的名称
        val decodedPath = Uri.decode(relativePath)
        val pathParts = decodedPath.split("/")
        var current = rootDoc

        for (part in pathParts) {
            val children = current.listFiles()
            val next = children.find { it.isDirectory && it.name == part }
            if (next == null) {
                android.util.Log.e("RenameViewModel", "navigateToFolder: could not find folder '$part'")
                return null
            }
            current = next
        }

        return current
    }

    private fun scanFolderFast(
        context: Application,
        treeUri: Uri,
        directoryUri: Uri,
        relativePath: String,
        result: MutableList<FileRenameItem>,
        includeSubfolders: Boolean
    ): Boolean {
        try {
            val paddingWidth = _uiState.value.paddingWidth
            val prefix = _uiState.value.prefix
            val suffix = _uiState.value.suffix
            val findText = _uiState.value.findText
            val replaceText = _uiState.value.replaceText
            val resolver = context.contentResolver
            val treePath = treeUri.pathSegments
            val treeDocId = if (treePath.size >= 2 && treePath[0] == "tree") treePath[1] else null
                ?: return false
            val docId = if (directoryUri == treeUri) treeDocId
                else DocumentsContract.getDocumentId(directoryUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

            val subdirs = mutableListOf<Pair<String, Uri>>()
            resolver.query(childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: ""
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (includeSubfolders) {
                            val subPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                            subdirs.add(subPath to childUri)
                        }
                    } else {
                        val renamedName = computeRenamedName(name, paddingWidth, prefix, suffix, findText, replaceText)
                        val item = FileRenameItem(
                            documentUri = childUri,
                            originalName = name,
                            renamedName = renamedName,
                            relativePath = relativePath,
                            parentUri = directoryUri
                        )
                        result.add(item)
                    }
                }
            }
            for ((subPath, subUri) in subdirs) {
                if (!scanFolderFast(context, treeUri, subUri, subPath, result, true)) return false
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun scanFolderCompat(
        context: Application,
        directory: DocumentFile,
        relativePath: String,
        result: MutableList<FileRenameItem>,
        includeSubfolders: Boolean
    ) {
        val paddingWidth = _uiState.value.paddingWidth
        val prefix = _uiState.value.prefix
        val suffix = _uiState.value.suffix
        val findText = _uiState.value.findText
        val replaceText = _uiState.value.replaceText
        for (child in directory.listFiles()) {
            if (child.isDirectory) {
                if (includeSubfolders) {
                    val subPath = if (relativePath.isEmpty()) child.name ?: continue else "$relativePath/${child.name}"
                    scanFolderCompat(context, child, subPath, result, true)
                }
            } else {
                val name = child.name ?: continue
                val renamedName = computeRenamedName(name, paddingWidth, prefix, suffix, findText, replaceText)
                val item = FileRenameItem(
                    documentUri = child.uri,
                    originalName = name,
                    renamedName = renamedName,
                    relativePath = relativePath,
                    parentUri = directory.uri
                )
                item.documentFile = child
                result.add(item)
            }
        }
    }

    private fun categorizeFiles(files: List<FileRenameItem>): List<FileRenameItem> {
        val nameCountMap = mutableMapOf<String, Int>()
        val nameToFiles = mutableMapOf<String, MutableList<String>>()
        
        files.forEach { f ->
            // 用完整文件名（含扩展名）判断冲突
            nameCountMap[f.renamedName] = (nameCountMap[f.renamedName] ?: 0) + 1
            nameToFiles.getOrPut(f.renamedName) { mutableListOf() }.add(f.originalName)
        }
        
        return files.map { item ->
            val hasDigits = hasExtractableDigits(item.originalName)
            val category = when {
                !hasDigits -> FileCategory.INVALID
                item.originalName == item.renamedName -> FileCategory.UNCHANGED
                (nameCountMap[item.renamedName] ?: 0) > 1 -> FileCategory.CONFLICT
                else -> FileCategory.VALID
            }
            val conflictWith = if (category == FileCategory.CONFLICT) {
                (nameToFiles[item.renamedName] ?: emptyList()).filter { it != item.originalName }
            } else {
                emptyList()
            }
            item.copy(category = category, conflictWith = conflictWith).also { it.documentFile = item.documentFile }
        }
    }

    private fun extractBaseName(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
    }

    private fun hasExtractableDigits(fileName: String): Boolean {
        val lastDot = fileName.lastIndexOf('.')
        val baseName = if (lastDot > 0) fileName.substring(0, lastDot) else fileName
        return ANY_DIGITS_REGEX.containsMatchIn(baseName)
    }

    /**
     * 改名后同步更新 DataStore 中 LibraryEntry 的 imageUris
     * 通过重新扫描文件夹获取最新的文件 URI
     */
    private suspend fun syncLibraryEntryAfterRename() {
        val folderUri = _uiState.value.selectedFolderUri ?: return
        val rootUri = _uiState.value.rootUri
        android.util.Log.d("RenameViewModel", "syncLibraryEntry: folderUri=$folderUri, rootUri=$rootUri")
        try {
            val dataStore = DataStore.getInstance(getApplication())
            val allEntries = dataStore.getAllEntries().toMutableList()
            android.util.Log.d("RenameViewModel", "syncLibraryEntry: searching for entry with uri=${folderUri.toString()}")
            val index = allEntries.indexOfFirst { it.uri == folderUri.toString() }
            if (index < 0) {
                android.util.Log.e("RenameViewModel", "syncLibraryEntry: entry not found")
                return
            }

            val entry = allEntries[index]
            android.util.Log.d("RenameViewModel", "syncLibraryEntry: found entry ${entry.name}, current imageUris=${entry.imageUris.size}")
            val freshImages = FolderScanner.getImageFiles(getApplication(), folderUri, rootUri)
            android.util.Log.d("RenameViewModel", "syncLibraryEntry: freshImages=${freshImages.size}")
            if (freshImages.isNotEmpty()) {
                val freshUriStrings = freshImages.map { it.toString() }
                android.util.Log.d("RenameViewModel", "syncLibraryEntry: freshUriStrings=${freshUriStrings.size}, entry.imageUris=${entry.imageUris.size}")
                if (freshUriStrings != entry.imageUris) {
                    allEntries[index] = entry.copy(
                        imageCount = freshImages.size,
                        imageUris = freshUriStrings
                    )
                    dataStore.saveEntries(allEntries)
                    android.util.Log.d("RenameViewModel", "syncLibraryEntry: updated ${freshImages.size} image URIs")
                } else {
                    android.util.Log.d("RenameViewModel", "syncLibraryEntry: imageUris unchanged")
                }
            } else {
                android.util.Log.e("RenameViewModel", "syncLibraryEntry: freshImages is empty")
            }
        } catch (e: Exception) {
            android.util.Log.e("RenameViewModel", "syncLibraryEntry error: ${e.message}")
        }
    }

    /**
     * 将 document URI 转换为 tree URI 格式
     * document: content://authority/document/primary:DCIM/comic1
     * tree:     content://authority/tree/primary%3ADCIM%2Fcomic1
     */
    private fun docUriToTreeUri(uri: Uri): Uri {
        val s = uri.toString()

        // 如果已经是 tree URI 格式（不包含 /document/），直接返回
        if (s.contains("/tree/") && !s.contains("/document/")) {
            return uri
        }

        // 提取最后一个 /document/ 后面的 document ID（处理混合格式）
        val marker = "/document/"
        val idx = s.lastIndexOf(marker)  // 使用 lastIndexOf 处理混合格式
        if (idx < 0) return uri

        val docPath = s.substring(idx + marker.length)
        val authority = uri.authority ?: return uri

        // URL 编码路径中的特殊字符
        val encodedPath = docPath
            .replace(":", "%3A")
            .replace("/", "%2F")
            .replace(" ", "%20")
            .replace("[", "%5B")
            .replace("]", "%5D")
            .replace("(", "%28")
            .replace(")", "%29")

        return Uri.parse("content://$authority/tree/$encodedPath")
    }

    private fun computeRenamedName(
        fileName: String,
        paddingWidth: Int,
        prefix: String = "",
        suffix: String = "",
        findText: String = "",
        replaceText: String = ""
    ): String {
        val lastDot = fileName.lastIndexOf('.')
        val baseName: String
        val ext: String
        if (lastDot > 0) {
            baseName = fileName.substring(0, lastDot)
            ext = fileName.substring(lastDot)
        } else {
            baseName = fileName
            ext = ""
        }

        val replacedBaseName = if (findText.isNotEmpty()) {
            baseName.replace(findText, replaceText)
        } else {
            baseName
        }

        val match = ANY_DIGITS_REGEX.findAll(replacedBaseName).lastOrNull() ?: return if (findText.isNotEmpty()) replacedBaseName + ext else fileName
        val digits = match.value
        val number = digits.toIntOrNull() ?: return if (findText.isNotEmpty()) replacedBaseName + ext else fileName
        val padded = number.toString().padStart(paddingWidth, '0')
        val newBaseName = replacedBaseName.replaceRange(match.range, prefix + padded + suffix)
        return newBaseName + ext
    }
}
