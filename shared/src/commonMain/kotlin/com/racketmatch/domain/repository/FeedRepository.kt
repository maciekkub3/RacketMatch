package com.racketmatch.domain.repository

import com.racketmatch.domain.model.FeedEvent

interface FeedRepository {
    suspend fun getFeed(before: Long? = null): List<FeedEvent>
}
