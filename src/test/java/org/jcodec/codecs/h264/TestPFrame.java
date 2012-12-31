package org.jcodec.codecs.h264;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

import junit.framework.TestCase;

import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;

public class TestPFrame extends TestCase {
	public void test8x8NoCabac() throws Exception {
		File path = new File("src/test/resources/h264/pframe_nocabac.264");
		FileInputStream is = new FileInputStream(path);
		MappedByteBuffer map = is.getChannel().map(MapMode.READ_ONLY, 0, path.length());
		is.close();

		MappedH264ES reader = new MappedH264ES(map);
		
		H264Decoder h264Decoder = new H264Decoder();
		Picture out = Picture.create(1920, 1088, ColorSpace.YUV420);
		h264Decoder.decodeFrame(reader.nextFrame(), out.getData());
		h264Decoder.decodeFrame(reader.nextFrame(), out.getData());
	}
}
