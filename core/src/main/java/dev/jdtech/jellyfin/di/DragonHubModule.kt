package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.dragonhub.api.DragonHubApiService
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DragonHubModule {
    @Singleton
    @Provides
    fun provideDragonHubApiService(
        okHttpClient: OkHttpClient,
        json: Json,
    ): DragonHubApiService {
        return DragonHubApiService(okHttpClient, json)
    }
}
