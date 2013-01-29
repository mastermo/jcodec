package org.jcodec.codecs.h264.annexb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
public class H264Utils {

    public static ByteBuffer nextNALUnit(ByteBuffer buf) {
        gotoNALUnit(buf, 0);
        return gotoNALUnit(buf, 1);
    }

    /**
     * Finds next Nth H.264 bitstream NAL unit (0x00000001) and returns the data
     * that preceeds it as a ByteBuffer slice
     * 
     * Segment byte order is always little endian
     * 
     * TODO: emulation prevention
     * 
     * @param buf
     * @return
     */
    public static final ByteBuffer gotoNALUnit(ByteBuffer buf, int n) {

        if (!buf.hasRemaining())
            return null;

        int from = buf.position();
        ByteBuffer result = buf.slice();
        result.order(ByteOrder.BIG_ENDIAN);

        int val = 0xffffffff;
        while (buf.hasRemaining()) {
            val <<= 8;
            val |= buf.get();
            if (val == 1) {
                if (n == 0) {
                    buf.position(buf.position() - 4);
                    result.limit(buf.position() - from);
                    break;
                }
                --n;
            }
        }
        return result;
    }

    public static final void unescapeNAL(ByteBuffer _buf) {
        ByteBuffer in = _buf.duplicate();
        ByteBuffer out = _buf.duplicate();
        byte p1 = in.get();
        out.put(p1);
        byte p2 = in.get();
        out.put(p2);
        while (in.hasRemaining()) {
            byte b = in.get();
            if (p1 != 0 || p2 != 0 || b != 3)
                out.put(b);
            p1 = p2;
            p2 = b;
        }
        _buf.limit(out.position());
    }

    public static final void escapeNAL(ByteBuffer src, ByteBuffer dst) {
        byte p1 = src.get(), p2 = src.get();
        dst.put(p1);
        dst.put(p2);
        while (src.hasRemaining()) {
            byte b = src.get();
            if (p1 == 0 && p2 == 0 && (b & 0xff) <= 3) {
                dst.put((byte) 3);
                p1 = p2;
                p2 = 3;
            }
            dst.put(b);
            p1 = p2;
            p2 = b;
        }
    }
}