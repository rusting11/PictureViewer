package com.example.pictureviewer.data.model

import android.net.Uri
import androidx.documentfile.provider.DocumentFile

enum class FileCategory { VALID, CONFLICT, INVALID, UNCHANGED }

data class FileRenameItem(
    val documentUri: Uri,
    val originalName: String,
    val renamedName: String,
    val category: FileCategory = FileCategory.VALID,
    val relativePath: String = "",
    val parentUri: Uri? = null,
    val isRenamed: Boolean = false,
    val hasError: Boolean = false,
    val isSelected: Boolean = true,
    val conflictWith: List<String> = emptyList()
) {
    @Transient
    var documentFile: DocumentFile? = null
}

data class RenameRecord(
    val oldUri: Uri,
    val newUri: Uri,
    val originalName: String,
    val renamedName: String
)
