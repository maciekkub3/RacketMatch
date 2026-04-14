package com.racketmatch.data.repository

import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.dto.CoachBookingDto
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test

class CoachRepositoryImplCourtTest {

    @MockK private lateinit var coachApi: CoachApi
    private val tokenStorage = InMemoryTokenStorage()
    private lateinit var repo: CoachRepositoryImpl

    private val baseBookingDto = CoachBookingDto(
        id = "b1", coachId = "c1", playerId = "p1",
        startsAt = "2026-05-01T10:00:00Z", endsAt = "2026-05-01T11:00:00Z",
        durationMinutes = 60, status = "PENDING"
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        repo = CoachRepositoryImpl(coachApi, tokenStorage)
    }

    @Test
    fun `createBooking passes courtName to API`() = runTest {
        coEvery { coachApi.createBooking(any()) } returns baseBookingDto.copy(courtName = "Kort A")

        val result = repo.createBooking(
            "c1", "s1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, null, "Kort A"
        )

        result.courtName shouldBe "Kort A"
        coVerify {
            coachApi.createBooking(match { it.courtName == "Kort A" })
        }
    }

    @Test
    fun `counterBooking passes courtName to API`() = runTest {
        coEvery { coachApi.counterBooking(any(), any()) } returns baseBookingDto.copy(courtName = "Kort B")

        val result = repo.counterBooking(
            "b1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, "Kort B"
        )

        result.courtName shouldBe "Kort B"
        coVerify {
            coachApi.counterBooking("b1", match { it.courtName == "Kort B" })
        }
    }

    @Test
    fun `createBooking with null courtName sends null to API`() = runTest {
        coEvery { coachApi.createBooking(any()) } returns baseBookingDto

        val result = repo.createBooking(
            "c1", "s1",
            Instant.parse("2026-05-01T10:00:00Z"),
            Instant.parse("2026-05-01T11:00:00Z"),
            60, null, null
        )

        result.courtName shouldBe null
        coVerify {
            coachApi.createBooking(match { it.courtName == null })
        }
    }
}
