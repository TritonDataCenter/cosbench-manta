package com.joyent.manta.cosbench.config;

import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.LogFactory;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.config.ConfigContext;
import com.joyent.manta.config.MapConfigContext;

/**
 * Cosbench specific implementation of {@link ConfigContext} that allows us
 * to connect Cosbench config seamlessly.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 */
public class CosbenchMantaConfigContext implements ConfigContext {
    /**
     * Logger instance.
     */
    private static Logger logger = LogFactory.getSystemLogger();

    /**
     * Embedded Cosbench config reference.
     */
    private final Config config;

    /**
     * Default constructor that wraps a Cosbench config instance.
     * @param config cosbench config instance
     */
    public CosbenchMantaConfigContext(final Config config) {
        this.config = config;
    }

    @Override
    public String getMantaURL() {
        return safeGetString(MapConfigContext.MANTA_URL_KEY,
                "Couldn't get url from COSBench config");
    }

    @Override
    public String getMantaUser() {
        return safeGetString(MapConfigContext.MANTA_USER_KEY,
                "Couldn't get username from COSBench config");
    }

    @Override
    public String getMantaKeyId() {
        return safeGetString(MapConfigContext.MANTA_KEY_ID_KEY,
                "Couldn't get fingerprint from COSBench config");
    }

    @Override
    public String getMantaKeyPath() {
        return safeGetString(MapConfigContext.MANTA_KEY_PATH_KEY,
                "Couldn't get key_path from COSBench config");
    }

    @Override
    public String getPrivateKeyContent() {
        return safeGetString(MapConfigContext.MANTA_PRIVATE_KEY_CONTENT_KEY,
                "Couldn't get private_key from COSBench config");
    }

    @Override
    public String getPassword() {
        return safeGetString(MapConfigContext.MANTA_PASSWORD_KEY,
                "Couldn't get password from COSBench config");
    }

    @Override
    public Integer getTimeout() {
        return safeGetInteger(MapConfigContext.MANTA_TIMEOUT_KEY,
                "Couldn't get timeout from COSBench config");
    }

    @Override
    public Integer getRetries() {
        return safeGetInteger(MapConfigContext.MANTA_RETRIES_KEY,
                "Couldn't get retries from COSBench config");
    }

    @Override
    public Integer getMaximumConnections() {
        return safeGetInteger(MapConfigContext.MANTA_MAX_CONNS_KEY,
                "Couldn't get maximum connections from COSBench config");
    }

    @Override
    public String getHttpTransport() {
        return safeGetString(MapConfigContext.MANTA_HTTP_TRANSPORT_KEY,
                "Couldn't get http transport from COSBench config");
    }

    @Override
    public String getHttpsProtocols() {
        return safeGetString(MapConfigContext.MANTA_HTTPS_PROTOCOLS_KEY,
                "Couldn't get http protocols from COSBench config");
    }

    @Override
    public String getHttpsCipherSuites() {
        return safeGetString(MapConfigContext.MANTA_HTTPS_CIPHERS_KEY,
                "Couldn't get http ciphers from COSBench config");
    }

    @Override
    public Boolean noAuth() {
        return safeGetBoolean(MapConfigContext.MANTA_NO_AUTH_KEY,
                "Couldn't get no auth setting from COSBench config");
    }

    @Override
    public Boolean disableNativeSignatures() {
        return safeGetBoolean(MapConfigContext.MANTA_NO_NATIVE_SIGS_KEY,
                "Couldn't get disable native signature generation setting from COSBench config");
    }

    @Override
    public Integer getSignatureCacheTTL() {
        return safeGetInteger(MapConfigContext.MANTA_SIGS_CACHE_TTL_KEY,
                "Couldn't get http signatures cache ttl from COSBench config");
    }

    /**
     * @return when true chunk encoding is enabled
     */
    public Boolean useChunking() {
        return safeGetBoolean("chunked",
                "Couldn't get chunking setting from COSBench config");
    }

    /**
     * @return the number of copies to store of an object
     */
    public Integer getDurabilityLevel() {
        return safeGetInteger("durability-level",
                "Couldn't get durability level setting from COSBench config");
    }

    /**
     * Utility method that checks for the presence of Integer values in
     * the COSBench configuration and then returns the value if found.
     * @param key key to check for
     * @param message message to display when value isn't present
     * @return null if not found, otherwise configuration value
     */
    private Integer safeGetInteger(final String key, final String message) {
        try {
            int configValue = config.getInt(key, Integer.MIN_VALUE);

            if (configValue != Integer.MIN_VALUE) {
                return configValue;
            } else {
                return null;
            }

        } catch (RuntimeException e) {
            logger.trace(message, e);
            return null;
        }
    }

    /**
     * Utility method that checks for the presence of String values in
     * the COSBench configuration and then returns the value if found.
     * @param key key to check for
     * @param message message to display when value isn't present
     * @return null if not found, otherwise configuration value
     */
    private String safeGetString(final String key, final String message) {
        try {
            return config.get(key);
        } catch (RuntimeException e) {
            logger.trace(message, e);
            return null;
        }
    }

    /**
     * Utility method that checks for the presence of Boolean values in
     * the COSBench configuration and then returns the value if found.
     * @param key key to check for
     * @param message message to display when value isn't present
     * @return null if not found, otherwise configuration value
     */
    private Boolean safeGetBoolean(final String key, final String message) {
        try {
            return Boolean.parseBoolean(config.get(key));
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
