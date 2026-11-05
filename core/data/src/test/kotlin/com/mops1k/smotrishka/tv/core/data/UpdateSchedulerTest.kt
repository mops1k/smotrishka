package com.mops1k.smotrishka.tv.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * TDD-спецификация планировщика: периодичность 6 часов, только при сети,
 * плюс разовый запуск при старте.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: UpdateScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = UpdateScheduler(workManager)
    }

    @Test
    fun `периодическая задача идёт раз в шесть часов при сети`() {
        scheduler.schedulePeriodic()

        val infos = workManager.getWorkInfosForUniqueWork(UpdateScheduler.PERIODIC_NAME).get()
        val info = infos.single()

        assertThat(info.state).isEqualTo(WorkInfo.State.ENQUEUED)
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(info.periodicityInfo?.repeatIntervalMillis)
            .isEqualTo(TimeUnit.HOURS.toMillis(UpdateScheduler.REFRESH_INTERVAL_HOURS))
    }

    @Test
    fun `разовое обновление ставится отдельной задачей`() {
        scheduler.syncNow()

        val infos = workManager.getWorkInfosForUniqueWork(UpdateScheduler.ONE_SHOT_NAME).get()

        assertThat(infos).hasSize(1)
        assertThat(infos.single().constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun `повторная постановка не плодит копии`() {
        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()

        val infos = workManager.getWorkInfosForUniqueWork(UpdateScheduler.PERIODIC_NAME).get()

        assertThat(infos).hasSize(1)
    }
}
