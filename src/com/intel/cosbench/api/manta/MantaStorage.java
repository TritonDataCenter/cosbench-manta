package com.intel.cosbench.api.manta;

import com.intel.cosbench.api.storage.NoneStorage;
import com.intel.cosbench.api.storage.StorageException;
import com.intel.cosbench.config.Config;
import com.intel.cosbench.log.Logger;
import com.joyent.manta.client.MantaClient;
import com.joyent.manta.client.MantaHttpHeaders;
import com.joyent.manta.client.MantaMetadata;
import com.joyent.manta.config.ChainedConfigContext;
import com.joyent.manta.config.DefaultsConfigContext;
import com.joyent.manta.config.EnvVarConfigContext;
import com.joyent.manta.config.SystemSettingsConfigContext;
import com.joyent.manta.exception.MantaClientHttpResponseException;
import com.joyent.manta.exception.MantaErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MantaStorage extends NoneStorage {
    private static final String COSBENCH_BASE_DIR = "stor/cosbench";
    private MantaClient client;
    private String currentTestDirectory;
    private boolean chunked = false;

    @Override
    public void init(Config config, Logger logger) {
        logger.debug("Manta client has started initialization");
        super.init(config, logger);

        ChainedConfigContext context = new ChainedConfigContext(
                new DefaultsConfigContext(),
                new EnvVarConfigContext(),
                new SystemSettingsConfigContext(),
                new CosbenchMantaConfigContext(config));

        if (logger.isDebugEnabled()) {
        	String msg = String.format("Configuration: [user=%s, key_path=%s, url=%s]",
        			context.getMantaUser(), context.getMantaKeyPath(),
        			context.getMantaURL());
    		logger.debug(msg);
        }

        try {
            this.chunked = config.getBoolean("chunked");
        } catch (RuntimeException e) {
            logger.warn("Couldn't get value of chunked configuration setting");
        }

        try {
            client = new MantaClient(context);

            // We rely on COSBench properly cleaning up after itself.
            currentTestDirectory = String.format("%s/%s",
                    context.getMantaHomeDirectory(), COSBENCH_BASE_DIR);

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
    public void createContainer(String container, Config config) {
        super.createContainer(container, config);

        try {
            client.putDirectory(directoryOfContainer(container));
        } catch (IOException e) {
        	logger.error("Error creating container", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void deleteContainer(String container, Config config) {
        super.deleteContainer(container, config);

        try {
            client.deleteRecursive(directoryOfContainer(container));
        } catch (MantaClientHttpResponseException e) {
            if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                logger.error("Error error deleting object", e);
                throw new StorageException(e);
            }
        } catch (IOException e) {
        	logger.error("Error deleting container", e);
            throw new StorageException(e);
        }
    }

    @Override
    public void createObject(String container, String object, InputStream data,
                             long length, Config config) {
        super.createObject(container, object, data, length, config);

        final String path = pathOfObject(container, object);

        try {
            MantaHttpHeaders headers = new MantaHttpHeaders();

            if (chunked) {
                headers.setContentEncoding("chunked");
            }

            client.put(path, data, headers);
        } catch (MantaClientHttpResponseException e) {
            // This is a fall-back in the weird cases where COSBench doesn't
            // do things in the right order.
        	if (e.getServerCode().equals(MantaErrorCode.DIRECTORY_DOES_NOT_EXIST_ERROR)) {
        		try {
        			String dir = directoryOfContainer(container);
        			client.putDirectory(dir, true);
        			client.put(path, data);
        		} catch (IOException ioe) {
        			throw new StorageException(ioe);
        		}
        	} else {
        		throw new StorageException(e);
        	}
        } catch (IOException e) {
        	logger.error("Error error creating object", e);
        	throw new StorageException(e);
        }
    }

    @Override
    public void deleteObject(String container, String object, Config config) {
        super.deleteObject(container, object, config);

        try {
            String path = pathOfObject(container, object);
            client.delete(path);
        } catch (MantaClientHttpResponseException e) {
        	if (!e.getServerCode().equals(MantaErrorCode.RESOURCE_NOT_FOUND_ERROR)) {
                logger.error("Error error deleting object", e);
                throw new StorageException(e);
            }
        } catch (IOException e) {
        	logger.error("Error error deleting object", e);
            throw new StorageException(e);
        }
    }

    @Override
    public InputStream getObject(String container, String object, Config config) {
        super.getObject(container, object, config);

        try {
            final String path = pathOfObject(container, object);
            return client.getAsInputStream(path);
        } catch (IOException e) {
        	logger.error("Error error getting object", e);
            throw new StorageException(e);
        }
    }

    @Override
    protected void createMetadata(String container, String object, Map<String, String> map, Config config) {
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
        } catch (IOException e) {
        	logger.error("Error error creating metadata", e);
            throw new StorageException(e);
        }
    }

    @Override
    protected Map<String, String> getMetadata(String container, String object, Config config) {
        super.getMetadata(container, object, config);

        try {
            String path = pathOfObject(container, object);
            return client.head(path).getMetadata();
        } catch (IOException e) {
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

    private String directoryOfContainer(String container) {
        return String.format("%s/%s", currentTestDirectory, container);
    }

    private String pathOfObject(String container, String object) {
        String dir = directoryOfContainer(container);
        return String.format("%s/%s", dir, object);
    }
}
