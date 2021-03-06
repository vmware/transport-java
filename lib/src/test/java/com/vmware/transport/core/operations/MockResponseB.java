/*
 * Copyright 2018-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package com.vmware.transport.core.operations;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public class MockResponseB {

    @Getter @Setter
    private UUID id;

    @Getter @Setter
    private String value;

}
