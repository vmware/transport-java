/*
 * Copyright 2017-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus.model;

public class MonitorObject {
    private MonitorType type;
    private String from;
    private String channel;
    private Object data;

    public MonitorObject(MonitorType type, String channel, String from) {
        this.type = type;
        this.from = from;
        this.channel = channel;
    }

    public MonitorObject(MonitorType type, String channel, String from, Object data) {
        this(type, channel, from);
        this.data = data;
    }

    public MonitorType getType() {
        return type;
    }

    public void setType(MonitorType type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean isNewChannel() {
        return this.type == MonitorType.MonitorNewChannel;
    }

    public boolean hasData() {
        return this.data != null;
    }
}
