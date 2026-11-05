package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Playlist
import com.mops1k.smotrishka.tv.core.model.StreamHeaders
import java.net.URI
import java.util.Locale

/**
 * Разбор M3U-плейлиста в доменную модель.
 *
 * Правила, на которые опирается парсер:
 * - `#EXTM3U` не обязателен: провайдеры его часто опускают;
 * - атрибуты `#EXTINF` могут быть в кавычках и без них;
 * - категория берётся из `group-title`, а если его нет — из `#EXTGRP`;
 * - имя канала может содержать запятую, поэтому оно берётся после всех атрибутов;
 * - архив описывается тегами `catchup`, `catchup-source`, `catchup-days`, а также
 *   провайдерскими `tvg-rec` и `tv_archive`/`tv_archive_duration` (приоритет у `catchup`);
 * - `#EXTVLCOPT` и `#KODIPROP` дают заголовки, без которых провайдер не отдаёт поток;
 * - относительный адрес потока разрешается относительно `baseUrl`; если `baseUrl` нет,
 *   запись пропускается (нерабочая ссылка хуже пропуска);
 * - строки-комментарии и мусор вне `#EXTINF` игнорируются.
 *
 * Парсер синхронный и не делает ввода-вывода: текст плейлиста передаётся целиком.
 */
class M3uParser {

    /**
     * Разбирает текст плейлиста.
     *
     * @param text содержимое M3U-файла;
     * @param baseUrl адрес, относительно которого разрешаются относительные ссылки
     *   (обычно адрес самого плейлиста).
     */
    fun parse(text: String, baseUrl: String? = null): M3uParseResult {
        val lines = text
            .removePrefix(BOM)
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()

        if (lines.isEmpty()) {
            return M3uParseResult.Success(Playlist(categories = emptyList(), channels = emptyList()))
        }

        val looksLikePlaylist = lines.any { it.startsWith(EXTM3U) || it.startsWith(EXTINF) }
        if (!looksLikePlaylist) {
            return M3uParseResult.Failure("Текст не похож на плейлист M3U: нет ни #EXTM3U, ни #EXTINF")
        }

        val categories = linkedMapOf<String, Category>()
        val channels = mutableListOf<Channel>()

        var pending: PendingChannel? = null
        var pendingGroup: String? = null
        var pendingHeaders = linkedMapOf<String, String>()
        var epgUrl: String? = null

        for (line in lines) {
            when {
                line.startsWith(EXTM3U) -> {
                    epgUrl = parseEpgUrl(line.removePrefix(EXTM3U))?.let { resolveUrl(it, baseUrl) }
                }

                line.startsWith(EXTINF) -> {
                    pending = parseExtInf(line.removePrefix(EXTINF))
                    pendingGroup?.let { group ->
                        pending = pending?.withGroup(group)
                        pendingGroup = null
                    }
                }

                line.startsWith(EXTGRP) -> {
                    val group = line.removePrefix(EXTGRP).trim()
                    val current = pending
                    when {
                        current != null && group.isNotEmpty() -> pending = current.withGroup(group)
                        group.isNotEmpty() -> pendingGroup = group
                    }
                }

                line.startsWith(EXTVLCOPT) -> recordHeader(line.removePrefix(EXTVLCOPT), pendingHeaders)

                line.startsWith(KODIPROP) -> recordHeader(line.removePrefix(KODIPROP), pendingHeaders)

                line.startsWith(COMMENT) -> Unit

                else -> {
                    val current = pending
                    val headers = pendingHeaders.toMap()
                    pending = null
                    pendingHeaders = linkedMapOf()

                    if (current != null) {
                        val streamUrl = resolveUrl(line, baseUrl)
                        if (streamUrl != null) {
                            channels += buildChannel(current, streamUrl, headers, categories)
                        }
                    }
                }
            }
        }

        return M3uParseResult.Success(
            Playlist(
                categories = categories.values.toList(),
                channels = channels,
                epgUrl = epgUrl,
            ),
        )
    }

    /** Адрес программы передач из заголовка `#EXTM3U`: `url-tvg` или `x-tvg-url`. */
    private fun parseEpgUrl(header: String): String? {
        var value: String? = null
        ATTRIBUTE.findAll(header).forEach { match ->
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            if (value == null && key in EPG_KEYS) {
                value = match.groupValues[2].ifEmpty { match.groupValues[3] }.trim()
            }
        }

        return value?.takeIf(String::isNotEmpty)
    }

    /** Разбирает строку `#EXTINF` (уже без префикса): длительность, атрибуты, имя. */
    private fun parseExtInf(rest: String): PendingChannel {
        var cursor = DURATION.matchAt(rest, 0)?.let { it.range.last + 1 } ?: 0
        val attributes = linkedMapOf<String, String>()

        while (true) {
            var keyStart = cursor
            while (keyStart < rest.length && rest[keyStart].isWhitespace()) keyStart++
            val match = ATTRIBUTE.find(rest, keyStart)
            if (match == null || match.range.first != keyStart) break
            val key = match.groupValues[1].lowercase(Locale.ROOT)
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            attributes[key] = value
            cursor = match.range.last + 1
        }

        val commaIndex = rest.indexOf(',', cursor)
        val name = when {
            commaIndex >= 0 -> rest.substring(commaIndex + 1).trim()
            // Файлы без запятой перед именем встречаются: берём остаток строки после атрибутов.
            else -> rest.substring(cursor).trim().trimStart(',').trim()
        }

        return PendingChannel(name = name, attributes = attributes)
    }

