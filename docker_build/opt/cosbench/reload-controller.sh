#!/usr/bin/env bash

CONSUL=${CONSUL:-consul}

consul-template \
        -once \
        -dedup \
        -consul ${CONSUL}:8500 \
        -template "/opt/cosbench/conf/controller.conf.ctmpl:/opt/cosbench/conf/controller.conf"
