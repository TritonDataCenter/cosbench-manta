/*
 * Copyright (c) 2016, Joyent, Inc. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.joyent.manta.cosbench;

import com.intel.cosbench.log.LogFactory;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Default OSGI activator that provides the startup and shutdown functionaly
 * needed to load and unload classes from OSGI.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 */
public class Activator implements BundleActivator {

    /**
     * OSGI start up method.
     * @param bundleContext OSGI bundle
     * @throws Exception when there is a problem in OSGI start up
     */
    public void start(final BundleContext bundleContext) throws Exception {
        LogFactory.getSystemLogger().info("Starting Manta adapter");
    }

    /**
     * OSGI stop method.
     * @param bundleContext OSGI bundle
     * @throws Exception when there is a problem in OSGI shut down
     */
    public void stop(final BundleContext bundleContext) throws Exception {
        LogFactory.getSystemLogger().info("Stopping Manta adapter");
    }
}
