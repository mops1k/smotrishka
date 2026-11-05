package com.mops1k.smotrishka.tv.core.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * TDD-спецификация чтения плейлиста из файловой системы.
 *
 * Кодировка и разбор проверяются в `:core:playlist`, здесь — ввод-вывод через
 * `ContentResolver` и то, что ошибки чтения превращаются в понятный текст, а не в исключение.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistFileSourceTest {

    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private val source = PlaylistFileSource(resolver)

    @Test
    fun `utf8 плейлист читается и разбирается`(): Unit = runBlocking {
        val uri = Uri.parse("content://test/utf8.m3u")
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(M3U_UTF8.toByteArray(Charsets.UTF_8)))

        val result = source.read(uri) as M3uParseResult.Success

        assertThat(result.playlist.channels.map { it.name }).containsExactly("Первый", "Второй")
    }

    @Test
    fun `cp1251 плейлист декодируется без кракозябр`(): Unit = runBlocking {
        val uri = Uri.parse("content://test/cp1251.m3u")
        val bytes = M3U_UTF8.toByteArray(charset("windows-1251"))
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(bytes))

        val result = source.read(uri) as M3uParseResult.Success

        assertThat(result.playlist.channels.map { it.name }).contains("Первый")
    }

    @Test
    fun `недоступный файл даёт понятную ошибку, а не исключение`(): Unit = runBlocking {
        val result = source.read("content://test/missing.m3u")

        assertThat(result).isInstanceOf(M3uParseResult.Failure::class.java)
        assertThat((result as M3uParseResult.Failure).reason).contains("Не удалось открыть файл")
    }

    private companion object {
        const val M3U_UTF8 = "#EXTM3U\n" +
            "#EXTINF:-1 tvg-id=\"1\" group-title=\"Новости\",Первый\n" +
            "http://example.com/1.m3u8\n" +
            "#EXTINF:-1 tvg-id=\"2\" group-title=\"Новости\",Второй\n" +
            "http://example.com/2.m3u8\n"
    }
}
