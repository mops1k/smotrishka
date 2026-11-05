package com.mops1k.smotrishka.tv.core.data

import java.net.URI
import java.time.Instant

/**
 * Профиль провайдера edem «по коду».
 *
 * Механизм снят с работающего JS-плеера (провайдерский профиль edem):
 * - канал-лист общий и кода не содержит: `http://epg.ottp.eu.org/edem/edem_epg_ico.m3u8`;
 * - в листе ссылки-заглушки вида `http://localhost/iptv/00000000000000/<id>/index.m3u8`;
 * - настоящая ссылка канала собирается как `<хост>/iptv/<код>/<id>/index.m3u8`,
 *   где хост зашит в профиль провайдера, а код — ключ доступа пользователя.
 *
 * Проверено на реальном коде: ответ `application/x-mpegURL` с рабочим HLS-плейлистом.
 * Хост провайдер может сменить без предупреждения — тогда достаточно поправить константу.
 *
 * @param channelListBase базовый адрес каталога каналов;
 * @param streamBase базовый адрес потоков провайдера.
 */
class IedemProvider(
    private val channelListBase: String = DEFAULT_CHANNEL_LIST_BASE,
    private val streamBase: String = DEFAULT_STREAM_BASE,
) {

    /**
     * Проверяет код.
     *
     * @param code код из личного кабинета провайдера.
     */
    fun validate(code: String): IedemCodeResult {
        val normalized = code.trim()
        if (normalized.isEmpty()) {
            return IedemCodeResult.Invalid("Введите код из личного кабинета iedem.tv")
        }
        if (!VALID_CODE.matches(normalized)) {
            return IedemCodeResult.Invalid("Код не должен содержать пробелов и слэшей")
        }

        return IedemCodeResult.Success(normalized)
    }

    /** Адрес каталога каналов провайдера. */
    fun channelListUrl(): String = "$channelListBase$PLAYLIST_PATH.m3u8"

    /**
     * Строит ссылку канала.
     *
     * @param code код провайдера;
     * @param channelId идентификатор канала из каталога.
     */
    fun streamUrl(code: String, channelId: String): String =
        "$streamBase$STREAM_PATH/$code/$channelId/index.m3u8"

    /**
     * Строит ссылку на архив передачи.
     *
     * Провайдер принимает два параметра: `utc` — начало передачи в секундах эпохи,
     * `lutc` — текущий момент (проверено: такой адрес отдаёт рабочий HLS-поток).
     *
     * @param code код провайдера;
     * @param channelId идентификатор канала из каталога;
     * @param start начало передачи;
     * @param now текущий момент.
     */
    fun archiveUrl(code: String, channelId: String, start: Instant, now: Instant): String =
        streamUrl(code, channelId) +
            "?utc=${start.epochSecond}&lutc=${now.epochSecond}"

    /**
     * Достаёт идентификатор канала из ссылки-заглушки каталога.
     *
     * @param url ссылка из каталога;
     * @return идентификатор либо `null`, если ссылка не провайдерская.
     */
    fun channelIdOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        val segments = path.split('/').filter(String::isNotEmpty)
        if (segments.size < 3 || !segments.contains(PROVIDER_PATH_SEGMENT)) return null
        if (!segments.last().endsWith(".m3u8")) return null

        return segments[segments.size - 2].takeIf(String::isNotEmpty)
    }

    companion object {
        const val DEFAULT_CHANNEL_LIST_BASE = "https://epg.ottp.eu.org"

        // Поток и программа передач у провайдера отдаются только по http, поэтому в манифесте
        // разрешён незашифрованный трафик: иначе Android блокирует и потоки, и EPG.
        const val DEFAULT_STREAM_BASE = "http://sewv654wfcsdwfi87fwvgbngh.siauliairsavlt.pw"
        const val PLAYLIST_PATH = "/edem/edem_epg_ico"
        const val STREAM_PATH = "/iptv"
        const val PROVIDER_PATH_SEGMENT = "iptv"

        /** Код — один сегмент пути: без пробелов и слэшей, иначе он ломает адрес. */
        val VALID_CODE = Regex("""[^\s/]+""")
    }
}
