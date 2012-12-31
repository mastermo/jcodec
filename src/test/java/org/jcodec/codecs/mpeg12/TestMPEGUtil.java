package org.jcodec.codecs.mpeg12;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.ByteBuffer;

import org.jcodec.common.ByteBufferUtil;
import org.junit.Test;

public class TestMPEGUtil {

    @Test
    public void testGotoNextMarker() {
        ByteBuffer input = ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0x78, 1, 2, 3, 4, 5, 0, 0, 0, 0,
                0, 0, 0, 1, 0x75, 6, 7, 8, 9, 10, 0, 0, 1, 0x72, 11, 12, 13 });
        ByteBuffer before1 = MPEGUtil.gotoNextMarker(input);
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0 }, ByteBufferUtil.toArray(before1));
        assertEquals(input.position(), 7);
        input.position(input.position() + 4);
        ByteBuffer before2 = MPEGUtil.gotoNextMarker(input);
        assertArrayEquals(new byte[] { 1, 2, 3, 4, 5, 0, 0, 0, 0, 0 }, ByteBufferUtil.toArray(before2));
        assertEquals(input.position(), 21);
        assertEquals(1, before2.get(0));
        input.position(input.position() + 4);
        ByteBuffer before3 = MPEGUtil.gotoNextMarker(input);
        assertArrayEquals(new byte[] { 6, 7, 8, 9, 10 }, ByteBufferUtil.toArray(before3));
        assertEquals(input.position(), 30);
        assertEquals(6, before3.get(0));
        input.position(input.position() + 4);
        ByteBuffer before4 = MPEGUtil.gotoNextMarker(input);
        assertArrayEquals(new byte[] { 11, 12, 13 }, ByteBufferUtil.toArray(before4));
        assertFalse(input.hasRemaining());
        assertEquals(11, before4.get(0));
    }

    @Test
    public void testNextSegment() {
        ByteBuffer input = ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0x78, 1, 2, 3, 4, 5, 0, 0, 0, 0,
                0, 0, 0, 1, 0x75, 6, 7, 8, 9, 10, 0, 0, 1, 0x72, 11, 12, 13 });

        input.get();
        ByteBuffer segment1 = MPEGUtil.nextSegment(input);
        assertArrayEquals(new byte[] { 0, 0, 1, 0x78, 1, 2, 3, 4, 5, 0, 0, 0, 0, 0 }, ByteBufferUtil.toArray(segment1));
        ByteBuffer segment2 = MPEGUtil.nextSegment(input);
        assertArrayEquals(new byte[] { 0, 0, 1, 0x75, 6, 7, 8, 9, 10 }, ByteBufferUtil.toArray(segment2));
        ByteBuffer segment3 = MPEGUtil.nextSegment(input);
        assertArrayEquals(new byte[] { 0, 0, 1, 0x72, 11, 12, 13 }, ByteBufferUtil.toArray(segment3));
    }
}
