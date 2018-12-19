/*
 * Copyright 2018 VMware, Inc. All rights reserved. -- VMware Confidential
 */
package com.vmware.bifrost.bridge.spring.config.interceptors;

import org.springframework.messaging.Message;

/**
 * Interface for interceptors that are able to view and/or modify the
 * {@link Message Messages} before they are sent to Bifrost channels.
 */
public interface BifrostStompInterceptor {

    /**
     * Invoked before the Message is actually sent to the channel.
     * This allows for modification of the Message if necessary.
     * If this method returns {@code null} then the actual
     * send invocation will not occur.
     */
    Message<?> preSend(Message<?> message);
}
