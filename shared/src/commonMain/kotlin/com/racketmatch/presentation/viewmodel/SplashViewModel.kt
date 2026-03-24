package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.data.remote.TokenStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplashViewModel(
    private val tokenStorage: TokenStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    fun checkAuth(onResult: (isLoggedIn: Boolean) -> Unit) {
        viewModelScope.launch(dispatcher) {
            val isLoggedIn = !tokenStorage.accessToken.isNullOrBlank()
            onResult(isLoggedIn)
        }
    }
}
