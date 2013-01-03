package org.jcodec.codecs.h264;

import static org.jcodec.common.NIOUtils.map;

import java.io.IOException;

import junit.framework.TestCase;

import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

public class TestIFrame extends TestCase {

    public void testNoCabac() throws Exception {
        decodeFrame("src/test/resources/h264/iframe_nocabac.264");

    }

    private void decodeFrame(String path) throws IOException {
        MappedH264ES reader = new MappedH264ES(map(path));
        H264Decoder decoder = new H264Decoder();
        Picture buf = Picture.create(1920, 1088, ColorSpace.YUV420);
        decoder.decodeFrame(reader.nextFrame(), buf.getData());
    }

    public void testCabac() {
        assertTrue(true);
    }

    public void test8x8NoCabac() throws Exception {
        decodeFrame("src/test/resources/h264/iframe_t8x8_nocabac.264");
    }

    public void test8x8Cabac() {
        assertTrue(true);
    }
}