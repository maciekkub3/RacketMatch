package com.racketmatch.data.remote.api

import com.racketmatch.domain.model.FeedEvent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class FeedApi(private val client: HttpClient) {
    suspend fun getFeed(before: Long? = null): List<FeedEvent> {
        val url = if (before != null) "api/feed?before=$before" else "api/feed"
        return client.get(url).body()
    }
}
