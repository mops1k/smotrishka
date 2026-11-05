package com.mops1k.smotrishka.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

/**
 * TDD-спецификация потокового импорта публичного гида.
 *
 * Публичные гиды живут в другом пространстве идентификаторов (epg.one — числа,
 * iptvx.one — слаги), поэтому канал гида разрешается по каталогу: сначала по `tvg-id`,
 * затем по имени. Передачи чужих каналов не пишутся в базу — иначе она раздувается.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgStreamImporterTest {

    private lateinit var server: MockWebServer
    private lateinit var database: SmotrishkaDatabase
    private lateinit var store: EpgStore
    private lateinit var importer: EpgStreamImporter

    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:30:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        store = EpgStore(database)
        importer = EpgStreamImporter(
            client = HttpBytesClient(OkHttpClient()),
            store = store,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
    }

    private fun gzip(text: String): Buffer =
        Buffer().write(
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.toByteArray(),
        )

    private fun channel(id: String, name: String, epgId: String? = null) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.test/$id.m3u8",
        epgId = epgId,
    )

    private fun guide(channels: List<String>, programmes: List<String>): String = buildString {
        append("<tv>\n")
        channels.forEach { append(it) }
        programmes.forEach { append(it) }
        append("</tv>\n")
    }

    private val newsBySlug = """
        <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="slug-one">
          <title>Новости</title>
        </programme>
    """.trimIndent()

    @Test
    fun `совпадение по epgId добавляет передачу`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf(
                            """<channel id="9052"><display-name>Другое имя</display-name></channel>""",
                        ),
                        programmes = listOf(
                            """<programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="9052"><title>Новости</title></programme>""",
                        ),
                    ),
                ),
            ),
        )

        val result = importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Первый", epgId = "9052")),
        )

        assertThat(result).isEqualTo(EpgStreamResult.Success(1))
        assertThat(store.epgChannels().single().id).isEqualTo("9052")
    }

    @Test
    fun `совпадение по имени добавляет передачу`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf("""<channel id="slug-one"><display-name>Первый</display-name></channel>"""),
                        programmes = listOf(newsBySlug),
                    ),
                ),
            ),
        )

        val result = importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Первый", epgId = "9052")),
        )

        assertThat(result).isEqualTo(EpgStreamResult.Success(1))
        assertThat(store.epgChannels().single().id).isEqualTo("slug-one")
    }

    @Test
    fun `неоднозначное имя не привязывается`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf(
                            """<channel id="a"><display-name>Кино</display-name></channel>""",
                            """<channel id="b"><display-name>Кино</display-name></channel>""",
                        ),
                        programmes = listOf(
                            """<programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="a"><title>Фильм</title></programme>""",
                        ),
                    ),
                ),
            ),
        )

        val result = importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Кино")),
        )

        assertThat(result).isEqualTo(EpgStreamResult.Success(0))
        assertThat(store.epgChannels()).isEmpty()
    }

    @Test
    fun `передачи вне окна не добавляются`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf("""<channel id="9052"><display-name>Первый</display-name></channel>"""),
                        programmes = listOf(
                            """<programme start="20261001000000 +0000" stop="20261001010000 +0000" channel="9052"><title>Старая</title></programme>""",
                        ),
                    ),
                ),
            ),
        )

        val result = importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Первый", epgId = "9052")),
        )

        assertThat(result).isEqualTo(EpgStreamResult.Success(0))
    }

    @Test
    fun `режим замены стирает прежнюю программу провайдера`() = runTest {
        // Программа прошлого обновления: при замене она должна исчезнуть, а не смешаться с новой.
        store.replace(
            Epg(
                channels = listOf(EpgChannel(id = "old", displayName = "Старый канал")),
                programmes = listOf(
                    Programme(
                        channelId = "old",
                        title = "Старая передача",
                        start = clock.instant().minusSeconds(3600),
                        end = clock.instant().minusSeconds(1800),
                    ),
                ),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf("""<channel id="9052"><display-name>Первый</display-name></channel>"""),
                        programmes = listOf(
                            """<programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="9052"><title>Новости</title></programme>""",
                        ),
                    ),
                ),
            ),
        )

        val result = importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Первый", epgId = "9052")),
            replace = true,
        )

        assertThat(result).isEqualTo(EpgStreamResult.Success(1))
        assertThat(store.epgChannels().map { it.id }).containsExactly("9052")
        val from = clock.instant().minusSeconds(7200)
        val to = clock.instant().plusSeconds(7200)
        assertThat(store.programmes(channelId = "9052", from = from, to = to)).hasSize(1)
        assertThat(store.programmes(channelId = "old", from = from, to = to)).isEmpty()
    }

    @Test
    fun `без замены прежняя программа остаётся`() = runTest {
        store.replace(
            Epg(
                channels = listOf(EpgChannel(id = "old", displayName = "Старый канал")),
                programmes = listOf(
                    Programme(
                        channelId = "old",
                        title = "Старая передача",
                        start = clock.instant().minusSeconds(3600),
                        end = clock.instant().minusSeconds(1800),
                    ),
                ),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                gzip(
                    guide(
                        channels = listOf("""<channel id="9052"><display-name>Первый</display-name></channel>"""),
                        programmes = listOf(
                            """<programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="9052"><title>Новости</title></programme>""",
                        ),
                    ),
                ),
            ),
        )

        importer.import(
            url = server.url("/epg.xml.gz").toString(),
            backHours = 24,
            forwardHours = 48,
            catalogChannels = listOf(channel(id = "1", name = "Первый", epgId = "9052")),
        )

        // Пользовательские гиды дополняют программу, поэтому без замены старое остаётся.
        assertThat(store.epgChannels().map { it.id }).containsExactly("old", "9052")
    }
}
