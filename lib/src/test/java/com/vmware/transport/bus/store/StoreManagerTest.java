/*
 * Copyright 2019-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus.store;

import com.vmware.transport.bus.EventBus;
import com.vmware.transport.bus.EventBusImpl;
import com.vmware.transport.bus.store.model.BusStore;
import com.vmware.transport.bus.store.model.TestStoreItem;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class StoreManagerTest {

   private EventBus eventBus;
   private StoreManager storeManager;
   private int whenReadyCalls;
   private TestStoreItem storeItem;

   @Before
   public void before() throws Exception {
      this.eventBus = new EventBusImpl();
      storeManager = new StoreManager(eventBus);
      storeItem = new TestStoreItem("item1", 0);
   }

   @Test
   public void testCreateStore() {
      BusStore<UUID, String> userNamesStore = storeManager.createStore("userNames");
      Assert.assertNotNull(userNamesStore);
      UUID uuid1 = UUID.randomUUID();
      userNamesStore.getBusStoreInitializer()
            .add(uuid1, "user1")
            .done();

      userNamesStore = storeManager.createStore("userNames");
      Assert.assertEquals(userNamesStore.get(uuid1), "user1");

      Assert.assertNull(storeManager.createStore(null));
   }

   @Test
   public void testGetStore() {
      BusStore<UUID, String> userNamesStore = storeManager.createStore("userNames");
      Assert.assertEquals(storeManager.getStore("userNames"), userNamesStore);
      Assert.assertNull(storeManager.getStore("missingStore"));
      Assert.assertNull(storeManager.getStore(null));
   }

   @Test
   public void testDestroyStore() {
      BusStore<UUID, TestStoreItem> testStore = storeManager.createStore("testStore");
      testStore.getBusStoreInitializer()
            .add(storeItem.uuid, storeItem)
            .done();

      Assert.assertTrue(storeManager.destroyStore("testStore"));

      // verify that testStore content is not deleted
      Assert.assertTrue(testStore.allValues().contains(storeItem));

      Assert.assertFalse(storeManager.destroyStore(null));
      Assert.assertFalse(storeManager.destroyStore("invalidStore"));
   }

   @Test
   public void testWipeAllStores() {
      BusStore<UUID, TestStoreItem> testStore = storeManager.createStore("testStore");
      testStore.getBusStoreInitializer()
            .add(storeItem.uuid, storeItem)
            .done();
      BusStore<UUID, String> userNamesStore = storeManager.createStore("userNames");
      userNamesStore.getBusStoreInitializer()
            .add(UUID.randomUUID(), "user1")
            .done();

      storeManager.wipeAllStores();

      Assert.assertFalse(testStore.isInitialized());
      Assert.assertFalse(userNamesStore.isInitialized());
   }

   @Test
   public void testReadyJoin() throws Exception {

      Assert.assertNull(storeManager.readyJoin());

      storeManager.readyJoin("storeA", "storeB", "storeC", "storeD").whenReady(aVoid -> {
         whenReadyCalls++;
      });

      InitStoreThread storeAInitializer = new InitStoreThread(storeManager, "storeA");
      InitStoreThread storeBInitializer = new InitStoreThread(storeManager, "storeB");
      InitStoreThread storeCInitializer = new InitStoreThread(storeManager, "storeC");
      InitStoreThread storeDInitializer = new InitStoreThread(storeManager, "storeD");

      storeDInitializer.start();
      storeDInitializer.join();
      Assert.assertEquals(whenReadyCalls, 0);

      storeBInitializer.start();
      storeCInitializer.start();
      storeAInitializer.start();

      storeAInitializer.join();
      storeBInitializer.join();
      storeCInitializer.join();
      Assert.assertEquals(whenReadyCalls, 1);
   }

   private static class InitStoreThread extends Thread {

      private final StoreManager storeManager;
      private final String storeName;

      public InitStoreThread(StoreManager storeManager, String storeName) {
         this.storeManager = storeManager;
         this.storeName = storeName;
      }

      @Override
      public void run() {
         storeManager.getStore(storeName).initialize();
      }
   }
}
