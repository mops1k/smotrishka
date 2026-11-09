package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Правило определения языка канала.
 *
 * Порядок признаков (решение Александра 09.11.2026): явная метка `tvg-language`, затем
 * языковая группа. Имя канала не используется. Если ни один признак не сработал — `null`,
 * канал считается «без метки» и в списках показывается всегда.
 */
class ChannelLanguageTest {

    @Test
    fun `явная метка tvg-language важнее группы`() {
        val language = ChannelLanguage.resolve(
            tvgLanguage = "Русский",
            group = "Қазақша",
            name = "Кино HD ES",
        )

        assertThat(language).isEqualTo("Русский")
    }

    @Test
    fun `языковая группа даёт язык, если метки нет`() {
        assertThat(ChannelLanguage.resolve(null, "Қазақша", "Qazaq TV")).isEqualTo("Казахский")
        assertThat(ChannelLanguage.resolve(null, "Українські", "1+1")).isEqualTo("Украинский")
        assertThat(ChannelLanguage.resolve(null, "Беларускія", "Беларусь 1")).isEqualTo("Белорусский")
        assertThat(ChannelLanguage.resolve(null, "USA", "Cinemax Hits HD")).isEqualTo("Английский")
    }

    @Test
    fun `хвосты имени работают только при включённой настройке`() {
        // По умолчанию имя канала не признак языка (решение Александра 09.11.2026).
        assertNull(ChannelLanguage.resolve(null, "Кино", "M+ Comedia HD ES"))
        // С включённой настройкой «Определять язык по названию» хвост даёт язык.
        assertThat(ChannelLanguage.resolve(null, "Кино", "M+ Comedia HD ES", byName = true))
            .isEqualTo("Испанский")
        assertThat(ChannelLanguage.resolve(null, "Кино", "Cinemax Hits HD US", byName = true))
            .isEqualTo("Английский")
        // Явная метка и группа важнее названия и при включённой настройке.
        assertThat(ChannelLanguage.resolve("Немецкий", "Кино", "Канал US", byName = true))
            .isEqualTo("Немецкий")
        assertThat(ChannelLanguage.resolve(null, "Қазақша", "Канал US", byName = true))
            .isEqualTo("Казахский")
    }

    @Test
    fun `без признаков язык не определяется`() {
        assertNull(ChannelLanguage.resolve(null, "Кино", "CineMan Лесник"))
        assertNull(ChannelLanguage.resolve("   ", "Спорт", "Матч! Игра"))
        // Неязыковая группа не должна давать ложную метку.
        assertNull(ChannelLanguage.resolve(null, "HD", "BCU TruMotion HD"))
        assertNull(ChannelLanguage.resolve(null, "Взрослые", "Hustler HD"))
    }

    @Test
    fun `группа проверяется без учёта регистра и пробелов`() {
        assertThat(ChannelLanguage.resolve(null, "  українські ", "ТЕТ")).isEqualTo("Украинский")
    }
}
