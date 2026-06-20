package com.example.pictureviewer.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pictureviewer.data.DataStore
import com.example.pictureviewer.data.LibraryEntry
import com.example.pictureviewer.util.FolderScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DisplayMode { GRID, LIST }

enum class SortOrder {
    NAME_ASC, NAME_DESC,
    DATE_ASC, DATE_DESC,
    COUNT_ASC, COUNT_DESC
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = DataStore.getInstance(application)

    private val _comics = MutableStateFlow<List<LibraryEntry>>(emptyList())
    val comics: StateFlow<List<LibraryEntry>> = _comics.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanCount = MutableStateFlow(0)
    val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

    private val _displayMode = MutableStateFlow(DisplayMode.GRID)
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private var allComics: List<LibraryEntry> = emptyList()

    init {
        loadComics()
    }

    private fun loadComics() {
        viewModelScope.launch {
            allComics = withContext(Dispatchers.IO) { dataStore.getAllComics() }
            applySorting()
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        applySorting()
    }

    private fun applySorting() {
        val sorted = when (_sortOrder.value) {
            SortOrder.NAME_ASC -> allComics.sortedBy { it.name.lowercase() }
            SortOrder.NAME_DESC -> allComics.sortedByDescending { it.name.lowercase() }
            SortOrder.DATE_ASC -> allComics.sortedBy { it.lastModified }
            SortOrder.DATE_DESC -> allComics.sortedByDescending { it.lastModified }
            SortOrder.COUNT_ASC -> allComics.sortedBy { it.imageCount }
            SortOrder.COUNT_DESC -> allComics.sortedByDescending { it.imageCount }
        }
        _comics.value = sorted
    }

    fun importFolder(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanCount.value = 0
            try {
                val context = getApplication<Application>()
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)

                val batchEntries = java.util.concurrent.CopyOnWriteArrayList<LibraryEntry>()
                
                withContext(Dispatchers.IO) {
                    FolderScanner.scanFolderStreaming(context, uri) { entry ->
                        batchEntries.add(entry)
                        _scanCount.value++
                        
                        // 每20个条目批量保存一次并更新UI
                        if (batchEntries.size >= 20) {
                            val toSave = batchEntries.toList()
                            batchEntries.clear()
                            dataStore.addEntries(toSave)
                            allComics = dataStore.getAllComics()
                            applySorting()
                        }
                    }
                }
                
                // 保存剩余条目
                if (batchEntries.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        dataStore.addEntries(batchEntries.toList())
                    }
                }
                
                loadComics()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun deleteComic(entry: LibraryEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                dataStore.deleteEntry(entry.uri)
            }
            loadComics()
        }
    }

    fun refreshAllComics() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanCount.value = 0
            try {
                val allEntries = withContext(Dispatchers.IO) {
                    dataStore.getAllEntries()
                }
                val existingUris = allEntries.map { it.uri }.toSet()
                val rootUris = allEntries.mapNotNull { it.rootUri }.distinct()

                val allNewEntries = java.util.concurrent.CopyOnWriteArrayList<LibraryEntry>()
                
                for (rootUriStr in rootUris) {
                    val rootUri = Uri.parse(rootUriStr)
                    withContext(Dispatchers.IO) {
                        FolderScanner.refreshFolderStreaming(getApplication(), rootUri, existingUris) { entry ->
                            allNewEntries.add(entry)
                            _scanCount.value++
                        }
                    }
                }
                
                if (allNewEntries.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        dataStore.addEntries(allNewEntries.toList())
                    }
                }
                
                loadComics()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun refreshSingleComic(entry: LibraryEntry) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val rootUri = entry.rootUri?.let { Uri.parse(it) }
                if (rootUri == null) {
                    _isScanning.value = false
                    return@launch
                }

                val refreshed = withContext(Dispatchers.IO) {
                    FolderScanner.refreshSingleComic(getApplication(), rootUri, entry.uri)
                }
                if (refreshed != null) {
                    withContext(Dispatchers.IO) {
                        val allEntries = dataStore.getAllEntries().toMutableList()
                        val index = allEntries.indexOfFirst { it.uri == entry.uri }
                        if (index >= 0) {
                            allEntries[index] = refreshed
                        } else {
                            allEntries.add(refreshed)
                        }
                        dataStore.saveEntries(allEntries)
                    }
                    loadComics()
                }
            } finally {
                _isScanning.value = false
            }
        }
    }
}
