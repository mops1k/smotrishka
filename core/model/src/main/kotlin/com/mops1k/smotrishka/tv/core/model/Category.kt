package com.mops1k.smotrishka.tv.core.model

/**
 * Категория каналов из плейлиста (`group-title` или `#EXTGRP`).
 *
 * @param id нормализованное имя категории — стабильный ключ для запросов и фокуса;
 * @param name имя категории так, как его отдал провайдер.
 */
data class Category(
    val id: String,
    val name: String,
)
