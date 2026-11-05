package com.mops1k.smotrishka.tv.core.model

/**
 * Канал линейного вещания.
 *
 * @param id стабильный идентификатор (tvg-id или производный от URL);
 * @param name название канала;
 * @param streamUrl прямой адрес потока;
 * @param logoUrl логотип, если провайдер его отдал;
 * @param categoryId категория канала, `null` — без группы;
 * @param epgId идентификатор программы для сопоставления с EPG;
 * @param archive параметры архива, `null` — архива нет;
 * @param headers заголовки потока, `null` — провайдер их не требует.
 */
data class Channel(
    val id: ChannelId,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val epgId: String? = null,
    val archive: ArchiveInfo? = null,
    val headers: StreamHeaders? = null,
)
