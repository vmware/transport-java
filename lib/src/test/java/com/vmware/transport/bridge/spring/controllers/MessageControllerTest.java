/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bridge.spring.controllers;

import com.vmware.transport.bridge.Request;
import com.vmware.transport.bridge.RequestException;
import com.vmware.transport.bridge.Response;
import com.vmware.transport.broker.TestGalacticChannelConfig;
import com.vmware.transport.broker.TestMessageBrokerConnector;
import com.vmware.transport.bus.EventBus;
import com.vmware.transport.bus.EventBusImpl;
import com.vmware.transport.bus.model.Message;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageControllerTest {

    private EventBus bus;

    private MessageController controller;

    private Message message;
    private int count;

    @Before
    public void before() throws Exception {
        this.bus = new EventBusImpl();
        this.controller = new MessageController(bus);

        this.message = null;
        this.count = 0;
    }

    @Test
    public void testBridgeMessageWithInvalidRequest() {
        Exception ex = null;
        try {
            this.controller.bridgeMessage(new Request(), "channel1", null);
        } catch (Exception e) {
            ex = e;
        }

        assertRequestException(ex, "Request 'id' is missing");

        ex = null;
        try {
            this.controller.bridgeMessage(new Request(UUID.randomUUID(), null, null), "channel1", null);
        } catch (Exception e) {
            ex = e;
        }
        assertRequestException(ex, "Request 'request' is missing");

        ex = null;
        try {
            Request request = new Request();
            request.setRequest("request");
            request.setId(UUID.randomUUID());
            this.controller.bridgeMessage(request, "channel1", null);
        } catch (Exception e) {
            ex = e;
        }
        assertRequestException(ex, "Request 'version' is missing");
    }

    @Test
    public void testBridgeMessageWithValidRequest() throws Exception {
        this.bus.listenRequestStream("channel", message -> {
            this.message = message;
            this.count++;
        });

        Request bridgeRequest = new Request(UUID.randomUUID(), "test", "request-payload");

        this.controller.bridgeMessage(bridgeRequest, "channel", null);
        Assert.assertEquals(this.count, 1);
        Assert.assertEquals(this.message.getPayload(), bridgeRequest);
    }

    @Test
    public void testBridgeMessageWithSessionAttributes() throws Exception {
        this.bus.listenRequestStream("channel", message -> {
            this.message = message;
            this.count++;
        });

        Request bridgeRequest = new Request(UUID.randomUUID(), "test", "request-payload");

        Map<String, Object> headers = new HashMap<>();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put("attributeX", "valueX");
        headers.put(SimpMessageHeaderAccessor.SESSION_ATTRIBUTES, sessionAttributes);
        headers.put("headerX", "headerX_value");

        this.controller.bridgeMessage(bridgeRequest, "channel", headers);
        Assert.assertEquals(this.count, 1);
        Assert.assertEquals(this.message.getPayload(), bridgeRequest);
        Assert.assertEquals(bridgeRequest.getSessionAttribute("attributeX"), "valueX");
    }

    @Test
    public void testBridgeMessageToGalacticChannel() throws Exception {
        TestMessageBrokerConnector mbc1 = new TestMessageBrokerConnector("mbr1");
        TestGalacticChannelConfig galacticChannelConfig =
              new TestGalacticChannelConfig(mbc1.getMessageBrokerId(), "remote-channel-1");
        bus.registerMessageBroker(mbc1);

        this.bus.listenRequestStream("channel", message -> {
            this.message = message;
            this.count++;
        });
        this.bus.markChannelAsGalactic("channel", galacticChannelConfig);

        Request bridgeRequest = new Request(UUID.randomUUID(), "test", "request-payload");

        this.controller.bridgeMessage(bridgeRequest, "channel", null);
        Assert.assertEquals(this.count, 1);
        Assert.assertEquals("request-payload", this.message.getPayload());
    }

    @Test
    public void testHandleException() {
        Response response = this.controller.handleException(new Exception("request-exception"));
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getErrorCode(), 400);
        Assert.assertEquals(response.getErrorMessage(), "Request cannot be processed: request-exception");
    }

    @Test
    public void testHandleRequestException() {
        Response response = this.controller.handleRequestException(new Exception("request-exception"));
        Assert.assertNotNull(response);
        Assert.assertEquals(response.getErrorCode(), 400);
        Assert.assertEquals(response.getErrorMessage(), "Request cannot be processed: request-exception");
    }

    @Test
    public void testBridgeQueueMessageWithInvalidRequest() {
        Principal testPrincipal = new TestPrincipal();
        Exception ex = null;
        try {
            this.controller.bridgeQueueMessage(new Request(), "channel1", testPrincipal, null);
        } catch (Exception e) {
            ex = e;
        }
        assertRequestException(ex, "Request 'id' is missing");

        ex = null;
        try {
            this.controller.bridgeQueueMessage(new Request(
                    UUID.randomUUID(), null, null), "channel1", testPrincipal, null);
        } catch (Exception e) {
            ex = e;
        }
        assertRequestException(ex, "Request 'request' is missing");

        ex = null;
        try {
            Request request = new Request();
            request.setRequest("request");
            request.setId(UUID.randomUUID());
            this.controller.bridgeQueueMessage(request, "channel1", testPrincipal, null);
        } catch (Exception e) {
            ex = e;
        }
        assertRequestException(ex, "Request 'version' is missing");
    }

    @Test
    public void testBridgeQueueMessageWithValidRequest() throws Exception {
        this.bus.listenRequestStream("channel", message -> {
            this.message = message;
            this.count++;
        });

        Request bridgeRequest = new Request(UUID.randomUUID(), "test", "request-payload");
        Principal testPrincipal = new TestPrincipal();

        this.controller.bridgeQueueMessage(bridgeRequest, "channel", testPrincipal, null);
        Assert.assertEquals(this.count, 1);
        Assert.assertEquals(this.message.getPayload(), bridgeRequest);
        Assert.assertEquals(testPrincipal.getName(), this.message.getTargetUser());
    }

    private void assertRequestException(Exception ex, String expectedErrorMsg) {
        Assert.assertTrue(ex instanceof RequestException);
        Assert.assertEquals(ex.getMessage(), expectedErrorMsg);
    }

    class TestPrincipal implements Principal {
        @Override
        public String getName() {
            return "unique-id";
        }
    }
}
