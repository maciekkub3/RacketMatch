package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FeedApi
import com.racketmatch.data.remote.dto.toDomain
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.repository.FeedRepository

class FeedRepositoryImpl(private val api: FeedApi) : FeedRepository {
    override suspend fun getFeed(before: Long?): List<FeedEvent> = api.getFeed(before).map { it.toDomain() }
}
