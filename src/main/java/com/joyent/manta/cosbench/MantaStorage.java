package com.joyent.manta.cosbench;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.client.MantaClient;
import com.joyent.manta.client.MantaMetadata;
import com.joyent.manta.config.ChainedConfigContext;
import com.joyent.manta.config.DefaultsConfigContext;
import com.joyent.manta.config.EnvVarConfigContext;
import com.joyent.manta.config.StandardConfigContext;
import com.joyent.manta.config.SystemSettingsConfigContext;
import com.joyent.manta.cosbench.config.CosbenchMantaConfigContext;
import com.joyent.manta.exception.MantaClientHttpResponseException;
import com.joyent.manta.exception.MantaErrorCode;
import com.joyent.manta.http.MantaHttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manta implementation of the COSBench {@link com.intel.cosbench.api.storage.StorageAPI}.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 */
public class MantaStorage extends NoneStorage {
    /**
     * Hardcoded directory in Manta in which all benchmark files are stored.
     */
    private static final String DEFAULT_COSBENCH_BASE_DIR = "stor/cosbench";

    /**
     * The default number of maximum HTTP connections at one time to the
     * Manta API.
     */
    private static final int MAX_CONNECTIONS = 1024;

    /**
     * Manta client driver.
     */
    private MantaClient client;

    /**
     * The current test directory name.
     */
    private String currentTestDirectory;

    /**
     * Number of copies of object to store.
     */
    private Integer durabilityLevel;

    /**
     * Flag that toggles chunked transfer encoding.
     */
    private boolean chunked;

    @Override
    public void init(final Config config, final Logger logger) {
        logger.debug("Manta client has started initialization");
        super.init(config, logger);

        // We change the default number of connections to something more
        // fitting for COSBench.
        StandardConfigContext defaults = new StandardConfigContext();
        defaults.overwriteWithContext(new DefaultsConfigContext());
        defaults.setMaximumConnections(MAX_CONNECTIONS);

        final CosbenchMantaConfigContext cosbenchConfig =
                new CosbenchMantaConfigContext(config);
        final ChainedConfigContext context = new ChainedConfigContext(
                defaults,
                new EnvVarConfigContext(),
                new SystemSettingsConfigContext(),
                cosbenchConfig);

        this.durabilityLevel = cosbenchConfig.getDurabilityLevel();

        if (cosbenchConfig.chunked() == null) {
            logger.info("Chunked mode is disabled");
            this.chunked = false;
        } else {
            final String status;
            if (cosbenchConfig.chunked()) {
                status = "enabled";
            } else {
                status = "disabled";
            }

            logger.info("Chunked mode is " + status);
            this.chunked = cosbenchConfig.chunked();
        }

        logger.info(String.format("Client configuration: %s",
                context));


        try {
            client = new MantaClient(context);

            final String baseDir = Objects.toString(
                    cosbenchConfig.getBaseDirectory(),
                    DEFAULT_COSBENCH_BASE_DIR);

            // We rely on COSBench properly cleaning up after itself.
            currentTestDirectory = String.format("%s/%s",
                    context.getMantaHomeDirectory(), baseDir);

            client.putDirectory(currentTestDirectory, true);

            if (!client.existsAndIsAccessible(currentTestDirectory)) {
                String msg = "Unable to create base test directory";
                throw new StorageException(msg);
            }

        } catch (IOException e) {
            logger.error("Error in initialization", e);
            throw new StorageException(e);
        }

        logger.debug("Manta client has been initialized");
    }

    @Override
    public void createContainer(final String container, final Config config) {
        super.createContainer(container, config);

        try {
            client.putDirectory(directoryOfContainer(container));
        } catch (Exception e) {
            logger.error("Error creating container", e);
        }
    }

    @Override
    public void deleteContainer(final String container, final Config config) {
        super.deleteContainer(container, config);

        try {
            client.deleteRecursive(directoryOfContainer(container));
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                logger.error("Error error deleting object", e);
                throw new StorageException(e);
            }
        } catch (Exception e) {
            logger.error("Error deleting container", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void createObject(final String container,
                             final String object,
                             final InputStream data,
                             final long length,
                             final Config config) {
        super.createObject(container, object, data, length, config);

        final String path = pathOfObject(container, object);
        final long contentLength;

        if (chunked) {
            contentLength = -1L;
        } else {
            contentLength = length;
        }

        MantaHttpHeaders headers = new MantaHttpHeaders();

        try {
            if (durabilityLevel != null) {
                headers.setDurabilityLevel(durabilityLevel);
            }

            client.put(path, data, contentLength, headers, null);
        } catch (MantaClientHttpResponseException e) {
            // This is a fall-back in the weird cases where COSBench doesn't
            // do things in the right order.
            if (e.getServerCode().equals(MantaErrorCode.DIRECTORY_DOES_NOT_EXIST_ERROR)) {
                try {
                    String dir = directoryOfContainer(container);
                    client.putDirectory(dir, true);
                    client.put(path, data, contentLength, headers, null);
                } catch (IOException ioe) {
                    throw new StorageException(ioe);
                }
            } else {
                throw new StorageException(e);
            }
        } catch (Exception e) {
            logger.error("Error error creating object", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(final String container, final String object,
                             final Config config) {
        super.deleteObject(container, object, config);

        try {
            String path = pathOfObject(container, object);
            client.delete(path);
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                logger.error("Error error deleting object", e);
                throw new StorageException(e);
            }
        } catch (Exception e) {
            logger.error("Error error deleting object", e);
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getObject(final String container, final String object, final Config config) {
        super.getObject(container, object, config);

        try {
            final String path = pathOfObject(container, object);
            return client.getAsInputStream(path);
        } catch (Exception e) {
            logger.error("Error error getting object", e);
            throw new StorageException(e);
        }
    }

    @Override
    protected void createMetadata(final String container,
                                  final String object,
                                  final Map<String, String> map,
                                  final Config config) {
        super.createMetadata(container, object, map, config);

        try {
            String path = pathOfObject(container, object);
            Map<String, String> prefixedMap = new HashMap<>(map.size());
            String format = "m-%s";

            for (Map.Entry<String, String> entry : map.entrySet()) {
                prefixedMap.put(String.format(format, entry.getKey()), entry.getValue());
            }

            MantaMetadata metadata = new MantaMetadata(prefixedMap);
            client.putMetadata(path, metadata);
        } catch (Exception e) {
            logger.error("Error error creating metadata", e);
            throw new StorageException(e);
        }
    }

    @Override
    protected Map<String, String> getMetadata(final String container,
                                              final String object,
                                              final Config config) {
        super.getMetadata(container, object, config);

        try {
            String path = pathOfObject(container, object);
            return client.head(path).getMetadata();
        } catch (Exception e) {
            logger.error("Error error getting metadata", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.warn("Error when attempting to close Manta client", e);
        }

        client = null;
    }

    @Override
    public void abort() {
        super.abort();
    }

    /**
     * Utility method that provides the directory mapping of a container.
     * @param container container name
     * @return directory as string
     */
    private String directoryOfContainer(final String container) {
        return String.format("%s/%s", currentTestDirectory, container);
    }

    /**
     * Utility method that provides the directory mapping of an object.
     * @param container container name
     * @param object object name
     * @return full path to object as string
     */
    private String pathOfObject(final String container, final String object) {
        String dir = directoryOfContainer(container);
        return String.format("%s/%s", dir, object);
    }
}
