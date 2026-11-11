package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusSource
import dev.jdtech.jellyfin.dragonhub.account.DragonHubAccountStatusSource
import dev.jdtech.jellyfin.dragonhub.api.DragonHubApiService
import dev.jdtech.jellyfin.findragon.FindragonConfig
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DragonHubModule {
    @Singleton
    @Provides
    fun provideDragonHubApiService(
        @FindragonHttp okHttpClient: OkHttpClient,
        @FindragonHttp json: Json,
    ): DragonHubApiService {
        return DragonHubApiService(okHttpClient, json)
    }

    @Singleton
    @Provides
    fun provideAccountStatusSource(
        apiService: DragonHubApiService,
        appPreferences: AppPreferences,
        database: ServerDatabaseDao,
    ): AccountStatusSource {
        return DragonHubAccountStatusSource(
            apiService = apiService,
            appPreferences = appPreferences,
            database = database,
            isEnabled = FindragonConfig.isDragonHubEnabled,
        )
    }
}
