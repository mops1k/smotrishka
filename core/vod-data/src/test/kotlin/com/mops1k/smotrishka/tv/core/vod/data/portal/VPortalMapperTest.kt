package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleKind
import org.junit.Test

/**
 * TDD-спецификация разбора ответов портала.
 *
 * Фикстуры синтетические и намеренно «грязные»: с отсутствующими полями и незнакомыми ключами —
 * API приватное, и разбор обязан пережить изменения, а не упасть.
 */
class VPortalMapperTest {

    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test
    fun `корневой ответ даёт категории в порядке портала`() {
        val root = json(
            """
            {"type":"videoportal","items":[
              {"type":"category","title":"фильмы","request":{"cmd":"flicks","fid":1}},
              {"type":"category","title":"сериалы","request":{"cmd":"flicks","fid":2}},
              {"type":"next","title":"next","request":{"offset":2,"limit":0}}
            ],"controls":{"search":{}}}
            """.trimIndent(),
        )

        val categories = root.toCategories()

        assertThat(categories.map { it.title }).containsExactly("фильмы", "сериалы").inOrder()
        assertThat(categories.first().id.value).isEqualTo(1)
    }

    @Test
    fun `страница каталога отдаёт карточки, курсор и общее число`() {
        val page = json(
            """
            {"type":"category","count":13114,"items":[
              {"type":"multistream","title":"Выстрел","img":"http://p/h.jpg","imglr":"http://p/l.jpg",
               "fid":147704,"fhd":true,"4k":false,"hdr":true,"year":2014,"duration":0,
               "description":"Описание","agelimit":16},
              {"type":"next","title":"next","request":{"offset":31,"limit":0}}
            ]}
            """.trimIndent(),
        )

        val parsed = page.toCatalogPage()

        assertThat(parsed.total).isEqualTo(13114)
        assertThat(parsed.nextOffset).isEqualTo(31)
        assertThat(parsed.titles).hasSize(1)
        val card = parsed.titles.single()
        assertThat(card.id.value).isEqualTo(147704)
        assertThat(card.kind).isEqualTo(VodTitleKind.MULTISTREAM)
        assertThat(card.isFhd).isTrue()
        assertThat(card.isHdr).isTrue()
        assertThat(card.is4k).isFalse()
        assertThat(card.durationSeconds).isNull()
        assertThat(card.ageLimit).isEqualTo(16)
        assertThat(card.gridPosterUrl).isEqualTo("http://p/l.jpg")
    }

    @Test
    fun `последняя страница не имеет курсора`() {
        val page = json("""{"type":"category","count":2,"items":[{"type":"stream","title":"Фильм","fid":1}]}""")

        val parsed = page.toCatalogPage()

        assertThat(parsed.nextOffset).isNull()
        assertThat(parsed.hasMore).isFalse()
    }

    @Test
    fun `карточка без идентификатора или названия пропускается`() {
        val page = json(
            """{"type":"category","items":[
                 {"type":"stream","title":"Без идентификатора"},
                 {"type":"stream","fid":7},
                 {"type":"stream","title":"Нормальный","fid":8}
               ]}""",
        )

        assertThat(page.toTitles().map { it.id.value }).containsExactly(8)
    }

    @Test
    fun `детали фильма содержат варианты качества`() {
        val detailsJson = json(
            """
            {"type":"stream","title":"Одиссея","fid":147573,"duration":173,
             "url":"http://host/vod/key/S30/1/0/master.m3u8",
             "variants":{"480":"http://host/480.m3u8","1080":"http://host/1080.m3u8","144":"http://host/144.m3u8"}}
            """.trimIndent(),
        )
        val card = detailsJson.toTitle()!!

        val details = detailsJson.toTitleDetails(card, VodTitleId(147573))

        assertThat(details.isSerial).isFalse()
        assertThat(details.stream?.url).isEqualTo("http://host/vod/key/S30/1/0/master.m3u8")
        assertThat(details.stream?.durationSeconds).isEqualTo(173)
        assertThat(details.stream?.qualities?.get(VodQuality.SD_480)).isEqualTo("http://host/480.m3u8")
        assertThat(details.stream?.qualities?.get(VodQuality.FULL_HD_1080)).isEqualTo("http://host/1080.m3u8")
        assertThat(details.stream?.qualities?.size).isEqualTo(2)
    }

    @Test
    fun `детали сезона дают серии с номерами из названий`() {
        val detailsJson = json(
            """
            {"type":"multistream","title":"Сезон 1","fid":147673,"items":[
              {"type":"stream","title":"Серия 1","fid":147674,"url":"http://host/0/master.m3u8"},
              {"type":"stream","title":"Серия 2","fid":147675,"url":"http://host/1/master.m3u8"}
            ]}
            """.trimIndent(),
        )
        val card = detailsJson.toTitle()!!

        val details = detailsJson.toTitleDetails(card, VodTitleId(147673))

        assertThat(details.isSerial).isTrue()
        assertThat(details.episodes.map { it.number.value }).containsExactly(1, 2).inOrder()
        assertThat(details.episodes.map { it.id.value }).containsExactly(147674, 147675).inOrder()
        assertThat(details.firstEpisode?.streamUrl).isEqualTo("http://host/0/master.m3u8")
        assertThat(details.nextEpisode(VodTitleId(147674))?.id?.value).isEqualTo(147675)
    }

    @Test
    fun `одиночный мультипоток считается вариантом того же фильма`() {
        val detailsJson = json(
            """
            {"type":"multistream","title":"Выстрел","fid":147704,"items":[
              {"type":"stream","title":"Выстрел","fid":29752,"url":"http://host/S00/1/0/master.m3u8"}
            ]}
            """.trimIndent(),
        )
        val card = detailsJson.toTitle()!!

        val details = detailsJson.toTitleDetails(card, VodTitleId(147704))

        assertThat(details.isSerial).isFalse()
        assertThat(details.stream?.url).isEqualTo("http://host/S00/1/0/master.m3u8")
    }

    @Test
    fun `поиск отдаёт карточки без общего числа`() {
        val search = json(
            """{"type":"category","items":[
                 {"type":"stream","title":"Матрица","fid":1,"year":1999}
               ]}""",
        )

        val titles = search.toTitles()

        assertThat(titles).hasSize(1)
        assertThat(titles.single().year).isEqualTo(1999)
    }
}
