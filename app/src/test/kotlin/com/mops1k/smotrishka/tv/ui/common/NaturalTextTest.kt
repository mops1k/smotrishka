package com.mops1k.smotrishka.tv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация естественного порядка названий каналов.
 *
 * Замечание Александра 10.11.2026: в списке каналов «Зал № 11» стоял раньше «Зал № 2»,
 * потому что сортировка сравнивала строки посимвольно.
 */
class NaturalTextTest {

    @Test
    fun `номера залов идут по возрастанию`() {
        val sorted = listOf(
            "Зал № 13",
            "Зал № 2",
            "Зал № 10",
            "Зал № 1",
            "Зал № 20",
        ).sortedWith(NaturalText.COMPARATOR)

        assertThat(sorted).containsExactly(
            "Зал № 1",
            "Зал № 2",
            "Зал № 10",
            "Зал № 13",
            "Зал № 20",
        ).inOrder()
    }

    @Test
    fun `каналы без номера оставляют привычный порядок`() {
        val sorted = listOf("Первый", "Второй", "Третий").sortedWith(NaturalText.COMPARATOR)

        assertThat(sorted).containsExactly("Второй", "Первый", "Третий").inOrder()
    }

    @Test
    fun `регистр не влияет на порядок`() {
        val sorted = listOf("зал 2", "Зал 1").sortedWith(NaturalText.COMPARATOR)

        assertThat(sorted).containsExactly("Зал 1", "зал 2").inOrder()
    }

    @Test
    fun `числа в середине названия тоже сравниваются как числа`() {
        val sorted = listOf("Канал 100 HD", "Канал 9 HD", "Канал 20 HD")
            .sortedWith(NaturalText.COMPARATOR)

        assertThat(sorted).containsExactly("Канал 9 HD", "Канал 20 HD", "Канал 100 HD").inOrder()
    }
}
