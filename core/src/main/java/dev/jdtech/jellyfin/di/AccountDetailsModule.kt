package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.account.AccountDetailsRepositoryImpl
import dev.jdtech.jellyfin.account.api.JfaGoApiService
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AccountDetailsModule {
    @Singleton
    @Provides
    fun provideJfaGoApiService(
        okHttpClient: OkHttpClient,
        json: Json,
    ): JfaGoApiService {
        return JfaGoApiService(okHttpClient, json)
    }

    @Singleton
    @Provides
    fun provideAccountDetailsRepository(
        apiService: JfaGoApiService,
        serverDatabase: ServerDatabaseDao,
        appPreferences: AppPreferences,
        secureCredentialsStore: SecureCredentialsStore,
    ): AccountDetailsRepository {
        return AccountDetailsRepositoryImpl(
            apiService = apiService,
            serverDatabase = serverDatabase,
            appPreferences = appPreferences,
            secureCredentialsStore = secureCredentialsStore,
        )
    }
}
