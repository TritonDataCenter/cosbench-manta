/*
 * Copyright (c) 2016, Joyent, Inc. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.joyent.manta.cosbench;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.http.MantaHttpHeaders;
import org.apache.commons.io.input.BoundedInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link java.io.InputStream} implementation that joins multiple HTTP
 * range requests together in order to produce a single {@link java.io.InputStream}.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 * @since 1.1.0
 */
public class RangeJoiningInputStream extends InputStream {
    /**
     * End of file code returned by streams.
     */
    private static final int EOF = -1;

    /**
     * Path of object in Manta.
     */
    private final String path;

    /**
     * Reference to open Manta client.
     */
    private final MantaClient client;

    /**
     * Object representing the different sections to break the object into.
     */
    private final Range[] sections;

    /**
     * Supplier for each stream of input for each section of the file.
     */
    private final Supplier<InputStream> streamSupplier;

    /**
     * Counter of the current section.
     */
    private int currentSection = 0;

    /**
     * Number of bytes read so far.
     */
    private long bytesRead = 0;

    /**
     * Size of the object / file.
     */
    private long size;

    /**
     * The {@link InputStream} for the current section.
     */
    private InputStream backingStream;

    /**
     * Class representing the start and end ranges of a section.
     */
    static class Range {
        /**
         * Start position of the first byte to read.
         */
        private final long startInclusive;

        /**
         * End position of the last byte to read.
         */
        private final long endInclusive;

        /**
         * Creates a new instance of a section range.
         *
         * @param startInclusive start position of the first byte to read
         * @param endInclusive end position of the last byte to read
         */
        Range(final long startInclusive, final long endInclusive) {
            this.startInclusive = startInclusive;
            this.endInclusive = endInclusive;
        }

        /**
         * @return start position of the first byte to read
         */
        public long getStartInclusive() {
            return startInclusive;
        }

        /**
         * @return end position of the last byte to read
         */
        public long getEndInclusive() {
            return endInclusive;
        }

        /**
         * @return size in bytes of section
         */
        public long getSize() {
            return (this.endInclusive - this.startInclusive) + 1;
        }

        @Override
        public String toString() {
            return String.format("Range{startInclusive=%d, endInclusive=%d}",
                    startInclusive, endInclusive);
        }
    }

    /**
     * Supplier class that supplies section streams via Manta.
     */
    private class MantaObjectInputStreamSupplier implements Supplier<InputStream> {
        @Override
        public InputStream get() {
            MantaHttpHeaders headers = new MantaHttpHeaders();
            Range section = sections[currentSection++];

            try {
                return client.getAsInputStream(path, headers,
                        section.getStartInclusive(), section.getEndInclusive());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create Manta InputStream",
                        e);
            }
        }
    }

    /**
     * Supplier class that supplies section streams via a file.
     */
    private class FileSectionStreamSupplier implements Supplier<InputStream> {
        /**
         * Source file to read data from.
         */
        private final File file;

        /**
         * Creates a new instance based on the specified file.
         *
         * @param file file to use for sectional input
         */
        FileSectionStreamSupplier(final File file) {
            this.file = file;
        }

        @Override
        public InputStream get() {
            Range section = sections[currentSection++];

            try {
                FileInputStream fs = new FileInputStream(file);
                long toSkip = section.getStartInclusive();

                if (toSkip > 0) {
                    long skipped = fs.skip(toSkip);

                    if (skipped != toSkip) {
                        throw new AssertionError("To skip value should be equal to actual bytes skipped");
                    }
                }


                return new BoundedInputStream(fs, section.getSize());
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create file InputStream",
                        e);
            }
        }
    }

    /**
     * Creates a new instance that uses multiple HTTP range requests to get
     * a single file and glue it all together as a single {@link InputStream}.
     *
     * @param path path to object in Manta
     * @param client reference to an open Manta client
     * @param size size of the object
     * @param noOfSections number of sections to split object into
     */
    RangeJoiningInputStream(final String path,
                            final MantaClient client,
                            final long size,
                            final int noOfSections) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(client);

        if (size <= 0) {
            throw new IllegalArgumentException("Size of test object must be greater than zero");
        }

        if (noOfSections <= 1) {
            throw new IllegalArgumentException("Number of sections for test object must be greater than one");
        }

        this.path = path;
        this.client = client;
        this.size = size;
        this.sections = splitIntoSections(size, noOfSections);
        this.streamSupplier = new MantaObjectInputStreamSupplier();

    }

    /**
     * Test only constructor used for constructing an instance of the stream
     * that is based on a file input instead of a remote stream.
     *
     * @param size size of file
     * @param noOfSections number of sections to split file into
     * @param file reference to the file
     */
    RangeJoiningInputStream(final long size,
                            final int noOfSections,
                            final File file) {
        Objects.requireNonNull(file);

        if (size <= 0) {
            throw new IllegalArgumentException("Size of test object must be greater than zero");
        }

        if (noOfSections <= 1) {
            throw new IllegalArgumentException("Number of sections for test object must be greater than one");
        }

        this.path = null;
        this.client = null;
        this.size = size;
        this.sections = splitIntoSections(size, noOfSections);
        this.streamSupplier = new FileSectionStreamSupplier(file);
    }

    @Override
    public int available() throws IOException {
        final long remainingBytes = size - bytesRead;

        if (remainingBytes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        return (int)remainingBytes;
    }

    @Override
    public void close() throws IOException {
        backingStream.close();
    }

    @Override
    public int read() throws IOException {
        if (backingStream == null) {
            backingStream = streamSupplier.get();
        }

        final int read = backingStream.read();

        if (read > EOF) {
            bytesRead++;
        }

        if (read <= EOF && currentSection < sections.length) {
            backingStream.close();
            backingStream = streamSupplier.get();
            return read();
        }

        return read;
    }

    @Override
    public int read(final byte[] buffer) throws IOException {
        if (backingStream == null) {
            backingStream = streamSupplier.get();
        }

        final int read = backingStream.read(buffer);

        if (read > EOF) {
            bytesRead += read;
            return read;
        }

        if (currentSection < sections.length) {
            backingStream.close();
            backingStream = streamSupplier.get();

            return backingStream.read(buffer);
        }

        return EOF;
    }

    @Override
    public int read(final byte[] buffer, final int offset, final int length) throws IOException {
        if (backingStream == null) {
            backingStream = streamSupplier.get();
        }

        final int read = backingStream.read(buffer, offset, length);

        if (read > EOF) {
            bytesRead += read;
        }

        if (read <= EOF && currentSection < sections.length) {
            backingStream.close();
            backingStream = streamSupplier.get();
            return read(buffer, offset, length);
        }

        return read;
    }

    @Override
    public long skip(final long n) throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public synchronized void mark(final int readlimit) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * Utility method that determines how many bytes are allocated to each
     * file's section and the start and end positions of each section.
     *
     * @param size size of file
     * @param noOfSections number of sections
     * @return array of section ranges
     */
    static Range[] splitIntoSections(final long size, final int noOfSections) {
        final Range[] sections = new Range[noOfSections];
        final long partSize = (size / noOfSections);
        final long remainder = size - (partSize * noOfSections);

        long position = 0;
        for (int i = 0; i < noOfSections; i++) {
            final long startPosition = position;
            final long endPosition;

            if (i == noOfSections - 1) {
                endPosition = position + partSize + remainder - 1;
            } else {
                endPosition = position + partSize - 1;
            }

            sections[i] = new Range(startPosition, endPosition);
            position = endPosition + 1;
        }

        return sections;
    }
}
