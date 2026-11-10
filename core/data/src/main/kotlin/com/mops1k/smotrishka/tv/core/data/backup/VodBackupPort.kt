package com.mops1k.smotrishka.tv.core.data.backup

/**
 * Секция медиатеки в файле настроек.
 *
 * Инфраструктура каналов не знает про домен медиатеки: она лишь спрашивает секцию у этого порта
 * и отдаёт её обратно при восстановлении. Реализация живёт в модуле данных медиатеки.
 */
interface VodBackupPort {

    /** Собирает секцию медиатеки; `null` — медиатека не подключена. */
    suspend fun read(): SettingsBackup.Vod?

    /**
     * Восстанавливает медиатеку из файла.
     *
     * @param vod секция из файла настроек.
     */
    suspend fun restore(vod: SettingsBackup.Vod)

    /** Заглушка для случаев, когда медиатека не собрана (тесты инфраструктуры каналов). */
    object None : VodBackupPort {

        override suspend fun read(): SettingsBackup.Vod? = null

        override suspend fun restore(vod: SettingsBackup.Vod) = Unit
    }
}
