package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

/**
 * TDD-спецификация обновления данных: источник → каталог → программа передач → база.
 *
 * Ключевые требования: сбой программы передач не стирает каталог; гиды пользователя
 * дополняют программу и тогда, когда у плейлиста нет своего адреса гида; один и тот же
 * гид не скачивается дважды за обновление, а покрытый каталог не тянет остальные гиды.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogUpdaterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var database: SmotrishkaDatabase
    private lateinit var updater: CatalogUpdater
    private lateinit var catalogStore: CatalogStore
    private lateinit var epgStore: EpgStore
    private lateinit var guideRepository: EpgGuideRepository
    private lateinit var guideState: EpgGuideStateStore
    private lateinit var settingsStore: PlayerSettingsStore
    private lateinit var scope: CoroutineScope

    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:30:00Z"), ZoneOffset.UTC)

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="one" group-title="Основные",Первый
        ${'$'}{SERVER}/one.m3u8
        #EXTINF:-1 tvg-id="two" group-title="Кино",Второй
        ${'$'}{SERVER}/two.m3u8
    """.trimIndent()

    private val providerXmltv = """
        <tv>
          <channel id="one"><display-name>Первый</display-name></channel>
          <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
            <title>Новости</title>
          </programme>
        </tv>
    """.trimIndent()

    private val fullCoverageXmltv = """
        <tv>
          <channel id="one"><display-name>Первый</display-name></channel>
          <channel id="two"><display-name>Второй</display-name></channel>
          <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
            <title>Новости</title>
          </programme>
          <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="two">
            <title>Фильм</title>
          </programme>
        </tv>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        catalogStore = CatalogStore(database)
        epgStore = EpgStore(database)
        guideRepository = EpgGuideRepository(database, clock)
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "settings.preferences_pb")
        }
        guideState = EpgGuideStateStore(dataStore)
        settingsStore = PlayerSettingsStore(dataStore)
        val httpText = HttpTextClient(OkHttpClient())
        updater = CatalogUpdater(
            catalogStore = catalogStore,
            epgStore = epgStore,
            playlistLoader = PlaylistSourceLoader(
                httpText = httpText,
                iedem = IedemPlaylistLoader(httpText),
                fileReader = PlaylistFileReader { M3uParseResult.Failure("файл не читается в тесте") },
            ),
            epgLoader = EpgLoader(HttpBytesClient(OkHttpClient())),
            settings = settingsStore,
            // Время фиксировано: окно программы передач (24 ч назад / 48 ч вперёд) должно
            // накрывать передачи из фикстуры, иначе тест зависит от дня запуска.
            clock = clock,
            guideRepository = guideRepository,
            streamImporter = EpgStreamImporter(
                client = HttpBytesClient(OkHttpClient()),
                store = epgStore,
                clock = clock,
            ),
            guideState = guideState,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
        database.close()
    }

    private fun gzip(text: String): Buffer =
        Buffer().write(
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.toByteArray(),
        )

    /** Плейлист с подставленным хостом и, если нужно, адресом гида. */
    private fun playlistBody(base: String, epgUrl: String? = null): String {
        val header = if (epgUrl == null) "#EXTM3U" else """#EXTM3U url-tvg="$epgUrl""""
        return playlist.replace("\${SERVER}", base).replace("#EXTM3U", header)
    }

    @Test
    fun `обновляет каталог и программу передач`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(providerXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Success::class.java)
        val saved = result as UpdateResult.Success
        assertThat(saved.channels).isEqualTo(2)
        assertThat(saved.programmes).isEqualTo(1)
        assertThat(catalogStore.playlist().channels.map { it.name }).containsExactly("Первый", "Второй")
    }

    @Test
    fun `сбой программы передач не стирает каталог`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Partial::class.java)
        assertThat(catalogStore.playlist().channels).hasSize(2)
        assertThat((result as UpdateResult.Partial).reason).contains("500")
    }

    @Test
    fun `сбой плейлиста оставляет прежний каталог нетронутым`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(403))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Failure::class.java)
        assertThat(catalogStore.playlist().channels).isEmpty()
    }

    @Test
    fun `плейлист без EPG даёт частичный результат`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(playlistBody(base = base)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Partial::class.java)
        assertThat((result as UpdateResult.Partial).reason).contains("программ")
    }

    @Test
    fun `гиды пользователя дополняют программу без гида провайдера`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        guideRepository.add(url = "$base/guide.xml.gz", name = "Мой гид")
        server.enqueue(MockResponse().setBody(playlistBody(base = base)))
        server.enqueue(MockResponse().setBody(gzip(providerXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Partial::class.java)
        assertThat((result as UpdateResult.Partial).programmes).isEqualTo(1)
        assertThat(epgStore.coveredChannels(clock.instant().minusSeconds(3600), clock.instant().plusSeconds(7200)))
            .isEqualTo(1)
    }

    @Test
    fun `недоступный гид пользователя не мешает обновлению`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        guideRepository.add(url = "$base/guide.xml.gz", name = "Мёртвый гид")
        server.enqueue(MockResponse().setBody(playlistBody(base = base)))
        server.enqueue(MockResponse().setResponseCode(404))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Partial::class.java)
        assertThat(catalogStore.playlist().channels).hasSize(2)
    }

    @Test
    fun `гид с тем же хостом, что у гида провайдера, не качается повторно`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        guideRepository.add(url = "$base/guide.xml.gz", name = "Тот же хост")
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/provider.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(providerXmltv)))

        updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `покрытый каталог не тянет остальные гиды`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        val other = MockWebServer()
        other.start()
        guideRepository.add(
            // Другой хост: иначе гид пропустился бы по дедупликации, а проверяем порог покрытия.
            url = other.url("/guide.xml.gz").toString().replace("localhost", "127.0.0.1"),
            name = "Лишний гид",
        )
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/provider.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(fullCoverageXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Success::class.java)
        assertThat(other.requestCount).isEqualTo(0)
        other.shutdown()
    }

    @Test
    fun `недавно загруженный гид не качается повторно`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        guideRepository.add(url = "$base/guide.xml.gz", name = "Мой гид")
        repeat(2) {
            server.enqueue(MockResponse().setBody(playlistBody(base = base)))
            server.enqueue(MockResponse().setBody(gzip(providerXmltv)))
            updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))
        }

        // Плейлист дважды + гид один раз: второй раз гид ещё «свежий».
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun `глубина архива сохраняет старые передачи при обновлении`() = runTest {
        settingsStore.setArchiveDepthHours(168)
        val base = server.url("/").toString().trimEnd('/')
        val deepXmltv = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261029083000 +0000" stop="20261029093000 +0000" channel="one">
                <title>Старая передача</title>
              </programme>
            </tv>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(deepXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Success::class.java)
        assertThat((result as UpdateResult.Success).programmes).isEqualTo(1)
        // Передача ~100 часов назад: при глубине 168 часов обрезка её не трогает.
        val covered = epgStore.coveredChannels(
            clock.instant().minusSeconds(168 * 3600),
            clock.instant(),
        )
        assertThat(covered).isEqualTo(1)
    }

    @Test
    fun `передачи дальше 48 часов вперёд не загружаются`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        val farXmltv = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261104090000 +0000" stop="20261104100000 +0000" channel="one">
                <title>Близкая</title>
              </programme>
              <programme start="20261108120000 +0000" stop="20261108130000 +0000" channel="one">
                <title>Далёкая</title>
              </programme>
            </tv>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(farXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Success::class.java)
        assertThat((result as UpdateResult.Success).programmes).isEqualTo(1)
    }

    @Test
    fun `updateCatalogOnly заменяет каталог и не ходит на EPG-адрес`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        // В очереди только плейлист: если код пойдёт за программой передач, запрос будет не отвечен
        // и счётчик запросов это покажет.
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))

        val result = updater.updateCatalogOnly(
            source = PlaylistSource.M3uUrl(url = "$base/list.m3u"),
        )

        assertThat(result).isInstanceOf(CatalogUpdateResult.Success::class.java)
        val saved = result as CatalogUpdateResult.Success
        assertThat(saved.channels).isEqualTo(2)
        assertThat(saved.categories).isEqualTo(2)
        assertThat(saved.epgUrl).isEqualTo("$base/epg.xml.gz")
        assertThat(catalogStore.playlist().channels.map { it.name })
            .containsExactly("Первый", "Второй")
        assertThat(server.requestCount).isEqualTo(1)
        // Программа передач не грузилась: хранилище осталось пустым.
        assertThat(
            epgStore.coveredChannels(
                clock.instant().minusSeconds(7200),
                clock.instant().plusSeconds(7200),
            ),
        ).isEqualTo(0)
    }

    @Test
    fun `ошибка каталога даёт Failure и не портит прежний каталог`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(playlistBody(base = base)))
        updater.updateCatalogOnly(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))
        assertThat(catalogStore.playlist().channels).hasSize(2)

        server.enqueue(MockResponse().setResponseCode(500))

        val result = updater.updateCatalogOnly(
            source = PlaylistSource.M3uUrl(url = "$base/list.m3u"),
        )

        assertThat(result).isInstanceOf(CatalogUpdateResult.Failure::class.java)
        assertThat((result as CatalogUpdateResult.Failure).reason).contains("500")
        // Прежний каталог на месте: пользователь не остаётся без каналов из-за сбоя обновления.
        assertThat(catalogStore.playlist().channels.map { it.name })
            .containsExactly("Первый", "Второй")
    }

    @Test
    fun `обновление гида не стирает прежнюю программу`() = runTest {
        // Программа прошлого обновления: пока новый гид пишется пачками, она должна оставаться —
        // иначе у играющего канала в OSD пропадает передача (видно было на устройстве).
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Прошлая передача",
                        start = clock.instant().minusSeconds(3 * 3600),
                        end = clock.instant().minusSeconds(2 * 3600),
                    ),
                ),
            ),
        )
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody(playlistBody(base = base, epgUrl = "$base/epg.xml.gz")))
        server.enqueue(MockResponse().setBody(gzip(providerXmltv)))

        val result = updater.update(source = PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        assertThat(result).isInstanceOf(UpdateResult.Success::class.java)
        val from = clock.instant().minusSeconds(6 * 3600)
        val to = clock.instant().plusSeconds(6 * 3600)
        val titles = epgStore.programmes(channelId = "one", from = from, to = to).map { it.title }
        assertThat(titles).contains("Прошлая передача")
        assertThat(titles).contains("Новости")
    }
}
