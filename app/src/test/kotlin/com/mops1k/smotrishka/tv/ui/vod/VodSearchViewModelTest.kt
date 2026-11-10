package com.mops1k.smotrishka.tv.ui.vod

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodSearchPort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SearchVodTitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Поведение поиска по медиатеке: задержка ввода, результаты, пустой ответ и ошибка портала.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodSearchViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(port: FakeSearchPort): VodSearchViewModel {
        val config = FakeConfigPort(VodPortalConfig.from("http://portal.example/", "key")!!)
        return VodSearchViewModel(SearchVodTitles(port, config))
    }

    @Test
    fun `пустая строка не уходит на портал`() = runTest {
        val port = FakeSearchPort()
        val vm = viewModel(port)

        vm.setQuery("   ")
        advanceTimeBy(400)

        assertThat(port.queries).isEmpty()
        assertThat(vm.state.value.results).isEmpty()
    }

    @Test
    fun `запрос уходит после задержки ввода и даёт результаты`() = runTest {
        val port = FakeSearchPort(
            found = listOf(VodTitle(id = VodTitleId(1), title = "Матрица", year = 1999)),
        )
        val vm = viewModel(port)

        vm.setQuery("матрица")
        advanceTimeBy(400)

        assertThat(port.queries).containsExactly("матрица")
        assertThat(vm.state.value.results.map { it.title }).containsExactly("Матрица")
        assertThat(vm.state.value.loading).isFalse()
    }

    @Test
    fun `пустой ответ помечается как «ничего не найдено»`() = runTest {
        val vm = viewModel(FakeSearchPort(found = emptyList()))

        vm.setQuery("абракадабра")
        advanceTimeBy(400)

        assertThat(vm.state.value.isEmpty).isTrue()
    }

    @Test
    fun `ошибка портала показывается текстом`() = runTest {
        val vm = viewModel(FakeSearchPort(failure = VodFailure.Network(null)))

        vm.setQuery("матрица")
        advanceTimeBy(400)

        assertThat(vm.state.value.error).contains("Нет связи с порталом медиатеки")
    }

    @Test
    fun `очистка сбрасывает результаты`() = runTest {
        val vm = viewModel(FakeSearchPort(found = listOf(VodTitle(VodTitleId(1), "Матрица"))))
        vm.setQuery("матрица")
        advanceTimeBy(400)

        vm.clear()

        assertThat(vm.state.value.query).isEmpty()
        assertThat(vm.state.value.results).isEmpty()
    }

    /** Поиск, отдающий заданные результаты или ошибку. */
    private class FakeSearchPort(
        private val found: List<VodTitle> = emptyList(),
        private val failure: VodFailure? = null,
    ) : VodSearchPort {

        /** Ушедшие запросы. */
        val queries = mutableListOf<String>()

        override suspend fun search(
            config: VodPortalConfig,
            query: String,
            offset: Int,
        ): VodResult<List<VodTitle>> {
            queries += query
            return failure?.let { VodResult.Failure(it) } ?: VodResult.Success(found)
        }
    }

    /** Подключение в памяти. */
    private class FakeConfigPort(config: VodPortalConfig) : VodConfigPort {
        private val state = MutableStateFlow<VodPortalStatus>(VodPortalStatus.Connected(config))
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
}
