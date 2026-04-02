package com.racketmatch.data.repository

import com.racketmatch.data.remote.api.FeedApi
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.repository.FeedRepository

class FeedRepositoryImpl(private val api: FeedApi) : FeedRepository {
    override suspend fun getFeed(before: Long?) = api.getFeed(before)
}
