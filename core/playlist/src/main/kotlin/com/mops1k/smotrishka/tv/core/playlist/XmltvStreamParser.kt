package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import java.io.InputStream
import java.io.StringReader
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Потоковый разбор XMLTV: передачи отдаются порциями, а не одним списком.
 *
 * Публичные гиды бывают в сотни мегабайт после распаковки, и держать их целиком в памяти
 * нельзя — приложение падает по `OutOfMemoryError`. Здесь обработчик копит передачи до
 * [batchSize] и отдаёт пачку вызывающему коду, который сразу пишет её в базу.
 *
 * @param batchSize сколько передач накапливать перед выдачей.
 */
class XmltvStreamParser(
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {

    /**
     * Разбирает поток и вызывает [onBatch] по мере накопления пачек.
     *
     * @param input поток XMLTV (gzip определяется по сигнатуре);
     * @param onBatch получает очередную пачку передач и каналов.
     * @return число разобранных передач или причина ошибки.
     */
    fun parse(
        input: InputStream,
        onBatch: (programmes: List<Programme>, channels: List<EpgChannel>) -> Unit,
    ): Int {
        val handler = StreamingHandler(batchSize = batchSize, onBatch = onBatch)
        return try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
            XmltvSecurity.apply(factory)
            val parser = factory.newSAXParser()
            parser.xmlReader.entityResolver = EntityResolverProvider.empty()
            parser.parse(decompressed(input), handler)
            handler.flush()
            handler.total
        } catch (e: Exception) {
            throw XmltvStreamException(e.message ?: "ошибка разбора", e)
        }
    }

    private fun decompressed(input: InputStream): InputStream {
        val buffered = input.buffered()
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) {
            java.util.zip.GZIPInputStream(buffered)
        } else {
            buffered
        }
    }

    private companion object {
        const val DEFAULT_BATCH_SIZE = 1000
    }
}

/** Ошибка потокового разбора XMLTV. */
class XmltvStreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * SAX-обработчик: держит в памяти только текущую передачу и пачку.
 */
private class StreamingHandler(
    private val batchSize: Int,
    private val onBatch: (List<Programme>, List<EpgChannel>) -> Unit,
) : DefaultHandler() {

    private val programmes = ArrayList<Programme>(batchSize)
    private val channels = ArrayList<EpgChannel>()
    private val pendingChannels = ArrayList<EpgChannel>()

    private var channelId: String? = null
    private var displayName: String? = null

    private var programmeChannelId: String? = null
    private var start: Instant? = null
    private var stop: Instant? = null
    private var title: String? = null
    private var subTitle: String? = null
    private var description: String? = null
    private var iconUrl: String? = null

    /** Иконка канала: в потоковом гиде `<icon src>` встречается и внутри `<channel>`. */
    private var channelIconUrl: String? = null

    private val text = StringBuilder()

    var total: Int = 0
        private set

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        text.setLength(0)
        when (qName) {
            "channel" -> {
                channelId = attributes?.getValue("id")
                displayName = null
                channelIconUrl = null
            }

            "programme" -> {
                programmeChannelId = attributes?.getValue("channel")
                start = parseTime(attributes?.getValue("start"))
                stop = parseTime(attributes?.getValue("stop"))
                title = null
                subTitle = null
                description = null
                iconUrl = null
            }

            "icon" -> {
                val src = attributes?.getValue("src")
                if (programmeChannelId != null) {
                    iconUrl = src
                } else if (channelId != null) {
                    // Иконка канала: раньше терялась, из-за чего в списках были только буквы.
                    channelIconUrl = src
                }
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        text.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (qName) {
            "display-name" -> if (channelId != null && displayName == null) {
                displayName = text.toString().trim().takeIf { it.isNotEmpty() }
            }

            "channel" -> {
                pendingChannels += EpgChannel(
                    id = channelId.orEmpty(),
                    displayName = displayName ?: channelId.orEmpty(),
                    iconUrl = channelIconUrl,
                )
                channelId = null
                displayName = null
                channelIconUrl = null
            }

            "title" -> if (programmeChannelId != null) title = text.toString().trim().takeIf { it.isNotEmpty() }
            "sub-title" -> if (programmeChannelId != null) subTitle = text.toString().trim().takeIf { it.isNotEmpty() }
            "desc" -> if (programmeChannelId != null) description = text.toString().trim().takeIf { it.isNotEmpty() }

            "programme" -> {
                val channel = programmeChannelId
                val from = start
                val to = stop
                val name = title
                if (channel != null && from != null && to != null && name != null) {
                    programmes += Programme(
                        channelId = channel,
                        title = name,
                        subTitle = subTitle,
                        description = description,
                        start = from,
                        end = to,
                        iconUrl = iconUrl,
                    )
                    total++
                }
                programmeChannelId = null
                start = null
                stop = null
                title = null
                subTitle = null
                description = null
                iconUrl = null
            }
        }
        if (text.isNotEmpty()) text.setLength(0)
        if (programmes.size >= batchSize) flush()
    }

    /** Отдаёт накопленную пачку вызывающему коду. */
    fun flush() {
        if (programmes.isEmpty() && pendingChannels.isEmpty()) return
        val batch = ArrayList(programmes)
        val channelBatch = ArrayList(pendingChannels)
        programmes.clear()
        pendingChannels.clear()
        channels.clear()
        onBatch(batch, channelBatch)
    }

    private fun parseTime(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val cleaned = trimmed.substringBefore(' ')
        return try {
            if (cleaned.length >= 14 && cleaned[8].isDigit()) {
                val builder = StringBuilder(cleaned.substring(0, 14))
                val offset = cleaned.substring(14).trim()
                val normalized = builder
                    .insert(4, '-').insert(7, '-')
                    .insert(10, 'T').insert(13, ':').insert(16, ':').toString()
                val zone = if (offset.length >= 5) {
                    java.time.ZoneOffset.of(
                        if (offset.startsWith("+") || offset.startsWith("-")) offset.take(6) else "+0000",
                    )
                } else if (offset.length == 1) {
                    java.time.ZoneOffset.UTC
                } else if (trimmed.length > 14) {
                    java.time.ZoneOffset.UTC
                } else {
                    java.time.ZoneOffset.UTC
                }
                Instant.from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(normalized).let {
                    java.time.LocalDateTime.from(it).toInstant(zone)
                })
            } else {
                Instant.parse(cleaned)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Помощники безопасности разбора (общие для парсеров XMLTV). */
internal object XmltvSecurity {

    /** Выставляет доступные фабрике защитные фичи, пропуская отсутствующие. */
    fun apply(factory: SAXParserFactory) {
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        ).forEach { (name, value) ->
            runCatching { factory.setFeature(name, value) }
        }
    }
}

/** Подстановка пустого DTD: реальные файлы ссылаются на внешний xmltv.dtd. */
internal object EntityResolverProvider {
    fun empty(): org.xml.sax.EntityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
}
