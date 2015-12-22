# dekobon/cosbench-manta

# We start out with the default offical Java 1.8 image
FROM java:8

MAINTAINER Elijah Zupancic <elijah.zupancic@joyent.com>

ENV COSBENCH_VERSION 0.4.1.0

RUN apt-get update && \
    apt-get -qy upgrade && \
    apt-get install patch && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

COPY docker_build/usr/local/bin /usr/local/bin
RUN chmod +x /usr/local/bin/proclimit

# Install patches that will update configuration for use with Manta
# benchmarking. Check this directory for what we had to change to enable
# the adapter.
COPY docker_build/patches /patches

# Download and install Cosbench and patch it for use with the Manta adapter
RUN curl -Ls "https://github.com/intel-cloud/cosbench/releases/download/v${COSBENCH_VERSION}/${COSBENCH_VERSION}.zip" > /tmp/cosbench.zip && \
    unzip -q /tmp/cosbench.zip -d /opt/ && \
    mv "/opt/${COSBENCH_VERSION}" /opt/cosbench && \
    rm /tmp/cosbench.zip && \
    patch -p0 < /patches/manta_enabled.patch

