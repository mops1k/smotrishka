package com.mops1k.smotrishka.tv.update

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI кэша обновлений.
 *
 * Каталог — `cacheDir/update`, версия файла читается из манифеста самого APK: так
 * кэш отличает скачанный ранее APK нужной версии от битого или чужого файла.
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun updateCache(
        @ApplicationContext context: Context,
        installedApp: InstalledApp,
    ): UpdateCache = UpdateCache(UpdateCache.directory(context), installedApp::apkVersion)
}
