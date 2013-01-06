package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.io.CAVLC.coeffToken;
import static org.jcodec.common.model.ColorSpace.YUV420;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.MBlockIntra16x16;
import org.jcodec.codecs.h264.io.model.MBlockNeighbourhood;
import org.jcodec.codecs.h264.io.model.ResidualBlock;
import org.jcodec.codecs.h264.io.read.IntraMBlockReader;
import org.jcodec.codecs.util.BinUtil;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.model.ColorSpace;

public class TestMBlockI16x16 extends JAVCTestCase {

    public void testMB92() throws Exception {

        // @29204 mb_type 000011001 ( 24)

        String bits = "1 1 0000000000001011 010 011 10 11 10 00011 0100 110 00100 101 101 111 110"
                + "00110 1 1 1 1 00 00000100 101 1 011 10 111 1 01 1 0 1110 0 0010 1111 0000000110"
                + "0 1 11 011 010 100 100 01111 0 01 111 001101 01 1 10 011 00010 010 011 11 01 0"
                + "1010 011 1 10 111 11 01 1 1 001011 1 0011 111 00111 1 1 110 0 1101 10 111 0000100"
                + "000 01 11 011 011 11 0001 0 01000 0 1 011 0010 10 100 000 000 1011 010 01 110 11"
                + "01 1 000110 1 01 10 011 110 001 1 1 001000 1 0011 010 100 101 1 01000 0 001 11 011"
                + "10 101 11 001 1 1 001 10 1 000010 01 10 10 011 11 00011 010 111 0 1 11 00110 011"
                + "00001 0011 111 11 01 0 1110 0 0011 11 00000101 10 1 11 00011";

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));

        IntraMBlockReader intraMBlockReader = new IntraMBlockReader(false, YUV420, false);

        // MB 81
        int[] lumaTop = new int[] { coeffToken(5, 3), coeffToken(3, 1), coeffToken(8, 2),
                coeffToken(4, 3), coeffToken(1, 1), coeffToken(2, 1), coeffToken(3, 1),
                coeffToken(2, 1), coeffToken(8, 3), coeffToken(8, 2), coeffToken(1, 0),
                coeffToken(7, 3), coeffToken(4, 1), coeffToken(2, 0), coeffToken(11, 3),
                coeffToken(7, 3) };

        int[] cbTop = new int[] { coeffToken(1, 1), coeffToken(0, 0), coeffToken(4, 3),
                coeffToken(2, 2) };

        int[] crTop = new int[] { coeffToken(3, 2), coeffToken(0, 0), coeffToken(3, 1),
                coeffToken(2, 2) };

        // MB 91

        int[] lumaLeft = new int[] { coeffToken(1, 1), coeffToken(4, 2), coeffToken(1, 1),
                coeffToken(1, 1),

                coeffToken(2, 2), coeffToken(0, 0), coeffToken(4, 2), coeffToken(5, 3),

                coeffToken(2, 1), coeffToken(5, 3), coeffToken(1, 1), coeffToken(2, 1),

                coeffToken(3, 3), coeffToken(3, 2), coeffToken(3, 3), coeffToken(3, 3) };

        int[] cbLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };
        int[] crLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(lumaLeft, lumaTop, cbLeft, cbTop, crLeft, crTop,
                null, null, true, true);

        //

        MBlockIntra16x16 actual = intraMBlockReader.readMBlockIntra16x16(reader, neighbourhood, 3, 2, 15);

        assertArrayEquals(new int[] { 6, 2, -2, -1, -1, 5, 2, 3, -4, 1, 0, 0, -1, 1, -2, 3 }, actual.getLumaDC()
                .getCoeffs());

        ResidualBlock[] actualLuma = actual.getLumaAC();

        assertArrayEquals(new int[] { 1, -2, 0, 1, -1, 0, 1, -1, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[0].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[2].getCoeffs());
        assertArrayEquals(new int[] { 2, -2, -1, 2, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0 }, actualLuma[3].getCoeffs());
        assertArrayEquals(new int[] { -2, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[4].getCoeffs());
        assertArrayEquals(new int[] { 4, -2, 0, 1, 0, 0, 2, -1, 0, 0, 0, 0, 1, 0, 0 }, actualLuma[5].getCoeffs());
        assertArrayEquals(new int[] { 0, 1, 1, -1, 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[6].getCoeffs());
        assertArrayEquals(new int[] { -3, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[7].getCoeffs());
        assertArrayEquals(new int[] { 2, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[8].getCoeffs());
        assertArrayEquals(new int[] { 1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[9].getCoeffs());
        assertArrayEquals(new int[] { -1, -2, -2, -1, -1, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0 }, actualLuma[10].getCoeffs());
        assertArrayEquals(new int[] { 1, 3, -2, 0, 0, 0, 0, 0, 2, 0, 1, 0, 0, 0, 0 }, actualLuma[11].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, -1, 1, 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[12].getCoeffs());
        assertArrayEquals(new int[] { 0, -2, 1, -2, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0 }, actualLuma[13].getCoeffs());
        assertArrayEquals(new int[] { 0, 2, -3, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0 }, actualLuma[14].getCoeffs());
        assertArrayEquals(new int[] { 0, 1, -2, -1, 0, 0, 0, 0, 3, 1, 0, 0, 0, 0, 0 }, actualLuma[15].getCoeffs());

        assertEquals(0, actual.getQpDelta());

        assertArrayEquals(new int[] { 1, -1, 0, 0 }, actual.getChroma().getCbDC().getCoeffs());
        assertArrayEquals(new int[] { -2, 1, 1, -2 }, actual.getChroma().getCrDC().getCoeffs());

        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCbAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 1, -1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCbAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCbAC()[2].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCbAC()[3].getCoeffs());

        assertArrayEquals(new int[] { -3, 3, 0, -1, 0, 0, -1, 1, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCrAC()[0].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCrAC()[1].getCoeffs());
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCrAC()[2].getCoeffs());
        assertArrayEquals(new int[] { -1, 2, 1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                actual.getChroma().getCrAC()[3].getCoeffs());

        assertEquals(0, actual.getChromaMode());
        assertEquals(3, actual.getLumaMode());
        assertEquals(0, actual.getQpDelta());
    }

    public void testMBType2() throws Exception {
        String bits = "1 1 000000101 00 1 010 000011 101 011 00";

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));

        IntraMBlockReader intraMBlockReader = new IntraMBlockReader(false, YUV420, false);

//        int[] lumaLeft = new int[] { null, null, null, null, null, null, null, null, null, null, null,
//                null, null, null, null, null };
//
//        int[] cbLeft = new int[] { null, null, null, null };
//        int[] crLeft = new int[] { null, null, null, null };

        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(null, null, null, null, null, null, null,
                null, false, false);

        MBlockIntra16x16 actual = intraMBlockReader.readMBlockIntra16x16(reader, neighbourhood, 1, 0, 0);

        assertArrayEquals(new int[] { -5, 2, 2, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0 }, actual.getLumaDC().getCoeffs());
    }
}
