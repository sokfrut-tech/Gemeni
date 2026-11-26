package com.jarvis.breeno.assistant.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMcpClientTest {
    @Test
    fun validatesHttpAndHttpsUrlsOnly() {
        assertEquals("https://mcp.example.com/rpc", JarvisMcpClient.validateMcpEndpointUrl("https://mcp.example.com/rpc"))
        assertEquals("http://192.168.1.50:3000/mcp", JarvisMcpClient.validateMcpEndpointUrl("http://192.168.1.50:3000/mcp"))
        assertNull(JarvisMcpClient.validateMcpEndpointUrl("file:///tmp/mcp"))
        assertNull(JarvisMcpClient.validateMcpEndpointUrl("javascript:alert(1)"))
        assertNull(JarvisMcpClient.validateMcpEndpointUrl("   "))
    }

    @Test
    fun buildsAndParsesJsonRpcToolsListAndCall() {
        val req = JarvisMcpClient.buildCallToolRequest("get_weather", """{"city":"Moscow"}""")
        assertTrue(req.contains("\"method\":\"tools/call\""))
        assertTrue(req.contains("\"name\":\"get_weather\""))

        val tools = JarvisMcpClient.parseToolsListResponse(
            """{"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"search_notes","description":"Поиск заметок"}]}}"""
        )
        assertEquals(1, tools.size)
        assertEquals("search_notes", tools.first().name)

        val callOut = JarvisMcpClient.parseCallToolResponse(
            """{"jsonrpc":"2.0","id":2,"result":{"content":[{"type":"text","text":"Найдено 3 заметки"}]}}"""
        )
        assertEquals("Найдено 3 заметки", callOut)
    }
}
