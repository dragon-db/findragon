package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepository
import dev.jdtech.jellyfin.jellyseerr.JellyseerrRepositoryImpl
import dev.jdtech.jellyfin.jellyseerr.api.JellyseerrApiService
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object JellyseerrModule {
    @Singleton
    @Provides
    fun provideJellyseerrJson(): Json {
        return Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }

    @Singleton
    @Provides
    fun provideJellyseerrOkHttpClient(): OkHttpClient {
        return OkHttpClient()
    }

    @Singleton
    @Provides
    fun provideJellyseerrApiService(
        okHttpClient: OkHttpClient,
        json: Json,
    ): JellyseerrApiService {
        return JellyseerrApiService(okHttpClient, json)
    }

    @Singleton
    @Provides
    fun provideJellyseerrRepository(
        apiService: JellyseerrApiService,
        serverDatabase: ServerDatabaseDao,
        appPreferences: AppPreferences,
        secureCredentialsStore: SecureCredentialsStore,
        jellyfinRepository: JellyfinRepository,
    ): JellyseerrRepository {
        return JellyseerrRepositoryImpl(
            apiService = apiService,
            serverDatabase = serverDatabase,
            appPreferences = appPreferences,
            secureCredentialsStore = secureCredentialsStore,
            jellyfinRepository = jellyfinRepository,
        )
    }
}
