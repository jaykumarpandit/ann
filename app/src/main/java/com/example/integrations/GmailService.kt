package com.example.integrations

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class EphemeralEmailSummary(
    val id: String,
    val subject: String,
    val from: String,
    val date: String,
    val snippet: String
)

/**
 * Ephemeral read-only Gmail REST client.
 * Email metadata and snippets are kept strictly in-memory for the active turn
 * and are never persisted to Room or Firestore.
 */
object GmailService {
    private const val TAG = "GmailService"
    private const val GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me/messages"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchRecentReadOnlyEmails(
        accessToken: String,
        query: String = "",
        maxResults: Int = 8
    ): Result<List<EphemeralEmailSummary>> = withContext(Dispatchers.IO) {
        try {
            val listUrlBuilder = GMAIL_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("maxResults", maxResults.coerceIn(1, 15).toString())
            if (query.isNotBlank()) {
                listUrlBuilder.addQueryParameter("q", query.trim().take(120))
            }

            val listRequest = Request.Builder()
                .url(listUrlBuilder.build())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val messageIds = mutableListOf<String>()
            client.newCall(listRequest).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val errMsg = if (code == 401 || code == 403) {
                        "Gmail permission expired or needs consent (HTTP $code)."
                    } else {
                        "Gmail request failed (HTTP $code)."
                    }
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val messagesArray = json.optJSONArray("messages")
                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        val id = messagesArray.optJSONObject(i)?.optString("id").orEmpty()
                        if (id.isNotBlank()) messageIds.add(id)
                    }
                }
            }

            val summaries = mutableListOf<EphemeralEmailSummary>()
            for (msgId in messageIds.take(maxResults)) {
                val detailUrl = "$GMAIL_BASE/$msgId".toHttpUrl().newBuilder()
                    .addQueryParameter("format", "metadata")
                    .addQueryParameter("metadataHeaders", "Subject")
                    .addQueryParameter("metadataHeaders", "From")
                    .addQueryParameter("metadataHeaders", "Date")
                    .build()

                val detailReq = Request.Builder()
                    .url(detailUrl)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                client.newCall(detailReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val detailJson = JSONObject(resp.body?.string().orEmpty())
                        val snippet = detailJson.optString("snippet", "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .take(220)
                        val headers = detailJson.optJSONObject("payload")?.optJSONArray("headers")
                        var subject = "(No subject)"
                        var from = "Unknown sender"
                        var date = ""
                        if (headers != null) {
                            for (j in 0 until headers.length()) {
                                val header = headers.optJSONObject(j) ?: continue
                                when (header.optString("name").lowercase()) {
                                    "subject" -> subject = header.optString("value", subject).take(140)
                                    "from" -> from = header.optString("value", from).take(100)
                                    "date" -> date = header.optString("value", "").take(40)
                                }
                            }
                        }
                        summaries.add(
                            EphemeralEmailSummary(
                                id = msgId,
                                subject = subject,
                                from = from,
                                date = date,
                                snippet = snippet
                            )
                        )
                    }
                }
            }
            Result.success(summaries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed fetching Gmail messages", e)
            Result.failure(e)
        }
    }
}
