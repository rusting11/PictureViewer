package com.example.pictureviewer.ui.reader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pictureviewer.data.DataStore
import com.example.pictureviewer.data.ReadingProgress
import com.example.pictureviewer.util.FolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReadMode {
    VERTICAL, HORIZONTAL, GRID
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = DataStore.getInstance(application)

    private val _images = MutableStateFlow<List<Uri>>(emptyList())
    val images: StateFlow<List<Uri>> = _images.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _scrollOffset = MutableStateFlow(0)
    val scrollOffset: StateFlow<Int> = _scrollOffset.asStateFlow()

    private val _readMode = MutableStateFlow(ReadMode.VERTICAL)
    val readMode: StateFlow<ReadMode> = _readMode.asStateFlow()

    private val _targetPage = MutableStateFlow<Int?>(null)
    val targetPage: StateFlow<Int?> = _targetPage.asStateFlow()

    private var comicUri: String? = null

    fun loadComic(uri: String) {
        comicUri = uri
        Log.d("ReaderViewModel", "loadComic: uri=$uri")
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) {
                dataStore.getEntry(uri)
            }
            Log.d("ReaderViewModel", "entry=${entry?.name}, imageUris=${entry?.imageUris?.size}")
            
            if (entry != null && entry.imageUris.isNotEmpty()) {
                // 立即使用缓存的 imageUris 显示图片
                _images.value = entry.imageUris.map { Uri.parse(it) }
                Log.d("ReaderViewModel", "loaded ${_images.value.size} images from cache")
                
                // 后台异步更新 imageUris
                launch(Dispatchers.IO) {
                    val rootUri = entry.rootUri?.let { Uri.parse(it) }
                    val folderUri = Uri.parse(entry.uri)
                    val freshImages = FolderScanner.getImageFiles(getApplication(), folderUri, rootUri)
                    
                    if (freshImages.isNotEmpty() && freshImages.map { it.toString() } != entry.imageUris) {
                        // 图片数量或 URI 变化（如改名后），更新缓存
                        val updatedEntry = entry.copy(
                            imageCount = freshImages.size,
                            imageUris = freshImages.map { it.toString() },
                            coverImagePath = freshImages.firstOrNull()?.toString()
                        )
                        val allEntries = dataStore.getAllEntries().toMutableList()
                        val index = allEntries.indexOfFirst { it.uri == entry.uri }
                        if (index >= 0) {
                            allEntries[index] = updatedEntry
                            dataStore.saveEntries(allEntries)
                        }
                        // 更新 UI
                        withContext(Dispatchers.Main) {
                            _images.value = freshImages
                            Log.d("ReaderViewModel", "updated ${freshImages.size} images from folder (URIs changed)")
                        }
                    }
                }
            } else {
                // 没有缓存，重新扫描
                val rootUri = entry?.rootUri?.let { Uri.parse(it) }
                val folderUri = entry?.uri?.let { Uri.parse(it) }
                
                if (folderUri != null) {
                    val images = withContext(Dispatchers.IO) {
                        FolderScanner.getImageFiles(getApplication(), folderUri, rootUri)
                    }
                    if (images.isNotEmpty()) {
                        _images.value = images
                        Log.d("ReaderViewModel", "loaded ${images.size} images from folder")
                    }
                }
            }

            val progress = withContext(Dispatchers.IO) {
                dataStore.getProgress(uri)
            }
            if (progress != null) {
                _currentIndex.value = progress.lastReadIndex
                _scrollOffset.value = progress.scrollOffset
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        _currentIndex.value = index
        saveProgress()
    }

    fun goToPage(index: Int) {
        val target = index.coerceIn(0, (_images.value.size - 1).coerceAtLeast(0))
        _targetPage.value = target
        _currentIndex.value = target
        saveProgress()
    }

    fun onTargetPageConsumed() {
        _targetPage.value = null
    }

    fun setScrollOffset(offset: Int) {
        _scrollOffset.value = offset
    }

    fun setReadMode(mode: ReadMode) {
        _readMode.value = mode
    }

    fun toggleReadMode() {
        _readMode.value = when (_readMode.value) {
            ReadMode.VERTICAL -> ReadMode.HORIZONTAL
            ReadMode.HORIZONTAL -> ReadMode.GRID
            ReadMode.GRID -> ReadMode.VERTICAL
        }
    }

    fun saveProgress() {
        val uri = comicUri ?: return
        viewModelScope.launch {
            val progress = ReadingProgress(
                comicUri = uri,
                lastReadIndex = _currentIndex.value,
                scrollOffset = _scrollOffset.value,
                lastReadTime = System.currentTimeMillis()
            )
            withContext(Dispatchers.IO) {
                dataStore.saveProgress(progress)
            }
        }
    }
}
