package org.jcodec.codecs.h264.mp4;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.jcodec.common.NIOUtils;
import org.jcodec.containers.mp4.boxes.Box;
import org.jcodec.containers.mp4.boxes.Header;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Creates MP4 file out of a set of samples
 * 
 * @author Jay Codec
 * 
 */
public class AvcCBox extends Box {

    private List<ByteBuffer> spsList = new ArrayList<ByteBuffer>();
    private List<ByteBuffer> ppsList = new ArrayList<ByteBuffer>();

    public AvcCBox(Box other) {
        super(other);
    }

    public AvcCBox() {
        super(new Header(fourcc()));
    }

    public AvcCBox(Header header) {
        super(header);
    }

    public AvcCBox(List<ByteBuffer> spsList, List<ByteBuffer> ppsList) {
        this();
        this.spsList = spsList;
        this.ppsList = ppsList;
    }

    public static String fourcc() {
        return "avcC";
    }

    @Override
    public void parse(ByteBuffer input) {
        NIOUtils.skip(input, 5);

        int nSPS = input.get() & 0x1f; // 3 bits reserved + 5 bits number of
                                       // sps
        for (int i = 0; i < nSPS; i++) {
            int spsSize = input.getShort();
            Assert.assertEquals(0x67, input.get() & 0xff);
            spsList.add(NIOUtils.read(input, spsSize));
        }

        int nPPS = input.get() & 0xff;
        for (int i = 0; i < nPPS; i++) {
            int ppsSize = input.getShort();
            Assert.assertEquals(0x68, input.get() & 0xff);
            ppsList.add(NIOUtils.read(input, ppsSize));
        }
    }

    @Override
    protected void doWrite(ByteBuffer out) {
        out.put((byte) 0x1);
        out.put((byte) 0x64);
        out.put((byte) 0x0);
        out.put((byte) 0x1e);
        out.put((byte) 0xff);

        out.put((byte) (spsList.size() | 0xe0));
        for (ByteBuffer sps : spsList) {
            out.putShort((short) (sps.remaining() + 1));
            out.put((byte) 0x67);
            NIOUtils.write(out, sps);
        }

        out.put((byte) ppsList.size());
        for (ByteBuffer pps : ppsList) {
            out.putShort((byte) (pps.remaining() + 1));
            out.put((byte) 0x68);
            NIOUtils.write(out, pps);
        }
    }

    public List<ByteBuffer> getSpsList() {
        return spsList;
    }

    public List<ByteBuffer> getPpsList() {
        return ppsList;
    }
}