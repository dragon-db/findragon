package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.account.api.JfaGoApiService
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusSource
import dev.jdtech.jellyfin.dragonhub.account.DragonHubAccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AccountDetailsModule {
    /** Unused since the account card moved to DragonHub; goes away with the rest of JFA-GO. */
    @Singleton
    @Provides
    fun provideJfaGoApiService(
        @FindragonHttp okHttpClient: OkHttpClient,
        @FindragonHttp json: Json,
    ): JfaGoApiService {
        return JfaGoApiService(okHttpClient, json)
    }

    @Singleton
    @Provides
    fun provideAccountDetailsRepository(
        accountStatusSource: AccountStatusSource
    ): AccountDetailsRepository {
        return DragonHubAccountDetailsRepository(accountStatusSource)
    }
}
