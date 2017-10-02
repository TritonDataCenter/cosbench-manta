package com.joyent.manta.cosbench;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.input.BoundedInputStream;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.client.MantaClient;
import com.joyent.manta.client.MantaMetadata;
import com.joyent.manta.client.multipart.EncryptedServerSideMultipartManager;
import com.joyent.manta.client.multipart.MantaMultipartManager;
import com.joyent.manta.client.multipart.MantaMultipartUpload;
import com.joyent.manta.client.multipart.MantaMultipartUploadPart;
import com.joyent.manta.client.multipart.ServerSideMultipartManager;
import com.joyent.manta.config.ChainedConfigContext;
import com.joyent.manta.config.DefaultsConfigContext;
import com.joyent.manta.config.EnvVarConfigContext;
import com.joyent.manta.config.StandardConfigContext;
import com.joyent.manta.config.SystemSettingsConfigContext;
import com.joyent.manta.cosbench.config.CosbenchMantaConfigContext;
import com.joyent.manta.exception.MantaClientHttpResponseException;
import com.joyent.manta.exception.MantaErrorCode;
import com.joyent.manta.http.MantaHttpHeaders;

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
     * The default number of maximum HTTP connections at one time to the Manta API.
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

    /**
     * Flag indicating that logging is enabled.
     */
    private boolean logging;

    /**
     * FLag indicating that we will use multipart uploads.
     */
    private boolean multipart;

    /**
     * When multipart, how large should each file be.
     */
    private Integer splitSize;

    /**
     * 5mb is the default split for a file, it is the minimum split size.
     */
    public static final int DEFAULT_SPLIT = 5242880;

    /**
     * Number of sections in which to download files. If greater than one, then multiple HTTP Range requests will be
     * used to assemble the file.
     */
    private int sections;

    /**
     * Size of the object being benchmarked - used only with HTTP range request benchmarks.
     */
    private Integer objectSize;

    /**
     * Multipart manager for encrypted loads.
     */
    private EncryptedServerSideMultipartManager encryptedMultipartManager;

    /**
     * Multipart manager for non-encrypted loads.
     */
    private ServerSideMultipartManager serverMultipartManager;

    @Override
    public void init(final Config config, final Logger logger) {
        logger.debug("Manta client has started initialization");
        super.init(config, logger);

        // We change the default number of connections to something more
        // fitting for COSBench.
        StandardConfigContext defaults = new StandardConfigContext();
        defaults.overwriteWithContext(new DefaultsConfigContext());
        defaults.setMaximumConnections(MAX_CONNECTIONS);

        final CosbenchMantaConfigContext cosbenchConfig = new CosbenchMantaConfigContext(config);
        final ChainedConfigContext context = new ChainedConfigContext(defaults, new EnvVarConfigContext(),
                new SystemSettingsConfigContext(), cosbenchConfig);

        this.durabilityLevel = cosbenchConfig.getDurabilityLevel();
        this.logging = cosbenchConfig.logging();
        this.sections = cosbenchConfig.getNumberOfSections();
        this.objectSize = cosbenchConfig.getObjectSize();
        this.multipart = cosbenchConfig.isMultipart();

        this.splitSize = cosbenchConfig.getSplitSize();
        if (splitSize == null) {
            splitSize = DEFAULT_SPLIT;
        }
        if (cosbenchConfig.chunked() == null) {
            if (logging) {
                logger.info("Chunked mode is disabled");
            }

            this.chunked = false;
        } else {
            final String status;
            if (cosbenchConfig.chunked()) {
                status = "enabled";
            } else {
                status = "disabled";
            }

            if (logging) {
                logger.info("Chunked mode is " + status);
            }
            this.chunked = cosbenchConfig.chunked();
        }

        if (logging) {
            logger.info(String.format("Client configuration: %s", context));
        }

        try {
            client = new MantaClient(context);

            final String baseDir = Objects.toString(cosbenchConfig.getBaseDirectory(), DEFAULT_COSBENCH_BASE_DIR);

            // We rely on COSBench properly cleaning up after itself.
            currentTestDirectory = String.format("%s/%s", context.getMantaHomeDirectory(), baseDir);

            client.putDirectory(currentTestDirectory, true);

            if (!client.existsAndIsAccessible(currentTestDirectory)) {
                String msg = "Unable to create base test directory";
                throw new StorageException(msg);
            }
        } catch (IOException e) {
            logger.error("Error in initialization", e);
            throw new StorageException(e);
        }

        if (logging) {
            logger.debug("Manta client has been initialized");
        }
        if (cosbenchConfig.isClientEncryptionEnabled()) {
            encryptedMultipartManager = new EncryptedServerSideMultipartManager(client);
        }
        serverMultipartManager = new ServerSideMultipartManager(client);
    }

    @Override
    public void createContainer(final String container, final Config config) {
        if (logging) {
            logger.info("Performing PUT at /{}", container);
        }

        try {
            client.putDirectory(directoryOfContainer(container));
        } catch (Exception e) {
            if (logging) {
                logger.error("Error creating container", e);
            }
        }
    }

    @Override
    public void deleteContainer(final String container, final Config config) {
        if (logging) {
            logger.info("Performing DELETE at /{}", container);
        }

        try {
            client.deleteRecursive(directoryOfContainer(container));
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                if (logging) {
                    logger.error("Error error deleting object", e);
                }
                throw new StorageException(e);
            }
        } catch (Exception e) {
            if (logging) {
                logger.error("Error deleting container", e);
            }
            throw new StorageException(e);
        }
    }

    @Override
    public void createObject(final String container, final String object, final InputStream data, final long length,
            final Config config) {
        if (logging) {
            logger.info("Performing PUT at /{}/{}", container, object);
        }

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
            if (this.multipart) {
                if (client.getContext().isClientEncryptionEnabled()) {
                    multipartUpload(data, path, encryptedMultipartManager);
                } else {
                    multipartUpload(data, path, serverMultipartManager);
                }
            } else {
                client.put(path, data, contentLength, headers, null);
            }
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
            if (logging) {
                logger.error("Error error creating object", e);
            }

            throw new StorageException(e);
        }
    }

    /**
     * Helper method for parsing out the streams and uploading in the multi-part way.
     *
     * @param data - Data stream.
     * @param path - The path that we are going to put the object into.
     * @param multipartManager - This will be EncryptedServerSideMultipartManager or ServerSideMultipartManager.
     */
    @SuppressWarnings("unchecked")
    private void multipartUpload(final InputStream data, final String path,
            @SuppressWarnings("rawtypes") final MantaMultipartManager multipartManager) {
        MantaMultipartUpload upload = null;
        try {
            upload = multipartManager.initiateUpload(path);
            logger.info("Split Size : {} ", splitSize);
            int splits = Math.floorDiv(data.available(), splitSize);
            LinkedList<MantaMultipartUploadPart> parts = new LinkedList<MantaMultipartUploadPart>();
            int partNumber = 1;

            for (int i = 0; i < splits; i++) {
                try (BoundedInputStream bis = new BoundedInputStream(data, splitSize)) {
                    parts.add(multipartManager.uploadPart(upload, partNumber, bis));
                    partNumber++;
                    logger.info("grabbed the next {} bytes", splitSize);
                } catch (Exception e) {
                    logger.error("Error in putting together the MPU {}", e.getMessage());
                }
            }

            logger.info("getting the next {} bytes", splitSize);
            if ((data.available() % splitSize) != 0) {
                try (BoundedInputStream bis = new BoundedInputStream(data, data.available() % splitSize)) {
                    parts.add(multipartManager.uploadPart(upload, partNumber, bis));
                    partNumber++;
                    logger.info("grabbed the last {} bytes", data.available() % splitSize);
                } catch (Exception e) {
                    logger.error("Error in putting together the MPU {}", e.getMessage());
                }
            }
            multipartManager.complete(upload, parts);
        } catch (IOException e) {
            logger.error("Exception when uploading file {}", e);
        }
        logger.debug(path + " is now assembled and sent as a multipart stream!");
    }

    @Override
    public void deleteObject(final String container, final String object, final Config config) {
        if (logging) {
            logger.info("Performing DELETE at /{}/{}", container, object);
        }

        try {
            String path = pathOfObject(container, object);
            client.delete(path);
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                if (logging) {
                    logger.error("Error error deleting object", e);
                }
                throw new StorageException(e);
            }
        } catch (Exception e) {
            if (logging) {
                logger.error("Error error deleting object", e);
            }
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getObject(final String container, final String object, final Config config) {
        final InputStream objectStream;

        try {
            final String path = pathOfObject(container, object);

            if (sections == 1) {
                if (logging) {
                    logger.info("Performing GET at /{}/{}", container, object);
                }
                objectStream = client.getAsInputStream(path);
            } else if (objectSize != null) {
                if (logging) {
                    logger.info("Performing GET with HTTP byte range at /{}/{}", container, object);
                }

                int size = this.objectSize;
                objectStream = new RangeJoiningInputStream(path, client, size, sections);
            } else {
                String msg = "[object-size] must be set when [no-of-http-range-sections] is set";

                if (logging) {
                    logger.error(msg);
                }
                throw new StorageException(msg);
            }
        } catch (Exception e) {
            if (logging) {
                logger.error("Error error getting object", e);
            }
            throw new StorageException(e);
        }

        return objectStream;
    }

    @Override
    protected void createMetadata(final String container, final String object, final Map<String, String> map,
            final Config config) {
        if (logging) {
            logger.info("Performing POST at /{}/{}", container, object);
        }

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
            if (logging) {
                logger.error("Error error creating metadata", e);
            }
            throw new StorageException(e);
        }
    }

    @Override
    protected Map<String, String> getMetadata(final String container, final String object, final Config config) {
        if (logging) {
            logger.info("Performing HEAD at /{}/{}", container, object);
        }

        try {
            String path = pathOfObject(container, object);
            return client.head(path).getMetadata();
        } catch (Exception e) {
            if (logging) {
                logger.error("Error error getting metadata", e);
            }
            throw new StorageException(e);
        }
    }

    @Override
    public void dispose() {
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            if (logging) {
                logger.warn("Error when attempting to close Manta client", e);
            }
        }

        client = null;
    }

    @Override
    public void abort() {
        client.closeQuietly();
    }

    /**
     * Utility method that provides the directory mapping of a container.
     *
     * @param container container name
     * @return directory as string
     */
    private String directoryOfContainer(final String container) {
        return String.format("%s/%s", currentTestDirectory, container);
    }

    /**
     * Utility method that provides the directory mapping of an object.
     *
     * @param container container name
     * @param object object name
     * @return full path to object as string
     */
    private String pathOfObject(final String container, final String object) {
        String dir = directoryOfContainer(container);
        return String.format("%s/%s", dir, object);
    }
}
