package com.mops1k.smotrishka.tv.core.data.update

/**
 * Файл, приложенный к релизу GitHub.
 *
 * @param name имя файла (`smotrishka_0.2.0.release.apk`);
 * @param downloadUrl прямой адрес загрузки;
 * @param sizeBytes размер файла в байтах, `0` если GitHub его не сообщил.
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
)
