#!/bin/sh
set -ex

if [ -z "$MANTA_URL" ]; then
    echo invalid MANTA_URL
    exit 1
fi

if [ -z "$MANTA_USER" ]; then
    echo invalid MANTA_USER
    exit 1
fi

if [ ! -f "$MANTA_KEY_PATH" ]; then
    echo invalid MANTA_KEY_PATH
    exit 1
fi

CMD_BASE64=
if echo | base64 -w0 > /dev/null 2>&1; then
  # GNU coreutils base64, '-w' supported
  CMD_BASE64="base64 -w0"
else
  # Openssl base64, no wrapping by default
  CMD_BASE64="base64"
fi

sh build-docker-image.sh

docker run --name=cosbench \
    -e "MANTA_PRIVATE_KEY=$(cat $MANTA_KEY_PATH | $CMD_BASE64)" \
    -e "MANTA_URL=$MANTA_URL" \
    -e "MANTA_USER=$MANTA_USER" \
    -p 18088:18088 \
    -p 19088:19088 \
    -it \
    joyent/cosbench-manta:latest \
    bash -cli "bash /opt/cosbench/start-all.sh && sleep infinity"
