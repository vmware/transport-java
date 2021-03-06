/*
 * Copyright 2019-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus.store;

import com.vmware.transport.bus.EventBus;
import com.vmware.transport.bus.store.model.BusStore;
import com.vmware.transport.bus.store.model.BusStoreImpl;
import com.vmware.transport.bus.store.model.StoreReadyResult;
import com.vmware.transport.core.util.Loggable;
import io.reactivex.functions.Consumer;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component("storeManager")
@SuppressWarnings("unchecked")
public class StoreManager extends Loggable implements BusStoreApi {

   private final Map<String, Object> storeMap;
   private EventBus bus;


   @Autowired
   public StoreManager(EventBus bus) {
      this.storeMap = new ConcurrentHashMap<>();
      this.bus = bus;
   }

   @Override
   public <K, T> BusStore<K, T> createStore(String storeType) {
      if (storeType == null) {
         return null;
      }
      if (storeMap.containsKey(storeType)) {
         return (BusStore<K, T>) storeMap.get(storeType);
      }

      BusStore<K, T> busStore = new BusStoreImpl<>(this.bus, storeType);
      storeMap.put(storeType, busStore);

      return busStore;
   }

   @Override
   public <K, T> BusStore<K, T> getStore(String storeType) {
      if (storeType == null) {
         return null;
      }
      return (BusStore<K, T>) storeMap.get(storeType);
   }

   @Override
   public boolean destroyStore(String storeType) {
      if (storeType == null) {
         return false;
      }
      return storeMap.remove(storeType) != null;
   }

   @Override
   public StoreReadyResult readyJoin(String... storeTypes) {
      if (ArrayUtils.isEmpty(storeTypes)) {
         return null;
      }
      return new StoreReadyResult() {
         @Override
         public void whenReady(Consumer<Void> successHandler) {
            AtomicInteger readyStores = new AtomicInteger(0);
            for (String storeType: storeTypes) {
               createStore(storeType).whenReady(uuidObjectMap -> {
                  if (readyStores.incrementAndGet() == storeTypes.length) {
                     successHandler.accept(null);
                  }
               });
            }
         }
      };
   }

   @Override
   public void wipeAllStores() {
      this.logInfoMessage("\uD83D\uDDC4", getName(), "Stores: All data has been wiped out and reset.");
      for (Object storeObj : storeMap.values()) {
         ((BusStore<Object, Object>) storeObj).reset();
      }
   }
}
