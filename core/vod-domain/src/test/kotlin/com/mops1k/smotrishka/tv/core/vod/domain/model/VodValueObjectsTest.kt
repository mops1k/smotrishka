package com.mops1k.smotrishka.tv.core.vod.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Проверки value-объектов медиатеки: нормализация адреса, скрытие ключа и разбор качества.
 */
class VodValueObjectsTest {

    @Test
    fun `адрес портала нормализуется и получает завершающий слэш`() {
        val url = VodPortalUrl.from("  http://portal.example/api/v1  ")

        assertThat(url?.value).isEqualTo("http://portal.example/api/v1/")
    }

    @Test
    fun `адрес с завершающим слэшем не получает второй`() {
        val url = VodPortalUrl.from("https://portal.example/api/v1/")

        assertThat(url?.value).isEqualTo("https://portal.example/api/v1/")
    }

    @Test
    fun `не адрес и адрес с пробелами внутри отвергаются`() {
        assertThat(VodPortalUrl.from("")).isNull()
        assertThat(VodPortalUrl.from("portal.example")).isNull()
        assertThat(VodPortalUrl.from("ftp://portal.example/")).isNull()
        assertThat(VodPortalUrl.from("http://portal example/")).isNull()
    }

    @Test
    fun `ключ портала не печатается в логах`() {
        val key = VodPortalKey("e8179fbc74ceedf-731012aca5e3898")

        assertThat(key.toString()).isEqualTo("VodPortalKey(***)")
        assertThat(key.toString()).doesNotContain("e8179fbc")
    }

    @Test
    fun `качество разбирается по ключу портала`() {
        assertThat(VodQuality.fromKey("480")).isEqualTo(VodQuality.SD_480)
        assertThat(VodQuality.fromKey("2160")).isEqualTo(VodQuality.UHD_2160)
        assertThat(VodQuality.fromKey("auto")).isEqualTo(VodQuality.AUTO)
    }

    @Test
    fun `незнакомое качество отбрасывается, а не ломает разбор`() {
        assertThat(VodQuality.fromKey("144")).isNull()
    }

    @Test
    fun `сетка берёт превью постера, а при его отсутствии большой постер`() {
        val сPreview = VodTitle(
            id = VodTitleId(1),
            title = "Фильм",
            posterUrl = "http://portal/p_1.jpg",
            previewPosterUrl = "http://portal/pl_1.jpg",
        )
        val безPreview = сPreview.copy(previewPosterUrl = null)

        assertThat(сPreview.gridPosterUrl).isEqualTo("http://portal/pl_1.jpg")
        assertThat(безPreview.gridPosterUrl).isEqualTo("http://portal/p_1.jpg")
    }
}
