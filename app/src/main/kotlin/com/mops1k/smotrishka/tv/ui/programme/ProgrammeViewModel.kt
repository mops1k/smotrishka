package com.mops1k.smotrishka.tv.ui.programme

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Экран программы канала: что было, что идёт и что будет.
 *
 * @param catalogStore каталог каналов;
 * @param epgStore программа передач;
 * @param epgMatcher сопоставление канала с EPG;
 * @param clock текущее время (подменяется в тестах);
 * @param savedStateHandle аргументы навигации (id канала).
 */
@HiltViewModel
class ProgrammeViewModel @Inject constructor(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val epgMatcher: EpgMatcher,
    private val settingsStore: PlayerSettingsStore,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId = savedStateHandle.get<String>(ARG_CHANNEL_ID).orEmpty()

    private val _state = MutableStateFlow<ProgrammeUiState>(ProgrammeUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<ProgrammeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = buildState() }
    }

    /**
     * Пересобирает состояние.
     *
     * Экран зовёт его по таймеру: передача сменяется по ходу эфира, и «сейчас» должно
     * переехать на новую строку (замечание Александра 09.11.2026).
     */
    fun refresh() {
        viewModelScope.launch { _state.value = buildState() }
    }

    private suspend fun buildState(): ProgrammeUiState = runCatching {
        val channel = catalogStore.playlist().channels.firstOrNull { it.id == ChannelId(channelId) }
            ?: return@runCatching ProgrammeUiState.Error("Канал не найден в плейлисте")

        val epgId = epgMatcher.match(listOf(channel), epgStore.epgChannels()).matches[channel.id]
            ?: return@runCatching ProgrammeUiState.Error("Для этого канала нет программы передач")

        val now = clock.instant()
        val depthHours = settingsStore.archiveDepthHours.first()
        // Сдвиг гида: свой у канала важнее общего (решение Александра 09.11.2026).
        val shiftHours = settingsStore.epgShifts.first()[channel.id.value]
            ?: settingsStore.globalEpgShift.first()
        val shiftSeconds = shiftHours.toLong() * 60L * 60L
        // Список программы показывает передачи на всю глубину архива, а не только сутки назад.
        val backHours = maxOf(BACK_HOURS, depthHours.toLong())
        val programmes = epgStore.programmes(
            channelId = epgId,
            from = now.minus(backHours, ChronoUnit.HOURS),
            to = now.plus(FORWARD_HOURS, ChronoUnit.HOURS),
        ).map { programme ->
            if (shiftSeconds == 0L) {
                programme
            } else {
                // Сетка провайдера со смещением: показываем время с поправкой,
                // а «сейчас»/«прошла» считаем уже по исправленному времени.
                programme.copy(
                    start = programme.start.plusSeconds(shiftSeconds),
                    end = programme.end.plusSeconds(shiftSeconds),
                )
            }
        }

        ProgrammeUiState.Content(
            channelId = channel.id,
            channelName = channel.name,
            logoUrl = channel.logoUrl,
            items = programmes.map { programme ->
                ProgrammeUi(
                    title = programme.title,
                    description = programme.description,
                    start = programme.start,
                    end = programme.end,
                    isCurrent = !programme.start.isAfter(now) && programme.end.isAfter(now),
                    isPast = !programme.end.isAfter(now),
                    inArchive = ArchiveAvailability.available(
                        end = programme.end,
                        now = now,
                        depthHours = if (channel.archive != null) depthHours else 0,
                    ),
                )
            },
        )
    }.getOrElse { error ->
        ProgrammeUiState.Error("Не удалось прочитать программу: ${error.message ?: "неизвестная ошибка"}")
    }

    companion object {
        /** Имя аргумента навигации с id канала. */
        const val ARG_CHANNEL_ID = "channelId"

        private const val BACK_HOURS = 12L
        private const val FORWARD_HOURS = 24L
    }
}
