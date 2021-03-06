/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package samples.config;

import com.vmware.transport.bridge.spring.config.TransportBridgeConfiguration;
import com.vmware.transport.bridge.spring.config.TransportBridgeConfigurer;
import com.vmware.transport.bridge.spring.config.interceptors.AnyDestinationMatcher;
import com.vmware.transport.bridge.spring.config.interceptors.StartsWithDestinationMatcher;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import samples.interceptors.DropStompMessageInterceptor;
import samples.interceptors.MessageLoggerInterceptor;

import java.security.Principal;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer
      implements TransportBridgeConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue", "/pub");
        //config.enableStompBrokerRelay("/topic"); // enable rabbit as source of truth instead of local broker.
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        SocketHandshakeHandler socketHandshakeHandler = new SocketHandshakeHandler();

        HandshakeInterceptor sampleHandshakeInterceptor = new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
                // Attributes map holds session attributes.
                // Attributes added here later can be accessed via
                // com.vmware.transport.bridge.Request.getSessionAttributes() and getSessionAttribute() methods.
                attributes.put("remoteAddress", request.getRemoteAddress());
                return true;
            }

            @Override
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
                // do nothing
            }
        };

        registry.addEndpoint("/transport")
                .setHandshakeHandler(socketHandshakeHandler)
                .addInterceptors(sampleHandshakeInterceptor)
                .setAllowedOrigins("*");
        registry.addEndpoint("/fabric")
                .setHandshakeHandler(socketHandshakeHandler)
                .addInterceptors(sampleHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Override
    public void registerTransportDestinationPrefixes(TransportBridgeConfiguration configuration) {
        configuration.addTransportDestinationPrefixes("/topic", "/pub", "/user/queue");
    }

    @Override
    public void registerTransportStompInterceptors(TransportBridgeConfiguration configuration) {

        MessageLoggerInterceptor logger1 =
              new MessageLoggerInterceptor("[Logger1] SUBSCRIBE message to channel: ");
        MessageLoggerInterceptor logger2 =
              new MessageLoggerInterceptor("[Logger2] SUBSCRIBE message to channel: ");

        // Adds a new interceptor that logs every SUBSCRIBE message sent to
        // any channels.
        configuration.addTransportStompInterceptor(
              logger1,
              EnumSet.of(StompCommand.SUBSCRIBE),
              new AnyDestinationMatcher(),
              1000);

        // Adds a new interceptor that logs every SUBSCRIBE message sent to
        // "/topic/servbot/" and "/topic/sample-stream/" channels.
        // Note that due to the higher priority logger2 will always be invoked before logger1.
        configuration.addTransportStompInterceptor(
              logger2,
              EnumSet.of(StompCommand.SUBSCRIBE),
              new StartsWithDestinationMatcher("/topic/sample-stream/", "/topic/servbot/"),
              500);

        // Adds a new interceptor that drops every 5th request to some-service channel.
        configuration.addTransportStompInterceptor(
              new DropStompMessageInterceptor(5),
              EnumSet.of(StompCommand.SEND),
              new StartsWithDestinationMatcher("/pub/some-service"),
              1000);
    }
}

class SocketHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        return new SessionPrincipal(UUID.randomUUID().toString());
    }
}

class SessionPrincipal implements Principal {
    private String name;
    public SessionPrincipal(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
