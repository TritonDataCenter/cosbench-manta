#!bin/sh

COSBENCH_MANTA_PATH=$(find target -name 'cosbench-manta-*.jar' -print -quit)
COSBENCH_MANTA_CHECKSUM=$(shasum -a256 $COSBENCH_MANTA_PATH | cut -d' ' -f1)

docker build \
    --build-arg COSBENCH_MANTA_PATH=$COSBENCH_MANTA_PATH \
    --build-arg COSBENCH_MANTA_CHECKSUM=$COSBENCH_MANTA_CHECKSUM \
    . \
    -t joyent/cosbench-manta:latest
