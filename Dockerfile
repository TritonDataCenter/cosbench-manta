# dekobon/cosbench-manta

# We use the Azul OpenJDK because it is a well tested and supported build.
FROM azul/zulu-openjdk-debian:8

MAINTAINER Elijah Zupancic <elijah.zupancic@joyent.com>

ENV JAVA_HOME=/usr/lib/jvm/zulu-8-amd64
ENV COSBENCH_VERSION 0.4.2.c3
ENV COSBENCH_CHECKSUM 684b39a590a144360d8defb6c9755f69657830dbe1d54ca40fdb74c2b57793aa
ENV COSBENCH_MANTA_VERSION 1.1.0-SNAPSHOT
ENV COSBENCH_MANTA_CHECKSUM 69e794a11b7e34b669e3185e83803bb4a551979a9b1d400a07cf1152081cbe10
ENV CONTAINERPILOT_VER 2.6.0
ENV CONTAINERPILOT file:///etc/containerpilot.json
ENV OSGI_CONSOLE_PORT_DRIVER 18089
ENV OSGI_CONSOLE_PORT_CONTROLLER 19089
ENV MODE unknown

# Metadata for Docker containers: http://label-schema.org/
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="COSBench $COSBENCH_VERSION with Manta SDK Support" \
      org.label-schema.description="COSBench with Manta Support" \
      org.label-schema.url="https://github.com/joyent/cosbench-manta" \
      org.label-schema.vcs-url="org.label-schema.vcs-ref" \
      org.label-schema.vendor="Joyent" \
      org.label-schema.schema-version="1.0"

# Installed tools:
# ==============================================================================
# openssh-client:     for ssh-keygen to generate key fingerprints
# curl:               for downloading binaries
# ca-certifiactes:    for downloading via https
# vim:                for debugging cosbench (could be removed)
# unzip:              for installing binaries
# htop:               for analyzing cosbench performance (could be removed)
# netcat-traditional: for starting cosbench OSGI services
# dc:                 for calculating performance settings
# ==============================================================================

RUN export DEBIAN_FRONTEND=noninteractive && \
    apt-get -qq update && \
    apt-get -qy upgrade && \
    apt-get install --no-install-recommends -qy openssh-client curl ca-certificates vim \
                                                unzip htop netcat-traditional dc && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* \
           /tmp/* \
           /var/tmp/*

# Download and install Cosbench
RUN curl --retry 6 -Ls "https://github.com/intel-cloud/cosbench/releases/download/v${COSBENCH_VERSION}/${COSBENCH_VERSION}.zip" > /tmp/cosbench.zip && \
    echo "${COSBENCH_CHECKSUM}  /tmp/cosbench.zip" | sha256sum -c && \
    unzip -q /tmp/cosbench.zip -d /opt/ && \
    mv "/opt/${COSBENCH_VERSION}" /opt/cosbench && \
    rm /tmp/cosbench.zip

# Download and install the Manta adaptor
RUN curl --retry 6 -Ls "https://github.com/joyent/cosbench-manta/releases/download/cosbench-manta-${COSBENCH_MANTA_VERSION}/cosbench-manta-${COSBENCH_MANTA_VERSION}.jar" > /opt/cosbench/osgi/plugins/cosbench-manta.jar && \
    sha256sum /opt/cosbench/osgi/plugins/cosbench-manta.jar && \
    echo "${COSBENCH_MANTA_CHECKSUM}  /opt/cosbench/osgi/plugins/cosbench-manta.jar" | sha256sum -c

# Install Consul
# Releases at https://releases.hashicorp.com/consul
RUN export CONSUL_VERSION=0.7.0 \
    && export CONSUL_CHECKSUM=b350591af10d7d23514ebaa0565638539900cdb3aaa048f077217c4c46653dd8 \
    && curl --retry 7 --fail -vo /tmp/consul.zip "https://releases.hashicorp.com/consul/${CONSUL_VERSION}/consul_${CONSUL_VERSION}_linux_amd64.zip" \
    && echo "${CONSUL_CHECKSUM}  /tmp/consul.zip" | sha256sum -c \
    && unzip /tmp/consul -d /usr/local/bin \
    && rm /tmp/consul.zip \
    && mkdir /config

# Install Consul template
# Releases at https://releases.hashicorp.com/consul-template/
RUN export CONSUL_TEMPLATE_VERSION=0.14.0 \
    && export CONSUL_TEMPLATE_CHECKSUM=7c70ea5f230a70c809333e75fdcff2f6f1e838f29cfb872e1420a63cdf7f3a78 \
    && curl --retry 7 --fail -Lso /tmp/consul-template.zip "https://releases.hashicorp.com/consul-template/${CONSUL_TEMPLATE_VERSION}/consul-template_${CONSUL_TEMPLATE_VERSION}_linux_amd64.zip" \
    && echo "${CONSUL_TEMPLATE_CHECKSUM}  /tmp/consul-template.zip" | sha256sum -c \
    && unzip /tmp/consul-template.zip -d /usr/local/bin \
    && rm /tmp/consul-template.zip

# Create empty directories for Consul config and data
RUN mkdir -p /etc/consul && mkdir -p /var/lib/consul

RUN export CONTAINERPILOT_CHECKSUM=c1bcd137fadd26ca2998eec192d04c08f62beb1f \
    && curl -Lso /tmp/containerpilot.tar.gz \
         "https://github.com/joyent/containerpilot/releases/download/${CONTAINERPILOT_VER}/containerpilot-${CONTAINERPILOT_VER}.tar.gz" \
    && echo "${CONTAINERPILOT_CHECKSUM}  /tmp/containerpilot.tar.gz" | sha1sum -c \
    && tar zxf /tmp/containerpilot.tar.gz -C /usr/local/bin \
    && rm /tmp/containerpilot.tar.gz


# Adding machine sizing utility useful when on Triton
COPY docker_build/usr/local/bin /usr/local/bin
RUN chmod +x /usr/local/bin/proclimit

# Adding sample Manta configuration, init files and customized configuration
COPY docker_build/opt/cosbench /opt/cosbench

# Adding container pilot config
COPY docker_build/etc /etc

# Setup Tomcat user to run COSBench process
RUN groupadd -g 120 tomcat && \
    useradd -g 120 -G sudo -u 120 -c 'Tomcat User' -d /opt/cosbench -r -s /bin/false tomcat && \
    mkdir /opt/cosbench/.ssh && \
    chown -R tomcat:tomcat /opt/cosbench && \
    chown -R tomcat:tomcat /var/lib/consul

# Set executable bits on COSBench scripts
RUN find /opt/cosbench -maxdepth 1 -type f -name \*.sh -exec chmod +x '{}' \;

# Run the container using the tomcat user by default
USER tomcat

# COSBench driver port
EXPOSE 18088
# COSBench controller port
EXPOSE 19088

WORKDIR /opt/cosbench

CMD [ "/usr/local/bin/containerpilot", "/opt/cosbench/autopilot-start-tomcat.sh" ]
