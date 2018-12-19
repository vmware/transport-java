/*
 * Copyright 2018 VMware, Inc. All rights reserved. -- VMware Confidential
 */
package com.vmware.bifrost.bridge.spring.config.interceptors;

import com.vmware.bifrost.bridge.spring.config.BifrostBridgeConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptorAdapter;

import java.util.List;

/**
 * {@link ChannelInterceptorAdapter} instance responsible to apply all registered
 * {@link BifrostStompInterceptor} instances in the correct order.
 */
public class BifrostChannelInterceptor extends ChannelInterceptorAdapter {

    private BifrostBridgeConfiguration configuration;

    public BifrostChannelInterceptor(BifrostBridgeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        List<BifrostBridgeConfiguration.StompInterceptorRegistration> interceptors =
              this.configuration.getRegisteredBifrostStompInterceptors();

        if (interceptors.isEmpty()) {
            // Do nothing if there are no registered custom interceptors.
            return message;
        }

        StompHeaderAccessor header = StompHeaderAccessor.wrap(message);
        String destination = header.getDestination();

        if (destination == null || destination.isEmpty()) {
            // Ignore messages without valid destination.
            return message;
        }

        StompCommand stompCommand = header.getCommand();

        // this.configuration.interceptors list should be sorted by priority.
        for (BifrostBridgeConfiguration.StompInterceptorRegistration
              interceptorRegistration : interceptors) {

            // Determine whether the current interceptor is applicable for the incoming
            // message.
            if (interceptorRegistration.commandSet.contains(stompCommand) &&
                  interceptorRegistration.destinationMatcher.match(destination)) {
                // apply the interceptor and update the message
                message = interceptorRegistration.interceptor.preSend(message);
            }
            if (message == null) {
                break;
            }
        }
        return message;
    }
}
