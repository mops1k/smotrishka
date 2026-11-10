package com.mops1k.smotrishka.tv.ui.settings

import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Подделки портов медиатеки для тестов экрана настроек.
 *
 * Свои типы не мокаются: подделки реализуют порты домена и ведут себя как настоящие хранилища
 * и портал — в памяти.
 */
internal class SettingsTestPortalPort(
    private val result: VodResult<List<VodCategory>> = VodResult.Success(emptyList()),
) : VodPortalPort {

    override suspend fun loadCategories(config: VodPortalConfig): VodResult<List<VodCategory>> = result
}

/** Подключение медиатеки в памяти. */
internal class SettingsTestConfigPort : VodConfigPort {

    private val state = MutableStateFlow<VodPortalStatus>(VodPortalStatus.NotConfigured)

    override val status: Flow<VodPortalStatus> = state

    override suspend fun saveConnected(config: VodPortalConfig) {
        state.value = VodPortalStatus.Connected(config)
    }

    override suspend fun saveFailed(config: VodPortalConfig, failure: VodFailure) {
        state.value = VodPortalStatus.Failed(config, failure)
    }

    override suspend fun clear() {
        state.value = VodPortalStatus.NotConfigured
    }
}

/** Избранное медиатеки в памяти. */
internal class SettingsTestFavoritesPort : VodFavoritesPort {

    private val state = MutableStateFlow(VodFavorites())

    override val favorites: Flow<VodFavorites> = state

    override suspend fun replace(favorites: VodFavorites) {
        state.value = favorites
    }

    override suspend fun clear() {
        state.value = VodFavorites()
    }
}

/** История просмотра в памяти. */
internal class SettingsTestProgressPort : VodPlaybackProgressPort {

    private val state = MutableStateFlow<List<VodPlaybackProgress>>(emptyList())

    override val progress: Flow<List<VodPlaybackProgress>> = state

    override suspend fun save(progress: VodPlaybackProgress) {
        state.value = state.value + progress
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** Шина событий, которая ничего не хранит. */
internal class SettingsTestEventBus : VodEventBus {

    override val events: Flow<VodEvent> = emptyFlow()

    override suspend fun publish(event: VodEvent) = Unit
}

/** Часы с постоянным временем. */
internal class SettingsTestClock : VodClock {

    override fun nowMillis(): Long = 0L
}
