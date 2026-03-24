package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.ChatMessageDto
import com.racketmatch.data.remote.dto.SendMessageRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

class ChatApi(private val client: HttpClient) {

    suspend fun getMessages(matchId: String): List<ChatMessageDto> =
        client.get("api/matches/$matchId/messages").body()

    suspend fun sendMessage(matchId: String, text: String): ChatMessageDto =
        client.post("api/matches/$matchId/messages") {
            setBody(SendMessageRequestDto(text))
        }.body()

    fun observeMessages(matchId: String): Flow<ChatMessageDto> = flow {
        val session: WebSocketSession = client.webSocketSession("ws/matches/$matchId/chat")
        for (frame in session.incoming) {
            if (frame is Frame.Text) {
                val dto = Json.decodeFromString<ChatMessageDto>(frame.readText())
                emit(dto)
            }
        }
    }
}
