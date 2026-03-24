package com.racketmatch.di

import com.racketmatch.data.remote.HttpClientFactory
import com.racketmatch.data.remote.InMemoryTokenStorage
import com.racketmatch.data.remote.TokenStorage
import org.koin.dsl.module

val networkModule = module {
    single<TokenStorage> { InMemoryTokenStorage() }
    single { HttpClientFactory.create(get()) }
}
