package org.jcodec.codecs.h264;

import static org.jcodec.codecs.h264.io.CAVLC.coeffToken;
import static org.jcodec.codecs.h264.io.model.MBPartPredMode.Pred_L0;
import static org.jcodec.common.model.ColorSpace.YUV420;

import java.nio.ByteBuffer;

import org.jcodec.codecs.h264.io.model.InterPrediction;
import org.jcodec.codecs.h264.io.model.MBPartPredMode;
import org.jcodec.codecs.h264.io.model.MBlockInter;
import org.jcodec.codecs.h264.io.model.MBlockNeighbourhood;
import org.jcodec.codecs.h264.io.model.Vector;
import org.jcodec.codecs.h264.io.read.InterMBlockReader;
import org.jcodec.codecs.util.BinUtil;
import org.jcodec.common.io.BitReader;

public class TestMBlockInter extends JAVCTestCase {

    public void testMB8x16() throws Exception {

        // MB 28

        int[] lumaTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0),

                coeffToken(1, 1), coeffToken(0, 0), coeffToken(1, 1), coeffToken(1, 1),

                coeffToken(3, 3), coeffToken(2, 2), coeffToken(1, 1), coeffToken(1, 1) };

        int[] cbTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        // MB 38
        int[] lumaLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(1, 1), coeffToken(1, 1), coeffToken(1, 0),

                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(1, 1), coeffToken(1, 1), coeffToken(0, 0) };

        int[] cbLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        // MB 39
        // @24219 mb_type 011 ( 2)
        String bits = "000010011 00101 000010111 011 00000101110 1 01 1 1 01 1 1 01 1 1 1 01"
                + "1 1 1 01 1 1 01 1 1 01 1 0 001";

        InterMBlockReader mBlockReader = new InterMBlockReader(false, YUV420, false, 1, 5, true);

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));

        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(lumaLeft, lumaTop, cbLeft, cbTop, crLeft, crTop,
                null, null, true, true);

        MBlockInter actual = mBlockReader.readMBlockInter(reader, neighbourhood, 2, new MBPartPredMode[] { Pred_L0,
                Pred_L0 }, false, MBlockInter.Type.MB_8x16);

        InterPrediction actualPred = actual.getPrediction();

        assertArrayEquals(new Vector[] { new Vector(-9, -2, 0), new Vector(-11, -1, 0) }, actualPred.getDecodedMVsL0());

    }

    public void testMB16x8() throws Exception {
        // MB 19
        int[] lumaTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0) };

        int[] cbTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };
        // MB 29
        int[] lumaLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),

                coeffToken(2, 2), coeffToken(0, 0), coeffToken(1, 1), coeffToken(1, 1),

                coeffToken(0, 0), coeffToken(1, 1), coeffToken(0, 0), coeffToken(1, 1) };

        int[] cbLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        // MB 30
        // @23737 mb_type 010 ( 1)
        String bits = "1 1 0001011 00101 00110 010 1 01 0 1 000011 100 01 111 1 1 0 0100" + "001 01 111 0";

        InterMBlockReader mBlockReader = new InterMBlockReader(false, YUV420, false, 1, 5, true);

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));
        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(lumaLeft, lumaTop, cbLeft, cbTop, crLeft, crTop,
                null, null, true, true);
        MBlockInter actual = mBlockReader.readMBlockInter(reader, neighbourhood, 2, new MBPartPredMode[] { Pred_L0,
                Pred_L0 }, false, MBlockInter.Type.MB_16x8);

        InterPrediction actualPred = actual.getPrediction();

        assertArrayEquals(new Vector[] { new Vector(0, 0, 0), new Vector(-5, -2, 0) }, actualPred.getDecodedMVsL0());
    }

    public void testMB16x16() throws Exception {
        // MB 61
        int[] lumaTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0),

                coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),

                coeffToken(2, 2), coeffToken(1, 0), coeffToken(0, 0), coeffToken(0, 0),

                coeffToken(2, 2), coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0) };

        int[] cbTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crTop = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        // MB 71
        int[] lumaLeft = new int[] { coeffToken(0, 0), coeffToken(1, 1), coeffToken(3, 2),
                coeffToken(0, 0),

                coeffToken(1, 1), coeffToken(1, 1), coeffToken(0, 0), coeffToken(1, 1),

                coeffToken(4, 3), coeffToken(2, 0), coeffToken(1, 1), coeffToken(1, 0),

                coeffToken(1, 1), coeffToken(1, 0), coeffToken(2, 1), coeffToken(2, 1) };

        int[] cbLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        int[] crLeft = new int[] { coeffToken(0, 0), coeffToken(0, 0), coeffToken(0, 0),
                coeffToken(0, 0) };

        // MB 72

        // @26365 mb_type 1 ( 0)
        String bits = "011 1 000010100 010 1 000101 1 1 000011 100 0001 111 0 000101 10 001"
                + "011 0011 110 101 0 01 0 1 1 000111 001 011 110 0 00000110 1 001 0011 011"
                + "001 0 0100 110 01 00011 001000 01 10 0011 0101 10 0 010 11 000111 0001"
                + "0011 110 0 0000101 00 01 10 000011 101 000 1 10 0 010 01 1 1 001";

        InterMBlockReader mBlockReader = new InterMBlockReader(false, YUV420, false, 1, 5, true);

        BitReader reader = new BitReader(ByteBuffer.wrap(BinUtil.binaryStringToBytes(bits)));
        MBlockNeighbourhood neighbourhood = new MBlockNeighbourhood(lumaLeft, lumaTop, cbLeft, cbTop, crLeft, crTop,
                null, null, true, true);
        MBlockInter actual = mBlockReader.readMBlockInter(reader, neighbourhood, 1, new MBPartPredMode[] { Pred_L0 },
                false, MBlockInter.Type.MB_16x16);

        InterPrediction actualPred = actual.getPrediction();

        assertArrayEquals(new Vector[] { new Vector(-1, 0, 0) }, actualPred.getDecodedMVsL0());
    }
}