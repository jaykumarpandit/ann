package com.example.ui

import com.example.integrations.ContactMatch
import com.example.integrations.MediaTarget
import com.example.util.MobileSearchResult

enum class PermissionKind {
    MICROPHONE,
    CONTACTS,
    GMAIL,
    FOLDERS,
    OVERLAY,
    WRITE_SETTINGS,
    ACCESSIBILITY,
    DND_POLICY
}

data class ChatMessageActions(
    val contacts: List<ContactMatch> = emptyList(),
    val mediaQuery: String? = null,
    val mediaTarget: MediaTarget = MediaTarget.YOUTUBE,
    val fileLocations: List<MobileSearchResult> = emptyList(),
    val deviceActionSummaries: List<String> = emptyList(),
    val missingPermission: PermissionKind? = null
)

enum class CloudSyncStatus {
    SYNCING,
    SYNCED,
    ERROR
}
