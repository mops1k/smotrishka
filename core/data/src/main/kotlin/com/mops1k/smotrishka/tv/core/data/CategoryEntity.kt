package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Категория каналов в базе.
 *
 * @param id стабильный ключ категории;
 * @param name имя, которое видит пользователь;
 * @param position порядок в рейле главного экрана.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
)
