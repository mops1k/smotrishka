package com.mops1k.smotrishka.tv.core.data.watchnext

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Лента «Сейчас идёт» в TvProvider (Watch Next на домашнем экране Android TV).
 *
 * Публикация всегда полная: канал один, карточки удаляются и публикуются заново. Так не бывает
 * состояния, когда на домашнем экране висит канал, которого больше нет в каталоге.
 *
 * Любая ошибка провайдера (нет TvProvider, отказ в доступе) не роняет приложение: лента —
 * украшение домашнего экрана, телеканалы должны работать и без неё.
 *
 * @param context контекст приложения: TvProvider живёт на уровне процесса.
 */
class TvProviderWatchNextSync(
    private val context: Context,
) : WatchNextSync {

    private val helper = PreviewChannelHelper(context)

    override suspend fun publish(cards: List<WatchNextCard>) {
        withContext(Dispatchers.IO) {
            if (cards.isEmpty()) {
                clearChannel()
                return@withContext
            }
            val channelId = runCatching { ensureChannel(cards.first().deepLink) }
                .onFailure { Log.w(TAG, "не удалось получить канал ленты", it) }
                .getOrNull()
                ?: return@withContext
            Log.d(TAG, "публикуем ${cards.size} карточек в канал $channelId")
            deletePrograms(channelId)
            cards.forEachIndexed { index, card ->
                // Лаунчер сортирует по весу по убыванию, поэтому первому каналу отдаём
                // самый большой вес: иначе строка показывается справа налево.
                val weight = cards.size - 1 - index
                Log.d(TAG, "карточка ${index + 1}/${cards.size}: «${card.title}» weight=$weight")
                runCatching {
                    helper.publishPreviewProgram(program(channelId, weight, card))
                }.onFailure { error ->
                    Log.w(TAG, "карточка «${card.title}» не опубликована", error)
                }
            }
            Log.d(TAG, "лента обновлена: ${cards.size} карточек")
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { clearChannel() }
    }

    /**
     * Канал ленты: существующий или только что созданный.
     *
     * @param appLink ссылка, которую лаунчер откроет по нажатию на заголовок канала
     * (androidx.tvprovider без неё отказывается собирать канал).
     */
    private fun ensureChannel(appLink: String): Long {
        helper.allChannels.firstOrNull { it.displayName?.toString() == CHANNEL_NAME }?.let {
            Log.d(TAG, "канал ленты уже есть: ${it.id}")
            return it.id
        }
        val channel = PreviewChannel.Builder()
            .setDisplayName(CHANNEL_NAME)
            .setDescription(CHANNEL_DESCRIPTION)
            .setAppLinkIntentUri(Uri.parse(appLink))
            // Провайдер не создаёт канал без логотипа: берём иконку приложения из ресурсов.
            .setLogo(Uri.parse("android.resource://${context.packageName}/$BRAND_LOGO"))
            .build()
        val id = helper.publishDefaultChannel(channel)
        // Провайдер может вернуть пустой канал, если система запретила публикацию:
        // на Google TV канал приложения появляется после включения пользователем.
        if (id <= 0) throw IllegalStateException("провайдер вернул пустой канал ленты")
        Log.d(TAG, "канал ленты создан: $id")
        return id
    }

    /** Убирает канал ленты вместе с карточками. */
    private fun clearChannel() {
        val existing = runCatching { helper.allChannels }
            .onFailure { Log.w(TAG, "не удалось прочитать каналы ленты", it) }
            .getOrDefault(emptyList())
        if (existing.isEmpty()) {
            Log.d(TAG, "канал ленты отсутствует — очищать нечего")
            return
        }
        existing.filter { it.displayName?.toString() == CHANNEL_NAME }.forEach { channel ->
            runCatching {
                deletePrograms(channel.id)
                helper.deletePreviewChannel(channel.id)
                Log.d(TAG, "канал ленты удалён: ${channel.id}")
            }.onFailure { Log.w(TAG, "канал ленты не удалён: ${channel.id}", it) }
        }
    }

    /** Удаляет все карточки канала: они публикуются заново по текущей ленте. */
    private fun deletePrograms(channelId: Long) {
        runCatching {
            context.contentResolver.delete(
                TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                null,
                null,
            )
        }.onFailure { Log.w(TAG, "карточки канала $channelId не удалены", it) }
    }

    /** Карточка ленты: живой канал, текущая передача и ссылка сразу в плеер. */
    private fun program(channelId: Long, weight: Int, card: WatchNextCard): PreviewProgram {
        val builder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
            .setTitle(card.title)
            .setDescription(card.subtitle)
            .setLive(true)
            .setWeight(weight)
            .setIntentUri(Uri.parse(card.deepLink))
        // Постер обязан быть внешним URI: TvProvider не копирует байты картинки
        // (в 1.1.0 такого API нет), а лаунчер не читает файлы приложения. Поэтому — логотип канала.
        card.logoUrl?.takeIf { it.isNotBlank() }?.let { logo ->
            builder.setPosterArtUri(Uri.parse(logo))
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "SmotrishkaWatchNext"
        const val CHANNEL_NAME = "Сейчас идёт"
        const val CHANNEL_DESCRIPTION = "Каналы SmotrishkaTV с текущей передачей"

        /** Логотип канала ленты в ресурсах приложения. */
        const val BRAND_LOGO = "drawable/brand_mark"
    }
}
