package com.joyent.manta.cosbench;

import com.joyent.manta.client.MantaClient;
import com.joyent.manta.http.MantaHttpHeaders;
import com.joyent.manta.org.apache.commons.io.input.BoundedInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

/**
 * {@link java.io.InputStream} implementation that joins multiple HTTP
 * range requests together in order to produce a single {@link java.io.InputStream}.
 *
 * @author <a href="https://github.com/dekobon">Elijah Zupancic</a>
 * @since 1.1.0
 */
public class RangeJoiningInputStream extends InputStream {
    private static final int EOF = -1;

    private final String path;
    private final MantaClient client;
    private final Range[] sections;
    private final Supplier<InputStream> streamSupplier;
    private int currentSection = 0;
    private long bytesRead = 0;
    private long size;
    private InputStream backingStream;

    static class Range {
        final long startInclusive;
        final long endInclusive;

        public Range(final long startInclusive, final long endInclusive) {
            this.startInclusive = startInclusive;
            this.endInclusive = endInclusive;
        }

        public long getStartInclusive() {
            return startInclusive;
        }

        public long getEndInclusive() {
            return endInclusive;
        }

        public long getSize() {
            return this.endInclusive - this.startInclusive;
        }

        @Override
        public String toString() {
            return String.format("Range{startInclusive=%d, endInclusive=%d}",
                    startInclusive, endInclusive);
        }
    }

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

    private class FileSectionStreamSupplier implements Supplier<InputStream> {
        private final File file;

        FileSectionStreamSupplier(final File file) {
            this.file = file;
        }

        @Override
        public InputStream get() {
            Range section = sections[currentSection++];

            try {
                FileInputStream fs = new FileInputStream(file);
                long toSkip = section.getStartInclusive() - 1;

                if (toSkip > 0) {
                    long skipped = fs.skip(toSkip);

                    if (skipped != toSkip) {
                        throw new AssertionError("To skip value should be equal to actual bytes skipped");
                    }
                }


                return new BoundedInputStream(fs, section.getSize() + 1);
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to create file InputStream",
                        e);
            }
        }
    }

    RangeJoiningInputStream(final String path,
                            final MantaClient client,
                            final long size,
                            final int noOfSections) {
        this.path = path;
        this.client = client;
        this.size = size;
        this.sections = splitIntoSections(size, noOfSections);
        this.streamSupplier = new MantaObjectInputStreamSupplier();

    }

    RangeJoiningInputStream(final String path,
                            final MantaClient client,
                            final long size,
                            final int noOfSections,
                            final File file) {
        this.path = path;
        this.client = client;
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
        }

        if (read <= EOF && currentSection < sections.length) {
            backingStream.close();
            backingStream = streamSupplier.get();
            return read(buffer);
        }

        return read;
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
    public synchronized void mark(int readlimit) {
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

    static Range[] splitIntoSections(final long size, final int noOfSections) {
        final Range[] sections = new Range[noOfSections];
        final long partSize = (size / noOfSections) - 1;
        final long remainder = size - (partSize * noOfSections);

        long position = 0;
        for (int i = 0; i < noOfSections; i++) {
            final long startPosition = position;
            final long endPosition;

            if (i == noOfSections - 1) {
                endPosition = position + partSize + remainder;
            } else {
                endPosition = position + partSize;
            }

            sections[i] = new Range(startPosition, endPosition);
            position = endPosition + 1;
        }

        return sections;
    }
}
