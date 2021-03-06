/*
 * Copyright 2019-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.bus.store.model;

import io.reactivex.functions.Consumer;

/**
 * MutateStream allows mutating services to handle mutation requests.
 */
public interface MutateStream<T> {

   /**
    * Subscribe to Observable stream.
    * @param handler, a Consumer function to handle mutation requests.
    */
   void subscribe(Consumer<MutationRequestWrapper<T>> handler);

   /**
    * Unsubscribe from the mutation stream stream.
    */
   void unsubscribe();
}
