package com.racketmatch.di

import com.racketmatch.data.remote.HttpClientFactory
import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.AuthApi
import com.racketmatch.data.remote.api.ChatApi
import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.api.MatchApi
import com.racketmatch.data.remote.api.PaymentApi
import com.racketmatch.data.remote.api.PlayerApi
import com.racketmatch.data.remote.api.ProfileApi
import com.racketmatch.data.remote.api.UserApi
import com.racketmatch.data.repository.AuthRepositoryImpl
import com.racketmatch.data.repository.ChatRepositoryImpl
import com.racketmatch.data.repository.CoachRepositoryImpl
import com.racketmatch.data.repository.MatchRepositoryImpl
import com.racketmatch.data.repository.PaymentRepositoryImpl
import com.racketmatch.data.repository.PlayerRepositoryImpl
import com.racketmatch.data.repository.ProfileRepositoryImpl
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.domain.repository.ChatRepository
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.PaymentRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.ProfileRepository
import com.racketmatch.presentation.viewmodel.ChatViewModel
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.presentation.viewmodel.LoginViewModel
import com.racketmatch.presentation.viewmodel.MatchViewModel
import com.racketmatch.presentation.viewmodel.PaymentViewModel
import com.racketmatch.presentation.viewmodel.PlayersViewModel
import com.racketmatch.presentation.viewmodel.RegisterViewModel
import com.racketmatch.presentation.viewmodel.ProfileViewModel
import com.racketmatch.presentation.viewmodel.SplashViewModel
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
    single { CoachApi(get()) }
    single { PaymentApi(get()) }
    single { UserApi(get()) }
    single { ProfileApi(get()) }
}

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<PlayerRepository> { PlayerRepositoryImpl(get()) }
    single<MatchRepository> { MatchRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    single<CoachRepository> { CoachRepositoryImpl(get()) }
    single<PaymentRepository> { PaymentRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
}

val viewModelModule = module {
    factory { (matchId: String) -> ChatViewModel(get(), matchId) }
    factory { LoginViewModel(get()) }
    factory { RegisterViewModel(get()) }
    factory { PlayersViewModel(get()) }
    factory { MatchViewModel(get()) }
    factory { CoachesViewModel(get()) }
    factory { (coachId: String) -> CoachDetailViewModel(get(), coachId) }
    factory { PaymentViewModel(get()) }
    factory { SplashViewModel(get()) }
    factory { ProfileViewModel(get()) }
}
