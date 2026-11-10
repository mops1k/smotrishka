package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * TDD-спецификация транспорта портала: ключ в теле, JSON-заголовок, отказы портала и сетевые
 * неприятности превращаются в доменные причины, а не в исключения.
 *
 * Ответы синтетические: реальные выгрузки портала в тестах не используются.
 */
class VPortalClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: VPortalClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = VPortalClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `ключ подмешивается в тело каждого запроса`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"videoportal","items":[]}"""))

        client.post(server.url("/api/v1/").toString(), "secret-key", mapOf("cmd" to "flicks"))

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"key\":\"secret-key\"")
        assertThat(body).contains("\"cmd\":\"flicks\"")
    }

    @Test
    fun `запрос уходит методом POST с JSON-заголовком`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"videoportal","items":[]}"""))

        client.post(server.url("/api/v1/").toString(), "secret-key", emptyMap())

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
    }

    @Test
    fun `отказ по подписке становится доменной причиной`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"type":"error","description":"subscription required"}"""),
        )

        val result = client.post(server.url("/api/v1/").toString(), "bad", emptyMap())

        assertThat((result as VodResult.Failure).failure).isEqualTo(VodFailure.SubscriptionRequired)
    }

    @Test
    fun `неизвестная команда портала становится отказом с описанием`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"error","description":"illegal command"}"""))

        val result = client.post(server.url("/api/v1/").toString(), "key", emptyMap())

        val failure = (result as VodResult.Failure).failure
        assertThat(failure).isInstanceOf(VodFailure.PortalRejected::class.java)
        assertThat((failure as VodFailure.PortalRejected).description).isEqualTo("illegal command")
    }

    @Test
    fun `код ответа сервера сохраняется в причине`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = client.post(server.url("/api/v1/").toString(), "key", emptyMap())

        assertThat((result as VodResult.Failure).failure).isEqualTo(VodFailure.Http(500))
    }

    @Test
    fun `битый JSON не роняет запрос`() = runTest {
        server.enqueue(MockResponse().setBody("<html>502</html>"))

        val result = client.post(server.url("/api/v1/").toString(), "key", emptyMap())

        assertThat((result as VodResult.Failure).failure).isEqualTo(VodFailure.BadFormat)
    }

    @Test
    fun `некорректный адрес не доходит до сети`() = runTest {
        val result = client.post("не-адрес", "key", emptyMap())

        assertThat((result as VodResult.Failure).failure).isEqualTo(VodFailure.NotConfigured)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `недоступный сервер становится сетевой ошибкой`() = runTest {
        // Закрытый порт отвечает отказом сразу — без ожидания DNS и таймаутов.
        val unreachable = VodPortalConfig.from("http://127.0.0.1:1/api/v1/", "key")!!

        val result = client.post(unreachable.url.value, unreachable.key.value, emptyMap())

        assertThat((result as VodResult.Failure).failure).isInstanceOf(VodFailure.Network::class.java)
    }

    @Test
    fun `каталог запрашивается командой flicks со смещением`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"category","items":[],"count":0}"""))
        val config = VodPortalConfig.from(server.url("/api/v1/").toString(), "key")!!
        val adapter = VPortalCatalogAdapter(client, VPortalRequestTemplates())

        adapter.loadPage(config, com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId(1), 31, 31)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"cmd\":\"flicks\"")
        assertThat(body).contains("\"fid\":1")
        assertThat(body).contains("\"offset\":31")
    }

    @Test
    fun `поиск запрашивается командой search со строкой`() = runTest {
        server.enqueue(MockResponse().setBody("""{"type":"category","items":[]}"""))
        val config = VodPortalConfig.from(server.url("/api/v1/").toString(), "key")!!
        val adapter = VPortalSearchAdapter(client)

        adapter.search(config, "матрица", 0)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"cmd\":\"search\"")
        assertThat(body).contains("матрица")
    }
}
