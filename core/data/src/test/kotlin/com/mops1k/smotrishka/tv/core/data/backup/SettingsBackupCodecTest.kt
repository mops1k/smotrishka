package com.mops1k.smotrishka.tv.core.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация файла настроек: настройки переживают сериализацию,
 * а битый или чужой файл возвращается причиной, а не падением.
 */
class SettingsBackupCodecTest {

    private val codec = SettingsBackupCodec()

    @Test
    fun `настройки переживают сохранение и чтение`() {
        val backup = SettingsBackup(
            exportedAtMillis = 1_760_000_000_000L,
            source = SettingsBackup.Source(
                kind = "m3u",
                url = "https://example.com/list.m3u",
                name = "Провайдер",
            ),
            player = SettingsBackup.Player(bufferSeconds = 10, videoQuality = "HD"),
            guides = listOf(SettingsBackup.Guide(url = "https://example.com/g.xml", name = "Гид")),
            favorites = listOf("one", "two"),
            update = SettingsBackup.Update(includePrereleases = true),
        )

        val decoded = codec.decode(codec.encode(backup)) as SettingsBackupReadResult.Success

        assertThat(decoded.backup).isEqualTo(backup)
    }

    @Test
    fun `пустой файл возвращается причиной`() {
        val result = codec.decode("   ")

        assertThat((result as SettingsBackupReadResult.Failure).reason).contains("пуст")
    }

    @Test
    fun `битый json возвращается причиной`() {
        val result = codec.decode("{ это не json")

        assertThat(result).isInstanceOf(SettingsBackupReadResult.Failure::class.java)
    }

    @Test
    fun `чужая версия схемы не принимается`() {
        val result = codec.decode("""{"schema": 99, "favorites": []}""")

        assertThat((result as SettingsBackupReadResult.Failure).reason).contains("99")
    }

    @Test
    fun `неизвестные поля не мешают чтению`() {
        val result = codec.decode(
            """{"schema": 1, "favorites": ["one"], "futureField": true}""",
        )

        assertThat((result as SettingsBackupReadResult.Success).backup.favorites)
            .containsExactly("one")
    }
}
