#!/usr/bin/env bash

CONSUL=${CONSUL:-consul}

# Templates out the list of drivers in the controllers configuration
consul-template \
        -once \
        -dedup \
        -consul-addr ${CONSUL}:8500 \
        -template "/opt/cosbench/conf/controller.conf.ctmpl:/opt/cosbench/conf/controller.conf"

if [ "$MODE" == "driver" ]; then
    OSGI_PORT=$OSGI_CONSOLE_PORT_DRIVER
elif [ "$MODE" == "controller" ]; then
    OSGI_PORT=$OSGI_CONSOLE_PORT_CONTROLLER
fi

# Hot reload COSBench configuration to update the list of drivers
echo "refresh cosbench-config" | nc -q 1 0.0.0.0 $OSGI_PORT
