package org.jcodec.codecs.h264.io.read;

import static org.jcodec.common.tools.Debug.trace;

import org.jcodec.codecs.h264.io.BTree;
import org.jcodec.common.io.BitReader;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
public class CAVLCReader {

    private CAVLCReader() {

    }

    public static int readNBit(BitReader bits, int n, String message)  {
        int val = bits.readNBit(n);

        trace(message, String.valueOf(val));

        return val;
    }

    private static int readUE(BitReader bits)  {
        int cnt = 0;
        while (bits.read1Bit() == 0 && cnt < 31)
            cnt++;

        int res = 0;
        if (cnt > 0) {
            long val = bits.readNBit(cnt);

            res = (int) ((1 << cnt) - 1 + val);
        }

        return res;
    }

    public static int readUE(BitReader bits, String message)  {
        int res = readUE(bits);

        trace(message, String.valueOf(res));

        return res;
    }

    public static int readSE(BitReader bits, String message)  {
        int val = readUE(bits);

        int sign = ((val & 0x1) << 1) - 1;
        val = ((val >> 1) + (val & 0x1)) * sign;

        trace(message, String.valueOf(val));

        return val;
    }

    public static boolean readBool(BitReader bits, String message)  {

        boolean res = bits.read1Bit() == 0 ? false : true;

        trace(message, res ? "1" : "0");

        return res;
    }

    public static int readU(BitReader bits, int i, String string)  {
        return (int) readNBit(bits, i, string);
    }

    public static boolean readAE(BitReader bits) {
        throw new UnsupportedOperationException("Unsupported");
    }

    public static int readTE(BitReader bits, int max)  {
        if (max > 1)
            return readUE(bits);
        return ~bits.read1Bit() & 0x1;
    }

    public static int readAEI(BitReader bits) {
        throw new UnsupportedOperationException("Unsupported");
    }

    public static int readME(BitReader bits, String string)  {
        return readUE(bits, string);
    }

    public static Object readCE(BitReader bits, BTree bt, String message)  {
        while (true) {
            int bit = bits.read1Bit();
            bt = bt.down(bit);
            if (bt == null) {
                throw new RuntimeException("Illegal code");
            }
            Object i = bt.getValue();
            if (i != null) {
                trace(message, i.toString());
                return i;
            }
        }
    }

    public static int readZeroBitCount(BitReader bits, String message)  {
        int count = 0;
        while (bits.read1Bit() == 0 && count < 32)
            count++;

        trace(message, String.valueOf(count));

        return count;
    }

    public static void readTrailingBits(BitReader bits)  {
        bits.read1Bit();
        bits.align();
    }

    public static boolean moreRBSPData(BitReader bits)  {
        if (!bits.moreData())
            return false;
        int bitsRem = 8 - bits.curBit();
        if (bits.lastByte() && bits.checkNBit(bitsRem) == (1 << (bitsRem - 1)))
            return false;

        return true;
    }
}