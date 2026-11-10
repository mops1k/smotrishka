package com.mops1k.smotrishka.tv.ui.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.EventLogger
import com.mops1k.smotrishka.tv.core.data.VideoQuality

/**
 * Единственное место, где создаётся ExoPlayer: им пользуются живой плеер каналов и VOD-плеер
 * медиатеки.
 *
 * Общий билдер держит одинаковыми таймауты, буфер, политику повторов, включение FFmpeg-декодера
 * для AC-3 и ограничение качества из настроек. Разница только в источнике: канал идёт «вживую»
 * с живым окном, фильм — обычным HLS с позицией.
 */
object PlayerEngine {

    /** Сколько раз Media3 повторяет неудачную загрузку сегмента. */
    private const val LOAD_RETRIES = 6

    /**
     * Создаёт настроенный ExoPlayer и сразу готовит поток.
     *
     * @param context контекст;
     * @param url адрес потока;
     * @param buffer параметры буфера из настроек;
     * @param quality ограничение качества из настроек;
     * @param live живой поток (`true`) или запись с позицией (`false`);
     * @param startPositionMs стартовая позиция для записи.
     */
    @OptIn(UnstableApi::class)
    fun build(
        context: Context,
        url: String,
        buffer: PlayerBuffer,
        quality: VideoQuality = VideoQuality.AUTO,
        live: Boolean = true,
        startPositionMs: Long = 0L,
    ): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(PlayerHttp.client)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager
        val lowRam = activityManager?.isLowRamDevice == true

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                buffer.minBufferMs,
                buffer.maxBufferMs,
                buffer.forPlaybackMs,
                buffer.forPlaybackAfterRebufferMs,
            )
            // Потолок по байтам: при достижении плеер выбрасывает старые сегменты, а не копит их.
            .setTargetBufferBytes(PlayerMemory.targetBufferBytes(lowRam))
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))

        val trackSelector = DefaultTrackSelector(context).also { PlayerQuality.apply(it, quality) }

        // Расширения (FFmpeg-декодер) включаются, когда аппаратного декодера нет:
        // каналы с AC-3 иначе молчат на устройствах без поддержки этого кодека.
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                // Диагностика Media3: выбранные дорожки, инициализация кодеков, первый кадр,
                // ошибки. Нужна на слабых приставках, где «Загрузка…» висит без явной ошибки.
                addAnalyticsListener(EventLogger())
            }
            .apply {
                // Живой отступ нужен только сетевому эфиру; для записи — обычный HLS
                // с явной позицией, иначе плеер прыгает к «краю» окна.
                val item = if (url.startsWith("file://") || !live) {
                    MediaItem.fromUri(url)
                } else {
                    PlayerLive.mediaItem(url)
                }
                setMediaItem(item, if (live) 0L else startPositionMs)
                prepare()
                playWhenReady = true
            }
    }
}
