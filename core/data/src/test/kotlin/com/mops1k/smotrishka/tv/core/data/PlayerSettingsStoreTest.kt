package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TDD-спецификация настройки буфера плеера: значение по умолчанию, сохранение и границы.
 */
class PlayerSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: PlayerSettingsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PlayerSettingsStore(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tempFolder.root, "player.preferences_pb")
            },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `по умолчанию буфер пять секунд`() = runTest {
        assertThat(store.bufferSeconds.first()).isEqualTo(5)
    }

    @Test
    fun `сохранённое значение читается`() = runTest {
        store.setBufferSeconds(10)

        assertThat(store.bufferSeconds.first()).isEqualTo(10)
    }

    @Test
    fun `значение вне диапазона приводится к допустимому`() = runTest {
        store.setBufferSeconds(999)

        assertThat(store.bufferSeconds.first()).isEqualTo(PlayerSettingsStore.MAX_BUFFER_SECONDS)
    }

    @Test
    fun `качество по умолчанию автоматическое`() = runTest {
        assertThat(store.videoQuality.first()).isEqualTo(VideoQuality.AUTO)
    }

    @Test
    fun `выбранное качество сохраняется`() = runTest {
        store.setVideoQuality(VideoQuality.HD)

        assertThat(store.videoQuality.first()).isEqualTo(VideoQuality.HD)
    }

    @Test
    fun `потолок паузы восстановления по умолчанию десять секунд`() = runTest {
        assertThat(store.recoveryDelaySeconds.first()).isEqualTo(10)
    }

    @Test
    fun `выбранный потолок паузы сохраняется`() = runTest {
        store.setRecoveryDelaySeconds(15)

        assertThat(store.recoveryDelaySeconds.first()).isEqualTo(15)
    }

    @Test
    fun `потолок паузы приводится к краям списка`() = runTest {
        store.setRecoveryDelaySeconds(1)

        assertThat(store.recoveryDelaySeconds.first()).isEqualTo(5)
    }

    @Test
    fun `окно epg по умолчанию сутки`() = runTest {
        assertThat(store.epgWindowHours.first()).isEqualTo(24)
    }

    @Test
    fun `выбранное окно epg сохраняется`() = runTest {
        store.setEpgWindowHours(12)

        assertThat(store.epgWindowHours.first()).isEqualTo(12)
    }

    @Test
    fun `экономия трафика по умолчанию выключена`() = runTest {
        assertThat(store.dataSaver.first()).isFalse()
    }

    @Test
    fun `экономия трафика включается`() = runTest {
        store.setDataSaver(true)

        assertThat(store.dataSaver.first()).isTrue()
    }

    @Test
    fun `язык звука по умолчанию русский`() = runTest {
        assertThat(store.audioLanguage.first()).isEqualTo(AudioLanguage.RUSSIAN)
    }

    @Test
    fun `выбранный язык звука сохраняется`() = runTest {
        store.setAudioLanguage(AudioLanguage.ENGLISH)

        assertThat(store.audioLanguage.first()).isEqualTo(AudioLanguage.ENGLISH)
    }

    @Test
    fun `окно epg предлагает шаг в сутки до семи суток`() {
        assertThat(PlayerSettingsStore.EPG_WINDOW_CHOICES)
            .containsExactly(12, 24, 48, 72, 96, 120, 144, 168)
            .inOrder()
    }

    @Test
    fun `окно epg сохраняется во всю глубину до семи суток`() = runTest {
        store.setEpgWindowHours(168)

        assertThat(store.epgWindowHours.first()).isEqualTo(168)
    }

    @Test
    fun `окно epg ограничено краями списка`() = runTest {
        store.setEpgWindowHours(500)
        assertThat(store.epgWindowHours.first()).isEqualTo(168)

        store.setEpgWindowHours(1)
        assertThat(store.epgWindowHours.first()).isEqualTo(12)
    }

    @Test
    fun `глубина архива по умолчанию трое суток`() = runTest {
        assertThat(store.archiveDepthHours.first()).isEqualTo(72)
    }

    @Test
    fun `глубина архива предлагает шаг в сутки до семи суток`() {
        assertThat(PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES)
            .containsExactly(24, 48, 72, 96, 120, 144, 168)
            .inOrder()
    }

    @Test
    fun `глубина архива в семь суток сохраняется`() = runTest {
        store.setArchiveDepthHours(168)

        assertThat(store.archiveDepthHours.first()).isEqualTo(168)
    }

    @Test
    fun `глубина архива ограничена краями списка`() = runTest {
        store.setArchiveDepthHours(1000)
        assertThat(store.archiveDepthHours.first()).isEqualTo(168)

        store.setArchiveDepthHours(1)
        assertThat(store.archiveDepthHours.first()).isEqualTo(24)
    }

    @Test
    fun `общее смещение гида по умолчанию нулевое`() = runTest {
        // Молча сдвигать сетку нельзя: смещение по поясу ставит приложение один раз при старте.
        assertThat(store.globalEpgShift.first()).isEqualTo(0)
    }

    @Test
    fun `авто-смещение по поясу ставится один раз и не затирает ручной выбор`() = runTest {
        store.applyAutoEpgShiftOnce(hours = -3)
        assertThat(store.globalEpgShift.first()).isEqualTo(-3)

        // Второй запуск не должен ничего менять или сдвигать снова.
        store.applyAutoEpgShiftOnce(hours = -5)
        assertThat(store.globalEpgShift.first()).isEqualTo(-3)

        // Ручной выбор важнее авто-подстановки.
        store.setGlobalEpgShift(2)
        store.applyAutoEpgShiftOnce(hours = -3)
        assertThat(store.globalEpgShift.first()).isEqualTo(2)
    }

    @Test
    fun `общее смещение гида сохраняется и ограничивается`() = runTest {
        store.setGlobalEpgShift(3)
        assertThat(store.globalEpgShift.first()).isEqualTo(3)

        // Предел тот же, что у поканального сдвига: больше провайдеры не врут.
        store.setGlobalEpgShift(99)
        assertThat(store.globalEpgShift.first()).isEqualTo(PlayerSettingsStore.MAX_EPG_SHIFT_HOURS)

        store.setGlobalEpgShift(-3)
        assertThat(store.globalEpgShift.first()).isEqualTo(-3)

        // Явно выбранный ноль важнее автоматического значения: это решение пользователя.
        store.setGlobalEpgShift(0)
        assertThat(store.globalEpgShift.first()).isEqualTo(0)
    }

    @Test
    fun `лента на домашнем экране включена по умолчанию`() = runTest {
        assertThat(store.watchNextEnabled.first()).isTrue()
    }

    @Test
    fun `выключение ленты на домашнем экране сохраняется`() = runTest {
        store.setWatchNextEnabled(false)

        assertThat(store.watchNextEnabled.first()).isFalse()
    }

    @Test
    fun `количество карточек ленты по умолчанию десять`() = runTest {
        assertThat(store.watchNextLimit.first()).isEqualTo(10)
    }

    @Test
    fun `выбранное количество карточек ленты сохраняется`() = runTest {
        store.setWatchNextLimit(15)

        assertThat(store.watchNextLimit.first()).isEqualTo(15)
    }

    @Test
    fun `количество карточек ленты ограничено краями списка`() = runTest {
        store.setWatchNextLimit(1000)
        assertThat(store.watchNextLimit.first()).isEqualTo(20)

        store.setWatchNextLimit(1)
        assertThat(store.watchNextLimit.first()).isEqualTo(5)
    }
}
