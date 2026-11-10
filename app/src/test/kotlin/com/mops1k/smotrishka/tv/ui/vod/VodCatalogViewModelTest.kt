package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodCatalogPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Поведение экрана медиатеки: категории, первая страница, переключение категории,
 * догрузка и поведение при ошибке догрузки.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodCatalogViewModelTest {

    private val created = mutableListOf<VodCatalogViewModel>()
    private val config = VodPortalConfig.from("http://portal.example/api/v1/", "key")!!
    private val categories = listOf(
        VodCategory(VodCategoryId(1), "фильмы", 7),
        VodCategory(VodCategoryId(2), "сериалы", 7),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        portal: VodPortalPort = FakePortalPort(categories),
        catalog: VodCatalogPort = PagedCatalogPort(),
    ): VodCatalogViewModel {
        val configPort = FakeConfigPort(config)
        return VodCatalogViewModel(
            loadVodCategories = LoadVodCategories(portal, configPort),
            loadVodCatalogPage = LoadVodCatalogPage(catalog, configPort),
        ).also { created += it }
    }

    @Test
    fun `первая загрузка показывает категории и первую страницу`() {
        val vm = viewModel()

        val content = vm.state.value as VodCatalogUiState.Content
        assertThat(content.library.categories.map { it.title }).containsExactly("фильмы", "сериалы").inOrder()
        assertThat(content.library.selectedCategory?.id?.value).isEqualTo(1)
        assertThat(content.library.titles).hasSize(3)
        assertThat(content.library.hasMore).isTrue()
    }

    @Test
    fun `ошибка категорий заменяет экран понятным текстом`() {
        val vm = viewModel(portal = FakePortalPort(failure = VodFailure.SubscriptionRequired))

        val error = vm.state.value as VodCatalogUiState.Error
        assertThat(error.reason).contains("Подписка медиатеки не найдена")
    }

    @Test
    fun `переключение категории начинает с первой страницы`() {
        val catalog = PagedCatalogPort()
        val vm = viewModel(catalog = catalog)

        vm.selectCategory(VodCategoryId(2))

        val content = vm.state.value as VodCatalogUiState.Content
        assertThat(content.library.selectedCategoryId?.value).isEqualTo(2)
        assertThat(catalog.requested.last()).isEqualTo(2 to 0)
        assertThat(content.library.titles.map { it.id.value }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `догрузка добавляет следующую страницу к уже показанным`() {
        val catalog = PagedCatalogPort()
        val vm = viewModel(catalog = catalog)

        vm.loadMore()

        val content = vm.state.value as VodCatalogUiState.Content
        assertThat(content.library.titles.map { it.id.value }).containsExactly(1, 2, 3, 4, 5, 6).inOrder()
        assertThat(catalog.requested.last()).isEqualTo(1 to 3)
    }

    @Test
    fun `догрузка останавливается на конце категории`() {
        val catalog = PagedCatalogPort(total = 4)
        val vm = viewModel(catalog = catalog)
        vm.loadMore()

        val content = vm.state.value as VodCatalogUiState.Content
        assertThat(content.library.hasMore).isFalse()

        vm.loadMore()

        assertThat(catalog.requested.count { it.second == 0 }).isEqualTo(1)
        assertThat((vm.state.value as VodCatalogUiState.Content).library.titles).hasSize(4)
    }

    @Test
    fun `ошибка догрузки не стирает уже показанные карточки`() {
        val catalog = PagedCatalogPort()
        val vm = viewModel(catalog = catalog)
        // Ошибку включаем после первой страницы: проверяем именно догрузку.
        catalog.failNext = true

        vm.loadMore()

        val content = vm.state.value as VodCatalogUiState.Content
        assertThat(content.library.titles).hasSize(3)
        assertThat(content.loadingMore).isFalse()
    }

    @Test
    fun `выделение карточки обновляет превью`() {
        val vm = viewModel()
        val title = VodTitle(id = VodTitleId(5), title = "Пятый")

        vm.focusTitle(title)

        assertThat((vm.state.value as VodCatalogUiState.Content).selected?.id?.value).isEqualTo(5)
    }

    /** Портал, отдающий заданные категории или ошибку. */
    private class FakePortalPort(
        private val categories: List<VodCategory> = emptyList(),
        private val failure: VodFailure? = null,
    ) : VodPortalPort {
        override suspend fun loadCategories(config: VodPortalConfig): VodResult<List<VodCategory>> =
            failure?.let { VodResult.Failure(it) } ?: VodResult.Success(categories)
    }

    /** Каталог со страницами фиксированного размера. */
    private class PagedCatalogPort(
        private val total: Int = 10,
        private val pageSize: Int = 3,
    ) : VodCatalogPort {

        /** Запрошенные пары «категория — смещение». */
        val requested = mutableListOf<Pair<Int, Int>>()

        /** Следующая страница ответит ошибкой. */
        var failNext = false

        override suspend fun loadPage(
            config: VodPortalConfig,
            categoryId: VodCategoryId,
            offset: Int,
            limit: Int,
        ): VodResult<VodCatalogPage> {
            requested += categoryId.value to offset
            if (failNext) {
                failNext = false
                return VodResult.Failure(VodFailure.Network(null))
            }
            val ids = (offset until minOf(offset + pageSize, total)).map { it + 1 }
            val next = if (offset + pageSize < total) offset + pageSize else null
            return VodResult.Success(
                VodCatalogPage(
                    titles = ids.map { VodTitle(id = VodTitleId(it), title = "Фильм $it") },
                    nextOffset = next,
                    total = total,
                ),
            )
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
