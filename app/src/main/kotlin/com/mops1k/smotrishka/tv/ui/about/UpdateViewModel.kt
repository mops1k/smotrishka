package com.mops1k.smotrishka.tv.ui.about

import androidx.lifecycle.ViewModel
import com.mops1k.smotrishka.tv.update.UpdateRepository
import com.mops1k.smotrishka.tv.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Состояние обновления для навигации: диалог при запуске, тост и маркер в сайдбаре.
 *
 * @param repository обновления приложения.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    /** Текущее состояние обновления. */
    val state: StateFlow<UpdateState> = repository.state

    /** Сообщения для тоста: «Доступно обновление 0.2.0» и подобные. */
    val messages: SharedFlow<String> = repository.messages

    /** Скачивает и ставит найденное обновление. */
    fun downloadNow() {
        repository.downloadAndInstall()
    }

    /** «Не спрашивать об этой версии». */
    fun dismissCurrent() {
        repository.dismissCurrent()
    }
}
