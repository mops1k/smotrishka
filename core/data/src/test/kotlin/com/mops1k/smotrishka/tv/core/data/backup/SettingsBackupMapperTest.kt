package com.mops1k.smotrishka.tv.core.data.backup

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import org.junit.Test

/**
 * TDD-спецификация переноса источника и перечислений в файл настроек и обратно.
 */
class SettingsBackupMapperTest {

    @Test
    fun `m3u источник переживает перенос`() {
        val source = PlaylistSource.M3uUrl(url = "https://example.com/list.m3u", name = "Дом")

        val restored = SettingsBackupMapper.toSource(SettingsBackupMapper.toBackup(source))

        assertThat(restored).isEqualTo(source)
    }

    @Test
    fun `файл плейлиста переживает перенос`() {
        val source = PlaylistSource.File(uri = "content://documents/1", name = "list.m3u")

        assertThat(SettingsBackupMapper.toSource(SettingsBackupMapper.toBackup(source)))
            .isEqualTo(source)
    }

    @Test
    fun `код провайдера переживает перенос`() {
        val source = PlaylistSource.IedemCode(code = "ABC123")

        assertThat(SettingsBackupMapper.toSource(SettingsBackupMapper.toBackup(source)))
            .isEqualTo(source)
    }

    @Test
    fun `xtream переживает перенос вместе с паролем`() {
        val source = PlaylistSource.Xtream(
            server = "http://provider.example.com:8080",
            username = "user",
            password = "secret",
        )

        assertThat(SettingsBackupMapper.toSource(SettingsBackupMapper.toBackup(source)))
            .isEqualTo(source)
    }

    @Test
    fun `неизвестный тип источника не восстанавливается`() {
        val restored = SettingsBackupMapper.toSource(SettingsBackup.Source(kind = "unknown"))

        assertThat(restored).isNull()
    }

    @Test
    fun `неизвестные значения перечислений заменяются умолчаниями`() {
        assertThat(SettingsBackupMapper.toVideoQuality("последнее")).isEqualTo(VideoQuality.AUTO)
        assertThat(SettingsBackupMapper.toAudioLanguage("")).isEqualTo(AudioLanguage.RUSSIAN)
        assertThat(SettingsBackupMapper.toAspectMode("zoom")).isEqualTo(AspectMode.FIT)
    }
}
