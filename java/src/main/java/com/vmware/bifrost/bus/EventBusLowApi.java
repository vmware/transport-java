/*
 * Copyright 2018 VMware, Inc. All rights reserved. -- VMware Confidential
 */
package com.vmware.bifrost.bus;

import com.vmware.bifrost.bus.model.Channel;
import com.vmware.bifrost.bus.model.Message;
import com.vmware.bifrost.bus.model.MessageObject;
import io.reactivex.Observable;
import io.reactivex.subjects.Subject;

import java.util.Map;

public interface EventBusLowApi {

    /**
     * Returns the map for monitoring externally (read-only).
     */
    Map<String, Channel> getChannelMap();

    /**
     * Get a subscription to the monitor channel.
     */
    Subject<Message> getMonitor();

    /**
     * Turn logging on/off
     *
     * @param flag turn logging on/off.
     */
    boolean enableMonitorDump(boolean flag);

    /**
     * Get current event bus logging state.
     */
    boolean isLoggingEnabled();

    /**
     * Close a channel. If the closer is the last subscriber, then the channel is destroyed.
     *
     * @param channel channel you want to close
     * @param from optional calling actor (for logging)
     */
    void close(String channel, String from);

    /**
     * Complete the channel stream.
     *
     * @param channel the name of the channel you want to complete. This channel will no longer broadcast to subscribers.
     * @param from name optional calling actor (for logging)
     */
    void complete(String channel, String from);

    /**
     * Complete the channel stream.
     *
     * @param channel channel you want to complete. This channel will no longer broadcast to subscribers.
     * @param from name optional calling actor (for logging)
     */
    void complete(Channel channel, String from);

    /**
     * A new channel is created by the first reference to it. All subsequent references to that channel are handed
     * the same stream to subscribe to. Accessing this method increments the channels reference count.
     * This method subscribes to both command and response messages. See below for specific directional methods.
     * This method is not filtered.
     *
     * This is a raw object that encapsulates the channel stream.
     *
     * @param channel the name of the channel you want.
     * @param from optional calling actor (for logging)
     */
    Channel getChannelObject(String channel, String from);

    /**
     * Get a subscribable stream from channel. If the channel doesn't exist, it will be created.
     *
     * @param channel name of the channel you want to subscribe to.
     * @param from optional calling actor (for logging)
     */
    Observable<Message> getChannel(String channel, String from);

    /**
     * Filter bus events that contain command messages only. Returns observable
     * that will emit a command Message to any subscribers.
     *
     * @param channel name of the channel you want to listen to.
     * @param from optional calling actor (for logging)
     */
    Observable<Message> getRequestChannel(String channel, String from);

    /**
     * Filter bus events that contain response messages only. Returns observable
     * that will emit a response Message to any subscribers.
     *
     * @param channel name of the channel you want to listen to.
     * @param from optional calling actor (for logging)
     */
    Observable<Message> getResponseChannel(String channel, String from);

    /**
     * Filter bus events that contain error messages only. Returns observable that
     * will emit an error Message to any subscribers.
     *
     * @param channel name of the channel you want to listen to.
     * @param from optional calling actor (for logging)*
     */
    Observable<Message> getErrorChannel(String channel, String from);

    /**
     * Transmit arbitrary data on a channel on the message bus if it exists.
     * This routine is called with traceback strings to allow for debugging and monitoring
     * bus traffic.
     *
     * @param channel channel to send to.
     * @param messageObject Message to be sent.
     * @param from optional calling actor (for logging)
     */
    void send(String channel, MessageObject messageObject, String from);

    /**
     * Transmit error on a channel if it exists.
     *
     * @param channel channel to send to.
     * @param error the error to be send.
     */
    void error(String channel, Error error);
}