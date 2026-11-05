package com.mops1k.smotrishka.tv.ui.player

import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Отдаёт растущий файл как один непрерывный поток.
 *
 * Плеер видит обычный HTTP-поток без длины и без конца: сервер дописывает данные по мере записи
 * и не закрывает соединение. Поэтому нет «стены» длительности, на которой растущий файл
 * останавливался, и нет перепривязки к окну провайдера, которая давала зацикливание.
 *
 * @param file файл, в который пишется эфир.
 */
class LocalStreamServer(private val file: File) {

    private var server: ServerSocket? = null
    private var worker: Thread? = null

    /** Запускает сервер на свободном порту localhost. */
    fun start(): String {
        val socket = ServerSocket(0, 1, InetAddress.getByName(HOST))
        server = socket
        worker = Thread({ acceptLoop(socket) }, "timeshift-server").apply {
            isDaemon = true
            start()
        }
        return "http://$HOST:${socket.localPort}/live.ts"
    }

    /** Останавливает сервер и закрывает соединения. */
    fun stop() {
        runCatching { server?.close() }
        worker?.interrupt()
        server = null
        worker = null
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: return
            Thread({ serve(client) }, "timeshift-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { connection ->
            runCatching {
                val request = connection.getInputStream().bufferedReader()
                while (request.readLine()?.isNotEmpty() == true) {
                    // Заголовки запроса не нужны: отдаём единственный поток.
                }
                val output = connection.getOutputStream()
                output.write(HEADERS)
                output.flush()
                pump(output)
            }
        }
    }

    /** Пишет новые байты файла, пока клиент не отключился. */
    private fun pump(output: OutputStream) {
        var offset = 0L
        while (server?.isClosed == false) {
            val length = file.length()
            if (length > offset) {
                val chunk = file.readBytesFrom(offset)
                output.write(chunk)
                output.flush()
                offset += chunk.size
            } else {
                Thread.sleep(POLL_MS)
            }
        }
    }

    private fun File.readBytesFrom(offset: Long): ByteArray =
        java.io.RandomAccessFile(this, "r").use { access ->
            access.seek(offset)
            val available = (access.length() - offset).toInt().coerceAtLeast(0)
            ByteArray(available).also { access.readFully(it) }
        }

    private companion object {
        const val HOST = "127.0.0.1"
        const val POLL_MS = 100L

        /** Ответ без Content-Length: соединение живёт, пока идёт эфир. */
        val HEADERS: ByteArray = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: video/mp2t\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")
        }.toByteArray()
    }
}
