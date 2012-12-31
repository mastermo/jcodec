package org.jcodec.codecs.h264;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.jcodec.codecs.h264.annexb.H264Utils;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.io.read.SliceHeaderReader;
import org.jcodec.codecs.h264.io.write.NALUnitWriter;
import org.jcodec.codecs.h264.io.write.SliceHeaderWriter;
import org.jcodec.common.io.BitReader;
import org.jcodec.common.io.BitWriter;

public class CopyTest {
    private static SeqParameterSet sps;
    private static PictureParameterSet pps;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Syntax: <in> <out>");
            System.exit(-1);
        }
        FileInputStream is = null;
        FileOutputStream os = null;
        try {
            File in = new File(args[0]);
            is = new FileInputStream(in);
            os = new FileOutputStream(args[1]);

            NALUnitWriter out = new NALUnitWriter(os.getChannel());
            MappedByteBuffer map = is.getChannel().map(MapMode.READ_ONLY, 0, in.length());

            SliceHeaderReader reader = null;
            SliceHeaderWriter writer = null;
            ByteBuffer nus;
            while ((nus = H264Utils.nextNALUnit(map)) != null) {
                NALUnit nu = NALUnit.read(nus);
                if (nu.type == NALUnitType.SPS) {
                    ByteBuffer buf = ByteBuffer.allocate(1024);
                    sps = SeqParameterSet.read(nus);
                    sps.seq_parameter_set_id = 1;
                    sps.write(buf);
                    buf.flip();
                    out.writeUnit(nu, buf);
                    System.out.println("SPS");
                } else if (nu.type == NALUnitType.PPS) {
                    ByteBuffer buf = ByteBuffer.allocate(1024);
                    pps = PictureParameterSet.read(nus);
                    pps.seq_parameter_set_id = 1;
                    pps.pic_parameter_set_id = 1;
                    pps.write(buf);
                    buf.flip();
                    out.writeUnit(nu, buf);
                    reader = new SliceHeaderReader();
                    writer = new SliceHeaderWriter(sps, pps);
                    System.out.println("PPS");
                } else if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {
                    BitReader r = new BitReader(nus);
                    SliceHeader header = reader.readPart1(r);
                    reader.readPart2(header, nu, sps, pps, r);
                    header.pic_parameter_set_id = 1;
                    ByteBuffer oo = ByteBuffer.allocate(nus.remaining() + 1024);
                    BitWriter w = new BitWriter(oo);
                    writer.write(header, nu.type == NALUnitType.IDR_SLICE, nu.nal_ref_idc, w);

                    if (pps.entropy_coding_mode_flag) {
                        copyCABAC(w, r);
                    } else {
                        copyCAVLC(w, r);
                    }
                    oo.flip();
                    out.writeUnit(nu, oo);
                } else {
                    out.writeUnit(nu, nus);
                    System.out.println("OTHER");
                }
            }
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    private static void copyCAVLC(BitWriter w, BitReader r) {
        int rem = 8 - r.curBit();
        int l = r.readNBit(rem);
        w.writeNBit(l, rem);
        int b = r.readNBit(8), next;
        while ((next = r.readNBit(8)) != -1) {
            w.writeNBit(b, 8);
            b = next;
        }
        int len = 7;
        while ((b & 0x1) == 0) {
            b >>= 1;
            len--;
        }
        w.writeNBit(b, len);
        w.write1Bit(1);
        w.flush();
    }

    private static void copyCABAC(BitWriter w, BitReader r) {
        long bp = r.curBit();
        long rem = r.readNBit(8 - (int) bp);
        Assert.assertEquals(rem, (1 << (8 - bp)) - 1);

        if (w.curBit() != 0)
            w.writeNBit(0xff, 8 - w.curBit()); // 1 filler
        int b;
        while ((b = r.readNBit(8)) != -1)
            w.writeNBit(b, 8);
    }
}