package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.StreamHeaders
import org.junit.Test

/**
 * TDD-спецификация маппинга доменной модели в строки Room и обратно.
 *
 * Каталог хранится в базе и перечитывается при каждом старте, поэтому «потеря»
 * архива, логотипа или заголовков потока в маппинге ломает воспроизведение.
 */
class CatalogMapperTest {

    private val mapper = CatalogMapper()

    @Test
    fun `канал переживает сохранение и чтение без потерь`() {
        val channel = Channel(
            id = ChannelId("one"),
            name = "Первый",
            streamUrl = "http://example.com/1.m3u8",
            logoUrl = "http://logo/1.png",
            categoryId = "основные",
            epgId = "one",
            archive = ArchiveInfo(mode = CatchupMode.DEFAULT, source = "http://arch/{utc}", days = 7),
            headers = StreamHeaders(
                userAgent = "SuperPlayer/1.0",
                referrer = "http://ref.example.com/",
                headers = mapOf("X-Token" to "abc"),
            ),
        )

        val restored = mapper.toModel(mapper.toEntity(channel))

        assertThat(restored).isEqualTo(channel)
    }

    @Test
    fun `канал без архива и заголовков переживает сохранение`() {
        val channel = Channel(
            id = ChannelId("two"),
            name = "Второй",
            streamUrl = "http://example.com/2.m3u8",
        )

        val restored = mapper.toModel(mapper.toEntity(channel))

        assertThat(restored).isEqualTo(channel)
        assertThat(restored.archive).isNull()
        assertThat(restored.headers).isNull()
    }

    @Test
    fun `категория переживает сохранение вместе с позицией`() {
        val category = Category(id = "кино", name = "Кино")

        val entity = mapper.toEntity(category, position = 3)

        assertThat(entity.position).isEqualTo(3)
        assertThat(mapper.toModel(entity)).isEqualTo(category)
    }
}
