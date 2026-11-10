package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Ключ портала медиатеки.
 *
 * Ключ даёт доступ к подписке и попадает в адреса потоков, поэтому он не должен появляться
 * ни в логах, ни в сообщениях об ошибках: [toString] намеренно скрывает значение.
 *
 * @property value ключ в том виде, в каком его выдал провайдер.
 */
@JvmInline
value class VodPortalKey(val value: String) {

    /** Строковое представление без секрета: только факт наличия ключа. */
    override fun toString(): String = "VodPortalKey(***)"
}
