package com.mops1k.smotrishka.tv.core.vod.data.backup

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleKind
import org.junit.Test

/**
 * Перевод избранного медиатеки в плоский вид файла настроек и обратно: формат файла не должен
 * терять ни постеры, ни метки качества.
 */
class VodBackupMapperTest {

    @Test
    fun `избранное переживает перевод в плоский вид и обратно`() {
        val favorite = VodFavorite(
            title = VodTitle(
                id = VodTitleId(147673),
                title = "Сезон 1",
                year = 2026,
                posterUrl = "http://portal/p.jpg",
                previewPosterUrl = "http://portal/pl.jpg",
                description = "Описание",
                kind = VodTitleKind.MULTISTREAM,
                durationSeconds = 173,
                isFhd = true,
                is4k = false,
                isHdr = false,
                ageLimit = 18,
            ),
            addedAtMillis = 1_000,
        )

        val restored = favorite.toBackup().toDomain()

        assertThat(restored).isEqualTo(favorite)
    }

    @Test
    fun `неизвестный вид карточки читается как одиночный поток`() {
        val flat = com.mops1k.smotrishka.tv.core.data.backup.SettingsBackup.Favorite(
            titleId = 1,
            title = "Фильм",
            kind = "НЕИЗВЕСТНО",
        )

        assertThat(flat.toDomain().title.kind).isEqualTo(VodTitleKind.STREAM)
    }
}
