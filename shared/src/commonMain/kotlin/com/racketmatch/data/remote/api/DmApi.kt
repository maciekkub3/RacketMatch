package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.ConversationDto
import com.racketmatch.data.remote.dto.DirectMessageDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SendDmRequestDto(val text: String)

class DmApi(private val client: HttpClient) {

    suspend fun getConversations(): List<ConversationDto> =
        client.get("api/dm/conversations").body()

    suspend fun getMessages(conversationId: String): List<DirectMessageDto> =
        client.get("api/dm/$conversationId/messages").body()

    suspend fun sendMessage(conversationId: String, text: String): DirectMessageDto =
        client.post("api/dm/$conversationId") {
            setBody(SendDmRequestDto(text))
        }.body()

    suspend fun markRead(conversationId: String) {
        client.put("api/dm/$conversationId/read")
    }

    fun observeMessages(conversationId: String): Flow<DirectMessageDto> = flow {
        val session: WebSocketSession = client.webSocketSession("ws/dm/$conversationId")
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val msg = Json.decodeFromString<DirectMessageDto>(frame.readText())
                emit(msg)
            }
        }
    }
}
