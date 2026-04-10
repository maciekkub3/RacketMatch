package com.racketmatch.di

import com.racketmatch.data.remote.HttpClientFactory
import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.data.remote.api.AuthApi
import com.racketmatch.data.remote.api.ChatApi
import com.racketmatch.data.remote.api.CoachApi
import com.racketmatch.data.remote.api.CourtApi
import com.racketmatch.data.remote.api.MatchApi
import com.racketmatch.data.remote.api.OpenSessionApi
import com.racketmatch.data.remote.api.PaymentApi
import com.racketmatch.data.remote.api.PlayerApi
import com.racketmatch.data.remote.api.DmApi
import com.racketmatch.data.remote.api.FeedApi
import com.racketmatch.data.remote.api.FriendApi
import com.racketmatch.data.remote.api.ProfileApi
import com.racketmatch.data.remote.api.UserApi
import com.racketmatch.data.repository.AuthRepositoryImpl
import com.racketmatch.data.repository.ChatRepositoryImpl
import com.racketmatch.data.repository.CoachRepositoryImpl
import com.racketmatch.data.repository.CourtRepositoryImpl
import com.racketmatch.data.repository.MatchRepositoryImpl
import com.racketmatch.data.repository.OpenSessionRepositoryImpl
import com.racketmatch.data.repository.PaymentRepositoryImpl
import com.racketmatch.data.repository.PlayerRepositoryImpl
import com.racketmatch.data.repository.DmRepositoryImpl
import com.racketmatch.data.repository.FeedRepositoryImpl
import com.racketmatch.data.repository.FirestoreNotificationRepositoryImpl
import com.racketmatch.data.repository.FriendRepositoryImpl
import com.racketmatch.data.repository.ProfileRepositoryImpl
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.domain.repository.ChatRepository
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.CourtRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.OpenSessionRepository
import com.racketmatch.domain.repository.PaymentRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.DmRepository
import com.racketmatch.domain.repository.FeedRepository
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.domain.repository.NotificationRepository
import com.racketmatch.domain.repository.ProfileRepository
import com.racketmatch.presentation.viewmodel.ChatViewModel
import com.racketmatch.presentation.viewmodel.CoachAvailabilityViewModel
import com.racketmatch.presentation.viewmodel.CoachBookingsViewModel
import com.racketmatch.presentation.viewmodel.PlayerBookingsViewModel
import com.racketmatch.presentation.viewmodel.CoachCalendarViewModel
import com.racketmatch.presentation.viewmodel.CoachDetailViewModel
import com.racketmatch.presentation.viewmodel.CoachProfileEditViewModel
import com.racketmatch.presentation.viewmodel.CoachServicesViewModel
import com.racketmatch.presentation.viewmodel.CoachesViewModel
import com.racketmatch.presentation.viewmodel.DmChatViewModel
import com.racketmatch.presentation.viewmodel.ExploreViewModel
import com.racketmatch.presentation.viewmodel.LoginViewModel
import com.racketmatch.presentation.viewmodel.MatchViewModel
import com.racketmatch.presentation.viewmodel.PaymentViewModel
import com.racketmatch.presentation.viewmodel.PlayersViewModel
import com.racketmatch.presentation.viewmodel.ProfileSetupViewModel
import com.racketmatch.presentation.viewmodel.RegisterViewModel
import com.racketmatch.presentation.viewmodel.ProfileViewModel
import com.racketmatch.presentation.viewmodel.FeedViewModel
import com.racketmatch.presentation.viewmodel.FriendsViewModel
import com.racketmatch.presentation.viewmodel.MessagesViewModel
import com.racketmatch.presentation.viewmodel.MoreViewModel
import com.racketmatch.presentation.viewmodel.NotificationViewModel
import com.racketmatch.presentation.viewmodel.RankingsViewModel
import com.racketmatch.presentation.viewmodel.SettingsViewModel
import com.racketmatch.presentation.viewmodel.SplashViewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val networkModule = module {
    single { HttpClientFactory.create(get(), get(named("baseUrl"))) }
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
    single { CourtApi(get()) }
    single { OpenSessionApi(get()) }
    single { FriendApi(get()) }
    single { FeedApi(get()) }
    single { DmApi(get()) }
}

val repositoryModule = module {
    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<PlayerRepository> { PlayerRepositoryImpl(get()) }
    single<MatchRepository> { MatchRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get()) }
    single<CoachRepository> { CoachRepositoryImpl(get()) }
    single<PaymentRepository> { PaymentRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get()) }
    single<CourtRepository> { CourtRepositoryImpl(get()) }
    single<OpenSessionRepository> { OpenSessionRepositoryImpl(get(), get()) }
    single<FriendRepository> { FriendRepositoryImpl(get()) }
    single<FeedRepository> { FeedRepositoryImpl(get()) }
    single<DmRepository> { DmRepositoryImpl(get()) }
    single<NotificationRepository> { FirestoreNotificationRepositoryImpl() }
}

val viewModelModule = module {
    factory { (matchId: String) -> ChatViewModel(get(), matchId) }
    factory { (conversationId: String, currentUserId: String) -> DmChatViewModel(get(), conversationId, currentUserId) }
    factory { LoginViewModel(get()) }
    factory { RegisterViewModel(get(), get()) }
    factory { ProfileSetupViewModel(get(), get()) }
    factory { PlayersViewModel(get(), get(), get(), get()) }
    factory { ExploreViewModel(get(), get(), get(), get(), get(), get()) }
    factory { MatchViewModel(get(), get()) }
    factory { CoachesViewModel(get()) }
    factory { (coachId: String) -> CoachDetailViewModel(get(), coachId) }
    factory { PaymentViewModel(get()) }
    factory { SplashViewModel(get()) }
    factory { ProfileViewModel(get(), get(), get()) }
    factory { SettingsViewModel(get(), get()) }
    factory { RankingsViewModel(get(), get(), get()) }
    factory { FriendsViewModel(get(), get()) }
    factory { FeedViewModel(get()) }
    factory { MessagesViewModel(get()) }
    factory { MoreViewModel(get(), get()) }
    factory { (userId: String) -> NotificationViewModel(get(), userId) }
    factory { CoachServicesViewModel(get()) }
    factory { CoachCalendarViewModel(get()) }
    factory { CoachBookingsViewModel(get()) }
    factory { PlayerBookingsViewModel(get()) }
    factory { CoachProfileEditViewModel(get(), get(), get(), get()) }
    factory { CoachAvailabilityViewModel(get()) }
}
