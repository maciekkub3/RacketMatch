package com.racketmatch.data.remote

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class TokenStorageTest {

    @Test
    fun `stores and retrieves access token`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access123", "refresh456")
        storage.accessToken shouldBe "access123"
        storage.refreshToken shouldBe "refresh456"
    }

    @Test
    fun `clears tokens on logout`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("access123", "refresh456")
        storage.clear()
        storage.accessToken shouldBe null
        storage.refreshToken shouldBe null
    }

    @Test
    fun `initial state has null tokens`() {
        val storage = InMemoryTokenStorage()
        storage.accessToken shouldBe null
        storage.refreshToken shouldBe null
    }

    @Test
    fun `overwrites existing tokens on save`() {
        val storage = InMemoryTokenStorage()
        storage.saveTokens("old-access", "old-refresh")
        storage.saveTokens("new-access", "new-refresh")
        storage.accessToken shouldBe "new-access"
        storage.refreshToken shouldBe "new-refresh"
    }
}
