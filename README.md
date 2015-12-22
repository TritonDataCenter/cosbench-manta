[![Build Status](https://travis-ci.org/joyent/cosbench-manta.svg?branch=travis)](https://travis-ci.org/joyent/cosbench-manta)

# COSBench Manta Adaptor

This project provides an [adaptor](https://github.com/intel-cloud/cosbench/blob/master/COSBench-Adaptor-Dev-Guide.odt)
for benchmarking []Joyent's object store](https://www.joyent.com/object-storage) [Manta](https://github.com/joyent/manta)
using [COSBench](https://github.com/intel-cloud/cosbench/).

## Requirements
* [COSBench 0.4.1.0](https://github.com/intel-cloud/cosbench/releases/tag/v0.4.1.0)
* [Java 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or higher.
* [Maven 3.3.x](https://maven.apache.org/)

## Building from Source
If you prefer to build from source, you'll also need
[Maven](https://maven.apache.org/), and then invoke:

``` bash
# mvn package
```

This should generate an OSGI bundle inside the `./target` directory that you
can drop into the COSBench `osgi/plugins`` directory.

## Docker
You can use a preconfigured host with COSBench and the Manta adaptor preinstalled
when you run the project's [Docker](https://www.docker.com/) image.
