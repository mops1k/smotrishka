package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Регистрация миграций базы в контейнере.
 *
 * Каждая миграция попадает в набор `Set<Migration>`, который получает `DataModule`: новая
 * миграция — новый класс и одна строка здесь, перечислять их в builder больше не нужно
 * (решение Александра 10.11.2026).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MigrationsModule {

    @Binds
    @IntoSet
    abstract fun bindMigration1To2(migration: Migration1To2): Migration
    @Binds
    @IntoSet
    abstract fun bindMigration2To3(migration: Migration2To3): Migration
    @Binds
    @IntoSet
    abstract fun bindMigration3To4(migration: Migration3To4): Migration
    @Binds
    @IntoSet
    abstract fun bindMigration4To5(migration: Migration4To5): Migration
    @Binds
    @IntoSet
    abstract fun bindMigration5To6(migration: Migration5To6): Migration
    @Binds
    @IntoSet
    abstract fun bindMigration6To7(migration: Migration6To7): Migration
}
