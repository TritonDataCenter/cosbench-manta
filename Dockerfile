# dekobon/cosbench-manta

# We use an Ubuntu OS because it is the reference OS in the COSBench documentation.
# We use the phusion passenger base image of Ubuntu, so that we can run COSBench
# as a multi-process container.
FROM phusion/baseimage:0.9.18

MAINTAINER Elijah Zupancic <elijah.zupancic@joyent.com>

ENV JAVA_MAJOR_VERSION 8
ENV COSBENCH_VERSION 0.4.1.0
ENV COSBENCH_CHECKSUM a044cd232b3cc376802aa6a4a697988ec690a8b1d70040641710066acd322c5a
ENV COSBENCH_MANTA_VERSION 1.0.0
ENV COSBENCH_MANTA_CHECKSUM 021171693b86631d941706cd2275688c465268902b73c1515a09bcf5ae3613ed

# Setup the (Oracle) JVM and install needed utilities
RUN echo debconf shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
RUN echo debconf shared/accepted-oracle-license-v1-1 seen true | debconf-set-selections

RUN apt-add-repository ppa:webupd8team/java && \
    apt-get -qq update && \
    apt-get -qy upgrade && \
    apt-get install -y oracle-java${JAVA_MAJOR_VERSION}-installer patch unzip && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* \
           /tmp/* \
           /var/tmp/* \
           /var/cache/oracle-jdk* \
           /usr/lib/jvm/java-${JAVA_MAJOR_VERSION}-oracle/src.zip \
           /usr/lib/jvm/java-${JAVA_MAJOR_VERSION}-oracle/javafx-src.zip

# Download and install Cosbench
RUN curl -Ls "https://github.com/intel-cloud/cosbench/releases/download/v${COSBENCH_VERSION}/${COSBENCH_VERSION}.zip" > /tmp/cosbench.zip && \
    echo "${COSBENCH_CHECKSUM}  /tmp/cosbench.zip" | sha256sum -c && \
    unzip -q /tmp/cosbench.zip -d /opt/ && \
    mv "/opt/${COSBENCH_VERSION}" /opt/cosbench && \
    rm /tmp/cosbench.zip

# Download and install the Manta adaptor
RUN curl -Ls "https://github.com/joyent/cosbench-manta/releases/download/cosbench-manta-${COSBENCH_MANTA_VERSION}/cosbench-manta-${COSBENCH_MANTA_VERSION}.jar" > /opt/cosbench/osgi/plugins/cosbench-manta.jar && \
    echo "${COSBENCH_MANTA_CHECKSUM}  /opt/cosbench/osgi/plugins/cosbench-manta.jar" | sha256sum -c

# Adding machine sizing utility useful when on Triton
COPY docker_build/usr/local/bin /usr/local/bin
RUN chmod +x /usr/local/bin/proclimit

# Install patches that will update configuration for use with Manta
# benchmarking. Check this directory for what we had to change to enable
# the adapter.
COPY docker_build/patches /patches

# Adding sample Manta configuration
COPY conf/* /opt/cosbench/conf

# Patch Cosbench for use with the Manta adaptor
RUN patch -p0 < /patches/manta_enabled.patch

