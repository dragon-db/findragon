package dev.jdtech.jellyfin.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.auth.SecureCredentialsStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecureCredentialsModule {
    @Singleton
    @Provides
    fun provideSecureCredentialsStore(
        @ApplicationContext application: Context
    ): SecureCredentialsStore {
        return SecureCredentialsStore(application)
    }
}
