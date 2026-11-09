package com.mops1k.smotrishka.tv.epg

import android.util.Log
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.UpdateResult
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
 * Фоновая догрузка программы передач после подключения источника.
 *
 * Гид весит десятки мегабайт и разбирается минутами; экран подключения столько ждать не должен.
 * Работа идёт в собственном scope, поэтому уход с экрана её не обрывает, а статус виден
 * на главном экране.
 *
 * @param catalogUpdater обновление программы передач.
 */
@Singleton
class EpgUpdater @Inject constructor(
    private val catalogUpdater: CatalogUpdater,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<EpgUpdateState>(EpgUpdateState.Idle)

    /** Состояние догрузки для экрана. */
    val state: StateFlow<EpgUpdateState> = _state.asStateFlow()

    /**
     * Запускает догрузку гида.
     *
     * Адреса у плейлиста может не быть — тогда программу соберут гиды пользователя
     * из настроек, и догрузку всё равно запускаем.
     *
     * @param epgUrl адрес XMLTV из плейлиста либо `null`;
     * @param force полное обновление: кэш программы сбрасывается, гиды перекачиваются
     *   независимо от того, как давно их загружали.
     */
    fun update(epgUrl: String?, force: Boolean = false) {
        if (_state.value is EpgUpdateState.Running) return

        scope.launch {
            _state.value = EpgUpdateState.Running
            _state.value = try {
                when (val result = catalogUpdater.updateEpgOnly(epgUrl, force = force)) {
                    is UpdateResult.Success -> EpgUpdateState.Done(result.programmes)
                    // Гид провайдера не загрузился, но гиды пользователя что-то добавили:
                    // для экрана это успех, причина уходит в лог.
                    is UpdateResult.Partial ->
                        if (result.programmes > 0) {
                            Log.d(TAG, "epg partial: ${result.reason}, передач ${result.programmes}")
                            EpgUpdateState.Done(result.programmes)
                        } else {
                            EpgUpdateState.Failed(result.reason)
                        }

                    is UpdateResult.Failure -> EpgUpdateState.Failed(result.reason)
                }
            } catch (e: Exception) {
                Log.e(TAG, "epg update failed", e)
                EpgUpdateState.Failed(e.message ?: "неизвестная ошибка")
            }
        }
    }

    private companion object {
        const val TAG = "SmotrishkaEpg"
    }
}
