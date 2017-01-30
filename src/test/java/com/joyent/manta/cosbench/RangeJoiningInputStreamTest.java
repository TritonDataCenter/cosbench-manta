package com.joyent.manta.cosbench;

import com.joyent.manta.org.apache.commons.io.IOUtils;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Test
public class RangeJoiningInputStreamTest {
    public void canReadFileInChunksAsSingleStream() throws Exception {
        final long size = 10;
        final int noOfSections = 5;
        final Path path = Files.createTempFile("chunk-read", ".data");
        final byte[] expected = new byte[(int)size];

        long bytesCopied = 0;

        try (RandomInputStream ri = new RandomInputStream(size);
             FileOutputStream os = new FileOutputStream(path.toFile())) {
            IOUtils.copy(ri, os);

            try (FileInputStream fi = new FileInputStream(path.toFile())) {
                IOUtils.read(fi, expected);
            }

            final byte[] actual;

            try (RangeJoiningInputStream rjis = new RangeJoiningInputStream("",
                    null, size, noOfSections, path.toFile());
                 ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
                bytesCopied += IOUtils.copy(rjis, bout);

                actual = bout.toByteArray();
            }

            AssertJUnit.assertArrayEquals("Bytes read doesn't actual bytes of source file "
                    + "[bytesCopied=" + bytesCopied + "]",
                    expected, actual);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    public void splitPartsAddUpToOriginalSize() {
        final long size = 345345324532L;
        final int noOfSections = 19;

        RangeJoiningInputStream.Range[] sections = RangeJoiningInputStream.splitIntoSections(size, noOfSections);

        long sum = 0L;
        for (RangeJoiningInputStream.Range r : sections) {
            sum += r.getSize();
        }

        Assert.assertEquals(sum, size, "Actual size isn't the sum of the sections");
    }

    public void splitPartsAreCorrect() {
        final long size = 345345324532L;
        final int noOfSections = 19;

        RangeJoiningInputStream.Range[] sections = RangeJoiningInputStream.splitIntoSections(size, noOfSections);

        long lastEndPos = -1L;
        for (RangeJoiningInputStream.Range r : sections) {
            Assert.assertTrue(r.getStartInclusive() < r.getEndInclusive(),
                    "Start should always precede end position");

            Assert.assertTrue(r.getStartInclusive() > lastEndPos,
                    "Start should always precede the last end position");

            lastEndPos = r.getEndInclusive();
        }

    }


}
