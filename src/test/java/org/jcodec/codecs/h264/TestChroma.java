package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.io.CAVLC.coeffToken;
import static org.jcodec.common.model.ColorSpace.YUV420;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.CodedChroma;
import org.jcodec.codecs.h264.io.model.MBlockNeighbourhood;
import org.jcodec.codecs.h264.io.read.ChromaReader;
import org.jcodec.codecs.util.BinUtil;
import org.jcodec.common.io.BitReader;

public class TestChroma extends JAVCTestCase {

    public void testFirstMB() throws Exception {

        String bits = "00000011 1 001 10 11 0000011 0 00000001 0111 0 1 0 00110 110 1"
                + "001001 11 1 100 101 1 10 0 1 11 000000100 000 1 010 0011 10 101"
                + "00 001000 1 0010 00011 100 101 1 1111 11";

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));

        ChromaReader chromaReader = new ChromaReader(YUV420, false);

        int codedBlockPattern = 47;
        int codedBlockPatternChroma = codedBlockPattern / 16;

        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(null, null, null, null, null, null, null, null,
                false, false);

        CodedChroma actual = chromaReader.readChroma(reader, codedBlockPatternChroma, neighbourhood);

        assertArrayEquals(new int[] { -1, 1, 3, -1 }, actual.getCbDC().getCoeffs());
        assertArrayEquals(new int[] { -4, 0, -5, 1 }, actual.getCrDC().getCoeffs());

        assertArrayEquals(new int[] { 0, 1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 0, 2, -1, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[2].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[3].getCoeffs());

        assertArrayEquals(new int[] { 1, -3, 2, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 0, -4, 3, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[2].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[3].getCoeffs());

        assertEquals(coeffToken(2, 2), actual.getCoeffTokenCb()[0]);
        assertEquals(coeffToken(3, 2), actual.getCoeffTokenCb()[1]);
        assertEquals(coeffToken(1, 1), actual.getCoeffTokenCb()[2]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCb()[3]);

        assertEquals(coeffToken(7, 3), actual.getCoeffTokenCr()[0]);
        assertEquals(coeffToken(3, 0), actual.getCoeffTokenCr()[1]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCr()[2]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCr()[3]);
    }

    public void testMiddleMB() throws Exception {
        // 93
        String bits = "00000011 1 1 11 11 00000011 1 0001 10 10 11 1 1 1 11 1 001001 01 001 111 1 1 11";

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));

        ChromaReader chromaReader = new ChromaReader(YUV420, false);

        int codedBlockPattern = 47;
        int codedBlockPatternChroma = codedBlockPattern / 16;

        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(null, null, new int[] {
                coeffToken(0, 0), coeffToken(3, 3), coeffToken(0, 0), coeffToken(0, 0) },
                new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                        coeffToken(0, 0) }, new int[] { coeffToken(5, 3), coeffToken(2, 2),
                        coeffToken(0, 0), coeffToken(4, 2) }, new int[] { coeffToken(0, 0),
                        coeffToken(1, 1), coeffToken(1, 1), coeffToken(1, 1) }, null, null, true, true);

        CodedChroma actual = chromaReader.readChroma(reader, codedBlockPatternChroma, neighbourhood);

        assertArrayEquals(new int[] { -1, -1, 2, -1 }, actual.getCbDC().getCoeffs());
        assertArrayEquals(new int[] { 1, 1, -3, -1 }, actual.getCrDC().getCoeffs());

        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[2].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCbAC()[3].getCoeffs());

        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 3, -1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[2].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actual.getCrAC()[3].getCoeffs());

        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCb()[0]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCb()[1]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCb()[2]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCb()[3]);

        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCr()[0]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCr()[1]);
        assertEquals(coeffToken(3, 2), actual.getCoeffTokenCr()[2]);
        assertEquals(coeffToken(0, 0), actual.getCoeffTokenCr()[3]);
    }

}
