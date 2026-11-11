package dev.jdtech.jellyfin.setup.data.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.setup.domain.LoginPasswordSaver

/** The phone app binds a [LoginPasswordSaver]; the TV app does not, so TV stores no passwords. */
@Module
@InstallIn(SingletonComponent::class)
interface LoginPasswordModule {
    @BindsOptionalOf fun optionalLoginPasswordSaver(): LoginPasswordSaver
}
