package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.setup.data.SecureLoginPasswordSaver
import dev.jdtech.jellyfin.setup.domain.LoginPasswordSaver

/** Phone only: keep the sign-in password for Seerr and silent session recovery. */
@Module
@InstallIn(SingletonComponent::class)
interface FindragonLoginModule {
    @Binds fun bindLoginPasswordSaver(saver: SecureLoginPasswordSaver): LoginPasswordSaver
}
