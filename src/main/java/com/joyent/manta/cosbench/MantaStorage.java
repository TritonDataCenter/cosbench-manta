/*
 * Copyright (c) 2016, Joyent, Inc. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.joyent.manta.cosbench;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.client.MantaBucketListingIterator;
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
import org.apache.commons.io.input.BoundedInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

/**
 * Manta implementation of the COSBench {@link com.intel.cosbench.api.storage.StorageAPI}.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 *
 * @author <a href="https://github.com/1010sachin">Sachin Gupta</a>
 */
public class MantaStorage extends NoneStorage {
    /**
     * Hardcoded directory in Manta in which all benchmark files are stored.
     */
    private static final String DEFAULT_COSBENCH_BASE_DIR = "stor/cosbench";

    /**
     * Hardcoded bucket in Manta in which all buckets benchmark files are stored.
     */
    private static final String DEFAULT_COSBENCH_BUCKETS_PATH = "buckets/";

    /**
     * Hardcoded bucket in Manta in which all buckets benchmark files are stored.
     */
    private static final String DEFAULT_BUCKETS_OBJECT = "objects/";

    /**
     * The default number of maximum HTTP connections at one time to the Manta API.
     */
    private static final int MAX_CONNECTIONS = 1024;

    /**
     * Manta client driver.
     */
    private MantaClient client;

    /**
     * The current test directory or bucket name.
     */
    private String currentTestDirOrBucket;

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

    /**
     * String representing the type of test either dir or buckets.
     */
    private String testType;

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
        final ChainedConfigContext context = new ChainedConfigContext(
                defaults,
                new EnvVarConfigContext(),
                new SystemSettingsConfigContext(),
                cosbenchConfig);

        this.testType = cosbenchConfig.testType();
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

