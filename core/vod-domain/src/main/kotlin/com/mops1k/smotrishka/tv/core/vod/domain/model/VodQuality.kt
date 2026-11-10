package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Качество варианта потока, которое предлагает портал.
 *
 * Ключи совпадают с ключами поля `variants` ответа портала (`"480"`, `"720"`, `"1080"`,
 * `"1440"`, `"2160"`, `"auto"`), поэтому неизвестный ключ отбрасывается, а не ломает разбор.
 *
 * @property key значение ключа в ответе портала;
 * @property label подпись для интерфейса.
 */
enum class VodQuality(val key: String, val label: String) {

    /** 480p. */
    SD_480("480", "480p"),

    /** 720p. */
    HD_720("720", "720p"),

    /** 1080p. */
    FULL_HD_1080("1080", "1080p"),

    /** 1440p (QHD). */
    QHD_1440("1440", "1440p"),

    /** 2160p (4K). */
    UHD_2160("2160", "2160p"),

    /** Вариант «сам выбери по скорости» — `master.m3u8`. */
    AUTO("auto", "Авто");

    companion object {

        /**
         * Находит качество по ключу портала.
         *
         * @param key ключ из ответа портала;
         * @return качество либо `null` для незнакомого ключа.
         */
        fun fromKey(key: String): VodQuality? = entries.firstOrNull { it.key == key }
    }
}
