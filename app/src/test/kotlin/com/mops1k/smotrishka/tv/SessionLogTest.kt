package com.mops1k.smotrishka.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TDD-спецификация журнала долгого сеанса.
 *
 * Пятичасовой просмотр нельзя воспроизводить по заказу, поэтому состояние плеера и памяти
 * пишется в короткий файл: после обычного сеанса видно, растёт ли память и не отстаёт ли буфер.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionLogTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `строки дописываются по порядку`() {
        SessionLog.append(context, "первая")
        SessionLog.append(context, "вторая")

        assertThat(SessionLog.read(context)).containsExactly("первая", "вторая").inOrder()
    }

    @Test
    fun `журнал не растёт бесконечно, старые строки уходят`() {
        repeat(SessionLog.MAX_LINES + 20) { index -> SessionLog.append(context, "строка $index") }

        val lines = SessionLog.read(context)

        assertThat(lines).hasSize(SessionLog.MAX_LINES)
        assertThat(lines.last()).isEqualTo("строка ${SessionLog.MAX_LINES + 19}")
    }
}
