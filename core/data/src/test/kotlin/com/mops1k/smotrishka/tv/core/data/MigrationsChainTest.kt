package com.mops1k.smotrishka.tv.core.data

import androidx.room.migration.Migration
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.net.JarURLConnection
import java.net.URL

/**
 * TDD-спецификация полноты миграций базы.
 *
 * Проверка появилась после ошибки 10.11.2026: версия базы поднята, миграция написана, но не
 * попала в контейнер — приложение падало при старте. Тест собирает классы миграций из пакета
 * `migrations` и сверяет цепочку с [DB_VERSION]: поднял версию и забыл миграцию — тест красный.
 */
class MigrationsChainTest {

    /** Пакет с классами миграций: каждый лежит в своём файле и собирается контейнером. */
    private val packagePath = "com/mops1k/smotrishka/tv/core/data/migrations"

    @Test
    fun `каждая версия схемы закрыта миграцией до текущей`() {
        val migrations = migrationClasses()
            .map { it.getDeclaredConstructor().newInstance() as Migration }
            .sortedBy { it.startVersion }

        assertThat(migrations).isNotEmpty()
        assertThat(migrations.first().startVersion).isEqualTo(1)
        migrations.zipWithNext().forEach { (first, second) ->
            assertThat(second.startVersion).isEqualTo(first.endVersion)
        }
        assertThat(migrations.last().endVersion).isEqualTo(DB_VERSION)
    }

    /** Классы миграций: пакет лежит и каталогом, и внутри jar — поддерживаем оба случая. */
    private fun migrationClasses(): List<Class<*>> {
        val loader = requireNotNull(javaClass.classLoader)
        return loader.getResources(packagePath).toList().flatMap { url: URL ->
            when (url.protocol) {
                "file" -> File(url.toURI())
                    .listFiles { file -> isMigrationClass(file.name) }
                    .orEmpty()
                    .map { file -> load(file.name.removeSuffix(".class")) }

                "jar" -> {
                    val connection = url.openConnection() as JarURLConnection
                    connection.jarFile.use { jar ->
                        jar.entries().asSequence()
                            .map { it.name }
                            .filter { name -> name.startsWith("$packagePath/") }
                            .map { name -> name.substringAfterLast('/') }
                            .filter(::isMigrationClass)
                            .map { name -> load(name.removeSuffix(".class")) }
                            .toList()
                    }
                }

                else -> emptyList()
            }
        }
    }

    /** Класс внутри пакета миграций. */
    private fun load(simpleName: String): Class<*> =
        Class.forName(packagePath.replace('/', '.') + "." + simpleName)

    /** Файл класса — миграция, а не модуль или вложенный класс. */
    private fun isMigrationClass(name: String): Boolean =
        MIGRATION_NAME.matches(name.removeSuffix(".class"))

    private companion object {
        /** Имя класса миграции: версия начала и конца схемы. */
        val MIGRATION_NAME = Regex("""Migration\d+To\d+""")
    }
}
