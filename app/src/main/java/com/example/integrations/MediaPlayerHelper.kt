package com.example.integrations

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

enum class MediaTarget {
    YOUTUBE,
    MUSIC_APP
}

object MediaPlayerHelper {
    private const val TAG = "MediaPlayerHelper"

    /**
     * Opens YouTube or an installed media/music player for the requested song or video.
     * Falls back gracefully to a YouTube web search if no dedicated app handles the intent.
     */
    fun playMedia(
        context: Context,
        query: String,
        target: MediaTarget = MediaTarget.YOUTUBE
    ): Boolean {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return false

        return when (target) {
            MediaTarget.YOUTUBE -> openYouTubeSearch(context, cleaned)
            MediaTarget.MUSIC_APP -> {
                val musicLaunched = openInstalledMusicPlayer(context, cleaned)
                if (musicLaunched) true else openYouTubeSearch(context, cleaned)
            }
        }
    }

    private fun openYouTubeSearch(context: Context, query: String): Boolean {
        val youtubeSearchUrl = Uri.parse(
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        )

        val youtubeAppIntent = Intent(Intent.ACTION_VIEW, youtubeSearchUrl).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(youtubeAppIntent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Fall through to generic browser / YouTube web handler
        } catch (e: Exception) {
            Log.w(TAG, "YouTube package launch fallback: ${e.message}")
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, youtubeSearchUrl).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(fallbackIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open YouTube search", e)
            false
        }
    }

    private fun openInstalledMusicPlayer(context: Context, query: String): Boolean {
        val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(searchIntent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Media app launch failed: ${e.message}")
            false
        }
    }
}
