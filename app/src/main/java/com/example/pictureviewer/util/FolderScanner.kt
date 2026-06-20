package com.example.pictureviewer.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.example.pictureviewer.data.LibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

object FolderScanner {

    private const val TAG = "FolderScanner"
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in imageExtensions
    }

    fun normalizeUri(uri: Uri): Uri {
        return docUriToTreeUri(uri)
    }

    /**
     * 流式扫描：每扫描到一个文件夹就回调
     */
    suspend fun scanFolderStreaming(
        context: Context,
        rootUri: Uri,
        onEntryFound: suspend (LibraryEntry) -> Unit
    ) {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return
        val stats = java.util.concurrent.atomic.AtomicInteger(0)
        // 对 rootUri 进行转换，确保存储的是标准格式
        val normalizedRootUri = docUriToTreeUri(rootUri)
        scanDocStreaming(context, rootDoc, normalizedRootUri.toString(), null, onEntryFound, stats)
        Log.d(TAG, "scanFolderStreaming complete: scanned ${stats.get()} comic folders")
    }

    private suspend fun scanDocStreaming(
        context: Context,
        doc: DocumentFile,
        rootTreeUri: String,
        parentUri: String?,
        onEntryFound: suspend (LibraryEntry) -> Unit,
        stats: java.util.concurrent.atomic.AtomicInteger
    ) {
        val children = withContext(Dispatchers.IO) { doc.listFiles() }
        val subFolders = mutableListOf<DocumentFile>()
        val imageFiles = mutableListOf<Pair<String, String>>() // (文件名, URI)
        var coverImage: String? = null

        for (child in children) {
            if (child.isDirectory) {
                subFolders.add(child)
            } else if (child.isFile && child.name?.let { isImageFile(it) } == true) {
                if (coverImage == null) coverImage = child.uri.toString()
                imageFiles.add(child.name!! to child.uri.toString())
            }
        }

        // 按文件名排序后提取 URI
        val imageUris = imageFiles.sortedBy { it.first }.map { it.second }

        val storeUri = docUriToTreeUri(doc.uri)
        Log.d(TAG, "scanDocStreaming: ${doc.name}, doc.uri=${doc.uri}, storeUri=$storeUri")
        val isComic = subFolders.isEmpty() && imageUris.isNotEmpty()
        
        // 扫描到既有图片又有子文件夹的文件夹时打印日志
        if (imageUris.isNotEmpty() && subFolders.isNotEmpty()) {
            Log.w(TAG, "[!] ${doc.name}: ${imageUris.size}张图片 + ${subFolders.size}个子文件夹 (不会显示为漫画)")
        }
        
        // 只有图片的文件夹，计数
        if (isComic) {
            stats.incrementAndGet()
        }

        val lastModified = try { doc.lastModified() } catch (e: Exception) { System.currentTimeMillis() }

        val entry = LibraryEntry(
            uri = storeUri.toString(),
            documentUri = doc.uri.toString(),  // 保存原始 document URI
            parentUri = parentUri,
            rootUri = rootTreeUri,
            name = doc.name ?: "Unknown",
            isComic = isComic,
            coverImagePath = coverImage,
            imageCount = imageUris.size,
            imageUris = imageUris,
            lastModified = lastModified
        )

        Log.d(TAG, "scanDocStreaming: ${doc.name}, images=${imageUris.size}, subs=${subFolders.size}")
        onEntryFound(entry)

        // 并行扫描子文件夹
        if (subFolders.isNotEmpty()) {
            coroutineScope {
                subFolders.map { sub ->
                    async {
                        scanDocStreaming(context, sub, rootTreeUri, storeUri.toString(), onEntryFound, stats)
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * 流式增量刷新
     */
    suspend fun refreshFolderStreaming(
        context: Context,
        rootUri: Uri,
        existingComicUris: Set<String>,
        onEntryFound: suspend (LibraryEntry) -> Unit
    ) {
        Log.d(TAG, "refreshFolderStreaming: rootUri=$rootUri, existingComicUris=${existingComicUris.size}")
        scanDocQuickStreaming(context, rootUri, rootUri.toString(), null, existingComicUris, onEntryFound)
    }

    private suspend fun scanDocQuickStreaming(
        context: Context,
        treeUri: Uri,
        rootTreeUri: String,
        parentUri: String?,
        existingComicUris: Set<String>,
        onEntryFound: suspend (LibraryEntry) -> Unit
    ) {
        try {
            val doc = withContext(Dispatchers.IO) {
                DocumentFile.fromTreeUri(context, treeUri)
            } ?: return
            val storeUri = docUriToTreeUri(doc.uri)
            val storeUriStr = storeUri.toString()

            Log.d(TAG, "scanDocQuickStreaming: name=${doc.name}, storeUri=$storeUriStr, inExistingComic=${storeUriStr in existingComicUris}, parentUri=$parentUri")
            
            // 漫画已存在则跳过，非漫画（有子文件夹）需要继续扫描
            if (storeUriStr in existingComicUris && parentUri != null) {
                Log.d(TAG, "scanDocQuickSkipping existing comic: ${doc.name}")
                return
            }

            val children = withContext(Dispatchers.IO) { doc.listFiles() }
            val subFolders = mutableListOf<DocumentFile>()
            val imageFiles = mutableListOf<Pair<String, String>>() // (文件名, URI)
            var coverImage: String? = null

            for (child in children) {
                if (child.isDirectory) {
                    subFolders.add(child)
                } else if (child.isFile && child.name?.let { isImageFile(it) } == true) {
                    if (coverImage == null) coverImage = child.uri.toString()
                    imageFiles.add(child.name!! to child.uri.toString())
                }
            }

            // 按文件名排序后提取 URI
            val imageUris = imageFiles.sortedBy { it.first }.map { it.second }

            val isComic = subFolders.isEmpty() && imageUris.isNotEmpty()
            val lastModified = try { doc.lastModified() } catch (e: Exception) { System.currentTimeMillis() }

            Log.d(TAG, "scanDocQuickStreaming: ${doc.name}, images=${imageUris.size}, subs=${subFolders.size}, isComic=$isComic")

            val entry = LibraryEntry(
                uri = storeUriStr,
                documentUri = doc.uri.toString(),  // 保存原始 document URI
                parentUri = parentUri,
                rootUri = rootTreeUri,
                name = doc.name ?: "Unknown",
                isComic = isComic,
                coverImagePath = coverImage,
                imageCount = imageUris.size,
                imageUris = imageUris,
                lastModified = lastModified
            )

            onEntryFound(entry)

            // 并行扫描子文件夹
            if (subFolders.isNotEmpty()) {
                Log.d(TAG, "scanDocQuickStreaming: ${doc.name} has ${subFolders.size} subfolders, scanning...")
                coroutineScope {
                    subFolders.map { sub ->
                        async {
                            scanDocQuickStreaming(context, sub.uri, rootTreeUri, storeUriStr, existingComicUris, onEntryFound)
                        }
                    }.awaitAll()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanDocQuickStreaming error: ${e.message}")
        }
    }

    fun getImageFiles(context: Context, folderUri: Uri, rootUri: Uri? = null): List<Uri> {
        Log.d(TAG, "getImageFiles: folderUri=$folderUri, rootUri=$rootUri")

        tryWithUri(context, folderUri)?.let { return it }

        if (rootUri != null) {
            val relativePath = extractRelativePath(folderUri, rootUri)
            if (relativePath != null) {
                Log.d(TAG, "  navigating from rootUri via relativePath: $relativePath")
                val result = navigateToFolder(context, rootUri, relativePath)
                if (result != null) return result
            }
        }

        Log.e(TAG, "  all attempts failed")
        return emptyList()
    }

    private fun navigateToFolder(context: Context, rootUri: Uri, relativePath: String): List<Uri>? {
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        // 检查是否需要解码：如果包含 % 编码字符，则解码
        val decodedPath = if (relativePath.contains("%")) {
            Uri.decode(relativePath)
        } else {
            relativePath
        }
        val pathParts = decodedPath.split("/")
        var current = rootDoc

        for (part in pathParts) {
            val children = current.listFiles()
            val next = children.find { it.isDirectory && it.name == part }
            if (next == null) {
                Log.e(TAG, "  navigateToFolder: could not find folder '$part'")
                return null
            }
            current = next
        }

        val result = current.listFiles()
            .filter { it.isFile && it.name?.let { n -> isImageFile(n) } == true }
            .sortedBy { it.name }
            .map { it.uri }
        Log.d(TAG, "  navigateToFolder: found ${result.size} images")
        return result.ifEmpty { null }
    }

    private fun extractRelativePath(uri: Uri, rootUri: Uri): String? {
        // 先尝试原始格式比较
        val uriStr = uri.toString()
        val rootStr = rootUri.toString().trimEnd('/')
        if (uriStr.startsWith(rootStr)) {
            val rawRelative = uriStr.removePrefix(rootStr)
            // 解码后再去除开头的 /，然后重新编码
            val decodedRelative = Uri.decode(rawRelative).trimStart('/')
            val relative = Uri.encode(decodedRelative, "/")
            return relative.ifEmpty { null }
        }

        // 如果失败，尝试解码后比较
        val decodedUriStr = Uri.decode(uriStr)
        val decodedRootStr = Uri.decode(rootStr)
        if (decodedUriStr.startsWith(decodedRootStr)) {
            val relative = decodedUriStr.removePrefix(decodedRootStr).trimStart('/')
            // 重新编码相对路径中的特殊字符
            return Uri.encode(relative, "/").ifEmpty { null }
        }

        return null
    }

    private fun tryWithUri(context: Context, uri: Uri): List<Uri>? {
        return try {
            val doc = DocumentFile.fromTreeUri(context, uri) ?: return null
            val result = doc.listFiles()
                .filter { it.isFile && it.name?.let { n -> isImageFile(n) } == true }
                .sortedBy { it.name }
                .map { it.uri }
            Log.d(TAG, "  tryWithUri: ${result.size} images")
            result.ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "  tryWithUri exception: ${e.message}")
            null
        }
    }

    private fun docUriToTreeUri(uri: Uri): Uri {
        val s = uri.toString()
        val authority = uri.authority ?: return uri

        // 提取路径部分（支持 /tree/ 和 /document/ 两种格式）
        val treeMarker = "/tree/"
        val documentMarker = "/document/"
        val treeIdx = s.indexOf(treeMarker)
        val documentIdx = s.lastIndexOf(documentMarker)

        val path: String = when {
            documentIdx >= 0 -> s.substring(documentIdx + documentMarker.length)
            treeIdx >= 0 -> s.substring(treeIdx + treeMarker.length)
            else -> return uri
        }

        // URL 编码路径中的特殊字符
        val encodedPath = path
            .replace(":", "%3A")
            .replace("/", "%2F")
            .replace(" ", "%20")
            .replace("[", "%5B")
            .replace("]", "%5D")
            .replace("(", "%28")
            .replace(")", "%29")

        return Uri.parse("content://$authority/tree/$encodedPath")
    }

    /**
     * 单个漫画深度刷新：重新扫描该文件夹
     */
    fun refreshSingleComic(context: Context, rootUri: Uri, targetUri: String): LibraryEntry? {
        return try {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return null
            findAndScanSingleEntry(context, rootDoc, rootUri.toString(), null, targetUri)
        } catch (e: Exception) {
            Log.e(TAG, "refreshSingleComic error: ${e.message}")
            null
        }
    }

    private fun findAndScanSingleEntry(
        context: Context,
        doc: DocumentFile,
        rootTreeUri: String,
        parentUri: String?,
        targetUri: String
    ): LibraryEntry? {
        val storeUri = docUriToTreeUri(doc.uri)
        val storeUriStr = storeUri.toString()

        if (storeUriStr == targetUri) {
            val children = doc.listFiles()
            val subFolders = children.filter { it.isDirectory }
            val imageFiles = children.filter { it.isFile && it.name?.let { n -> isImageFile(n) } == true }
                .map { it.name!! to it.uri.toString() }

            val isComic = subFolders.isEmpty() && imageFiles.isNotEmpty()
            val coverImage = imageFiles.firstOrNull()?.second
            // 按文件名排序后提取 URI
            val imageUriList = imageFiles.sortedBy { it.first }.map { it.second }

            return LibraryEntry(
                uri = storeUriStr,
                documentUri = doc.uri.toString(),  // 保存原始 document URI
                parentUri = parentUri,
                rootUri = rootTreeUri,
                name = doc.name ?: "Unknown",
                isComic = isComic,
                coverImagePath = coverImage,
                imageCount = imageFiles.size,
                imageUris = imageUriList
            )
        }

        val children = doc.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                val childUri = docUriToTreeUri(child.uri)
                if (childUri.toString() == targetUri) {
                    val subChildren = child.listFiles()
                    val subFolders = subChildren.filter { it.isDirectory }
                    val imageFiles = subChildren.filter { it.isFile && it.name?.let { n -> isImageFile(n) } == true }
                        .map { it.name!! to it.uri.toString() }

                    val isComic = subFolders.isEmpty() && imageFiles.isNotEmpty()
                    val coverImage = imageFiles.firstOrNull()?.second
                    // 按文件名排序后提取 URI
                    val imageUriList = imageFiles.sortedBy { it.first }.map { it.second }

                    return LibraryEntry(
                        uri = childUri.toString(),
                        documentUri = child.uri.toString(),  // 保存原始 document URI
                        parentUri = storeUriStr,
                        rootUri = rootTreeUri,
                        name = child.name ?: "Unknown",
                        isComic = isComic,
                        coverImagePath = coverImage,
                        imageCount = imageFiles.size,
                        imageUris = imageUriList
                    )
                }
            }
        }

        return null
    }
}
