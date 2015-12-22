package com.intel.cosbench.api.manta;

import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.LogFactory;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.config.ConfigContext;

public class CosbenchMantaConfigContext implements ConfigContext {
    private static Logger logger = LogFactory.getSystemLogger();

    private final Config config;

    public CosbenchMantaConfigContext(final Config config) {
        this.config = config;
    }

    @Override
    public String getMantaURL() {
        try {
            return config.get("url");
        } catch (RuntimeException e) {
            logger.trace("Couldn't get url from COSBench config", e);
            return null;
        }
    }

    @Override
    public String getMantaUser() {
        try {
            return config.get("username");
        } catch (RuntimeException e) {
            logger.trace("Couldn't get username from COSBench config", e);
            return null;
        }
    }

    @Override
    public String getMantaKeyId() {
        try {
            return config.get("fingerprint");
        } catch (RuntimeException e) {
            logger.trace("Couldn't get fingerprint from COSBench config", e);
            return null;
        }
    }

    @Override
    public String getMantaKeyPath() {
        try {
            return config.get("key_path");
        } catch (RuntimeException e) {
            logger.trace("Couldn't get key_path from COSBench config", e);
            return null;
        }
    }

    @Override
    public String getPrivateKeyContent() {
        // We don't support embedded key content with COSBench
        return null;
    }

    @Override
    public String getPassword() {
        try {
            return config.get("password");
        } catch (RuntimeException e) {
            logger.trace("Couldn't get password from COSBench config", e);
            return null;
        }
    }

    @Override
    public Integer getTimeout() {
        return safeGetInteger(
                "timeout", "Couldn't get timeout from COSBench config");
    }

    @Override
    public Integer getRetries() {
        return safeGetInteger(
                "retries", "Couldn't get retries from COSBench config");
    }

    private Integer safeGetInteger(final String key, final String message) {
        try {
            int configValue = config.getInt(key, Integer.MIN_VALUE);

            return configValue != Integer.MIN_VALUE ? configValue : null;
        } catch (RuntimeException e) {
            logger.trace(message, e);
            return null;
        }
    }

    @Override
    public String getMantaHomeDirectory() {
        return ConfigContext.deriveHomeDirectoryFromUser(getMantaUser());
    }
}
