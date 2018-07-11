package com.joyent.manta.cosbench.config;

import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.LogFactory;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.config.ConfigContext;
import com.joyent.manta.config.EncryptionAuthenticationMode;
import com.joyent.manta.config.MapConfigContext;
import org.bouncycastle.util.encoders.Base64;

/**
 * Cosbench specific implementation of {@link ConfigContext} that allows us to
 * connect Cosbench config seamlessly.
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
     *
     * @param config cosbench config instance
     */
    public CosbenchMantaConfigContext(final Config config) {
        this.config = config;
    }

    @Override
    public String getMantaURL() {
        return safeGetString(MapConfigContext.MANTA_URL_KEY, "Couldn't get url from COSBench config");
    }

    @Override
    public String getMantaUser() {
        return safeGetString(MapConfigContext.MANTA_USER_KEY, "Couldn't get username from COSBench config");
    }

    @Override
    public String getMantaKeyId() {
        return safeGetString(MapConfigContext.MANTA_KEY_ID_KEY, "Couldn't get fingerprint from COSBench config");
    }

    @Override
    public String getMantaKeyPath() {
        return safeGetString(MapConfigContext.MANTA_KEY_PATH_KEY, "Couldn't get key_path from COSBench config");
    }

    @Override
    public String getPrivateKeyContent() {
        return safeGetString(MapConfigContext.MANTA_PRIVATE_KEY_CONTENT_KEY,
                "Couldn't get private_key from COSBench config");
    }

    @Override
    public String getPassword() {
        return safeGetString(MapConfigContext.MANTA_PASSWORD_KEY, "Couldn't get password from COSBench config");
    }

    @Override
    public Integer getTimeout() {
        return safeGetInteger(MapConfigContext.MANTA_TIMEOUT_KEY, "Couldn't get timeout from COSBench config");
    }

    @Override
    public Integer getRetries() {
        return safeGetInteger(MapConfigContext.MANTA_RETRIES_KEY, "Couldn't get retries from COSBench config");
    }

    @Override
    public Integer getMaximumConnections() {
        return safeGetInteger(MapConfigContext.MANTA_MAX_CONNS_KEY,
                "Couldn't get maximum connections from COSBench config");
    }

    @Override
    public Integer getHttpBufferSize() {
        return safeGetInteger(MapConfigContext.MANTA_HTTP_BUFFER_SIZE_KEY,
                "Couldn't get http buffer size from COSBench config");
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
        return safeGetBoolean(MapConfigContext.MANTA_NO_AUTH_KEY, "Couldn't get no auth setting from COSBench config");
    }

    @Override
    public Boolean disableNativeSignatures() {
        return safeGetBoolean(MapConfigContext.MANTA_NO_NATIVE_SIGS_KEY,
                "Couldn't get disable native signature generation setting from COSBench config");
    }

    @Override
    public Integer getTcpSocketTimeout() {
        return safeGetInteger(MapConfigContext.MANTA_TCP_SOCKET_TIMEOUT_KEY,
                "Couldn't get TCP socket timeout from COSBench config");
    }

    @Override
    public Integer getConnectionRequestTimeout() {
        return safeGetInteger(MapConfigContext.MANTA_CONNECTION_REQUEST_TIMEOUT_KEY,
                "Couldn't get connection request timeout from COSBench config");
    }

    @Override
    public Integer getExpectContinueTimeout() {
        return safeGetInteger(MapConfigContext.MANTA_CONNECTION_REQUEST_TIMEOUT_KEY,
                "Couldn't get expect continue timeout from COSBench config");
    }

    @Override
    public Boolean verifyUploads() {
        return safeGetBoolean(MapConfigContext.MANTA_VERIFY_UPLOADS_KEY,
                "Couldn't get verify uploads flag from COSBench config");
    }

    @Override
    public Integer getUploadBufferSize() {
        return safeGetInteger(MapConfigContext.MANTA_UPLOAD_BUFFER_SIZE_KEY,
                "Couldn't get upload buffer size from COSBench config");
    }

    @Override
    public Integer getSkipDirectoryDepth() {
        return safeGetInteger(MapConfigContext.MANTA_SKIP_DIRECTORY_DEPTH_KEY,
                "Couldn't get skip directory depth from COSBench config");
    }

    @Override
    public Boolean isClientEncryptionEnabled() {
        return safeGetBoolean(MapConfigContext.MANTA_CLIENT_ENCRYPTION_ENABLED_KEY,
                "Couldn't get encryption enabled boolean flag");
    }

    @Override
    public Boolean permitUnencryptedDownloads() {
        return safeGetBoolean(MapConfigContext.MANTA_PERMIT_UNENCRYPTED_DOWNLOADS_KEY,
                "Couldn't get permit unencrypted downloads boolean flag");
    }

    @Override
    public String getEncryptionKeyId() {
        return safeGetString(MapConfigContext.MANTA_ENCRYPTION_KEY_ID_KEY, "Couldn't get encryption key id");
    }

    @Override
    public String getEncryptionAlgorithm() {
        return safeGetString(MapConfigContext.MANTA_ENCRYPTION_ALGORITHM_KEY, "Could'nt get encryption algorithm");
    }

    @Override
    public EncryptionAuthenticationMode getEncryptionAuthenticationMode() {
        EncryptionAuthenticationMode mode = safeGetEnum(MapConfigContext.MANTA_ENCRYPTION_AUTHENTICATION_MODE_KEY,
                "Couldn't get object authentication mode", EncryptionAuthenticationMode.class);

        return mode;
    }

    @Override
    public String getEncryptionPrivateKeyPath() {
        return safeGetString(MapConfigContext.MANTA_ENCRYPTION_PRIVATE_KEY_PATH_KEY,
                "Couldn't get client-side encryption private key path");
    }

    @Override
    public byte[] getEncryptionPrivateKeyBytes() {
        String base64 = safeGetString(MapConfigContext.MANTA_ENCRYPTION_PRIVATE_KEY_BYTES_BASE64_KEY,
                "Couldn't get client-side encryption private key base64 data");

        if (base64 != null) {
            return Base64.decode(base64);
        } else {
            return null;
        }
    }

    // ========================================================================
    // COSBench Parameters
    // ========================================================================

    /**
     * @return true when logging is enabled (default is true)
     */
    public boolean logging() {
        Boolean enabled = safeGetBoolean("logging", "Couldn't get logging setting from COSBench config");

        if (enabled == null) {
            return true;
        } else {
            return enabled;
        }
    }

    /**
     * @return the number of copies to store of an object
     */
    public Integer getDurabilityLevel() {
        return safeGetInteger("durability-level", "Couldn't get durability level setting from COSBench config");
    }

    /**
     * @return when true chunk encoding is enabled
     */
    public Boolean chunked() {
        return safeGetBoolean("chunked", "Couldn't get chunked setting from COSBench config");
    }

    /**
     * @return the base directory under the home directory in Manta to write
     *         test data
     */
    public String getBaseDirectory() {
        return safeGetString("manta-directory", "Couldn't get Manta directory setting from COSBench config");
    }

    /**
     * @return Flag indicating if multipart part upload mode is enabled
     */
    public boolean isMultipart() {
        Boolean enabled = safeGetBoolean("multipart", "Couldn't get multipart setting from COSBench config");
        if (enabled == null) {
            return false;
        } else {
            return enabled;
        }
    }

    /**
     * @return - An override of the default split size.
     */
    public Integer getSplitSize() {
        return this.safeGetInteger("splitSize", "Couldn't get splitSize setting from COSBench config");

    }

    /**
     * Reads the configuration and determines the number of HTTP Range requests
     * needed to download the object. By default this returns 1 which means do
     * not do range requests and download the file normally.
     *
     * @return the number of sections a file is broken into
     */
    public int getNumberOfSections() {
        Integer sections = safeGetInteger("no-of-http-range-sections",
                "Couldn't get number of http byte range sections from COSBench config");

        if (sections == null) {
            return 1;
        }

        if (sections <= 0) {
            throw new IllegalArgumentException("Sections should be set to one or greater");
        }

        return sections;
    }

    /**
     * Reads the configuration and finds the set size of the objects being
     * benchmarked. This option doesn't work with random object sizes and is
     * only used when number of sections is greater than 1.
     *
     * @return the number in bytes of the size of files being benchmarked
     */
    public Integer getObjectSize() {
        return safeGetInteger("object-size", "Couldn't get object size from COSBench config");
    }

    /**
     * Utility method that checks for the presence of Integer values in the
     * COSBench configuration and then returns the value if found.
     *
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
     * Utility method that checks for the presence of String values in the
     * COSBench configuration and then returns the value if found.
     *
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
     * Utility method that checks for the presence of Boolean values in the
     * COSBench configuration and then returns the value if found.
     *
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

    /**
     * Utility method to checks for the presence of an Enum value in the
     * COSBench configuration and then returns the value if found.
     *
     * @param key key to check for
     * @param message message to display when value isn't present
     * @param enumClass enum class to parse as
     * @param <T> enum type
     * @return enum instance matching the value of the key
     */
    private <T extends Enum<T>> T safeGetEnum(final String key, final String message, final Class<T> enumClass) {
        try {
            String value = config.get(key);

            if (value == null) {
                return null;
            }

            return Enum.valueOf(enumClass, value);
        } catch (RuntimeException e) {
            logger.trace(message, e);
            return null;
        }
    }

    @Override
    public String getMantaHomeDirectory() {
        return ConfigContext.deriveHomeDirectoryFromUser(getMantaUser());
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CosbenchMantaConfigContext{");

        // generic java-manta configuration
        sb.append(ConfigContext.toString(this));

        // configs provided by workload xml at xpath: /workload/storage[@config]
        sb.append("Config{");
        sb.append("logging='");
        sb.append(this.logging());
        sb.append("durabilityLevel='");
        sb.append(this.getDurabilityLevel());
        sb.append("chunked='");
        sb.append(this.chunked());
        sb.append("baseDirectory='");
        sb.append(this.getBaseDirectory());
        sb.append("isMultipart='");
        sb.append(this.isMultipart());
        sb.append("getSplitSize='");
        sb.append(this.getSplitSize());
        sb.append("getNumberOfSections='");
        sb.append(this.getNumberOfSections());
        sb.append("getObjectSize='");
        sb.append(this.getObjectSize());
        sb.append("}");

        return sb.toString();
    }
}
