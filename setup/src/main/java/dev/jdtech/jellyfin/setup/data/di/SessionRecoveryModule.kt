package dev.jdtech.jellyfin.setup.data.di

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.setup.data.session.DefaultSessionAccess
import dev.jdtech.jellyfin.setup.domain.SetupRepository
import dev.jdtech.jellyfin.setup.domain.session.SessionRecovery
import dev.jdtech.jellyfin.setup.presentation.session.SessionClock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SessionRecoveryModule {
    @Singleton
    @Provides
    fun provideSessionRecovery(
        jellyfinApi: JellyfinApi,
        setupRepository: SetupRepository,
        secureCredentialsStore: SecureCredentialsStore,
        appPreferences: AppPreferences,
    ): SessionRecovery {
        return SessionRecovery(
            DefaultSessionAccess(
                jellyfinApi = jellyfinApi,
                setupRepository = setupRepository,
                secureCredentialsStore = secureCredentialsStore,
                appPreferences = appPreferences,
            )
        )
    }

    @Provides
    fun provideSessionClock(): SessionClock = SessionClock { SystemClock.elapsedRealtime() }
}
