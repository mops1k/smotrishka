package com.mops1k.smotrishka.tv.ui.settings

import android.util.Log
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Импорт плейлиста из файла в фоне, независимо от экрана настроек.
 *
 * Работа запускается в собственном scope: если пользователь уйдёт с экрана настроек,
 * `viewModelScope` отменится, и раньше вместе с ним обрывалась загрузка программы передач —
 * каналы появлялись, а программы оставались пустыми.
 */
@Singleton
class PlaylistImporter @Inject constructor(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val parser: M3uParser,
    private val catalogUpdater: CatalogUpdater,
    private val settings: PlayerSettingsStore,
    private val sourceRepository: com.mops1k.smotrishka.tv.core.data.SourceRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<String?>(null)

    /** Сообщение о результате импорта для строки состояния. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _running = MutableStateFlow(false)

    /** Идёт ли импорт сейчас. */
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /**
     * Запускает импорт плейлиста из текста файла.
     *
     * @param text содержимое файла M3U;
     * @param fileName имя файла для подписи;
     * @param uri адрес файла (SAF) — сохраняется как источник, чтобы «Обновить» перечитывало файл.
     */
    fun import(text: String, fileName: String? = null, uri: String? = null) {
        if (_running.value) return
        scope.launch {
            _running.value = true
            try {
                when (val parsed = parser.parse(text)) {
                    is M3uParseResult.Failure -> _status.value = "Файл не разобран: ${parsed.reason}"

                    is M3uParseResult.Success -> {
                        if (parsed.playlist.channels.isEmpty()) {
                            _status.value = "В файле нет каналов"
                        } else {
                            apply(parsed.playlist, fileName, uri)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmotrishkaSync", "import failed", e)
                _status.value = "Импорт не удался: ${e.message ?: "ошибка"}"
            } finally {
                _running.value = false
            }
        }
    }

    private suspend fun apply(
        playlist: com.mops1k.smotrishka.tv.core.model.Playlist,
        fileName: String?,
        uri: String?,
    ) {
        Log.d(
            "SmotrishkaSync",
            "import file: каналов=${playlist.channels.size} " +
                "первые=${playlist.channels.take(3).map { it.name }} категорий=${playlist.categories.size}",
        )
        catalogStore.replaceCatalog(playlist)
        settings.setFileSource(fileName ?: "imported")
        // Файл становится активным источником: «Обновить» в настройках перечитает его по URI,
        // а не старый провайдерский источник.
        if (uri != null) {
            sourceRepository.save(
                com.mops1k.smotrishka.tv.core.model.PlaylistSource.File(uri = uri, name = fileName),
                fileName ?: "Плейлист из файла",
            )
        }
        // Идентификаторы каналов после импорта другие — прежние отметки об архиве к ним
        // не относятся, иначе значки архива пропадают у всех каналов.
        settings.saveArchiveVerified(emptySet(), 0L)

        // Программу не чистим до загрузки новой: гид провайдера большой (46 МБ gzip),
        // разбор занимает минуты, и при очистке список оставался без передач всё это время.
        Log.d("SmotrishkaSync", "import epg: старая программа оставлена до загрузки новой")

        // У каждого плейлиста своя программа передач; если ссылки нет, программу соберут
        // гиды пользователя из настроек — у них тот же путь, что у фонового обновления.
        val epgUrl = playlist.epgUrl?.takeIf { it.isNotBlank() }
        Log.d("SmotrishkaSync", "import epg source=$epgUrl")
        val result = catalogUpdater.updateEpgOnly(epgUrl)
        Log.d("SmotrishkaSync", "import epg result=$result")

        _status.value = when (result) {
            is com.mops1k.smotrishka.tv.core.data.UpdateResult.Success ->
                "Плейлист из файла: ${playlist.channels.size} каналов, передач ${result.programmes}"

            is com.mops1k.smotrishka.tv.core.data.UpdateResult.Partial ->
                "Плейлист из файла: ${playlist.channels.size} каналов. ${result.reason}"

            else -> "Плейлист из файла: ${playlist.channels.size} каналов"
        }
    }
}
