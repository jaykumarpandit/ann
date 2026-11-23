package com.example.integrations

import android.content.Context
import android.net.Uri
import com.example.data.GrantedFile
import com.example.util.FileHelper
import com.example.util.MobileSearchResult

/**
 * Intelligent keyword and synonym-aware search over overall device storage (when All Files Access
 * is granted) as well as Room granted files and SAF folder trees.
 */
object SmartFileSearchHelper {

    private val FILE_INTENT_STOP_WORDS = setOf(
        "find", "search", "locate", "where", "show", "open", "file", "files", "folder",
        "folders", "document", "documents", "photo", "photos", "image", "images", "picture",
        "pictures", "please", "could", "would", "around", "phone", "mobile", "device", "location",
        "path", "saved", "stored", "have", "that", "this", "with", "from", "about", "there",
        "give", "list", "check", "look", "inside", "storage"
    )

    private val TYPE_HINTS = mapOf(
        "photo" to "image",
        "photos" to "image",
        "pic" to "image",
        "pics" to "image",
        "picture" to "image",
        "pictures" to "image",
        "image" to "image",
        "images" to "image",
        "selfie" to "image",
        "screenshot" to "image",
        "folder" to "folder",
        "folders" to "folder",
        "directory" to "folder"
    )

    fun extractSearchQueries(prompt: String): Pair<List<String>, String?> {
        val normalized = prompt.lowercase().trim()
        val tokens = normalized
            .split(Regex("[^a-z0-9._-]+"))
            .filter { it.isNotBlank() }

        val inferredTypeFilter = tokens.firstNotNullOfOrNull { TYPE_HINTS[it] }

        val keywords = tokens
            .filter { it.length >= 2 && it !in FILE_INTENT_STOP_WORDS }
            .distinct()
            .take(6)

        return keywords to inferredTypeFilter
    }

    fun isLikelyFileQuery(prompt: String): Boolean {
        val lower = prompt.lowercase()
        val fileVerbs = listOf(
            "find ", "where is ", "where are ", "locate ", "search my ", "search file",
            "search folder", "which folder", "in my files", "in my folders", "on my phone",
            "in storage", "downloads", "download", "pdf", "resume", "cv", "tax",
            "passport", "receipt", "notes", "photo", "folder", "document", "apk", "invoice"
        )
        return fileVerbs.any { lower.contains(it) }
    }

    fun intelligentSearch(
        context: Context,
        grantedFiles: List<GrantedFile>,
        queryText: String,
        maxResults: Int = 6
    ): List<MobileSearchResult> {
        val hasAllFiles = FileHelper.hasAllFilesAccess(context)
        if (!hasAllFiles && grantedFiles.isEmpty()) return emptyList()

        val (keywords, typeFilter) = extractSearchQueries(queryText)
        val searchTokens = if (keywords.isEmpty()) {
            queryText.trim().split(Regex("\\s+")).filter { it.length >= 2 }.take(3)
        } else keywords

        val scored = linkedMapOf<String, Pair<Int, MobileSearchResult>>()

        fun scoreAndStore(item: MobileSearchResult) {
            var itemScore = 0
            val itemLower = item.name.lowercase()
            val pathLower = item.relativePath.lowercase()
            for (token in searchTokens) {
                if (itemLower == token) itemScore += 12
                else if (itemLower.contains(token)) itemScore += 7
                else if (pathLower.contains(token)) itemScore += 4
                if (item.matchSnippet?.contains(token, ignoreCase = true) == true) itemScore += 3
            }
            if (typeFilter != null && item.fileType == typeFilter) itemScore += 2
            val existing = scored[item.uri]
            if (existing == null || itemScore > existing.first) {
                scored[item.uri] = itemScore to item
            }
        }

        val queriesToRun = if (searchTokens.isEmpty()) listOf("") else searchTokens

        // 1. Overall mobile storage search (single fast breadth-first pass)
        if (hasAllFiles) {
            val deviceMatches = FileHelper.searchOverallDeviceStorageMulti(
                queries = queriesToRun,
                typeFilter = typeFilter,
                maxDepth = 5,
                maxVisited = 2000,
                maxResults = 30,
                includeContentSearch = false,
                maxDurationMs = 1500L
            )
            deviceMatches.forEach(::scoreAndStore)
        }

        // 2. Also include any manually linked Vault items / SAF folders
        for (vaultFile in grantedFiles) {
            val lowerName = vaultFile.name.lowercase()
            var score = 0
            for (kw in searchTokens) {
                if (lowerName == kw) score += 10
                else if (lowerName.contains(kw)) score += 6
                if (vaultFile.contentExcerpt?.contains(kw, ignoreCase = true) == true) score += 3
            }
            if (typeFilter != null && vaultFile.fileType == typeFilter) score += 2

            if (score > 0 || (searchTokens.isEmpty() && typeFilter != null && vaultFile.fileType == typeFilter)) {
                scored[vaultFile.uri] = score to MobileSearchResult(
                    uri = vaultFile.uri,
                    name = vaultFile.name,
                    relativePath = "Vault/${vaultFile.name}",
                    fileType = vaultFile.fileType,
                    mimeType = vaultFile.mimeType,
                    size = vaultFile.size,
                    lastModified = vaultFile.timestamp,
                    parentVaultName = "Vault",
                    isFromVaultRoot = true,
                    vaultId = vaultFile.id
                )
            }

            if (vaultFile.fileType == "folder") {
                val treeUri = Uri.parse(vaultFile.uri)
                for (kw in queriesToRun) {
                    val nested = FileHelper.searchInsideFolderTree(
                        context = context,
                        treeUri = treeUri,
                        rootName = vaultFile.name,
                        query = kw,
                        typeFilter = typeFilter,
                        maxResults = 12
                    )
                    nested.forEach(::scoreAndStore)
                }
            }
        }

        return scored.values
            .sortedByDescending { it.first }
            .map { it.second }
            .take(maxResults)
    }
}
