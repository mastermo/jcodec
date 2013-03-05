package org.jcodec.codecs.h264;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.jcodec.common.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.Yuv420pToRgb;
import org.junit.Assert;
import org.junit.Test;

public class TestI16x16 {
    @Test
    public void testMBlockCABAC1() throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.fetchFrom(new File(
                "src/test/resources/h264/cabac/i16x16_1/16x16.264")));
        ByteBuffer data = es.nextFrame().getData();
        Picture buf = Picture.create(16, 16, ColorSpace.YUV420);
        Picture out = new H264Decoder(new Picture[0]).decodeFrame(data, buf.getData());

        ByteBuffer yuv = NIOUtils.fetchFrom(new File("src/test/resources/h264/cabac/i16x16_1/16x16.yuv"));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(0));
        Assert.assertArrayEquals(getAsIntArray(yuv, 64), out.getPlaneData(1));
        Assert.assertArrayEquals(getAsIntArray(yuv, 64), out.getPlaneData(2));
    }

    @Test
    public void testMBlockCABAC2() throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.fetchFrom(new File(
                "src/test/resources/h264/cabac/i16x16_2/32x32.264")));
        ByteBuffer data = es.nextFrame().getData();
        Picture buf = Picture.create(32, 32, ColorSpace.YUV420);
        Picture out = new H264Decoder(new Picture[0]).decodeFrame(data, buf.getData());

        Picture rgb = Picture.create(32, 32, ColorSpace.RGB);
        new Yuv420pToRgb(0, 0).transform(out, rgb);
        BufferedImage bufferedImage = AWTUtil.toBufferedImage(rgb);
        ImageIO.write(bufferedImage, "png", new File("/Users/stan/Desktop/cool.png"));

        ByteBuffer yuv = NIOUtils.fetchFrom(new File("src/test/resources/h264/cabac/i16x16_2/32x32.yuv"));
        Assert.assertArrayEquals(getAsIntArray(yuv, 1024), out.getPlaneData(0));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(1));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(2));
    }
    
    @Test
    public void testMBlockCABAC3() throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.fetchFrom(new File(
                "src/test/resources/h264/cabac/i16x16_3/32x32.264")));
        ByteBuffer data = es.nextFrame().getData();
        Picture buf = Picture.create(32, 32, ColorSpace.YUV420);
        Picture out = new H264Decoder(new Picture[0]).decodeFrame(data, buf.getData());
        
        ByteBuffer yuv = NIOUtils.fetchFrom(new File("src/test/resources/h264/cabac/i16x16_3/32x32.yuv"));
        Assert.assertArrayEquals(getAsIntArray(yuv, 1024), out.getPlaneData(0));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(1));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(2));
    }
    
    @Test
    public void testMBlockCABAC4() throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.fetchFrom(new File(
                "src/test/resources/h264/cabac/i16x16_4/32x32.264")));
        ByteBuffer data = es.nextFrame().getData();
        Picture buf = Picture.create(32, 32, ColorSpace.YUV420);
        Picture out = new H264Decoder(new Picture[0]).decodeFrame(data, buf.getData());
        
        ByteBuffer yuv = NIOUtils.fetchFrom(new File("src/test/resources/h264/cabac/i16x16_4/32x32.yuv"));
        Assert.assertArrayEquals(getAsIntArray(yuv, 1024), out.getPlaneData(0));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(1));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(2));
    }
    
    @Test
    public void testMBlockCABAC5() throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.fetchFrom(new File(
                "src/test/resources/h264/cabac/i16x16_5/32x32.264")));
        ByteBuffer data = es.nextFrame().getData();
        Picture buf = Picture.create(32, 32, ColorSpace.YUV420);
        Picture out = new H264Decoder(new Picture[0]).decodeFrame(data, buf.getData());
        
        ByteBuffer yuv = NIOUtils.fetchFrom(new File("src/test/resources/h264/cabac/i16x16_5/32x32.yuv"));
        Assert.assertArrayEquals(getAsIntArray(yuv, 1024), out.getPlaneData(0));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(1));
        Assert.assertArrayEquals(getAsIntArray(yuv, 256), out.getPlaneData(2));
    }

    private int[] getAsIntArray(ByteBuffer yuv, int size) {
        byte[] b = new byte[size];
        int[] result = new int[size];
        yuv.get(b);
        for (int i = 0; i < b.length; i++) {
            result[i] = b[i] & 0xff;
        }
        return result;
    }
}