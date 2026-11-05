package com.mops1k.smotrishka.tv.core.data

/**
 * Полнотекстовый поиск по каталогу и программе передач.
 *
 * Индекс (`search_index`, FTS4) собирается один раз после обновления каталога или гида и живёт
 * в базе. Одна строка поиска ищет сразу в трёх местах: названия каналов, названия категорий и
 * названия передач (текущих и ближайших).
 *
 * @param catalogDao каталог: категории и каналы;
 * @param epgDao программа передач;
 * @param searchDao полнотекстовый индекс.
 */
class SearchRepository(
    private val catalogDao: CatalogDao,
    private val epgDao: EpgDao,
    private val searchDao: SearchDao,
) {

    /**
     * Готовит индекс, если он пуст (первый запуск или после очистки базы).
     *
     * @param nowMs текущее время в миллисекундах эпохи.
     */
    suspend fun ensureIndexed(nowMs: Long = System.currentTimeMillis()) {
        if (searchDao.count() > 0L) return
        rebuild(nowMs)
    }

    /**
     * Пересобирает индекс целиком.
     *
     * Передачи берём окном около текущего момента: искать имеет смысл то, что идёт сейчас или
     * скоро начнётся, а весь гид (тысячи записей) только замедлил бы сборку на слабой приставке.
     *
     * @param nowMs текущее время в миллисекундах эпохи.
     */
    suspend fun rebuild(nowMs: Long = System.currentTimeMillis()) {
        val categories = catalogDao.categories()
        val channels = catalogDao.channels()
        val categoryNames = categories.associate { it.id to it.name }
        val channelNames = channels.associate { it.id to it.name }

        val rows = buildList {
            categories.forEach { category ->
                add(
                    SearchIndexEntity(
                        kind = KIND_CATEGORY,
                        refId = category.id,
                        title = category.name,
                        subtitle = SUBTITLE_CATEGORY,
                    ),
                )
            }
            channels.forEach { channel ->
                add(
                    SearchIndexEntity(
                        kind = KIND_CHANNEL,
                        refId = channel.id,
                        title = channel.name,
                        subtitle = categoryNames[channel.categoryId].orEmpty(),
                    ),
                )
            }
            epgDao.programmesInRange(nowMs - PROGRAMME_BACK_MS, nowMs + PROGRAMME_FORWARD_MS)
                .forEach { programme ->
                    add(
                        SearchIndexEntity(
                            kind = KIND_PROGRAMME,
                            refId = programme.channelId,
                            title = programme.title,
                            subtitle = channelNames[programme.channelId].orEmpty(),
                        ),
                    )
                }
        }

        searchDao.clear()
        rows.chunked(INSERT_CHUNK).forEach { chunk -> searchDao.insertAll(chunk) }
    }

    /**
     * Ищет совпадения по одной строке.
     *
     * @param raw строка из поля поиска.
     * @return найденные записи, не больше лимита индекса.
     */
    suspend fun search(raw: String): List<SearchHit> {
        val match = matchExpression(raw) ?: return emptyList()
        return searchDao.search(match).map { row ->
            SearchHit(
                kind = when (row.kind) {
                    KIND_CATEGORY -> SearchHitKind.CATEGORY
                    KIND_PROGRAMME -> SearchHitKind.PROGRAMME
                    else -> SearchHitKind.CHANNEL
                },
                refId = row.refId,
                title = row.title,
                subtitle = row.subtitle,
            )
        }
    }

    private companion object {
        const val KIND_CHANNEL = "channel"
        const val KIND_CATEGORY = "category"
        const val KIND_PROGRAMME = "programme"
        const val SUBTITLE_CATEGORY = "Категория"

        /** Окно передач в индексе: два часа назад и двенадцать вперёд. */
        const val PROGRAMME_BACK_MS = 2 * 60 * 60 * 1000L
        const val PROGRAMME_FORWARD_MS = 12 * 60 * 60 * 1000L

        /** Сколько строк индекса вставляем за одну транзакцию. */
        const val INSERT_CHUNK = 400

        /**
         * Собирает выражение FTS4 MATCH.
         *
         * Служебные символы выбрасываем (иначе запрос падает на кавычках и операторах), каждое
         * слово ищем по префиксу, слова объединяем через `AND`.
         *
         * @param raw строка пользователя.
         * @return выражение или `null`, если после чистки ничего не осталось.
         */
        fun matchExpression(raw: String): String? {
            val words = raw
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
                .split(' ')
                .filter { it.isNotEmpty() }
            if (words.isEmpty()) return null
            return words.joinToString(" AND ") { "$it*" }
        }
    }
}
