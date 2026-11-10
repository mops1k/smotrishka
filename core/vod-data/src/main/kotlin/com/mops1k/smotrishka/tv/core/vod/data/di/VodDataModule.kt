package com.mops1k.smotrishka.tv.core.vod.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.mops1k.smotrishka.tv.core.data.SecretStore
import com.mops1k.smotrishka.tv.core.data.backup.VodBackupPort
import com.mops1k.smotrishka.tv.core.vod.data.backup.VodBackupAdapter
import com.mops1k.smotrishka.tv.core.vod.data.event.InProcessVodEventBus
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalCatalogAdapter
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalClient
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalPortalAdapter
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalRequestTemplates
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalSearchAdapter
import com.mops1k.smotrishka.tv.core.vod.data.portal.VPortalTitleAdapter
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodConfigStore
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodDatabase
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodFavoritesDao
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodFavoritesStore
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodProgressDao
import com.mops1k.smotrishka.tv.core.vod.data.storage.VodProgressStore
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodCatalogPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodSearchPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.CompleteVodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ConnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.DisconnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCategories
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodQualities
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodConnection
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SaveVodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SearchVodTitles
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.StartVodPlayback
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ToggleVodFavorite
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Сборка контекста медиатеки: портал, база, хранилища, шина событий и use case-ы.
 *
 * Use case-ы живут в домене и ничего не знают про Hilt, поэтому связываются здесь явно:
 * так зависимости домена остаются видимыми в одном месте.
 */
@Module
@InstallIn(SingletonComponent::class)
object VodDataModule {

    private const val VOD_DB_NAME = "vod.db"

    @Provides
    @Singleton
    fun vodDatabase(@ApplicationContext context: Context): VodDatabase =
        Room.databaseBuilder(context, VodDatabase::class.java, VOD_DB_NAME).build()

    @Provides
    fun favoritesDao(database: VodDatabase): VodFavoritesDao = database.favoritesDao()

    @Provides
    fun progressDao(database: VodDatabase): VodProgressDao = database.progressDao()

    @Provides
    @Singleton
    fun vPortalClient(okHttpClient: OkHttpClient): VPortalClient = VPortalClient(okHttpClient)

    @Provides
    @Singleton
    fun portalPort(
        client: VPortalClient,
        templates: VPortalRequestTemplates,
    ): VodPortalPort = VPortalPortalAdapter(client, templates)

    @Provides
    @Singleton
    fun catalogPort(
        client: VPortalClient,
        templates: VPortalRequestTemplates,
    ): VodCatalogPort = VPortalCatalogAdapter(client, templates)

    @Provides
    @Singleton
    fun titlePort(client: VPortalClient): VodTitlePort = VPortalTitleAdapter(client)

    @Provides
    @Singleton
    fun searchPort(client: VPortalClient): VodSearchPort = VPortalSearchAdapter(client)

    @Provides
    @Singleton
    fun favoritesPort(dao: VodFavoritesDao): VodFavoritesPort = VodFavoritesStore(dao)

    @Provides
    @Singleton
    fun playbackProgressPort(dao: VodProgressDao): VodPlaybackProgressPort = VodProgressStore(dao)

    @Provides
    @Singleton
    fun configPort(
        preferences: DataStore<Preferences>,
        secretStore: SecretStore,
    ): VodConfigPort = VodConfigStore(preferences, secretStore)

    @Provides
    @Singleton
    fun eventBus(bus: InProcessVodEventBus): VodEventBus = bus

    @Provides
    @Singleton
    fun vodBackupPort(
        configPort: VodConfigPort,
        favoritesPort: VodFavoritesPort,
        connectVodPortal: ConnectVodPortal,
    ): VodBackupPort = VodBackupAdapter(configPort, favoritesPort, connectVodPortal)

    @Provides
    @Singleton
    fun vodClock(clock: java.time.Clock): VodClock = VodClock { clock.millis() }

    @Provides
    fun connectVodPortal(
        portalPort: VodPortalPort,
        configPort: VodConfigPort,
        favoritesPort: VodFavoritesPort,
        playbackProgressPort: VodPlaybackProgressPort,
        eventBus: VodEventBus,
        clock: VodClock,
    ): ConnectVodPortal = ConnectVodPortal(
        portalPort = portalPort,
        configPort = configPort,
        favoritesPort = favoritesPort,
        progressPort = playbackProgressPort,
        eventBus = eventBus,
        clock = clock,
    )

    @Provides
    fun disconnectVodPortal(
        configPort: VodConfigPort,
        favoritesPort: VodFavoritesPort,
        playbackProgressPort: VodPlaybackProgressPort,
    ): DisconnectVodPortal = DisconnectVodPortal(
        configPort = configPort,
        favoritesPort = favoritesPort,
        progressPort = playbackProgressPort,
    )

    @Provides
    fun observeVodConnection(configPort: VodConfigPort): ObserveVodConnection =
        ObserveVodConnection(configPort)

    @Provides
    fun loadVodCategories(
        portalPort: VodPortalPort,
        configPort: VodConfigPort,
    ): LoadVodCategories = LoadVodCategories(portalPort, configPort)

    @Provides
    fun loadVodCatalogPage(
        catalogPort: VodCatalogPort,
        configPort: VodConfigPort,
    ): LoadVodCatalogPage = LoadVodCatalogPage(catalogPort, configPort)

    @Provides
    fun loadVodTitleDetails(
        titlePort: VodTitlePort,
        configPort: VodConfigPort,
    ): LoadVodTitleDetails = LoadVodTitleDetails(titlePort, configPort)

    @Provides
    fun provideLoadVodQualities(
        titlePort: VodTitlePort,
        configPort: VodConfigPort,
    ): LoadVodQualities = LoadVodQualities(titlePort, configPort)

    @Provides
    fun searchVodTitles(
        searchPort: VodSearchPort,
        configPort: VodConfigPort,
    ): SearchVodTitles = SearchVodTitles(searchPort, configPort)

    @Provides
    fun toggleVodFavorite(
        favoritesPort: VodFavoritesPort,
        eventBus: VodEventBus,
        clock: VodClock,
    ): ToggleVodFavorite = ToggleVodFavorite(favoritesPort, eventBus, clock)

    @Provides
    fun observeVodFavorites(favoritesPort: VodFavoritesPort): ObserveVodFavorites =
        ObserveVodFavorites(favoritesPort)

    @Provides
    fun startVodPlayback(
        playbackProgressPort: VodPlaybackProgressPort,
        eventBus: VodEventBus,
        clock: VodClock,
    ): StartVodPlayback = StartVodPlayback(playbackProgressPort, eventBus, clock)

    @Provides
    fun saveVodPlaybackProgress(
        playbackProgressPort: VodPlaybackProgressPort,
        eventBus: VodEventBus,
        clock: VodClock,
    ): SaveVodPlaybackProgress = SaveVodPlaybackProgress(playbackProgressPort, eventBus, clock)

    @Provides
    fun completeVodEpisode(eventBus: VodEventBus, clock: VodClock): CompleteVodEpisode =
        CompleteVodEpisode(eventBus, clock)

    @Provides
    fun observeVodProgress(playbackProgressPort: VodPlaybackProgressPort): ObserveVodProgress =
        ObserveVodProgress(playbackProgressPort)
}
