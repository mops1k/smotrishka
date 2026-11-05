package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация стартового набора гидов.
 *
 * Набор предустанавливается, чтобы фоновое обновление работало сразу после установки.
 * epg.one идёт первым: это тот же бэкенд, что гид провайдера (EPG it999), и его
 * идентификаторы каналов совпадают с каталогом. HTTPS у него сломан (сертификат истёк
 * 02.11.2026), поэтому адрес обязан быть http — иначе загрузка падает по проверке TLS.
 */
class EpgGuideDefaultsTest {

    @Test
    fun `epg one идёт первым и берётся по http`() {
        val guide = EpgGuideDefaults.ALL.first()

        assertThat(guide.name).isEqualTo("epg.one")
        assertThat(guide.url).startsWith("http://epg.one/")
    }

    @Test
    fun `в наборе три проверенных гида`() {
        assertThat(EpgGuideDefaults.ALL.map { it.name }).containsExactly(
            "epg.one",
            "EPG it999 (зеркало epg.one)",
            "iptvx.one",
        )
    }

    @Test
    fun `хост гида вычисляется из адреса`() {
        assertThat(guideHost(EpgGuideDefaults.ALL.first().url)).isEqualTo("epg.one")
    }
}
