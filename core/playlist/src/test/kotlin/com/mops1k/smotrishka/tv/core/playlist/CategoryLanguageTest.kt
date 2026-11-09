package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Классификация категорий: язык или тема.
 *
 * Названия языковых категорий встречаются по-русски, по-английски и в родной письменности
 * (решение Александра 09.11.2026). Тематические категории языком быть не должны.
 */
class CategoryLanguageTest {

    @Test
    fun `языковые категории в разных написаниях`() {
        assertThat(CategoryLanguage.resolve("Қазақша")).isEqualTo("Казахский")
        assertThat(CategoryLanguage.resolve("Казахские")).isEqualTo("Казахский")
        assertThat(CategoryLanguage.resolve("Kazakh")).isEqualTo("Казахский")
        assertThat(CategoryLanguage.resolve("Українські")).isEqualTo("Украинский")
        assertThat(CategoryLanguage.resolve("Ukraine")).isEqualTo("Украинский")
        assertThat(CategoryLanguage.resolve("Беларускія")).isEqualTo("Белорусский")
        assertThat(CategoryLanguage.resolve("USA")).isEqualTo("Английский")
        assertThat(CategoryLanguage.resolve("العربية")).isEqualTo("Арабский")
        assertThat(CategoryLanguage.resolve("Arabic")).isEqualTo("Арабский")
        assertThat(CategoryLanguage.resolve("Русские")).isEqualTo("Русский")
    }

    @Test
    fun `языки в необычных написаниях провайдера`() {
        // Реальные названия категорий из плейлиста iedem.tv.
        assertThat(CategoryLanguage.resolve("Точик")).isEqualTo("Таджикский")
        assertThat(CategoryLanguage.resolve("O'zbek")).isEqualTo("Узбекский")
        assertThat(CategoryLanguage.resolve("Oʻzbek")).isEqualTo("Узбекский")
        assertThat(CategoryLanguage.resolve("Moldovenească")).isEqualTo("Молдавский")
        assertThat(CategoryLanguage.resolve("Türk")).isEqualTo("Турецкий")
    }

    @Test
    fun `тематические категории языком не считаются`() {
        assertNull(CategoryLanguage.resolve("Кино"))
        assertNull(CategoryLanguage.resolve("Сериалы"))
        assertNull(CategoryLanguage.resolve("Спорт"))
        assertNull(CategoryLanguage.resolve("Новости"))
        assertNull(CategoryLanguage.resolve("Музыка"))
        assertNull(CategoryLanguage.resolve("Детские"))
        assertNull(CategoryLanguage.resolve("Познавательные"))
        assertNull(CategoryLanguage.resolve("Развлекательные"))
        assertNull(CategoryLanguage.resolve("Другие"))
        assertNull(CategoryLanguage.resolve("Все каналы"))
        assertNull(CategoryLanguage.resolve("HD"))
        assertNull(CategoryLanguage.resolve("Взрослые"))
        assertNull(CategoryLanguage.resolve("Movies 4K"))
    }

    @Test
    fun `тема важнее вхождения языка`() {
        // «Новости на казахском» — тематическая категория, а не язык.
        assertNull(CategoryLanguage.resolve("Новости на казахском"))
        assertNull(CategoryLanguage.resolve("Спорт Казахстан"))
        // А чистое название языка — язык.
        assertThat(CategoryLanguage.resolve("Казахстан")).isEqualTo("Казахский")
    }

    @Test
    fun `родная письменность определяет язык`() {
        assertThat(CategoryLanguage.resolve("ქართული")).isEqualTo("Грузинский")
        assertThat(CategoryLanguage.resolve("Հայերեն")).isEqualTo("Армянский")
        assertThat(CategoryLanguage.resolve("日本語")).isEqualTo("Японский")
        assertThat(CategoryLanguage.resolve("한국어")).isEqualTo("Корейский")
        assertThat(CategoryLanguage.resolve("中文")).isEqualTo("Китайский")
        assertThat(CategoryLanguage.resolve("עברית")).isEqualTo("Иврит")
        assertThat(CategoryLanguage.resolve("ไทย")).isEqualTo("Тайский")
    }

    @Test
    fun `пустое название языка не даёт`() {
        assertNull(CategoryLanguage.resolve(null))
        assertNull(CategoryLanguage.resolve("   "))
        assertNull(CategoryLanguage.resolve("Разное"))
    }
}
