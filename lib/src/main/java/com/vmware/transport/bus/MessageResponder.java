/*
 * Copyright 2017-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus;

import com.vmware.transport.bus.model.Message;
import io.reactivex.disposables.Disposable;

import java.util.function.Function;

/**
 * MessageResponder allows consumers to generate responses to incoming requests on configured send channel.
 * @param <T> the Type your generator will return as a response, that is sent on the configured return channel.
 */
public interface MessageResponder<T> {

    /**
     * Generate a response with a Function that accepts a Message class and returns a generic type.
     * @param generator
     * @return
     */
    Disposable generate(Function<Message, T> generator);

    /**
     * Send a new response message with payload to the configured return channel.
     * @param payload
     */
    void tick(T payload);

    /**
     * Send an error message with payload to the configured return channel.
     * @param payload
     */
    void error(T payload);

    /**
     * Dispose of subscription, if using stream responder.
     */
    void close();

    /**
     * Check if Disposable as been closed / un-subscribed.
     * @return true if disposed.
     */
    boolean isClosed();
}
