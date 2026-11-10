package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * TDD-спецификация провайдера программы: выбор гида, «что идёт сейчас» для списков каналов
 * и передача на момент (архив).
 *
 * Подмена на одноимённый канал другого гида допустима, только если сетки за прошедший
 * период совпадают: иначе пользователь увидел бы программу чужого канала.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SmotrishkaDatabase
    private lateinit var store: EpgStore
    private lateinit var settingsStore: PlayerSettingsStore
    private lateinit var settingsScope: CoroutineScope
    private lateinit var provider: EpgProvider

    private val now: Instant = Instant.parse("2026-11-09T20:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        store = EpgStore(database)
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        settingsStore = PlayerSettingsStore(
            PreferenceDataStoreFactory.create(scope = settingsScope) {
                File(tempFolder.root, "epg.preferences_pb")
            },
        )
        provider = EpgProvider(
            channelEpgStore = ChannelEpgStore(
                dao = database.channelEpgDao(),
                epgStore = store,
                epgMatcher = EpgMatcher(),
            ),
            epgStore = store,
            settingsStore = settingsStore,
        )
    }

    @After
    fun tearDown() {
        settingsScope.cancel()
        database.close()
    }

    private fun channel(name: String, epgId: String?) = Channel(
        id = ChannelId("catalog"),
        name = name,
        streamUrl = "http://example.test/catalog.m3u8",
        epgId = epgId,
    )

    /** Пишет канал гида и сетку передач: `start` — часы от текущего момента. */
    private suspend fun guide(id: String, name: String, vararg hours: Long) =
        guideTitled(id = id, name = name, title = "Передача", *hours)

    /** Пишет канал гида и сетку передач с заданным названием передачи. */
    private suspend fun guideTitled(id: String, name: String, title: String, vararg hours: Long) {
        store.merge(
            Epg(
                channels = listOf(EpgChannel(id = id, displayName = name)),
                programmes = hours.map { hour ->
                    Programme(
                        channelId = id,
                        title = title,
                        start = now.plusSeconds(hour * 3600),
                        end = now.plusSeconds(hour * 3600 + 3000),
                    )
                },
            ),
        )
    }

    @Test
    fun `переставший обновляться гид уступает свежему с той же сеткой`() = runTest {
        // Привязанный гид: передачи только в прошлом, сетка та же, что у свежего.
        guide(id = "stale", name = "4Ever Drama HD UA", -24, -22)
        // Свежий гид: та же сетка в прошлом и передача сейчас.
        guide(id = "fresh", name = "4ever Drama HD", -24, -22, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("fresh")
    }

    @Test
    fun `подмена не делается, если сетки расходятся`() = runTest {
        guide(id = "stale", name = "4Ever Drama HD UA", -24, -22)
        guide(id = "other", name = "4ever Drama HD", -30, -28, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("stale")
    }

    @Test
    fun `свежий привязанный гид остаётся без подмены`() = runTest {
        guide(id = "stale", name = "4Ever Drama HD UA", -24, 0)
        guide(id = "fresh", name = "4ever Drama HD", -24, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("stale")
    }

    @Test
    fun `заглушка уступает гиду с настоящей передачей сейчас`() = runTest {
        // Привязанный гид пуст, у двух одноимённых: в одном заглушка, в другом — фильм.
        guideTitled(id = "filler", name = "4ever Drama HD", title = "Програма відсутня", 0)
        guideTitled(id = "real", name = "4Ever Drama UA", title = "Фільм", 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "missing")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("real")
    }

    @Test
    fun `если настоящей передачи нет нигде, гид остаётся`() = runTest {
        guideTitled(id = "filler", name = "4ever Drama HD", title = "Програма відсутня", 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "missing")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("filler")
    }

    @Test
    fun `текущая передача канала с общим сдвигом минус три часа попадает в выборку`() = runTest {
        // Гид отстаёт на три часа: «сейчас» для него — момент now + 3 ч.
        guideTitled(id = "g", name = "Канал", title = "Юрский период", 3)
        settingsStore.setGlobalEpgShift(-3)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Канал", epgId = "g")),
            now = now,
        )

        val current = programmes[ChannelId("c")]
        assertThat(current?.title).isEqualTo("Юрский период")
        // Границы показываются со сдвигом: человек видит их по своему времени.
        assertThat(current?.start).isEqualTo(now)
        assertThat(current?.end).isEqualTo(now.plusSeconds(3000))
    }

    @Test
    fun `текущая передача канала с общим сдвигом плюс три часа попадает в выборку`() = runTest {
        // Гид спешит на три часа: передача идёт в момент now − 3 ч по времени гида.
        guideTitled(id = "g", name = "Канал", title = "Фильм", -3)
        settingsStore.setGlobalEpgShift(3)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Канал", epgId = "g")),
            now = now,
        )

        assertThat(programmes[ChannelId("c")]?.title).isEqualTo("Фильм")
    }

    @Test
    fun `свой сдвиг канала важнее общего`() = runTest {
        guideTitled(id = "g", name = "Канал", title = "Своя сетка", 2)
        settingsStore.setGlobalEpgShift(-5)
        settingsStore.setEpgShift(channelId = "c", hours = -2)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Канал", epgId = "g")),
            now = now,
        )

        assertThat(programmes[ChannelId("c")]?.title).isEqualTo("Своя сетка")
    }

    @Test
    fun `передачи считаются только по запрошенным каналам`() = runTest {
        guideTitled(id = "a", name = "Первый", title = "Передача А", 0)
        guideTitled(id = "b", name = "Второй", title = "Передача Б", 0)
        guideTitled(id = "extra", name = "Третий", title = "Передача В", 0)

        val programmes = provider.current(
            channels = listOf(
                channel(id = "a", name = "Первый", epgId = "a"),
                channel(id = "b", name = "Второй", epgId = "b"),
            ),
            now = now,
        )

        assertThat(programmes.keys).containsExactly(ChannelId("a"), ChannelId("b"))
        assertThat(programmes[ChannelId("a")]?.title).isEqualTo("Передача А")
    }

    @Test
    fun `пустая выборка гида не выдумывает передачу`() = runTest {
        guideTitled(id = "g", name = "Канал", title = "Далёкая передача", 30)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Канал", epgId = "g")),
            now = now,
        )

        assertThat(programmes).isEmpty()
    }

    @Test
    fun `без связи с гидом используется epgId канала`() = runTest {
        guideTitled(id = "g", name = "Гид", title = "Передача по epgId", 0)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Совсем другое имя", epgId = "g")),
            now = now,
        )

        assertThat(programmes[ChannelId("c")]?.title).isEqualTo("Передача по epgId")
    }

    @Test
    fun `следующая передача отдаётся вместе с текущей`() = runTest {
        guideTitled(id = "g", name = "Канал", title = "Сейчас", 0)
        guideTitled(id = "g", name = "Канал", title = "Дальше", 2)

        val programmes = provider.current(
            channels = listOf(channel(id = "c", name = "Канал", epgId = "g")),
            now = now,
        )

        assertThat(programmes[ChannelId("c")]?.title).isEqualTo("Сейчас")
        assertThat(programmes[ChannelId("c")]?.nextTitle).isEqualTo("Дальше")
    }

    @Test
    fun `архивная передача ищется по сетке гида со сдвигом`() = runTest {
        // В сетке гида передача стоит на now − 5 ч, у канала сдвиг −3 ч: в показываемом
        // времени она идёт на now − 8 ч. Раньше архив искался без поправки на сдвиг,
        // и в шапке плеера оказывалось другое название (замечание Александра 11.11.2026).
        guideTitled(id = "g", name = "Канал", title = "Архивная", -5)
        settingsStore.setGlobalEpgShift(-3)
        val shownStart = now.minusSeconds(8 * 3600)

        val found = provider.at(
            channel = channel(id = "c", name = "Канал", epgId = "g"),
            moment = shownStart.plusSeconds(60),
        )

        assertThat(found?.title).isEqualTo("Архивная")
        assertThat(found?.start).isEqualTo(shownStart)
        assertThat(found?.end).isEqualTo(shownStart.plusSeconds(3000))
    }

    @Test
    fun `полная программа канала отдаётся со сдвигом гида`() = runTest {
        guideTitled(id = "g", name = "Канал", title = "Передача со сдвигом", 3)
        settingsStore.setGlobalEpgShift(-3)

        val schedule = provider.schedule(
            channel = channel(id = "c", name = "Канал", epgId = "g"),
            from = now.minusSeconds(3600),
            to = now.plusSeconds(6 * 3600),
            now = now,
        )

        assertThat(schedule.map { it.title }).contains("Передача со сдвигом")
        assertThat(schedule.single().start).isEqualTo(now)
    }

    /** Канал каталога с заданным идентификатором: для проверок отбора и сдвигов. */
    private fun channel(id: String, name: String, epgId: String?) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.test/$id.m3u8",
        epgId = epgId,
    )

    @Test
    fun `играющий момент в архиве — начало передачи, в эфире — сейчас`() {
        val archiveStart = now.minusSeconds(7200)

        assertThat(provider.playbackMoment(archiveStart.epochSecond, now)).isEqualTo(archiveStart)
        assertThat(provider.playbackMoment(null, now)).isEqualTo(now)
        assertThat(provider.playbackMoment(-1L, now)).isEqualTo(now)
    }
}
