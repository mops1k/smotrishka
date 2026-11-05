package com.mops1k.smotrishka.tv.core.data

import java.net.URI

/**
 * Хост адреса гида — для сравнения гида провайдера с гидами пользователя.
 *
 * Сравнение по хосту (а не по полному адресу) намеренное: у epg.one разные пути
 * (`epg.xml.gz`, `epg2.xml.gz`) отдают один и тот же гид, и качать его второй раз незачем.
 *
 * @param url адрес XMLTV;
 * @return хост в нижнем регистре либо `null`, если адрес не разбирается.
 */
internal fun guideHost(url: String): String? =
    runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()
