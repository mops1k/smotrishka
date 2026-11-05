package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.xml.sax.SAXNotRecognizedException

/**
 * TDD-спецификация отключения внешних сущностей в XMLTV-парсере.
 *
 * Настоящая причина падения на устройстве: часть SAX-фич платформа не поддерживает.
 * Ошибка при выставлении одной фичи не должна ломать разбор всего файла.
 */
class XmltvParserSecurityTest {

    @Test
    fun `неподдерживаемая платформа фича не мешает разбору`() {
        var failed = 0

        applySecurityFeatures { name, _ ->
            if (name.contains("load-external-dtd")) {
                failed++
                throw SAXNotRecognizedException(name)
            }
        }

        assertThat(failed).isEqualTo(1)
    }

    @Test
    fun `все три фичи выставляются в false`() {
        val features = mutableMapOf<String, Boolean>()

        applySecurityFeatures { name, value -> features[name] = value }

        assertThat(features).hasSize(3)
        assertThat(features.values).containsExactly(false, false, false)
    }
}
