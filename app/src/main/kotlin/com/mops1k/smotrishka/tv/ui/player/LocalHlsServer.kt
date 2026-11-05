package com.mops1k.smotrishka.tv.ui.player

import java.io.File

/**
 * Локальный HLS-сервер для эфира.
 *
 * Отдаёт плейлист `live.m3u8` и файлы уже скачанных сегментов. Плейлист собирается нами, поэтому
 * на разрывах меток времени ставится `#EXT-X-DISCONTINUITY`: плеер корректно переживает скачок
 * PTS, а не встаёт в буферизацию (что и происходило при склейке байтов в один файл).
 *
 * Сегменты лежат на диске, в памяти только их список.
 */
class LocalHlsServer {

    private data class Entry(val name: String, val file: File?, val discontinuity: Boolean)

    /** Отступ старта от живого края; растёт, если канал не успевает отдавать сегменты. */
    @Volatile
    var startOffsetSeconds: Int = DEFAULT_START_OFFSET

    /**
     * Отдавать плейлист как VOD: `EXT-X-PLAYLIST-TYPE:VOD` и `EXT-X-ENDLIST` в конце.
     *
     * Нужен архиву: Media3 корректно начинает VOD с первого сегмента, а живой/событийный
     * плейлист ставит к краю окна.
     */
    @Volatile
    var vodPlaylist: Boolean = false

    /**
     * Отдавать плейлист как событие (`EXT-X-PLAYLIST-TYPE:EVENT`).
     *
     * Нужно для архива: без этого Media3 считает HLS живым, игнорирует стартовую позицию и
     * прыгает к краю окна — из-за чего вместо выбранной передачи показывался эфир.
     */
    @Volatile
    var eventPlaylist: Boolean = false

    private val entries = mutableListOf<Entry>()
    private val remoteUrls = mutableMapOf<String, String>()
    private var remoteCounter = 0
    private var removed = 0
    private var server: java.net.ServerSocket? = null

    /** Добавляет сегмент в плейлист. */
    fun add(file: File, discontinuity: Boolean) {
        synchronized(entries) {
            entries += Entry(file.name, file, discontinuity)
            // Держим плейлист коротким: плееру хватает последних двух минут.
            // Файл удаляем вместе с записью, иначе архив забивает диск (ENOSPC).
            while (entries.size > MAX_ENTRIES) {
                entries.removeAt(0)?.file?.delete()
                removed++
            }
        }
    }

    /** Сколько сегментов сейчас в плейлисте. */
    val entryCount: Int get() = synchronized(entries) { entries.size }

    /**
     * Добавляет сегмент ссылкой на провайдера, без скачивания.
     *
     * Так архив не занимает диск: плеер тянет сегменты напрямую, а сервер отдаёт редирект.
     */
    fun addRemote(url: String) {
        val name = "remote-${remoteCounter++}.ts"
        synchronized(remoteUrls) { remoteUrls[name] = url }
        synchronized(entries) {
            entries += Entry(name, null, false)
            while (entries.size > MAX_ENTRIES) {
                val evicted = entries.removeAt(0)
                synchronized(remoteUrls) { remoteUrls.remove(evicted.name) }
                removed++
            }
        }
    }

    /** Запускает сервер и возвращает адрес плейлиста. */
    fun start(): String {
        val socket = java.net.ServerSocket(0, 4, java.net.InetAddress.getByName(HOST))
        server = socket
        Thread({ acceptLoop(socket) }, "hls-server").apply {
            isDaemon = true
            start()
        }
        return "http://$HOST:${socket.localPort}/live.m3u8"
    }

    /** Останавливает сервер. */
    fun stop() {
        runCatching { server?.close() }
        server = null
        // Сегменты больше не нужны: освобождаем место на диске.
        synchronized(entries) {
            entries.forEach { it.file?.delete() }
            synchronized(remoteUrls) { remoteUrls.clear() }
            entries.clear()
        }
    }

    private fun acceptLoop(socket: java.net.ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: return
            Thread({ serve(client) }, "hls-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun serve(client: java.net.Socket) {
        client.use { connection ->
            runCatching {
                val reader = connection.getInputStream().bufferedReader()
                val requestLine = reader.readLine().orEmpty()
                while (reader.readLine()?.isNotEmpty() == true) Unit

                val path = requestLine.split(' ').getOrNull(1).orEmpty()
                val output = connection.getOutputStream()
                val name = path.substringAfterLast('/')
                val remote = synchronized(remoteUrls) { remoteUrls[name] }
                if (remote != null) {
                    // Сегмент живёт у провайдера: отдаём редирект, диск не используем.
                    output.write(
                        (
                            "HTTP/1.1 302 Found\r\n" +
                                "Location: $remote\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(),
                    )
                    output.flush()
                    return@runCatching
                }
                val body: ByteArray = when {
                    path.endsWith(".m3u8") -> playlist().toByteArray()
                    else -> readSegment(name)
                }
                android.util.Log.d(
                    "SmotrishkaArchive",
                    "serve $name bytes=${body.size} vod=$vodPlaylist entries=$entryCount",
                )

                output.write(
                    (
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: ${if (path.endsWith(".m3u8")) "application/vnd.apple.mpegurl" else "video/mp2t"}\r\n" +
                            "Content-Length: ${body.size}\r\n" +
                            "Cache-Control: no-store\r\n" +
                            "Connection: close\r\n\r\n"
                        ).toByteArray(),
                )
                output.write(body)
                output.flush()
            }
        }
    }

    private fun playlist(): String {
        val snapshot = synchronized(entries) { entries.toList() }
        val sequence = synchronized(entries) { removed }
        val builder = StringBuilder()
        builder.append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:11\n")
        // Номер последовательности растёт только при вытеснении сегментов: иначе плеер
        // считает окно «перемотанным» и перезапрашивает плейлист вхолостую.
        // Старт с отступом от края: плеер не упирается в последний скачанный сегмент и не
        // голодает, когда сеть отдаёт очередной кусок с задержкой.
        if (vodPlaylist) {
            builder.append("#EXT-X-PLAYLIST-TYPE:VOD\n")
        } else if (eventPlaylist) {
            builder.append("#EXT-X-PLAYLIST-TYPE:EVENT\n")
        } else {
            builder.append("#EXT-X-START:TIME-OFFSET=$startOffsetSeconds\n")
        }
        // У VOD номер последовательности всегда ноль: иначе плеер считает окно сдвинутым.
        builder.append("#EXT-X-MEDIA-SEQUENCE:${if (vodPlaylist) 0 else sequence}\n")
        snapshot.forEach { entry ->
            if (entry.discontinuity) builder.append("#EXT-X-DISCONTINUITY\n")
            builder.append("#EXTINF:10.0,\n")
            builder.append(entry.name).append('\n')
        }
        if (vodPlaylist) builder.append("#EXT-X-ENDLIST\n")
        return builder.toString()
    }

    private fun readSegment(name: String): ByteArray {
        val file = File(directory, name)
        return if (file.exists()) file.readBytes() else ByteArray(0)
    }

    /** Каталог сегментов задаётся при создании, чтобы сервер мог их отдавать. */
    var directory: File = File(".")
        set(value) {
            field = value
        }

    private companion object {
        const val HOST = "127.0.0.1"
        const val MAX_ENTRIES = 120

        /** Отступ старта по умолчанию, секунды (отрицательный — назад от края). */
        const val DEFAULT_START_OFFSET = -60
    }
}
