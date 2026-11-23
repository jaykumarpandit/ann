package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Cloud sync repository backed by the provisioned named Firestore database.
 * Applies strict data minimization before syncing any user content to the cloud.
 */
class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user access." }
        }
        return uid
    }

    fun observeProfile(userId: String): Flow<CloudUserProfile?> = flow {
        val uid = requireUserId(userId)
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            CloudUserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val toLog = firestoreError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(userId: String, profile: UserProfile): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()

            val displayName = profile.name.trim().take(80)
            val birthday = profile.birthday.trim().take(40)
            val interests = profile.interests.trim().take(300)
            val goals = profile.goals.trim().take(300)
            val likes = profile.likes.trim().take(300)
            val dislikes = profile.dislikes.trim().take(300)
            val customNotes = profile.customMemories.trim().take(500)

            if (!snapshot.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (birthday.isNotEmpty()) payload["birthday"] = birthday
                if (interests.isNotEmpty()) payload["interests"] = interests
                if (goals.isNotEmpty()) payload["goals"] = goals
                if (likes.isNotEmpty()) payload["likes"] = likes
                if (dislikes.isNotEmpty()) payload["dislikes"] = dislikes
                if (customNotes.isNotEmpty()) payload["customNotes"] = customNotes
                docRef.set(payload).await()
            } else {
                val updates = mapOf(
                    "displayName" to displayName,
                    "birthday" to birthday,
                    "interests" to interests,
                    "goals" to goals,
                    "likes" to likes,
                    "dislikes" to dislikes,
                    "customNotes" to customNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchUserProfile(userId: String): Result<CloudUserProfile?> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val snap = db.collection("users").document(uid).get().await()
            val profile = if (snap.exists()) {
                snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun upsertMemory(userId: String, docId: String, memory: UserMemory): Result<Unit> {
        val safeDocId = sanitizeDocId(docId)
        val path = "users/$userId/memories/$safeDocId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("memories").document(safeDocId)

            val trimmedContent = memory.content.trim().take(320)
            if (trimmedContent.isEmpty()) return Result.success(Unit)
            val category = if (memory.category in MemoryCategory.all) memory.category else MemoryCategory.GENERAL
            val importance = memory.importance.coerceIn(1, 5).toLong()
            val source = if (memory.source == "chat") "chat" else "manual"

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "content" to trimmedContent,
                    "category" to category,
                    "importance" to importance,
                    "source" to source,
                    "pinned" to memory.isPinned,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "content" to trimmedContent,
                        "category" to category,
                        "importance" to importance,
                        "pinned" to memory.isPinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMemory(userId: String, docId: String): Result<Unit> {
        val safeDocId = sanitizeDocId(docId)
        val path = "users/$userId/memories/$safeDocId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users").document(uid)
                .collection("memories").document(safeDocId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchMemories(userId: String): Result<List<CloudUserMemory>> {
        val path = "users/$userId/memories"
        return try {
            val uid = requireUserId(userId)
            val snap = db.collection("users").document(uid)
                .collection("memories")
                .get().await()
            val list = snap.toObjects(
                CloudUserMemory::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun upsertSession(userId: String, session: ChatSession): Result<Unit> {
        val safeId = sanitizeDocId("session_${session.id}")
        val path = "users/$userId/sessions/$safeId"
        return try {
            val uid = requireUserId(userId)
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(safeId)

            val title = session.title.trim().take(120).ifEmpty { "Chat with Ann" }
            val summary = session.summary?.trim()?.take(300)?.takeIf { it.isNotEmpty() }

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "title" to title,
                    "active" to session.isActive,
                    "clientUpdatedAt" to session.updatedAt.coerceAtLeast(0L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (summary != null) payload["summary"] = summary
                docRef.set(payload).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "title" to title,
                    "active" to session.isActive,
                    "clientUpdatedAt" to session.updatedAt.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (summary != null) updates["summary"] = summary
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun insertMessage(userId: String, sessionId: Long, message: ChatMessage): Result<Unit> {
        val safeSessionId = sanitizeDocId("session_$sessionId")
        val safeMsgId = sanitizeDocId("msg_${message.id}")
        val path = "users/$userId/sessions/$safeSessionId/messages/$safeMsgId"
        return try {
            val uid = requireUserId(userId)
            val sessionRef = db.collection("users").document(uid)
                .collection("sessions").document(safeSessionId)
            val msgRef = sessionRef.collection("messages").document(safeMsgId)

            val role = if (message.role == "user") "user" else "model"
            val content = message.content.take(8000)
            val attachmentName = message.attachedFileName?.trim()?.take(160)?.takeIf { it.isNotEmpty() }
            val attachmentType = message.attachedFileType?.takeIf { it in setOf("image", "file", "folder") }

            val payload = mutableMapOf<String, Any>(
                "role" to role,
                "content" to content,
                "clientTimestamp" to message.timestamp.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (attachmentName != null) payload["attachmentName"] = attachmentName
            if (attachmentType != null) payload["attachmentType"] = attachmentType

            msgRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSessionAndMessages(userId: String, sessionId: Long): Result<Unit> {
        val safeSessionId = sanitizeDocId("session_$sessionId")
        val path = "users/$userId/sessions/$safeSessionId"
        return try {
            val uid = requireUserId(userId)
            val sessionRef = db.collection("users").document(uid)
                .collection("sessions").document(safeSessionId)
            val messages = sessionRef.collection("messages").get().await()
            for (doc in messages.documents) {
                doc.reference.delete().await()
            }
            sessionRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearSessionMessages(userId: String, sessionId: Long): Result<Unit> {
        val safeSessionId = sanitizeDocId("session_$sessionId")
        val path = "users/$userId/sessions/$safeSessionId/messages"
        return try {
            val uid = requireUserId(userId)
            val sessionRef = db.collection("users").document(uid)
                .collection("sessions").document(safeSessionId)
            val messages = sessionRef.collection("messages").get().await()
            for (doc in messages.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchSessionsWithMessages(userId: String): Result<List<Pair<CloudChatSession, List<CloudChatMessage>>>> {
        val path = "users/$userId/sessions"
        return try {
            val uid = requireUserId(userId)
            val sessionsSnap = db.collection("users").document(uid)
                .collection("sessions")
                .orderBy("clientUpdatedAt", Query.Direction.DESCENDING)
                .get().await()

            val sessions = sessionsSnap.toObjects(
                CloudChatSession::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            val result = sessions.map { session ->
                val msgSnap = db.collection("users").document(uid)
                    .collection("sessions").document(session.id)
                    .collection("messages")
                    .orderBy("clientTimestamp", Query.Direction.ASCENDING)
                    .get().await()
                val msgs = msgSnap.toObjects(
                    CloudChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                session to msgs
            }
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllCloudUserData(userId: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId(userId)
            val userDoc = db.collection("users").document(uid)
            val memories = userDoc.collection("memories").get().await()
            for (doc in memories.documents) {
                doc.reference.delete().await()
            }
            val sessions = userDoc.collection("sessions").get().await()
            for (sess in sessions.documents) {
                val msgs = sess.reference.collection("messages").get().await()
                for (msg in msgs.documents) {
                    msg.reference.delete().await()
                }
                sess.reference.delete().await()
            }
            userDoc.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    companion object {
        fun sanitizeDocId(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            return cleaned.take(120).ifEmpty { "doc_${System.currentTimeMillis()}" }
        }
    }
}
