package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * TDD-спецификация решения о сроке обновления гида.
 *
 * Главный признак — завершённость импорта. Случай 10.11.2026: на проекторе импорт оборвался,
 * программа осталась у 315 каналов из 3147, а планировщик по дальности сетки отложил обновление
 * на 54 часа. Покрытие каналов для решения не используется: части каналов нет в гидах вообще.
 */
class GuideRefreshPolicyTest {

    private val now: Instant = Instant.parse("2026-11-10T01:00:00Z")

    @Test
    fun `незавершённый импорт — обновляем сразу`() {
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = now.plus(54, ChronoUnit.HOURS),
            hasUnfinishedImport = true,
        )

        assertThat(delay).isEqualTo(0L)
    }

    @Test
    fun `импорт завершён и сетка уходит далеко — обновление откладывается`() {
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = now.plus(48, ChronoUnit.HOURS),
            hasUnfinishedImport = false,
        )

        assertThat(delay).isGreaterThan(0L)
    }

    @Test
    fun `импорт завершён, но сетка кончилась — обновляем сразу`() {
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = now.minus(1, ChronoUnit.HOURS),
            hasUnfinishedImport = false,
        )

        assertThat(delay).isEqualTo(0L)
    }

    @Test
    fun `программы в базе нет — обновляем сразу`() {
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = null,
            hasUnfinishedImport = false,
        )

        assertThat(delay).isEqualTo(0L)
    }

    @Test
    fun `до конца сетки меньше срока упреждения — обновляем сразу`() {
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = now.plus(1, ChronoUnit.HOURS),
            hasUnfinishedImport = false,
        )

        assertThat(delay).isEqualTo(0L)
    }
}
