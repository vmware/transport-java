/*
 * Copyright 2018 VMware, Inc. All rights reserved. -- VMware Confidential
 */
package com.vmware.bifrost.bridge.spring.services;

import com.vmware.bifrost.bus.EventBus;
import com.vmware.bifrost.bus.EventBusImpl;
import com.vmware.bifrost.bus.model.Message;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        EventBusImpl.class,
        BifrostSubscriptionService.class,
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class BifrostSubscriptionServiceTest {

    @Autowired
    private EventBus bus;

    @Autowired
    private BifrostSubscriptionService subscriptionService;

    @MockBean
    private SimpMessagingTemplate msgTmpl;

    private String channel;
    private String channel2;
    private String destinationPrefix;

    @Before
    public void before() {
        this.channel = "local-channel";
        this.channel2 = "local-channel2";
        this.destinationPrefix = "/destinationPrefix/";
    }

    @Test
    public void testAddSubscriptionWithResponse() {

        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);

        Assert.assertTrue(bus.getApi().getChannelMap().containsKey(this.channel));
        Assert.assertEquals(bus.getApi().getChannelMap().get(this.channel).getRefCount().intValue(), 1);
        Assert.assertTrue(subscriptionService.getOpenChannels().contains(this.channel));

        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);
        BifrostSubscriptionService.BifrostSubscription sub =
              subscriptionService.getSubscriptions().iterator().next();

        Assert.assertEquals(sub.subId, "sub1");
        Assert.assertEquals(sub.sessionId, "session1");
        Assert.assertEquals(sub.channelName, this.channel);

        bus.sendResponseMessage(this.channel, "response1");
        Mockito.verify(msgTmpl).convertAndSend(this.destinationPrefix + this.channel, "response1");
    }

    @Test
    public void testAddSubscriptionWithMultipleSubscriptions() {

        // Verify that we can add subscriptions from different sessions with the same subscriptionId
        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);
        subscriptionService.addSubscription("sub1", "session2", this.channel, this.destinationPrefix);
        subscriptionService.addSubscription("sub2", "session1", this.channel, this.destinationPrefix);

        Assert.assertTrue(bus.getApi().getChannelMap().containsKey(this.channel));
        Assert.assertEquals(bus.getApi().getChannelMap().get(this.channel).getRefCount().intValue(), 1);

        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 3);

        bus.sendResponseMessage(this.channel, "response1");
        Mockito.verify(msgTmpl, Mockito.times(1)).convertAndSend(
              this.destinationPrefix + this.channel, "response1");

        subscriptionService.removeSubscription("sub1", "session1");
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 2);
    }

    @Test
    public void testRemoveLastSubscriptionToChannel() {

        this.bus.listenRequestStream(this.channel, (Message m) ->  {});
        this.bus.listenRequestStream(this.channel2, (Message m) ->  {});

        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);
        subscriptionService.addSubscription("sub2", "session1", this.channel2, this.destinationPrefix);
        subscriptionService.addSubscription("sub3", "session2", this.channel, this.destinationPrefix);

        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 3);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 2);

        // Remove all subscriptions for session1
        subscriptionService.unsubscribeSessionsAfterDisconnect("session1");

        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        Assert.assertTrue(subscriptionService.getOpenChannels().contains(this.channel));

        // Send a response to channel2 and verify that it's ignored since
        // there are no active subscriptions for channel2.
        bus.sendResponseMessage(this.channel2, "channel2-response1");
        Mockito.verify(msgTmpl, Mockito.never()).convertAndSend(
              this.destinationPrefix + this.channel2, "channel2-response1");

        // Send response to first channel and verify that it's sent back via
        // the messaging template.
        bus.sendResponseMessage(this.channel, "channel-response1");
        Mockito.verify(msgTmpl, Mockito.times(1)).convertAndSend(
              this.destinationPrefix + this.channel, "channel-response1");

        // Remove last subscription to first channel
        subscriptionService.removeSubscription("sub3", "session2");
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 0);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 0);

        // Verify that reponses to first channel are ignored.
        bus.sendResponseMessage(this.channel, "channel-response1");
        Mockito.verify(msgTmpl, Mockito.times(1)).convertAndSend(
              this.destinationPrefix + this.channel, "channel-response1");

        // Verify that we can re-subscribe successfully to first channel
        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);

        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        // Verify that responses are processed correctly after the re-subscription.
        bus.sendResponseMessage(this.channel, "channel-response1");
        Mockito.verify(msgTmpl, Mockito.times(2)).convertAndSend(
              this.destinationPrefix + this.channel, "channel-response1");
    }

    @Test
    public void testRemoveSubscriptionWithInvalidSubId() {
        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);

        subscriptionService.removeSubscription("sub1", "session2");
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);
    }

    @Test
    public void testAddSubscriptionWithAlreadySubscribedSubId() {
        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);
        subscriptionService.addSubscription("sub1", "session1", this.channel, this.destinationPrefix);
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 1);
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 1);

        subscriptionService.removeSubscription("sub1", "session1");
        Assert.assertEquals(subscriptionService.getOpenChannels().size(), 0);
        Assert.assertEquals(subscriptionService.getSubscriptions().size(), 0);
    }
}