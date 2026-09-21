package com.example.core

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Google's Custom Search JSON API — free tier is 100 queries/day, needs TWO credentials:
 * an API key (console.cloud.google.com → enable "Custom Search API" → Credentials) and a
 * Search Engine ID (programmablesearchengine.google.com → create a search engine → "Search
 * the entire web" → copy the "Search engine ID"). Both are free to obtain.
 *
 * When configured, this returns real result snippets the AI can read/summarize. When not
 * configured, [SystemControlManager] falls back to just opening a browser search — works with
 * zero setup, just doesn't feed results back into the conversation.
 */
object SearchTask {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class SearchResultItem(val title: String, val snippet: String, val link: String)

    fun search(query: String, apiKey: String, searchEngineId: String): List<SearchResultItem> {
        if (apiKey.isBlank() || searchEngineId.isBlank()) return emptyList()

        return runCatching {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host("www.googleapis.com")
                .addPathSegments("customsearch/v1")
                .addQueryParameter("key", apiKey)
                .addQueryParameter("cx", searchEngineId)
                .addQueryParameter("q", query)
                .addQueryParameter("num", "5")
                .build()

            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string() ?: return@use emptyList()
                val items = JSONObject(body).optJSONArray("items") ?: return@use emptyList()
                (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    SearchResultItem(
                        title = item.optString("title"),
                        snippet = item.optString("snippet"),
                        link = item.optString("link")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
