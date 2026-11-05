package com.mops1k.smotrishka.tv.core.model

/**
 * Тип источника плейлиста: определяет, как приложение получает каталог каналов.
 */
enum class SourceKind {
    /** Прямая ссылка на M3U/M3U8-плейлист. */
    M3U_URL,

    /** Логин Xtream Codes: сервер, логин, пароль. */
    XTREAM,

    /** Короткий код провайдера iedem.tv. */
    IEDEM_CODE,

    /** Плейлист из файловой системы (`content://` с persistable-разрешением). */
    FILE,
}
