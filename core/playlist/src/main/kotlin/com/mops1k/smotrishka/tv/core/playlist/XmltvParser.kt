package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.io.StringReader
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

/**
 * Разбор XMLTV в доменную модель.
 *
 * Разбор потоковый (SAX): файл программы передач на несколько дней и сотни каналов
 * не читается в память целиком. Gzip определяется по магическим байтам, потому что
 * провайдеры отдают `.xml.gz` под видом обычного `xmltv.php`.
 *
 * Программа без времени начала/конца или без канала пропускается: по такой передаче
 * невозможно ни показать ленту, ни построить архивный адрес.
 */
class XmltvParser {

    /**
     * Разбирает XMLTV из строки.
     *
     * @param xml содержимое файла.
     */
    fun parse(xml: String): XmltvParseResult =
        parseBytes(xml.removePrefix(BOM).toByteArray(Charsets.UTF_8))

    /**
     * Разбирает XMLTV из байтов (поддерживается gzip).
     *
     * @param bytes содержимое файла.
     */
    fun parseBytes(bytes: ByteArray): XmltvParseResult {
        if (bytes.isEmpty()) {
            return XmltvParseResult.Failure("Пустой файл программы передач")
        }

        return parseStream(ByteArrayInputStream(bytes))
    }

    /**
     * Разбирает XMLTV из потока.
     *
     * Поток читается последовательно и не разворачивается в память целиком: программа передач
     * у крупных провайдеров занимает сотни мегабайт после распаковки, и попытка прочитать её
     * в массив роняет приложение по OOM. Gzip распознаётся по магическим байтам.
     *
     * @param input поток с файлом XMLTV (возможно gzip);
     * @param from начало окна: передачи, закончившиеся раньше, отбрасываются;
     * @param to конец окна: передачи, начинающиеся позже, отбрасываются.
     */
    fun parseStream(
        input: InputStream,
        from: java.time.Instant? = null,
        to: java.time.Instant? = null,
    ): XmltvParseResult {
        val handler = XmltvHandler(from = from, to = to)

        return try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
            // Реальные файлы объявляют `<!DOCTYPE tv SYSTEM "xmltv.dtd">`, внешний DTD
            // подменяется пустым InputSource ниже. Фичи выставляются по одной: на части
            // платформ (Android) их нет, и попытка выставить такую роняла весь разбор.
            applySecurityFeatures { name, value -> factory.setFeature(name, value) }

            val parser = factory.newSAXParser()
            parser.xmlReader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }
            parser.parse(decompressed(input), handler)
            XmltvParseResult.Success(handler.toEpg())
        } catch (e: Exception) {
            XmltvParseResult.Failure("Файл программы передач повреждён: ${e.message ?: "ошибка разбора"}")
        }
    }

    /**
     * Оборачивает поток распаковщиком, если на входе gzip.
     *
     * @param input исходный поток.
     */
    private fun decompressed(input: InputStream): InputStream {
        val pushback = PushbackInputStream(BufferedInputStream(input), GZIP_MAGIC.size)
        val header = ByteArray(GZIP_MAGIC.size)
        val read = pushback.read(header)
        if (read > 0) {
            pushback.unread(header, 0, read)
        }

        val isGzip = read == GZIP_MAGIC.size &&
            header[0] == GZIP_MAGIC[0] &&
            header[1] == GZIP_MAGIC[1]

        return if (isGzip) GZIPInputStream(pushback) else pushback
    }

    private companion object {
        const val BOM = "\uFEFF"

        /** Первые байты gzip-файла: `1f 8b`. */
        val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
    }
}

/**
 * Отключает внешние сущности и загрузку DTD, игнорируя неподдерживаемые платформой фичи.
 *
 * Android-платформа не знает часть фич Xerces: исключение при выставлении одной фичи
 * раньше роняло разбор целого файла. Здесь каждая фича выставляется отдельно.
 *
 * @param setFeature как платформа выставляет фичу (имя, значение).
 */
internal fun applySecurityFeatures(setFeature: (String, Boolean) -> Unit) {
    listOf(
        "http://apache.org/xml/features/nonvalidating/load-external-dtd",
        "http://xml.org/sax/features/external-general-entities",
        "http://xml.org/sax/features/external-parameter-entities",
    ).forEach { name ->
        try {
            setFeature(name, false)
        } catch (e: Exception) {
            // Платформа не знает эту фичу — значит, внешние сущности и не загружаются.
        }
    }
}


/**
 * SAX-обработчик XMLTV: собирает каналы и передачи.
 *
 * @param from начало окна программы, `null` — без ограничения;
 * @param to конец окна программы, `null` — без ограничения.
 */
