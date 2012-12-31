package org.jcodec.common.io;

import java.nio.ByteBuffer;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class BitReader {
    protected int deficit;
    protected int curInt;
    private ByteBuffer bb;

    public BitReader(ByteBuffer bb) {
        this.bb = bb;
        curInt = readInt();
        deficit = 0;
    }

    private BitReader(BitReader other) {
        bb = other.bb.duplicate();
        curInt = other.curInt;
        deficit = other.deficit;
    }

    public final int readInt() {
        if (bb.remaining() >= 4) {
            deficit -= 32;
            return ((bb.get() & 0xff) << 24) | ((bb.get() & 0xff) << 16) | ((bb.get() & 0xff) << 8) | (bb.get() & 0xff);
        } else
            return readIntSafe();
    }

    private int readIntSafe() {
        deficit -= (bb.remaining() << 3);
        int res = 0;
        if (bb.hasRemaining())
            res |= bb.get() & 0xff;
        res <<= 8;
        if (bb.hasRemaining())
            res |= bb.get() & 0xff;
        res <<= 8;
        if (bb.hasRemaining())
            res |= bb.get() & 0xff;
        res <<= 8;
        if (bb.hasRemaining())
            res |= bb.get() & 0xff;
        return res;
    }

    public int read1Bit() {

        int ret = curInt >>> 31;
        curInt <<= 1;
        ++deficit;
        if (deficit == 32) {
            curInt = readInt();
        }

        return ret;
    }

    public int readNBit(int n) {
        if (n > 32)
            throw new IllegalArgumentException("Can not read more then 32 bit");

        int ret = 0;
        if (n + deficit > 31) {
            ret |= (curInt >>> deficit);
            n -= 32 - deficit;
            ret <<= n;
            deficit = 32;
            curInt = readInt();
        }

        if (n != 0) {
            ret |= curInt >>> (32 - n);
            curInt <<= n;
            deficit += n;
        }

        return ret;
    }

    public boolean moreData() {
        int remaining = bb.remaining() + 4 - ((deficit + 7) >> 3);
        return remaining > 1 || (remaining == 1 && curInt != 0);
    }

    public int remaining() {
        return (bb.remaining() << 3) + 32 - deficit;
    }

    public final boolean isByteAligned() {
        return (deficit & 0x7) == 0;
    }

    public int skip(int bits) {
        int left = bits;

        if (left + deficit > 31) {
            left -= 32 - deficit;
            deficit = 32;
            if (left > 31) {
                int skip = Math.min(left >> 3, bb.remaining());
                bb.position(bb.position() + skip);
                left -= skip << 3;
            }
            curInt = readInt();
        }

        deficit += left;
        curInt <<= left;

        return bits;
    }

    public int align() {
        return (deficit & 0x7) > 0 ? skip(8 - (deficit & 0x7)) : 0;
    }

    public int checkNBit(int n) {
        if (n > 24)
            throw new IllegalArgumentException("Can not check more then 24 bit");

        while (deficit + n > 32) {
            deficit -= 8;
            curInt |= nextIgnore() << deficit;
        }
        return curInt >>> (32 - n);
    }

    private int nextIgnore() {
        return bb.hasRemaining() ? bb.get() & 0xff : 0;
    }

    public int curBit() {
        return deficit & 0x7;
    }

    public boolean lastByte() {
        return bb.remaining() + 4 - (deficit >> 3) <= 1;
    }

    public BitReader fork() {
        return new BitReader(this);
    }
}