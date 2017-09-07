#!/usr/bin/env bash

source /etc/bash.bashrc

if [ "$MODE" == "unknown" ]; then
    >&2 echo "MODE environment variable should be set to 'driver' or 'controller'"
    exit 1
fi

if [ "$MANTA_ENV_WORKING" -eq 0 ]; then
    exit 1
fi

# If we are running on Triton, then we will tune the JVM for the platform
if [ -d /native ]; then
    HW_THREADS=$(/usr/local/bin/proclimit)

    if [ $HW_THREADS -le 8 ]; then
        GC_THREADS=$HW_THREADS
    else
        # ParallelGCThreads = (ncpus <= 8) ? ncpus : 3 + ((ncpus * 5) / 8)
        ADJUSTED=$(echo "8k $HW_THREADS 5 * pq" | dc)
        DIVIDED=$(echo "8k $ADJUSTED 8 / pq" | dc)
        GC_THREADS=$(echo "8k $DIVIDED 3 + pq" | dc | awk 'function ceil(valor) { return (valor == int(valor) && value != 0) ? valor : int(valor)+1 } { printf "%d", ceil($1) }')
    fi

    TOMCAT_OPTS="-XX:-UseGCTaskAffinity -XX:-BindGCTaskThreadsToCPUs -XX:ParallelGCThreads=${GC_THREADS}"
else
    TOMCAT_OPTS=""
fi

if [ "$MODE" == "driver" ]; then
    OSGI_PORT=$OSGI_CONSOLE_PORT_DRIVER
elif [ "$MODE" == "controller" ]; then
    OSGI_PORT=$OSGI_CONSOLE_PORT_CONTROLLER
fi

exec /usr/bin/java \
    ${TOMCAT_OPTS} \
    -Dorg.osgi.framework.system.packages.extra=sun.misc,sun.reflect \
    -Dcosbench.tomcat.config=conf/$MODE-tomcat-server.xml \
    -server \
    -cp main/* org.eclipse.equinox.launcher.Main \
    -configuration conf/.$MODE \
    -console $OSGI_PORT
