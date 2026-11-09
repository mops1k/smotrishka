package com.mops1k.smotrishka.tv.core.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Загружает APK релиза в файл.
 *
 * Ошибки сети, обрыв посреди тела и проблемы записи на диск возвращаются
 * значением [ApkDownloadResult.Failure]: обновление не должно ронять приложение.
 *
 * @param client HTTP-клиент OkHttp.
 */
class ApkDownloader(private val client: OkHttpClient) {

    /**
     * Скачивает файл.
     *
     * @param url прямой адрес APK;
     * @param target файл, куда писать (существующий будет перезаписан);
     * @param onProgress процент загрузки, `-1` если размер неизвестен.
     */
    suspend fun download(
        url: String,
        target: File,
        onProgress: (Int) -> Unit = {},
    ): ApkDownloadResult = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()
        } catch (e: IllegalArgumentException) {
            return@withContext ApkDownloadResult.Failure("Некорректный адрес APK")
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use ApkDownloadResult.Failure(
                        "GitHub ответил кодом ${response.code}",
                    )
                }

                val body = response.body
                val total = body.contentLength()
                target.parentFile?.mkdirs()

                val written = body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            bytes += read
                            if (total > 0) onProgress(((bytes * 100) / total).toInt())
                        }
                        bytes
                    }
                }

                ApkDownloadResult.Success(file = target, bytes = written)
            }
        } catch (e: IOException) {
            // Обрыв соединения и недоступный диск одинаково не должны доходить до UI.
            ApkDownloadResult.Failure(e.message ?: "обрыв загрузки")
        } catch (e: SecurityException) {
            ApkDownloadResult.Failure("Нет доступа к файлу загрузки")
        } catch (e: IllegalStateException) {
            ApkDownloadResult.Failure(e.message ?: "загрузка прервана")
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
