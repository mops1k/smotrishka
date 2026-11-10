package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonObject
import org.junit.Test

/**
 * Сборка запроса страницы каталога: обязательные поля портала не теряются при листании.
 *
 * «Сериалы Турции» и «С наступающим!» отдают элементы только с `vc: 1` в запросе, поэтому
 * шаблон категории переносится как есть, а смещение и размер страницы подставляются свои.
 */
class VPortalRequestsTest {

    @Test
    fun `шаблон категории сохраняется, смещение и размер подставляются`() {
        val template = JsonObject().apply {
            addProperty("cmd", "flicks")
            addProperty("fid", 11)
            addProperty("offset", 0)
            addProperty("limit", 0)
            addProperty("vc", 1)
        }

        val request = pageRequest(template = template, fid = 11, offset = 60, limit = 300)

        assertThat(request.get("vc").asInt).isEqualTo(1)
        assertThat(request.get("fid").asInt).isEqualTo(11)
        assertThat(request.get("offset").asInt).isEqualTo(60)
        assertThat(request.get("limit").asInt).isEqualTo(300)
        assertThat(request.get("cmd").asString).isEqualTo("flicks")
    }

    @Test
    fun `без шаблона запрос собирается по умолчанию`() {
        val request = pageRequest(template = null, fid = 1, offset = 0, limit = 30)

        assertThat(request.entrySet().map { it.key })
            .containsExactly("cmd", "fid", "offset", "limit")
        assertThat(request.get("fid").asInt).isEqualTo(1)
    }

    @Test
    fun `шаблоны запоминаются по корневому ответу`() {
        val root = com.google.gson.JsonParser.parseString(
            """
            {"items":[
              {"title":"Сериалы Турции","request":{"cmd":"flicks","fid":11,"offset":0,"limit":0,"vc":1}},
              {"title":"фильмы","request":{"cmd":"flicks","fid":1,"offset":0,"limit":0}},
              {"title":"без запроса"}
            ]}
            """,
        ).asJsonObject
        val templates = VPortalRequestTemplates()

        templates.rememberFrom(root)

        assertThat(templates.template(11)?.get("vc")?.asInt).isEqualTo(1)
        assertThat(templates.template(1)?.get("vc")).isNull()
        assertThat(templates.template(99)).isNull()
    }
}
