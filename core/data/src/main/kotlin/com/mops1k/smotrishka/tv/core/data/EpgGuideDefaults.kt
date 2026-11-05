package com.mops1k.smotrishka.tv.core.data

/**
 * Проверенные публичные гиды, которые предлагаются при первом запуске.
 *
 * Порядок = приоритет разбора. Проверено 05.11.2026:
 * - `epg.one` — тот же бэкенд, что гид провайдера (EPG it999), идентификаторы каналов
 *   совпадают с каталогом; HTTPS у него не работает (сертификат истёк 02.11.2026),
 *   поэтому адрес обязан быть `http`;
 * - `epg.it999.ru/edem.xml.gz` — рабочее зеркало того же гида;
 * - `iptvx.one` — работает, но идентификаторы каналов там слаговые (`10-rentv`),
 *   поэтому каналы привязываются только по имени.
 *
 * Мёртвые адреса из статьи ottfox.com проверены и НЕ включены: `ottepg.ru` и
 * `iptv-content.webhop.net` не разрешаются, `st.kineskop.tv/epg.xml.gz` даёт 404,
 * `stb.shara-tv.org` не разрешается, `topiptv.info` не отвечает.
 */
object EpgGuideDefaults {

    /** Гиды, предлагаемые пользователю при первом запуске. */
    val ALL: List<EpgGuideDraft> = listOf(
        EpgGuideDraft(name = "epg.one", url = "http://epg.one/epg2.xml.gz"),
        EpgGuideDraft(name = "EPG it999 (зеркало epg.one)", url = "http://epg.it999.ru/edem.xml.gz"),
        EpgGuideDraft(name = "iptvx.one", url = "https://iptvx.one/epg/epg.xml.gz"),
    )
}
