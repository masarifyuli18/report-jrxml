package com.datacomsolusindo.reports.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.GenericMessage
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import org.springframework.web.socket.messaging.SessionConnectEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic")
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun configureWebSocketTransport(registration: WebSocketTransportRegistration) {
        registration.setMessageSizeLimit(128 * 1024)
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/_websocket").setAllowedOrigins("*")
    }
}

@Component
class WebSocketEventListener {
    val logger: Logger = LoggerFactory.getLogger(WebSocketEventListener::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun doSomethingAfterStartup() {
        logger.info("communication server start")
    }

    @EventListener
    fun onConnect(event: SessionConnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
    }

    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        logger.info("client disconnect with sessionId ${event.sessionId}")
    }

    @EventListener
    fun handleSessionSubscribeEvent(event: SessionSubscribeEvent) {
        val message = event.message as GenericMessage<*>
        val simDestination = message.headers["simpDestination"] as String?
        val accessor = StompHeaderAccessor.wrap(event.message)
        logger.info("client ${accessor.sessionId} subscription $simDestination")
    }

}

@Controller
class SocketController {

    @MessageMapping("/report/state/{code}")
    @SendTo("/topic/report/state/{code}")
    @Throws(Exception::class)
    fun reportState(
        message: MutableMap<*, *>, @DestinationVariable("code") code: String
    ): MutableMap<*, *> {
        return message
    }

}