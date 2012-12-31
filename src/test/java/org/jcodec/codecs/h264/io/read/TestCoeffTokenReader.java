package org.jcodec.codecs.h264.io.read;

import java.nio.ByteBuffer;

import junit.framework.TestCase;

import org.jcodec.codecs.h264.io.model.CoeffToken;
import org.jcodec.codecs.util.BinUtil;
import org.jcodec.common.io.BitReader;
import org.junit.Test;

public class TestCoeffTokenReader extends TestCase {

    @Test
    public void testRead1() throws Exception {
        String code = "0000100"; // nC = {0, 1}

        testRead(code, new CoeffToken(5, 3), 0);
    }

    @Test
    public void testRead2() throws Exception {
        String code = "0000000110"; // nC = {0, 1}

        testRead(code, new CoeffToken(5, 1), 0);
    }

    @Test
    public void testRead3() throws Exception {
        String code = "00011"; // nC = {0, 1}

        testRead(code, new CoeffToken(3, 3), 0);
    }

    public void testEstimate() throws Exception {
        assertTrue(true);
    }

    private void testRead(String code, CoeffToken expected, int estimatedCoeffs) throws Exception {
        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(code)));

        CoeffToken actual = CoeffTokenReader.read(reader, estimatedCoeffs);

        assertEquals(expected, actual);
    }
}
