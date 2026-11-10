package com.mops1k.smotrishka.tv.core.vod.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Разбор строки из личного кабинета провайдера.
 *
 * Провайдер выдаёт одну строку вида `portal::[key:…]http://host/api/v1/`: ключ и адрес внутри
 * одной ссылки. Пользователь вставляет её целиком, склеивать два поля вручную он не должен.
 */
class VodPortalLinkTest {

    private val key = "e8179fbc74ceedf-731012aca5e3898"
    private val url = "http://a2c998438402.stremersons.net/api/v1/"

    @Test
    fun `строка из кабинета разбирается на ключ и адрес`() {
        val parsed = VodPortalLink.parse("portal::[key:$key]$url") as VodPortalLinkParseResult.Parsed

        assertThat(parsed.config.key.value).isEqualTo(key)
        assertThat(parsed.config.url.value).isEqualTo(url)
    }

    @Test
    fun `пробелы и переводы строк вокруг ссылки не мешают`() {
        val parsed = VodPortalLink.parse("  portal::[key:$key]$url\n ") as VodPortalLinkParseResult.Parsed

        assertThat(parsed.config.key.value).isEqualTo(key)
        assertThat(parsed.config.url.value).isEqualTo(url)
    }

    @Test
    fun `адрес без завершающего слэша нормализуется`() {
        val parsed = VodPortalLink.parse("portal::[key:$key]http://portal.example/api/v1") as VodPortalLinkParseResult.Parsed

        assertThat(parsed.config.url.value).isEqualTo("http://portal.example/api/v1/")
    }

    @Test
    fun `ссылка без ключа даёт отдельную причину`() {
        assertThat(VodPortalLink.parse(url)).isEqualTo(VodPortalLinkParseResult.MissingKey)
    }

    @Test
    fun `пустой ключ в ссылке даёт отдельную причину`() {
        assertThat(VodPortalLink.parse("portal::[key:]$url")).isEqualTo(VodPortalLinkParseResult.MissingKey)
    }

    @Test
    fun `мусор вместо ссылки не принимается`() {
        assertThat(VodPortalLink.parse("просто текст")).isEqualTo(VodPortalLinkParseResult.BadUrl)
        assertThat(VodPortalLink.parse("portal.example")).isEqualTo(VodPortalLinkParseResult.BadUrl)
        assertThat(VodPortalLink.parse("")).isEqualTo(VodPortalLinkParseResult.BadUrl)
    }
}
