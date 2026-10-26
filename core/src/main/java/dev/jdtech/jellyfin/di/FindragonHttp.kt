package dev.jdtech.jellyfin.di

import javax.inject.Qualifier

/**
 * Marks the OkHttpClient and Json instances shared by the Findragon integrations (Seerr, JFA-GO,
 * Dragon Hub), so they never clash with bindings upstream may add later.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class FindragonHttp