            initializeClient(cosbenchConfig, context);

        } catch (IOException e) {
            logger.error("Error in initialization", e);
            throw new StorageException(e);
        }

        if (logging) {
            logger.debug("Manta client has been initialized");
        }

        if (context.isClientEncryptionEnabled()) {
            encryptedMultipartManager = new EncryptedServerSideMultipartManager(client);
            serverMultipartManager = null;
        } else {
            encryptedMultipartManager = null;
            serverMultipartManager = new ServerSideMultipartManager(client);
        }

    }


    /**
     * Helper method for initializing cosbench.
     *
     * @param cosbenchConfig - The cosbench config.
     * @param context - The manta config context.
     * @throws IOException when there is a problem in initializing the manta client
     */
    private void initializeClient(final CosbenchMantaConfigContext cosbenchConfig,
                                  final ChainedConfigContext context) throws IOException {
        if ("buckets".equals(testType)) {
            final String bucketsPath = context.getMantaBucketsDirectory();
            try {
                client.options(bucketsPath);
            } catch (MantaClientHttpResponseException e) {
                if (MantaErrorCode.RESOURCE_NOT_FOUND_ERROR.equals(e.getServerCode())) {
                    logger.error("Buckets not supported in current Manta",
                            e.getStatusMessage());
                    throw new StorageException(e);
                }
            }
            currentTestDirOrBucket = String.format("%s%s%s",
                    context.getMantaHomeDirectory(), MantaClient.SEPARATOR,
                    DEFAULT_COSBENCH_BUCKETS_PATH);
        } else {
            final String baseDir = Objects.toString(cosbenchConfig.getBaseDirectory(),
                    DEFAULT_COSBENCH_BASE_DIR);
            currentTestDirOrBucket = String.format("%s%s%s",
                    context.getMantaHomeDirectory(), MantaClient.SEPARATOR, baseDir);
            client.putDirectory(currentTestDirOrBucket, true);
            if (!client.existsAndIsAccessible(currentTestDirOrBucket)) {
                String msg = "Unable to create base test directory";
                throw new StorageException(msg);
            }
        }
    }

    @Override
    public void createContainer(final String container, final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing CREATE bucket at /{}", container);
            } else {
                logger.info("Performing PUT dir at /{}", container);
            }
        }
        try {
            if ("buckets".equals(testType)) {
                final String bucketPath = pathOfBaseContainer(container);
                client.createBucket(bucketPath);
                if (!client.existsAndIsAccessible(bucketPath)) {
                    String msg = "Unable to create test bucket";
                    throw new StorageException(msg);
                }
            } else {
                client.putDirectory(pathOfBaseContainer(container));
            }
        } catch (Exception e) {
            if (logging) {
                logger.error("Error creating container", e);
            }
        }
    }

    @Override
    public void deleteContainer(final String container, final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing DELETE bucket at /{}", container);
            } else {
                logger.info("Performing DELETE dir at /{}", container);
            }
        }

        try {
            if ("buckets".equals(testType)) {
                client.deleteBucket(pathOfBaseContainer(container));
            } else {
                client.deleteRecursive(pathOfBaseContainer(container));
            }
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)
                    || !e.getServerCode().equals(MantaErrorCode.BUCKET_NOT_FOUND_ERROR)) {

                if (e.getServerCode().equals(MantaErrorCode.BUCKET_NOT_EMPTY_ERROR)) {
                    try {
                        deleteObjectsInBucket(container);
                        client.deleteBucket(pathOfBaseContainer(container));
                    } catch (MantaClientHttpResponseException me) {
                        if (!me.getServerCode().equals(MantaErrorCode.BUCKET_NOT_FOUND_ERROR)) {
                            if (logging) {
                                logger.error("Error in deleting container", me);
                            }
                            throw new StorageException(me);
                        }
                    } catch (IOException ie) {
                        throw new StorageException(ie);
                    }
                } else {
                    if (logging) {
                        logger.error("Error deleting container", e);
                    }
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
    public void createObject(final String container,
            final String object,
            final InputStream data,
            final long length,
            final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing PUT bucketobject at /{}/objects/{}",
                        container, object);
            } else {
                logger.info("Performing PUT at /{}/{}", container, object);
            }
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
                    String dir = pathOfBaseContainer(container);
                    client.putDirectory(dir, true);
                    client.put(path, data, contentLength, headers, null);
                } catch (IOException ioe) {
                    throw new StorageException(ioe);
                }
            } else if (e.getServerCode().equals(MantaErrorCode.BUCKET_NOT_FOUND_ERROR)) {
                try {
                    String bucketPath = pathOfBaseContainer(container);
                    client.createBucket(bucketPath);
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
    private void multipartUpload(final InputStream data,
            final String path,
            @SuppressWarnings("rawtypes") final MantaMultipartManager multipartManager) {
        MantaMultipartUpload upload = null;
        try {
            upload = multipartManager.initiateUpload(path);
            int splits = Math.floorDiv(data.available(), splitSize);
            LinkedList<MantaMultipartUploadPart> parts = new LinkedList<MantaMultipartUploadPart>();
            int partNumber = 1;
            for (int i = 0; i < splits; i++) {
                try (BoundedInputStream bis = new BoundedInputStream(data, splitSize)) {
                    parts.add(multipartManager.uploadPart(upload, partNumber, bis));
                    partNumber++;
                } catch (Exception e) {
                    if (logging) {
                        logger.error("Error in putting together the MPU {}", e.getMessage());
                    }
                    throw new StorageException(e);
                }
            }
            if ((data.available() % splitSize) != 0) {
                try (BoundedInputStream bis = new BoundedInputStream(data, data.available() % splitSize)) {
                    parts.add(multipartManager.uploadPart(upload, partNumber, bis));
                    partNumber++;
                } catch (Exception e) {
                    if (logging) {
                        logger.error("Error in putting together the MPU {}", e.getMessage());
                    }
                    throw new StorageException(e);
                }
            }
            multipartManager.complete(upload, parts);
        } catch (IOException e) {
            if (logging) {
                logger.error("Exception when uploading file {}", e);
            }
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(final String container, final String object,
                             final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing DELETE bucketobject at /{}/objects/{}",
                        container, object);
            } else {
                logger.info("Performing DELETE at /{}/{}", container, object);
            }
        }

        try {
            String path = pathOfObject(container, object);
            client.delete(path);
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)
            || !e.getServerCode().equals(MantaErrorCode.OBJECT_NOT_FOUND_ERROR)) {
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
                    if ("buckets".equals(testType)) {
                        logger.info("Performing GET bucketobject at /{}/objects/{}",
                                container, object);
                    } else {
                        logger.info("Performing GET at /{}/{}", container, object);
                    }
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
    protected void createMetadata(final String container,
            final String object,
            final Map<String, String> map,
            final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing POST at /{}/objects/{}",
                        container, object);
            } else {
                logger.info("Performing POST at /{}/{}", container, object);
            }
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
    protected Map<String, String> getMetadata(final String container,
                                              final String object, final Config config) {
        if (logging) {
            if ("buckets".equals(testType)) {
                logger.info("Performing HEAD at /{}/objects/{}",
                        container, object);
            } else {
                logger.info("Performing HEAD at /{}/{}", container, object);
            }
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
     * Utility method that provides the base path of bucket or dir container.
     *
     * @param container container name
     * @return path of the directory or bucket as string
     */
    private String pathOfBaseContainer(final String container) {
        if ("buckets".equals(testType)) {
            return String.format("%s%s", currentTestDirOrBucket,
                    container.toLowerCase().replaceAll("[^a-zA-Z0-9]", ""));
        } else {
            return String.format("%s%s%s", currentTestDirOrBucket,
                    MantaClient.SEPARATOR, container);
        }
    }

    /**
     * Utility method that provides the directory or bucket mapping of an object.
     *
     * @param container container name
     * @param object object name
     * @return full path to object as string
     */
    private String pathOfObject(final String container, final String object) {
        if ("buckets".equals(testType)) {
            String bucketPath = pathOfBaseContainer(container);
            return String.format("%s%s%s%s", bucketPath,
                    MantaClient.SEPARATOR,
                    DEFAULT_BUCKETS_OBJECT,
                    object.toLowerCase().replaceAll("[^a-zA-Z0-9]", ""));
        } else {
            String dir = pathOfBaseContainer(container);
            return String.format("%s%s%s", dir, MantaClient.SEPARATOR, object);
        }
    }

    /**
     * Utility method that iterates through the contents of the bucket and deletes
     * the objects in order to empty the bucket.
     *
     * @param container container name
     * @throws IOException when object cannot be deleted
     */
    private void deleteObjectsInBucket(final String container) throws IOException {
        String bucketIteratorPath = String.format("%s%s%s", pathOfBaseContainer(container),
                MantaClient.SEPARATOR, DEFAULT_BUCKETS_OBJECT);
        MantaBucketListingIterator itr =
                client.streamingBucketIterator(bucketIteratorPath);
        while (itr.hasNext()) {
            Map<String, Object> next = itr.next();
            if ("bucketobject".equals(Objects.toString(next.get("type")))) {
                String objectPath = pathOfObject(container,
                        Objects.toString(next.get("name")));
                try {
                    client.delete(objectPath);
                } catch (MantaClientHttpResponseException e) {
                    if (!e.getServerCode().equals(MantaErrorCode.OBJECT_NOT_FOUND_ERROR)
                    || !e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                        if (logging) {
                            String msg = String.format("Bucket /%s is not empty. "
                                            + "Error in attempting to delete object at %s",
                                    container, objectPath);
                            logger.error(msg, e);
                        }
                        throw new StorageException(e);
                    }
                }
            }
        }
    }
}
