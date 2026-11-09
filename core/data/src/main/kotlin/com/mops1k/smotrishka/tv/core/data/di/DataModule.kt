package com.mops1k.smotrishka.tv.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.WorkManager
import com.mops1k.smotrishka.tv.core.data.AesGcmSecretCipher
import com.mops1k.smotrishka.tv.core.data.CatalogDao
import com.mops1k.smotrishka.tv.core.data.ChannelEpgDao
import com.mops1k.smotrishka.tv.core.data.CatalogMapper
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.DatabaseMigrations
import com.mops1k.smotrishka.tv.core.data.SearchDao
import com.mops1k.smotrishka.tv.core.data.SearchRepository
import com.mops1k.smotrishka.tv.core.data.EpgDao
import com.mops1k.smotrishka.tv.core.data.EpgGuideDao
import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.EpgGuideStateStore
import com.mops1k.smotrishka.tv.core.data.EpgGuideValidator
import com.mops1k.smotrishka.tv.core.data.EpgLoader
import com.mops1k.smotrishka.tv.core.data.EpgMapper
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.HttpBytesClient
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import com.mops1k.smotrishka.tv.core.data.IedemPlaylistLoader
import com.mops1k.smotrishka.tv.core.data.IedemProvider
import com.mops1k.smotrishka.tv.core.data.KeystoreSecretKeyProvider
import com.mops1k.smotrishka.tv.core.data.PlaylistFileSource
import com.mops1k.smotrishka.tv.core.data.PlaylistSourceLoader
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SecretCipher
import com.mops1k.smotrishka.tv.core.data.SecretStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
import com.mops1k.smotrishka.tv.core.data.UpdateScheduler
import com.mops1k.smotrishka.tv.core.data.backup.SettingsBackupService
import com.mops1k.smotrishka.tv.core.data.update.ApkDownloader
import com.mops1k.smotrishka.tv.core.data.update.GithubReleasesClient
import com.mops1k.smotrishka.tv.core.data.update.UpdateChecker
import com.mops1k.smotrishka.tv.core.data.update.UpdateSettingsStore
import com.mops1k.smotrishka.tv.core.data.watchnext.TvProviderWatchNextSync
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextSync
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import com.mops1k.smotrishka.tv.core.playlist.XmltvParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.mops1k.smotrishka.tv.core.data.EpgStreamImporter

