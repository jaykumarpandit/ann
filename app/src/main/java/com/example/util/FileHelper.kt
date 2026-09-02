package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream

data class FileDetails(
    val name: String,
    val size: Long,
    val mimeType: String?,
    val fileType: String // "image", "file", "folder"
)

object FileHelper {

    fun getFileType(mimeType: String?, name: String?): String {
        return when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == DocumentsContract.Document.MIME_TYPE_DIR || name?.endsWith("/") == true -> "folder"
            else -> "file"
        }
    }

    fun getFileDetails(context: Context, uri: Uri): FileDetails {
        var name = "Unknown"
        var size = 0L
        var mimeType = context.contentResolver.getType(uri)

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "Unknown"
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Handle folders
        if (uri.toString().contains("document/primary") || uri.toString().contains("tree/")) {
            if (mimeType == null) {
                mimeType = DocumentsContract.Document.MIME_TYPE_DIR
            }
        }

        val fileType = getFileType(mimeType, name)
        return FileDetails(name, size, mimeType, fileType)
    }

    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getCompressedBase64Image(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                // Scale down to max 800px width/height to avoid OOM and heavy network payloads
                val maxDimension = 800
                var scale = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val maxSide = Math.max(options.outHeight, options.outWidth)
                    scale = Math.round(maxSide.toDouble() / maxDimension.toDouble()).toInt()
                    if (scale < 1) scale = 1
                }

                val scaleOptions = BitmapFactory.Options().apply {
                    inSampleSize = scale
                }

                // Re-open input stream to decode scaled bitmap
                context.contentResolver.openInputStream(uri)?.use { scaledStream ->
                    val bitmap = BitmapFactory.decodeStream(scaledStream, null, scaleOptions)
                    if (bitmap != null) {
                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                        Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listFilesInFolder(context: Context, folderUri: Uri): List<String> {
        val fileNames = mutableListOf<String>()
        try {
            val documentId = if (DocumentsContract.isDocumentUri(context, folderUri)) {
                DocumentsContract.getDocumentId(folderUri)
            } else {
                DocumentsContract.getTreeDocumentId(folderUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, documentId)
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                
                if (nameIndex != -1 && mimeIndex != -1) {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex) ?: "Unnamed"
                        val mime = cursor.getString(mimeIndex)
                        val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                        fileNames.add(name + (if (isDir) " (folder)" else ""))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return fileNames
    }
}
