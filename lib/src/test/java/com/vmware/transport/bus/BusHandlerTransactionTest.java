/*
 * Copyright 2017-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus;

import com.vmware.transport.bus.model.Message;
import com.vmware.transport.bus.model.MessageObjectHandlerConfig;
import com.vmware.transport.bus.model.MessageType;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.TestObserver;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class BusHandlerTransactionTest {

    private EventBus bus;
    private String channel = "#test-local";

    @Before
    public void setUp() throws Exception {
        this.bus = new EventBusImpl();
    }

    @Test
    public void unsubscribe() throws Exception {
        BusHandlerTransaction transaction = this.createTransaction();
        Assert.assertTrue(transaction.isSubscribed());
        transaction.unsubscribe();
        Assert.assertFalse(transaction.isSubscribed());

        transaction = this.createNullSubTransaction();
        Assert.assertFalse(transaction.isSubscribed());

        transaction.unsubscribe();
        Assert.assertFalse(transaction.isSubscribed());

        transaction = this.createNullHandlerTransaction();
        Assert.assertTrue(transaction.isSubscribed());

        transaction.unsubscribe();
        Assert.assertFalse(transaction.isSubscribed());


    }

    @Test
    public void isSubscribed() throws Exception {
        BusHandlerTransaction transaction = this.createTransaction();
        Assert.assertTrue(transaction.isSubscribed());
    }

    @Test
    public void tick() throws Exception {
        BusHandlerTransaction transaction = this.createTransaction();
        TestObserver<Message> observer =
                this.bus.getApi().getRequestChannel(channel, this.getClass().getName()).test();

        observer.assertSubscribed();
        observer.assertValueCount(0);

        transaction.tick("pop");
        observer.assertValueCount(1);
        transaction.tick("shop");
        observer.assertValueCount(2);

        transaction = this.createNullHandlerTransaction();
        transaction.tick("tick");
        observer.assertValueCount(2);
        transaction.tick("tock");
        observer.assertValueCount(2);

        transaction = this.createTransaction();
        transaction.tick("hot");
        observer.assertValueCount(3);
        transaction.tick("rock");
        observer.assertValueCount(4);

    }

    @Test
    public void error() throws Exception {
        BusHandlerTransaction transaction = this.createTransaction();
        TestObserver<Message> observer =
                this.bus.getApi().getErrorChannel(channel, this.getClass().getName()).test();

        observer.assertSubscribed();
        observer.assertValueCount(0);

        transaction.error("pop");
        observer.assertValueCount(1);
        transaction.error("shop");
        observer.assertValueCount(2);

        transaction = this.createNullHandlerTransaction();
        transaction.error("tick");
        observer.assertValueCount(2);
        transaction.error("tock");
        observer.assertValueCount(2);

        transaction = this.createTransaction();
        transaction.error("hot");
        observer.assertValueCount(3);
        transaction.error("rock");
        observer.assertValueCount(4);

    }

    @Test
    public void getObservableForRequests() throws Exception {
        BusHandlerTransaction<String> transaction =
                new BusHandlerTransaction<>(this.createSub(), this.createHandler());

        TestObserver<Message> observerRequest =
                this.bus.getApi().getRequestChannel(channel, this.getClass().getName()).test();

        TestObserver<Message> observerResponse =
                this.bus.getApi().getResponseChannel(channel, this.getClass().getName()).test();

        observerRequest.assertSubscribed();
        observerRequest.assertValueCount(0);

        observerResponse.assertSubscribed();
        observerResponse.assertValueCount(0);

        Observable<String> obs = transaction.getObservable(MessageType.MessageTypeRequest);

        Disposable sub = obs.subscribe(
                (String val) -> Assert.assertEquals("chickie & maggie", val)
        );

        transaction.tick("chickie & maggie");
        observerRequest.assertValueCount(1);
        observerResponse.assertValueCount(0);
        Assert.assertFalse(sub.isDisposed());

        transaction.error("foxy pup");

        observerRequest.assertValueCount(1);
        observerResponse.assertValueCount(0);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());

        sub = obs.subscribe(
                (String val) -> Assert.assertEquals("foxy pup", val)
        );

        transaction.tick("foxy pup");
        observerRequest.assertValueCount(2);
        observerResponse.assertValueCount(0);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());
    }

    @Test
    public void getObservableForResponses() throws Exception {
        BusHandlerTransaction<String> transaction =
                new BusHandlerTransaction<>(this.createSub(), this.createHandler());

        TestObserver<Message> observerRequest =
                this.bus.getApi().getRequestChannel(channel, this.getClass().getName()).test();

        TestObserver<Message> observerResponse =
                this.bus.getApi().getResponseChannel(channel, this.getClass().getName()).test();

        observerRequest.assertSubscribed();
        observerRequest.assertValueCount(0);

        observerResponse.assertSubscribed();
        observerResponse.assertValueCount(0);

        Observable<String> obs = transaction.getObservable(MessageType.MessageTypeResponse);

        Disposable sub = obs.subscribe(
                (String val) -> Assert.assertEquals("chick chick", val)
        );

        bus.sendResponseMessage(channel, "chick chick");
        observerRequest.assertValueCount(0);
        observerResponse.assertValueCount(1);
        Assert.assertFalse(sub.isDisposed());

        transaction.error("foxy pup");

        observerRequest.assertValueCount(0);
        observerResponse.assertValueCount(1);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());

        sub = obs.subscribe(
                (String val) -> Assert.assertEquals("kitty too", val)
        );

        bus.sendResponseMessage(channel, "kitty too");
        observerRequest.assertValueCount(0);
        observerResponse.assertValueCount(2);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());
    }

    @Test
    public void getObservableForErrors() throws Exception {
        BusHandlerTransaction<String> transaction =
                new BusHandlerTransaction<>(this.createSub(), this.createHandler());

        TestObserver<Message> observerRequest =
                this.bus.getApi().getRequestChannel(channel, this.getClass().getName()).test();

        TestObserver<Message> observerResponse =
                this.bus.getApi().getResponseChannel(channel, this.getClass().getName()).test();

        TestObserver<Message> observerError =
                this.bus.getApi().getErrorChannel(channel, this.getClass().getName()).test();

        observerRequest.assertSubscribed();
        observerRequest.assertValueCount(0);

        observerResponse.assertSubscribed();
        observerResponse.assertValueCount(0);

        observerError.assertSubscribed();
        observerError.assertValueCount(0);

        Observable<String> obs = transaction.getObservable(MessageType.MessageTypeError);

        Consumer<String> success =
                (String val) -> Assert.assertEquals("maggie is well", val);

        Consumer<Throwable> error =
                (Throwable err) -> Assert.assertEquals("maggie got sick", err.getMessage());


        Disposable sub = obs.subscribe(success, error);

        transaction.error("maggie got sick");

        observerRequest.assertValueCount(0);
        observerResponse.assertValueCount(0);
        observerError.assertValueCount(1);
        Assert.assertTrue(sub.isDisposed());

    }

    @Test
    public void getObservableForAllNonErrors() throws Exception {
        BusHandlerTransaction<String> transaction =
                new BusHandlerTransaction<>(this.createSub(), this.createHandler());

        TestObserver<Message> observerAll =
                this.bus.getApi().getChannel(channel, this.getClass().getName()).test();


        observerAll.assertSubscribed();
        observerAll.assertValueCount(0);

        Observable<String> obs = transaction.getObservable();

        Disposable sub = obs.subscribe(
                (String val) -> Assert.assertEquals("bork bork pups", val)
        );

        transaction.tick("bork bork pups");
        observerAll.assertValueCount(1);
        Assert.assertFalse(sub.isDisposed());


        bus.sendResponseMessage(channel, "bork bork pups");
        observerAll.assertValueCount(2);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());

        sub = obs.subscribe(
                (String val) -> Assert.assertEquals("mlem mlem pups", val)
        );

        transaction.tick("mlem mlem pups");
        observerAll.assertValueCount(3);
        Assert.assertFalse(sub.isDisposed());

        sub.dispose();
        Assert.assertTrue(sub.isDisposed());
    }

    @Test
    public void getObservableIfHandlerIsNull() throws Exception {
        BusHandlerTransaction<String> transaction =
                new BusHandlerTransaction<>(this.createSub(), null);


        Observable<String> obs = transaction.getObservable();
        Assert.assertNull(obs);

        obs = transaction.getObservable(MessageType.MessageTypeRequest);
        Assert.assertNull(obs);

    }

    private BusHandlerTransaction createTransaction() {
        return new BusHandlerTransaction(this.createSub(), this.createHandler());
    }

    private BusHandlerTransaction createNullSubTransaction() {
        return new BusHandlerTransaction(null, this.createHandler());
    }

    private BusHandlerTransaction createNullHandlerTransaction() {
        return new BusHandlerTransaction(this.createSub(), null);
    }

    private Disposable createSub() {
        Observable<Message> chan = this.bus.getApi().getChannel(channel, this.getClass().getName());
        return chan.subscribe();
    }

    private MessageHandler createHandler() {

        MessageObjectHandlerConfig config = new MessageObjectHandlerConfig();
        config.setSingleResponse(false);
        config.setSendChannel("#test-local");
        config.setReturnChannel("#test-local");
        MessageHandler handler = new MessageHandlerImpl(false, config, this.bus);
        handler.handle(null);
        return handler;
    }

}