private class XmltvHandler(
    private val from: java.time.Instant? = null,
    private val to: java.time.Instant? = null,
) : DefaultHandler() {

    private val channels = mutableListOf<EpgChannel>()
    private val programmes = mutableListOf<Programme>()

    private var pendingChannel: PendingChannel? = null
    private var pendingProgramme: PendingProgramme? = null
    private var readingTag: String? = null
    private val text = StringBuilder()

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        when (val name = elementName(localName, qName)) {
            "channel" -> pendingChannel = PendingChannel(id = attributes?.getValue("id").orEmpty())

            "display-name" -> startText(name)

            "programme" -> {
                val start = XmltvTime.parse(attributes?.getValue("start"))
                val stop = XmltvTime.parse(attributes?.getValue("stop"))
                val channelId = attributes?.getValue("channel")

                pendingProgramme = if (start != null && stop != null && stop.isAfter(start) && !channelId.isNullOrBlank()) {
                    PendingProgramme(channelId = channelId, start = start, end = stop)
                } else {
                    null
                }
            }

            "title", "sub-title", "desc", "catchup-id" -> if (pendingProgramme != null) startText(name)

            "icon" -> {
                val src = attributes?.getValue("src")?.takeIf(String::isNotBlank)
                when {
                    pendingProgramme != null -> pendingProgramme?.iconUrl = src
                    pendingChannel != null -> pendingChannel?.iconUrl = src
                }
            }
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (readingTag != null) {
            text.append(ch, start, length)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (val name = elementName(localName, qName)) {
            "display-name" -> {
                // В реальных файлах у канала бывает несколько display-name (с lang):
                // берём первое непустое, обычно это основное имя.
                if (readingTag == name && pendingChannel != null) {
                    val parsed = text.toString().trim()
                    if (parsed.isNotEmpty() && pendingChannel?.displayName.isNullOrBlank()) {
                        pendingChannel?.displayName = parsed
                    }
                }
                finishText(name)
            }

            "title", "sub-title", "desc", "catchup-id" -> {
                val value = text.toString().trim().ifEmpty { null }
                when (name) {
                    "title" -> pendingProgramme?.title = value.orEmpty()
                    "sub-title" -> pendingProgramme?.subTitle = value
                    "desc" -> pendingProgramme?.description = value
                    "catchup-id" -> pendingProgramme?.catchupId = value
                }
                finishText(name)
            }

            "channel" -> {
                pendingChannel?.let { channel ->
                    if (channel.id.isNotBlank()) {
                        channels += EpgChannel(
                            id = channel.id,
                            displayName = channel.displayName.ifBlank { channel.id },
                            iconUrl = channel.iconUrl,
                        )
                    }
                }
                pendingChannel = null
            }

            "programme" -> {
                pendingProgramme?.let { programme ->
                    if (programme.title.isNotBlank() && inWindow(programme)) {
                        programmes += Programme(
                            channelId = programme.channelId,
                            title = programme.title,
                            subTitle = programme.subTitle,
                            description = programme.description,
                            start = programme.start,
                            end = programme.end,
                            catchupId = programme.catchupId,
                            iconUrl = programme.iconUrl,
                        )
                    }
                }
                pendingProgramme = null
            }
        }
    }

    /** Отдаёт разобранную программу: передачи необъявленных каналов отбрасываются. */
    fun toEpg(): Epg {
        val known = channels.mapTo(mutableSetOf()) { it.id }
        return Epg(
            channels = channels,
            programmes = programmes.filter { it.channelId in known },
        )
    }

    /** Попадает ли передача в окно: важно пересечение, а не полное вхождение. */
    private fun inWindow(programme: PendingProgramme): Boolean {
        if (from != null && !programme.end.isAfter(from)) return false
        if (to != null && !programme.start.isBefore(to)) return false
        return true
    }

    private fun startText(name: String) {
        readingTag = name
        text.clear()
    }

    private fun finishText(name: String) {
        if (readingTag == name) {
            readingTag = null
            text.clear()
        }
    }

    private fun elementName(localName: String?, qName: String?): String =
        // При namespaceAware=false SAX отдаёт localName пустой строкой, а не null,
        // поэтому пустое имя нужно пропускать, иначе ни один элемент не распознаётся.
        listOfNotNull(localName, qName)
            .firstOrNull(String::isNotEmpty)
            ?.lowercase()
            .orEmpty()

    private class PendingChannel(
        val id: String,
        var displayName: String = "",
        var iconUrl: String? = null,
    )

    private class PendingProgramme(
        val channelId: String,
        val start: java.time.Instant,
        val end: java.time.Instant,
        var title: String = "",
        var subTitle: String? = null,
        var description: String? = null,
        var catchupId: String? = null,
        var iconUrl: String? = null,
    )
}
