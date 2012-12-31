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
    
    /**
     * Removes emulation bytes from h.264 bytestream
     * @param src
     * @return
     */
    public static ByteBuffer emulationDecode(ByteBuffer src) {
        return null;
    }
}