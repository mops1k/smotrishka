package com.mops1k.smotrishka.tv.core.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация бэкапа медиатеки в файле настроек: секция переживает запись и чтение,
 * а файл старой схемы без неё читается и не ломает остальные настройки.
 */
class VodBackupCodecTest {

    private val codec = SettingsBackupCodec()

    @Test
    fun `медиатека переживает сохранение и чтение`() {
        val backup = SettingsBackup(
            exportedAtMillis = 1_760_000_000_000L,
            vod = SettingsBackup.Vod(
                url = "http://portal.example/api/v1/",
                key = "e8179fbc74ceedf-731012aca5e3898",
                favorites = listOf(
                    SettingsBackup.Favorite(
                        titleId = 147704,
                        title = "Выстрел",
                        year = 2014,
                        posterUrl = "http://portal/p.jpg",
                        previewPosterUrl = "http://portal/pl.jpg",
                        description = "Описание",
                        kind = "MULTISTREAM",
                        isFhd = true,
                        isHdr = true,
                        ageLimit = 16,
                        addedAtMillis = 5,
                    ),
                ),
            ),
        )

        val decoded = codec.decode(codec.encode(backup)) as SettingsBackupReadResult.Success

        assertThat(decoded.backup).isEqualTo(backup)
    }

    @Test
    fun `файл старой схемы без медиатеки читается`() {
        val json = """{"schema":1,"favorites":["channel-one"]}"""

        val decoded = codec.decode(json) as SettingsBackupReadResult.Success

        assertThat(decoded.backup.vod).isNull()
        assertThat(decoded.backup.favorites).containsExactly("channel-one")
    }
}
