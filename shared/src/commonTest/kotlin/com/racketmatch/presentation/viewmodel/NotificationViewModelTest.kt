package com.racketmatch.presentation.viewmodel

import app.cash.turbine.test
import com.racketmatch.domain.model.AppNotification
import com.racketmatch.domain.model.NotificationType
import com.racketmatch.domain.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class NotificationViewModelTest {

    @MockK private lateinit var repo: NotificationRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: NotificationViewModel

    private val unreadMatch = AppNotification(
        id = "n1", type = NotificationType.CHALLENGE_RECEIVED,
        title = "title", body = "body", data = emptyMap(),
        read = false, createdAt = Clock.System.now()
    )
    private val unreadFriend = AppNotification(
        id = "n2", type = NotificationType.FRIEND_REQUEST_RECEIVED,
        title = "title2", body = "body2", data = emptyMap(),
        read = false, createdAt = Clock.System.now()
    )
    private val readNotif = AppNotification(
        id = "n3", type = NotificationType.NEW_MESSAGE,
        title = "title3", body = "body3", data = emptyMap(),
        read = true, createdAt = Clock.System.now()
    )

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has loading true`() = runTest {
        every { repo.observeNotifications(any()) } returns flowOf(emptyList())
        vm = NotificationViewModel(repo, "user1", dispatcher)
        vm.state.value.loading shouldBe true
    }

    @Test
    fun `notifications emitted from repo update state`() = runTest {
        every { repo.observeNotifications("user1") } returns flowOf(listOf(unreadMatch, readNotif))
        vm = NotificationViewModel(repo, "user1", dispatcher)

        vm.state.test {
            skipItems(1)
            dispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            state.notifications shouldBe listOf(unreadMatch, readNotif)
            state.unreadCount shouldBe 1
            state.loading shouldBe false
        }
    }

    @Test
    fun `unreadMatchCount counts only match types`() = runTest {
        every { repo.observeNotifications("user1") } returns
                flowOf(listOf(unreadMatch, unreadFriend, readNotif))
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.state.value.unreadMatchCount shouldBe 1
        vm.state.value.unreadFriendCount shouldBe 1
        vm.state.value.unreadDmCount shouldBe 0
    }

    @Test
    fun `MarkAllRead calls repo with unread ids only`() = runTest {
        every { repo.observeNotifications("user1") } returns
                flowOf(listOf(unreadMatch, unreadFriend, readNotif))
        coJustRun { repo.markAllAsRead(any(), any()) }
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationEvent.MarkAllRead)
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.markAllAsRead("user1", listOf("n1", "n2")) }
    }

    @Test
    fun `MarkRead calls repo with single id`() = runTest {
        every { repo.observeNotifications("user1") } returns flowOf(listOf(unreadMatch))
        coJustRun { repo.markAsRead(any(), any()) }
        vm = NotificationViewModel(repo, "user1", dispatcher)
        dispatcher.scheduler.advanceUntilIdle()

        vm.onEvent(NotificationEvent.MarkRead("n1"))
        dispatcher.scheduler.advanceUntilIdle()

        coVerify { repo.markAsRead("user1", "n1") }
    }
}
