/*
 * Copyright (c) 2016, Joyent, Inc. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.joyent.manta.cosbench;

import com.intel.cosbench.api.storage.StorageAPI;
import com.intel.cosbench.api.storage.StorageAPIFactory;

/**
 * {@link StorageAPIFactory} implementation that defines the properties
 * of the Manta adaptor.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 */
public class MantaStorageFactory implements StorageAPIFactory {

    @Override
    public String getStorageName() {
        return "manta";
    }

    @Override
    public StorageAPI getStorageAPI() {
        return new MantaStorage();
    }

}

