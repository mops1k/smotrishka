package com.mops1k.smotrishka.tv.core.model

/**
 * Профиль провайдера: как из пользовательского ввода получается источник каталога.
 *
 * @param id стабильный ключ профиля (используется в настройках и хранилище);
 * @param displayName имя, которое видит пользователь;
 * @param sourceKind тип источника, который вводит пользователь;
 * @param urlTemplate шаблон адреса с подстановкой кода/логина, если профиль именованный;
 * @param requiresServer нужен ли ввод адреса сервера (Xtream), а не кода.
 */
data class ProviderProfile(
    val id: String,
    val displayName: String,
    val sourceKind: SourceKind,
    val urlTemplate: String? = null,
    val requiresServer: Boolean = false,
)
