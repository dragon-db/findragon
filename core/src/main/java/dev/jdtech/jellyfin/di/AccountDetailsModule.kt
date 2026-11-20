package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.dragonhub.account.AccountStatusSource
import dev.jdtech.jellyfin.dragonhub.account.DragonHubAccountDetailsRepository
import dev.jdtech.jellyfin.settings.domain.AccountDetailsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AccountDetailsModule {
    @Singleton
    @Provides
    fun provideAccountDetailsRepository(
        accountStatusSource: AccountStatusSource
    ): AccountDetailsRepository {
        return DragonHubAccountDetailsRepository(accountStatusSource)
    }
}
