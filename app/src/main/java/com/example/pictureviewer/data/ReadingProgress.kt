package com.example.pictureviewer.data

data class ReadingProgress(
    val comicUri: String,
    val lastReadIndex: Int = 0,
    val scrollOffset: Int = 0,
    val lastReadTime: Long = System.currentTimeMillis()
)
