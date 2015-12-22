package com.intel.cosbench.api.manta;

import com.intel.cosbench.api.storage.StorageAPI;
import com.intel.cosbench.api.storage.StorageAPIFactory;

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

