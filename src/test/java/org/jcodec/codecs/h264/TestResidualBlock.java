package org.jcodec.codecs.h264;

import static org.jcodec.common.model.ColorSpace.YUV420;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.CAVLC;
import org.jcodec.codecs.util.BinUtil;
import org.jcodec.common.io.BitReader;
import org.junit.Test;

public class TestResidualBlock extends JAVCTestCase {

    @Test
    public void testLuma1() throws Exception {

        // 0000100 5,3
        String code = "0000100 01110010111101101";
        int[] coeffs = { 0, 3, 0, 1, -1, -1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0 };

        testResidual(code, coeffs);
    }

    @Test
    public void testLuma2() throws Exception {
        // 0000000110 5,1
        String code = "0000000110 10001001000010111001100";
        int[] coeffs = { -2, 4, 3, -3, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        testResidual(code, coeffs);
    }

    @Test
    public void testLuma3() throws Exception {
        // 00011 3,3
        String code = "00011 10001110010";
        int[] coeffs = { 0, 0, 0, 1, 0, 1, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0 };

        testResidual(code, coeffs);
    }

    @Test
    public void testChromaAC3() throws Exception {
        // 00011
        String code = "000000100 000 1 010 0011 10 101 00";

        int[] coeffs = { 1, -3, 2, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0 };

        testResidual(code, coeffs);
    }

    private void testResidual(String code, int[] expected) throws IOException {
        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(code)));

        CAVLC residualReader = new CAVLC(YUV420);
        int[] actual = new int[expected.length];
        residualReader.readCoeffs(reader, H264Const.coeffToken[0], H264Const.totalZeros16, actual);

        assertArrayEquals(expected, actual);
    }
}
