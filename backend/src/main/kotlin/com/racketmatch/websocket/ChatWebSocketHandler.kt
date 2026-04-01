package com.racketmatch.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.racketmatch.api.dto.ChatMessageDto
import com.racketmatch.config.JwtService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class ChatWebSocketHandler(
    private val jwtService: JwtService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    // matchId -> set of active sessions
    private val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val matchId = extractMatchId(session) ?: run { session.close(CloseStatus.BAD_DATA); return }
        // Authenticate via query param token or Authorization header
        val token = extractToken(session) ?: run { session.close(CloseStatus.NOT_ACCEPTABLE); return }
        if (!jwtService.isTokenValid(token)) { session.close(CloseStatus.NOT_ACCEPTABLE); return }
        rooms.getOrPut(matchId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val matchId = extractMatchId(session) ?: return
        rooms[matchId]?.remove(session)
        if (rooms[matchId]?.isEmpty() == true) rooms.remove(matchId)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // Mobile sends messages via REST POST, not via WS. This handler is receive-only.
        // Ignore incoming WS frames for now.
    }

    fun broadcast(matchId: UUID, dto: ChatMessageDto) {
        val json = objectMapper.writeValueAsString(dto)
        rooms[matchId]?.filter { it.isOpen }?.forEach { it.sendMessage(TextMessage(json)) }
    }

    private fun extractMatchId(session: WebSocketSession): UUID? {
        val path = session.uri?.path ?: return null
        // path: /ws/matches/{matchId}/chat
        val segments = path.split("/")
        val idx = segments.indexOf("matches")
        if (idx < 0 || idx + 1 >= segments.size) return null
        return runCatching { UUID.fromString(segments[idx + 1]) }.getOrNull()
    }

    private fun extractToken(session: WebSocketSession): String? {
        // Try Authorization header first, then ?token= query param
        val header = session.handshakeHeaders["Authorization"]?.firstOrNull()
        if (header != null && header.startsWith("Bearer ")) return header.removePrefix("Bearer ")
        return session.uri?.query?.split("&")
            ?.firstOrNull { it.startsWith("token=") }
            ?.removePrefix("token=")
    }
}
