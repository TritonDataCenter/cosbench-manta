# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## [1.0.5] - ?
### Changed
 - Upgraded Java Manta driver library. Fixes multi-threaded issues with HTTP signing.

## [1.0.4] - 2016-01-03
### Changed
 - Changed from IOException to Exception traps.

## [1.0.3] - 2016-01-02
### Changed
- Updated Manta client library to 2.2.0.
- We now support all of the new configuration options in java-manta 2.2.0.

## [1.0.2] - 2015-12-22
### Changed
- Updated Manta client library to 2.1.0 so that we can set maximum connections.
- We now allow setting the private key in the COSBench configuration.

## [1.0.1] - 2015-12-22
### Fixed
- Fixed OSGI import package scoping issue

## [1.0.0] - 2015-12-22
### Added
- Initial check in of benchmarking functionality.
- Added Dockerfile support.
- Added sane build system using Maven.
