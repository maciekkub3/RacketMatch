package com.racketmatch.presentation.viewmodel

import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.AuthRepository
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class LoginViewModelTest {

    @MockK private lateinit var authRepository: AuthRepository
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    private val mockUser = User("1", "a@a.com", "Jan", null, false, "Kraków", 1200, false, null, false)
    private val mockResult = AuthResult("tok", "ref", mockUser)

    @BeforeTest
    fun setUp() {
        MockKAnnotations.init(this)
        viewModel = LoginViewModel(authRepository, dispatcher)
    }

    @Test
    fun `initial state is idle`() = runTest {
        viewModel.state.value shouldBe LoginState.Idle
    }

    @Test
    fun `login success emits NavigateToHome effect`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns mockResult

        viewModel.effectFlow.test {
            viewModel.onEvent(LoginEvent.Submit("a@a.com", "pass"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe LoginEffect.NavigateToHome
        }
    }

    @Test
    fun `login failure emits ShowError effect`() = runTest {
        // Use "401" in the message so ErrorMapper.toLoginMessage() routes
        // to the user-friendly bad-credentials string instead of the
        // generic fallback.
        coEvery { authRepository.login(any(), any()) } throws Exception("401 Unauthorized")

        viewModel.effectFlow.test {
            viewModel.onEvent(LoginEvent.Submit("a@a.com", "wrong"))
            dispatcher.scheduler.advanceUntilIdle()
            awaitItem() shouldBe LoginEffect.ShowError("Nieprawidłowy email lub hasło.")
        }
    }

    @Test
    fun `login failure resets state to idle`() = runTest {
        coEvery { authRepository.login(any(), any()) } throws Exception("error")

        viewModel.onEvent(LoginEvent.Submit("a@a.com", "wrong"))
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.state.value shouldBe LoginState.Idle
    }
}