    /** Собирает канал из ожидающей записи `#EXTINF`, адреса потока и заголовков. */
    private fun buildChannel(
        pending: PendingChannel,
        url: String,
        headers: Map<String, String>,
        categories: MutableMap<String, Category>,
    ): Channel {
        val attributes = pending.attributes
        val name = pending.name.ifBlank { attributes["tvg-name"].orEmpty() }.trim()
        val channelId = attributes["tvg-id"]
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(::ChannelId)
            ?: ChannelId("url:$url")

        val group = attributes["group-title"]?.trim()?.takeIf(String::isNotEmpty) ?: pending.group
        val categoryId = group?.trim()?.takeIf(String::isNotEmpty)?.let { raw ->
            val id = normalizeKey(raw)
            categories.getOrPut(id) { Category(id = id, name = raw) }
            id
        }

        return Channel(
            id = channelId,
            name = name,
            streamUrl = url,
            logoUrl = attributes["tvg-logo"]?.trim()?.takeIf(String::isNotEmpty),
            categoryId = categoryId,
            epgId = attributes["tvg-id"]?.trim()?.takeIf(String::isNotEmpty),
            archive = buildArchive(attributes),
            headers = buildHeaders(headers),
        )
    }

    /** Заголовки потока: удобные поля плюс остальные значения как есть. */
    private fun buildHeaders(headers: Map<String, String>): StreamHeaders? {
        if (headers.isEmpty()) return null

        return StreamHeaders(
            userAgent = headers[USER_AGENT_HEADER],
            referrer = headers[REFERRER_HEADER],
            headers = headers - USER_AGENT_HEADER - REFERRER_HEADER,
        )
    }

    /** Запоминает заголовок из `#EXTVLCOPT`/`#KODIPROP` в виде «ключ=значение». */
    private fun recordHeader(raw: String, target: MutableMap<String, String>) {
        val key = raw.substringBefore('=').trim()
        if (key.isEmpty()) return
        target[key] = raw.substringAfter('=', "").trim()
    }

    /**
     * Разбирает теги архива.
     *
     * `catchup` задаёт формат и приоритетен; провайдерские `tvg-rec`,
     * `tv_archive`/`tv_archive_duration` описывают только глубину — для них считается
     * режим `default`.
     */
    private fun buildArchive(attributes: Map<String, String>): ArchiveInfo? {
        val catchup = attributes["catchup"]?.trim()?.takeIf(String::isNotEmpty)
        if (catchup != null) {
            return ArchiveInfo(
                mode = parseCatchupMode(catchup),
                source = attributes["catchup-source"]?.trim()?.takeIf(String::isNotEmpty),
                days = attributes["catchup-days"]?.trim()?.toIntOrNull() ?: 0,
            )
        }

        val days = attributes["tvg-rec"]?.trim()?.toIntOrNull()
            ?: attributes["tv_archive_duration"]?.trim()?.toIntOrNull()
        // Ноль означает «архива нет»: провайдер ставит tvg-rec="0" почти всем каналам.
        if (days != null && days > 0) {
            return ArchiveInfo(mode = CatchupMode.DEFAULT, days = days)
        }

        return if (attributes["tv_archive"]?.trim() == "1") {
            ArchiveInfo(mode = CatchupMode.DEFAULT)
        } else {
            null
        }
    }

    /** Разрешает адрес потока; `null` — ссылка непригодна (относительная без базы). */
    private fun resolveUrl(url: String, baseUrl: String?): String? = when {
        ABSOLUTE_URL.containsMatchIn(url) -> url
        baseUrl.isNullOrBlank() -> null
        else -> try {
            URI(baseUrl).resolve(url).toString()
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun parseCatchupMode(raw: String): CatchupMode =
        when (raw.lowercase(Locale.ROOT)) {
            "default" -> CatchupMode.DEFAULT
            "append" -> CatchupMode.APPEND
            "shift" -> CatchupMode.SHIFT
            "flussonic" -> CatchupMode.FLUSSONIC
            "flussonic-hls" -> CatchupMode.FLUSSONIC_HLS
            else -> CatchupMode.UNKNOWN
        }

    /** Идентификатор категории: имя без регистра, пробелы заменены на дефис. */
    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase(Locale.ROOT).replace(WHITESPACE, "-")

    /** Запись `#EXTINF`, ожидающая свой адрес потока. */
    private data class PendingChannel(
        val name: String,
        val attributes: Map<String, String>,
        val group: String? = null,
    ) {
        fun withGroup(value: String): PendingChannel = copy(group = value)
    }

    private companion object {
        const val BOM = "\uFEFF"
        const val EXTM3U = "#EXTM3U"
        const val EXTINF = "#EXTINF:"
        const val EXTGRP = "#EXTGRP:"
        const val EXTVLCOPT = "#EXTVLCOPT:"
        const val KODIPROP = "#KODIPROP:"
        const val COMMENT = "#"

        const val USER_AGENT_HEADER = "http-user-agent"
        const val REFERRER_HEADER = "http-referrer"

        val EPG_KEYS = setOf("url-tvg", "x-tvg-url")

        val DURATION = Regex("""-?\d+(?:\.\d+)?""")
        val ATTRIBUTE = Regex("""([\w-]+)=(?:"([^"]*)"|([^\s,]*))""")
        val WHITESPACE = Regex("""\s+""")
        val ABSOLUTE_URL = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*://""")
    }
}
