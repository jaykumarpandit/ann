package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream
import java.io.File

data class FileDetails(
    val name: String,
    val size: Long,
    val mimeType: String?,
    val fileType: String // "image", "file", "folder"
)

data class MobileSearchResult(
    val uri: String,
    val name: String,
    val relativePath: String,
    val fileType: String, // "image", "file", "folder"
    val mimeType: String?,
    val size: Long,
    val lastModified: Long,
    val parentVaultName: String,
    val matchSnippet: String? = null,
    val isFromVaultRoot: Boolean = false,
    val vaultId: Long? = null
)

object FileHelper {

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "csv", "json", "xml", "log", "kt", "java", "py", "js", "ts", "html", "yml", "yaml", "ini"
    )

    private val SKIPPED_DIR_NAMES = setOf(
        "android", ".thumbnails", ".trash", ".cache", "cache", "lost.dir"
    )

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            try {
                Environment.getExternalStorageDirectory()?.canRead() == true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun createAllFilesAccessIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null)
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }
    }

    fun getFileType(mimeType: String?, name: String?): String {
        return when {
            mimeType?.startsWith("image/") == true -> "image"
            mimeType == DocumentsContract.Document.MIME_TYPE_DIR || name?.endsWith("/") == true -> "folder"
            isImageFileName(name) -> "image"
            else -> "file"
        }
    }

    private fun isImageFileName(name: String?): Boolean {
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    }

    fun isTextReadable(mimeType: String?, name: String?): Boolean {
        if (mimeType?.startsWith("text/") == true) return true
        if (mimeType in setOf("application/json", "application/xml", "application/javascript")) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return ext in TEXT_EXTENSIONS
    }

    fun guessMimeType(fileName: String, isDirectory: Boolean): String? {
        if (isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    fun takePersistableReadPermission(context: Context, uri: Uri) {
        if (uri.scheme == "file") return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some pickers (e.g. Photo Picker) do not require/support persistable permissions
        }
    }

    fun getFileDetails(context: Context, uri: Uri): FileDetails {
        if (uri.scheme == "file") {
            val file = uri.path?.let { File(it) }
            if (file != null) {
                val isDir = file.isDirectory
                val mime = guessMimeType(file.name, isDir)
                val type = if (isDir) "folder" else getFileType(mime, file.name)
                return FileDetails(
                    name = file.name.ifBlank { "Folder" },
                    size = if (isDir) 0L else file.length(),
                    mimeType = mime,
                    fileType = type
                )
            }
        }

        var name = "Unknown"
        var size = 0L
        var mimeType = context.contentResolver.getType(uri)

        val isTree = try {
            DocumentsContract.isTreeUri(uri)
        } catch (_: Exception) {
            uri.toString().contains("/tree/")
        }

        val queryUri = if (isTree) {
            try {
                DocumentsContract.buildDocumentUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )
            } catch (_: Exception) {
                uri
            }
        } else {
            uri
        }

        try {
            context.contentResolver.query(queryUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "Unknown"
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (isTree && mimeType == null) {
            mimeType = DocumentsContract.Document.MIME_TYPE_DIR
        }

        if (name == "Unknown") {
            name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Unknown"
        }

        val fileType = if (isTree) "folder" else getFileType(mimeType, name)
        return FileDetails(name, size, mimeType, fileType)
    }

    fun readTextFromUri(context: Context, uri: Uri, maxChars: Int = 12_000): String? {
        return try {
            val stream = if (uri.scheme == "file") {
                uri.path?.let { File(it).inputStream() }
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { inputStream ->
                val reader = inputStream.bufferedReader()
                val buffer = CharArray(maxChars)
                val readCount = reader.read(buffer, 0, maxChars)
                if (readCount > 0) String(buffer, 0, readCount) else ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decodeSampledBitmapFromUri(context: Context, uri: Uri, sampleSize: Int = 2): Bitmap? {
        return try {
            val stream = if (uri.scheme == "file") {
                uri.path?.let { File(it).inputStream() }
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use {
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getCompressedBase64Image(context: Context, uri: Uri): String? {
        return try {
            val openStream = {
                if (uri.scheme == "file") {
                    uri.path?.let { File(it).inputStream() }
                } else {
                    context.contentResolver.openInputStream(uri)
                }
            }
            openStream()?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)

                val maxDimension = 800
                var scale = 1
                if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                    val maxSide = maxOf(options.outHeight, options.outWidth)
                    scale = Math.round(maxSide.toDouble() / maxDimension.toDouble()).toInt()
                    if (scale < 1) scale = 1
                }

                val scaleOptions = BitmapFactory.Options().apply {
                    inSampleSize = scale
                }

                openStream()?.use { scaledStream ->
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
        return listFolderChildrenDetailed(context, folderUri).map { item ->
            item.name + if (item.fileType == "folder") " (folder)" else ""
        }
    }

    fun listFolderChildrenDetailed(
        context: Context,
        folderUri: Uri,
        parentVaultName: String = "Folder"
    ): List<MobileSearchResult> {
        if (folderUri.scheme == "file") {
            val dir = folderUri.path?.let { File(it) } ?: return emptyList()
            if (!dir.exists() || !dir.isDirectory) return emptyList()
            val rootPath = Environment.getExternalStorageDirectory()?.absolutePath ?: ""
            return dir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
                ?.take(80)
                ?.map { child ->
                    val isDir = child.isDirectory
                    val mime = guessMimeType(child.name, isDir)
                    val type = getFileType(mime, child.name)
                    val relPath = if (rootPath.isNotEmpty() && child.absolutePath.startsWith(rootPath)) {
                        child.absolutePath.removePrefix(rootPath).trimStart('/')
                    } else {
                        "$parentVaultName/${child.name}"
                    }
                    MobileSearchResult(
                        uri = Uri.fromFile(child).toString(),
                        name = child.name,
                        relativePath = relPath,
                        fileType = type,
                        mimeType = mime,
                        size = if (isDir) 0L else child.length(),
                        lastModified = child.lastModified(),
                        parentVaultName = "Phone Storage"
                    )
                }.orEmpty()
        }

        val results = mutableListOf<MobileSearchResult>()
        try {
            val docId = if (DocumentsContract.isDocumentUri(context, folderUri)) {
                DocumentsContract.getDocumentId(folderUri)
            } else {
                DocumentsContract.getTreeDocumentId(folderUri)
            }
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, docId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            )

            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childDocId = if (idIdx != -1) cursor.getString(idIdx) else continue
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Unnamed" else "Unnamed"
                    val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) else null
                    val size = if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                    val modified = if (modIdx != -1 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                    val childUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, childDocId)
                    val fileType = getFileType(mime, name)

                    results.add(
                        MobileSearchResult(
                            uri = childUri.toString(),
                            name = name,
                            relativePath = "$parentVaultName/$name",
                            fileType = fileType,
                            mimeType = mime,
                            size = size,
                            lastModified = modified,
                            parentVaultName = parentVaultName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    /**
     * Searches overall phone shared storage directly when MANAGE_EXTERNAL_STORAGE (All Files Access)
     * is granted, returning real folder and file locations in a single fast breadth-first pass.
     */
    fun searchOverallDeviceStorage(
        query: String,
        typeFilter: String? = null,
        maxDepth: Int = 6,
        maxVisited: Int = 3000,
        maxResults: Int = 80,
        includeContentSearch: Boolean = true,
        maxDurationMs: Long = 2500L
    ): List<MobileSearchResult> {
        val queries = if (query.isBlank()) emptyList() else listOf(query.trim())
        return searchOverallDeviceStorageMulti(
            queries = queries,
            typeFilter = typeFilter,
            maxDepth = maxDepth,
            maxVisited = maxVisited,
            maxResults = maxResults,
            includeContentSearch = includeContentSearch,
            maxDurationMs = maxDurationMs
        )
    }

    fun searchOverallDeviceStorageMulti(
        queries: List<String>,
        typeFilter: String? = null,
        maxDepth: Int = 5,
        maxVisited: Int = 2000,
        maxResults: Int = 40,
        includeContentSearch: Boolean = false,
        maxDurationMs: Long = 1500L
    ): List<MobileSearchResult> {
        val rootDir = try {
            Environment.getExternalStorageDirectory()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        if (!rootDir.exists() || !rootDir.canRead()) return emptyList()

        val normalizedQueries = queries
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

        val startTime = System.currentTimeMillis()
        val matches = mutableListOf<MobileSearchResult>()
        data class DirEntry(val dir: File, val relativePrefix: String, val depth: Int)

        val queue = ArrayDeque<DirEntry>()
        queue.add(DirEntry(rootDir, "", 0))
        var visited = 0

        while (queue.isNotEmpty() && visited < maxVisited && matches.size < maxResults) {
            if (System.currentTimeMillis() - startTime > maxDurationMs) break
            val current = queue.removeFirst()
            val children = try {
                current.dir.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

            val sortedChildren = children.sortedWith(
                compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() }
            )

            for (child in sortedChildren) {
                if (visited >= maxVisited || matches.size >= maxResults) break
                if (System.currentTimeMillis() - startTime > maxDurationMs) break
                val name = child.name
                if (name.startsWith(".")) continue
                val isDir = child.isDirectory
                if (isDir && current.depth == 0 && name.lowercase() in SKIPPED_DIR_NAMES) continue
                if (isDir && name.lowercase() in SKIPPED_DIR_NAMES && current.depth <= 2) continue

                visited++
                val relPath = if (current.relativePrefix.isEmpty()) name else "${current.relativePrefix}/$name"
                val mime = guessMimeType(name, isDir)
                val fileType = getFileType(mime, name)
                val size = if (isDir) 0L else child.length()
                val modified = child.lastModified()

                if (isDir && current.depth < maxDepth) {
                    queue.add(DirEntry(child, relPath, current.depth + 1))
                }

                val matchesType = typeFilter == null || fileType == typeFilter
                if (!matchesType) continue

                if (normalizedQueries.isEmpty()) {
                    matches.add(
                        MobileSearchResult(
                            uri = Uri.fromFile(child).toString(),
                            name = name,
                            relativePath = relPath,
                            fileType = fileType,
                            mimeType = mime,
                            size = size,
                            lastModified = modified,
                            parentVaultName = "Phone Storage"
                        )
                    )
                } else {
                    val lowerName = name.lowercase()
                    val lowerPath = relPath.lowercase()
                    val lowerMime = mime?.lowercase().orEmpty()
                    val nameOrPathMatch = normalizedQueries.any { q ->
                        lowerName.contains(q) || lowerPath.contains(q) || lowerMime.contains(q)
                    }

                    var snippet: String? = null
                    val contentMatch = if (!nameOrPathMatch &&
                        includeContentSearch &&
                        !isDir &&
                        fileType == "file" &&
                        size in 1..(128 * 1024L) &&
                        isTextReadable(mime, name)
                    ) {
                        val text = try {
                            child.bufferedReader().use { r ->
                                val buf = CharArray(3000)
                                val count = r.read(buf, 0, buf.size)
                                if (count > 0) String(buf, 0, count) else ""
                            }
                        } catch (_: Exception) {
                            ""
                        }
                        val matchedQ = normalizedQueries.firstOrNull { it.length >= 3 && text.lowercase().contains(it) }
                        if (matchedQ != null) {
                            val idx = text.lowercase().indexOf(matchedQ)
                            val start = (idx - 30).coerceAtLeast(0)
                            val end = (idx + matchedQ.length + 70).coerceAtMost(text.length)
                            snippet = text.substring(start, end).replace("\n", " ").trim()
                            true
                        } else false
                    } else false

                    if (nameOrPathMatch || contentMatch) {
                        matches.add(
                            MobileSearchResult(
                                uri = Uri.fromFile(child).toString(),
                                name = name,
                                relativePath = relPath,
                                fileType = fileType,
                                mimeType = mime,
                                size = size,
                                lastModified = modified,
                                parentVaultName = "Phone Storage",
                                matchSnippet = snippet
                            )
                        )
                    }
                }
            }
        }
        return matches
    }

    /**
     * Recursively searches an authorized Storage Access Framework folder tree for matching
     * folders, files, and images by name, path, MIME type, or readable text content.
     */
    fun searchInsideFolderTree(
        context: Context,
        treeUri: Uri,
        rootName: String,
        query: String,
        typeFilter: String? = null,
        maxDepth: Int = 5,
        maxVisited: Int = 400,
        maxResults: Int = 80
    ): List<MobileSearchResult> {
        val matches = mutableListOf<MobileSearchResult>()
        val normalizedQuery = query.trim().lowercase()

        data class QueueEntry(val docId: String, val pathPrefix: String, val depth: Int)

        val rootDocId = try {
            if (DocumentsContract.isDocumentUri(context, treeUri)) {
                DocumentsContract.getDocumentId(treeUri)
            } else {
                DocumentsContract.getTreeDocumentId(treeUri)
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val queue = ArrayDeque<QueueEntry>()
        queue.add(QueueEntry(rootDocId, rootName, 0))
        var visitedCount = 0

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        while (queue.isNotEmpty() && visitedCount < maxVisited && matches.size < maxResults) {
            val current = queue.removeFirst()
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current.docId)
            } catch (_: Exception) {
                continue
            }

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                    while (cursor.moveToNext() && visitedCount < maxVisited && matches.size < maxResults) {
                        visitedCount++
                        val childDocId = if (idIdx != -1) cursor.getString(idIdx) else continue
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "Unnamed" else "Unnamed"
                        val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) else null
                        val size = if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                        val modified = if (modIdx != -1 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L

                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        val fileType = getFileType(mime, name)
                        val childPath = "${current.pathPrefix}/$name"

                        if (fileType == "folder" && current.depth < maxDepth) {
                            queue.add(QueueEntry(childDocId, childPath, current.depth + 1))
                        }

                        val matchesType = typeFilter == null || fileType == typeFilter
                        if (!matchesType) continue

                        if (normalizedQuery.isEmpty()) {
                            matches.add(
                                MobileSearchResult(
                                    uri = childUri.toString(),
                                    name = name,
                                    relativePath = childPath,
                                    fileType = fileType,
                                    mimeType = mime,
                                    size = size,
                                    lastModified = modified,
                                    parentVaultName = rootName
                                )
                            )
                        } else {
                            val nameOrPathMatches = name.lowercase().contains(normalizedQuery) ||
                                childPath.lowercase().contains(normalizedQuery) ||
                                (mime?.lowercase()?.contains(normalizedQuery) == true)

                            var snippet: String? = null
                            val contentMatches = if (!nameOrPathMatches &&
                                normalizedQuery.length >= 3 &&
                                fileType == "file" &&
                                size in 1..(256 * 1024L) &&
                                isTextReadable(mime, name)
                            ) {
                                val text = readTextFromUri(context, childUri, maxChars = 4000)
                                val idx = text?.lowercase()?.indexOf(normalizedQuery) ?: -1
                                if (idx >= 0 && text != null) {
                                    val start = (idx - 30).coerceAtLeast(0)
                                    val end = (idx + normalizedQuery.length + 70).coerceAtMost(text.length)
                                    snippet = text.substring(start, end).replace("\n", " ").trim()
                                    true
                                } else {
                                    false
                                }
                            } else {
                                false
                            }

                            if (nameOrPathMatches || contentMatches) {
                                matches.add(
                                    MobileSearchResult(
                                        uri = childUri.toString(),
                                        name = name,
                                        relativePath = childPath,
                                        fileType = fileType,
                                        mimeType = mime,
                                        size = size,
                                        lastModified = modified,
                                        parentVaultName = rootName,
                                        matchSnippet = snippet
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return matches
    }
}
