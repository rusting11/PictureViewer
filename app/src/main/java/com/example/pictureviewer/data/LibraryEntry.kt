package com.example.pictureviewer.data

data class LibraryEntry(
    val uri: String,
    val documentUri: String? = null,  // 原始 document URI，用于改名等操作
    val parentUri: String?,
    val rootUri: String? = null,
    val name: String,
    val isComic: Boolean,
    val coverImagePath: String? = null,
    val imageCount: Int = 0,
    val imageUris: List<String> = emptyList(),
    val lastModified: Long = System.currentTimeMillis()
)
