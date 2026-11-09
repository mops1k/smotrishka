package com.mops1k.smotrishka.tv.core.data.backup

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Чтение и запись файла настроек.
 *
 * Битый или чужой файл возвращается [SettingsBackupReadResult.Failure]: импорт не должен
 * ронять приложение.
 *
 * @param gson сериализатор JSON.
 */
class SettingsBackupCodec(private val gson: Gson = Gson()) {

    /**
     * Сериализует настройки.
     *
     * @param backup настройки;
     * @return JSON-файл.
     */
    fun encode(backup: SettingsBackup): String = gson.toJson(backup)

    /**
     * Читает файл настроек.
     *
     * @param json содержимое файла;
     * @return настройки или причина отказа.
     */
    fun decode(json: String): SettingsBackupReadResult {
        if (json.isBlank()) return SettingsBackupReadResult.Failure("Файл настроек пуст")

        val backup = try {
            gson.fromJson(json, SettingsBackup::class.java)
        } catch (e: JsonSyntaxException) {
            return SettingsBackupReadResult.Failure("Файл настроек повреждён")
        } catch (e: RuntimeException) {
            return SettingsBackupReadResult.Failure("Файл настроек не читается")
        } ?: return SettingsBackupReadResult.Failure("Файл настроек пуст")

        if (backup.schema != SettingsBackup.SCHEMA_VERSION) {
            return SettingsBackupReadResult.Failure(
                "Файл настроек версии ${backup.schema} не поддерживается",
            )
        }

        return SettingsBackupReadResult.Success(backup)
    }
}