/**
 * Сборка инфраструктуры данных приложения: сеть, база, хранилища, загрузчики.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    private const val DB_NAME = "smotrishka.db"
    private const val PREFS_FILE = "smotrishka.preferences_pb"

    /** Каталог HTTP-кэша и его размер. */
    private const val HTTP_CACHE_DIR = "http"
    private const val HTTP_CACHE_BYTES = 20L * 1024 * 1024

    /** Сколько секунд разрешено отдавать ответ из кэша. */
    private const val HTTP_CACHE_SECONDS = 60

    @Provides
    fun provideChannelEpgDao(database: SmotrishkaDatabase): ChannelEpgDao = database.channelEpgDao()

    @Provides
    @Singleton
    fun okHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // Программа передач бывает в десятки мегабайт: на слабом канале 60 секунд
            // не хватало, загрузка обрывалась и EPG не появлялась вовсе.
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(240, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Кэш на диске: каталог iedem весит 732 КБ и не меняется между запусками,
            // поэтому повторная загрузка в течение минуты берётся из кэша.
            .cache(Cache(File(context.cacheDir, HTTP_CACHE_DIR), HTTP_CACHE_BYTES))
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=$HTTP_CACHE_SECONDS")
                    .build()
            }
            .build()

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SmotrishkaDatabase =
        Room.databaseBuilder(context, SmotrishkaDatabase::class.java, DB_NAME)
            .addMigrations(
                DatabaseMigrations.MIGRATION_1_2,
                DatabaseMigrations.MIGRATION_2_3,
                DatabaseMigrations.MIGRATION_3_4,
                DatabaseMigrations.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun catalogDao(database: SmotrishkaDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun epgDao(database: SmotrishkaDatabase): EpgDao = database.epgDao()

    @Provides
    fun epgGuideDao(database: SmotrishkaDatabase): EpgGuideDao = database.epgGuideDao()

    @Provides
    fun searchDao(database: SmotrishkaDatabase): SearchDao = database.searchDao()

    @Provides
    fun searchRepository(
        catalogDao: CatalogDao,
        epgDao: EpgDao,
        searchDao: SearchDao,
    ): SearchRepository = SearchRepository(catalogDao, epgDao, searchDao)

    @Provides
    @Singleton
    fun epgGuideRepository(database: SmotrishkaDatabase): EpgGuideRepository =
        EpgGuideRepository(database)

    @Provides
    @Singleton
    fun epgGuideValidator(httpBytesClient: HttpBytesClient): EpgGuideValidator =
        EpgGuideValidator(httpBytesClient)

    @Provides
    @Singleton
    fun epgGuideStateStore(preferences: DataStore<Preferences>): EpgGuideStateStore =
        EpgGuideStateStore(preferences)

    @Provides
    @Singleton
    fun preferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { File(context.filesDir, PREFS_FILE) }

    @Provides
    @Singleton
    fun secretCipher(): SecretCipher = AesGcmSecretCipher(KeystoreSecretKeyProvider().key())

    @Provides
    @Singleton
    fun secretStore(preferences: DataStore<Preferences>, cipher: SecretCipher): SecretStore =
        SecretStore(preferences, cipher)

    @Provides
    @Singleton
    fun syncStatusStore(preferences: DataStore<Preferences>): SyncStatusStore =
        SyncStatusStore(preferences)

    @Provides
    @Singleton
    fun playerSettingsStore(preferences: DataStore<Preferences>): PlayerSettingsStore =
        PlayerSettingsStore(preferences)

    @Provides
    @Singleton
    fun updateSettingsStore(preferences: DataStore<Preferences>): UpdateSettingsStore =
        UpdateSettingsStore(preferences)

    @Provides
    @Singleton
    fun githubReleasesClient(httpTextClient: HttpTextClient): GithubReleasesClient =
        GithubReleasesClient(httpTextClient)

    @Provides
    @Singleton
    fun updateChecker(client: GithubReleasesClient): UpdateChecker = UpdateChecker(client)

    @Provides
    @Singleton
    fun apkDownloader(okHttpClient: OkHttpClient): ApkDownloader = ApkDownloader(okHttpClient)

    @Provides
    @Singleton
    fun settingsBackupService(
        sourceRepository: SourceRepository,
        playerSettingsStore: PlayerSettingsStore,
        epgGuideRepository: EpgGuideRepository,
        favoritesStore: FavoritesStore,
        updateSettingsStore: UpdateSettingsStore,
    ): SettingsBackupService = SettingsBackupService(
        sourceRepository = sourceRepository,
        playerSettingsStore = playerSettingsStore,
        epgGuideRepository = epgGuideRepository,
        favoritesStore = favoritesStore,
        updateSettingsStore = updateSettingsStore,
    )

    @Provides
    @Singleton
    fun catalogStore(database: SmotrishkaDatabase): CatalogStore = CatalogStore(database, CatalogMapper())

    @Provides
    @Singleton
    fun epgStore(database: SmotrishkaDatabase): EpgStore = EpgStore(database, EpgMapper())

    @Provides
    @Singleton
    fun httpTextClient(okHttpClient: OkHttpClient): HttpTextClient = HttpTextClient(okHttpClient)

    @Provides
    @Singleton
    fun httpBytesClient(okHttpClient: OkHttpClient): HttpBytesClient = HttpBytesClient(okHttpClient)

    @Provides
    fun m3uParser(): M3uParser = M3uParser()

    @Provides
    fun xmltvParser(): XmltvParser = XmltvParser()

    @Provides
    @Singleton
    fun iedemPlaylistLoader(httpTextClient: HttpTextClient, m3uParser: M3uParser): IedemPlaylistLoader =
        IedemPlaylistLoader(httpTextClient, IedemProvider(), m3uParser)

    @Provides
    @Singleton
    fun playlistSourceLoader(
        httpTextClient: HttpTextClient,
        iedemPlaylistLoader: IedemPlaylistLoader,
        playlistFileSource: PlaylistFileSource,
        m3uParser: M3uParser,
    ): PlaylistSourceLoader =
        PlaylistSourceLoader(httpTextClient, iedemPlaylistLoader, playlistFileSource, m3uParser)

    @Provides
    @Singleton
    fun epgLoader(httpBytesClient: HttpBytesClient, xmltvParser: XmltvParser): EpgLoader =
        EpgLoader(httpBytesClient, xmltvParser)

    @Provides
    @Singleton
    fun catalogUpdater(
        catalogStore: CatalogStore,
        epgStore: EpgStore,
        playlistSourceLoader: PlaylistSourceLoader,
        epgLoader: EpgLoader,
        playerSettingsStore: PlayerSettingsStore,
        httpClient: OkHttpClient,
        epgGuideRepository: EpgGuideRepository,
        epgGuideStateStore: EpgGuideStateStore,
        clock: java.time.Clock,
    ): CatalogUpdater = CatalogUpdater(
        catalogStore = catalogStore,
        epgStore = epgStore,
        playlistLoader = playlistSourceLoader,
        epgLoader = epgLoader,
        settings = playerSettingsStore,
        clock = clock,
        guideRepository = epgGuideRepository,
        guideState = epgGuideStateStore,
        // Гиды огромные, поэтому дописываются потоком, а не целиком в память.
        streamImporter = EpgStreamImporter(
            client = HttpBytesClient(httpClient),
            store = epgStore,
            clock = clock,
        ),
    )

    @Provides
    @Singleton
    fun sourceRepository(catalogStore: CatalogStore, secretStore: SecretStore): SourceRepository =
        SourceRepository(catalogStore, secretStore)

    @Provides
    @Singleton
    fun playlistFileSource(
        @ApplicationContext context: Context,
        m3uParser: M3uParser,
    ): PlaylistFileSource = PlaylistFileSource(context.contentResolver, m3uParser)

    @Provides
    @Singleton
    fun updateScheduler(@ApplicationContext context: Context): UpdateScheduler =
        UpdateScheduler(WorkManager.getInstance(context))

    @Provides
    fun clock(): java.time.Clock = java.time.Clock.systemUTC()

    /** Лента «Сейчас идёт» на домашнем экране Android TV. */
    @Provides
    @Singleton
    fun watchNextSync(@ApplicationContext context: Context): WatchNextSync =
        TvProviderWatchNextSync(context)

    @Provides
    @Singleton
    fun epgMatcher(): com.mops1k.smotrishka.tv.core.playlist.EpgMatcher =
        com.mops1k.smotrishka.tv.core.playlist.EpgMatcher()
}
