package com.racketmatch.di

import com.racketmatch.data.remote.HttpClientFactory
import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.AuthApi
import com.racketmatch.data.remote.api.ChatApi
import com.racketmatch.data.remote.api.MatchApi
import com.racketmatch.data.remote.api.PlayerApi
import com.racketmatch.data.repository.AuthRepositoryImpl
import com.racketmatch.data.repository.ChatRepositoryImpl
import com.racketmatch.data.repository.MatchRepositoryImpl
import com.racketmatch.data.repository.PlayerRepositoryImpl
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.domain.repository.ChatRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.presentation.viewmodel.LoginViewModel
import com.racketmatch.presentation.viewmodel.MatchViewModel
import com.racketmatch.presentation.viewmodel.PlayersViewModel
import com.racketmatch.presentation.viewmodel.RegisterViewModel
import org.koin.dsl.module

val networkModule = module {
    single<TokenStorage> { InMemoryTokenStorage() }
    single { HttpClientFactory.create(get()) }
}

val apiModule = module {
    single { AuthApi(get()) }
    single { PlayerApi(get()) }
    single { MatchApi(get()) }
    single { ChatApi(get()) }
}

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<PlayerRepository> { PlayerRepositoryImpl(get()) }
    single<MatchRepository> { MatchRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
}

val viewModelModule = module {
    factory { LoginViewModel(get()) }
    factory { RegisterViewModel(get()) }
    factory { PlayersViewModel(get()) }
    factory { MatchViewModel(get()) }
}
