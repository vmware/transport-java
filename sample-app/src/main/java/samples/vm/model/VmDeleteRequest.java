/*
 * Copyright 2019-2020 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
 *
 */
package samples.vm.model;

import lombok.Getter;
import lombok.Setter;

public class VmDeleteRequest extends BaseVmRequest {

   @Getter @Setter
   VmRef vm;
}
