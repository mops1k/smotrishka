package com.mops1k.smotrishka.tv.core.model

/**
 * Настроенный источник каталога каналов.
 *
 * Источник — не сам плейлист, а способ его получить: приложение хранит именно его
 * и обновляет каталог, не спрашивая данные заново.
 */
sealed interface PlaylistSource {

    /** M3U по прямой ссылке. */
    data class M3uUrl(
        val url: String,
        val name: String? = null,
    ) : PlaylistSource

    /** Xtream Codes: `player_api.php` на сервере провайдера. */
    data class Xtream(
        val server: String,
        val username: String,
        val password: String,
    ) : PlaylistSource

    /** Провайдер iedem.tv по короткому коду из личного кабинета. */
    data class IedemCode(
        val code: String,
    ) : PlaylistSource

    /**
     * Плейлист из файловой системы.
     *
     * `uri` — адрес из системного выбора файла (SAF) с persistable-разрешением: по нему
     * каталог можно перечитать позже, не спрашивая файл заново.
     */
    data class File(
        val uri: String,
        val name: String? = null,
    ) : PlaylistSource
}
