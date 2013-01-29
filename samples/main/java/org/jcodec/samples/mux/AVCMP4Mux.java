package org.jcodec.samples.mux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.MP4Muxer;
import org.jcodec.containers.mp4.MP4Muxer.CompressedTrack;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.SampleEntry;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * Sample code. Muxes H.264 ( MPEG4 AVC ) elementary stream into MP4 ( ISO
 * 14496-1/14496-12/14496-14, Quicktime ) container
 * 
 * @author Jay Codec
 * 
 */
public class AVCMP4Mux {
    private static AvcCBox avcC;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Syntax: <in.264> <out.mp4>\n" + "\tWhere:\n"
                    + "\t-q\tLook for stream parameters only in the beginning of stream");
            return;
        }

        File in = new File(args[0]);
        File out = new File(args[1]);

        FileChannel file = new FileOutputStream(out).getChannel();
        MP4Muxer muxer = new MP4Muxer(file);
        CompressedTrack track = muxer.addTrackForCompressed(TrackType.VIDEO, 25);

        mux(track, in);

        muxer.writeHeader();

        file.close();
    }

    private static void mux(CompressedTrack track, File f) throws IOException {
        MappedH264ES es = new MappedH264ES(NIOUtils.map(f));

        AvcCBox avcCBox = new AvcCBox();

        Packet frame = null;
        int i = 0;
        do {
            frame = es.nextFrame();
            if (frame == null)
                continue;
            ByteBuffer data = frame.getData();
            NALUnit nu = NALUnit.read(data);
            if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {

                MP4Packet pkt = new MP4Packet(formPacket(nu, data), i, 25, 1, i, nu.type == NALUnitType.IDR_SLICE,
                        null, i, 0);
                pkt.setDisplayOrder(frame.getDisplayOrder());
                track.addFrame(pkt);
                i++;
            } else if (nu.type == NALUnitType.SPS) {
                avcCBox.getSpsList().add(data);
                if (avcCBox.getSpsList().size() == 1) {
                    addSampleEntry(track, SeqParameterSet.read(data.duplicate()));
                }
            } else if (nu.type == NALUnitType.PPS) {
                avcCBox.getPpsList().add(data);
            }
        } while (frame != null);
    }

    private static void addSampleEntry(CompressedTrack track, SeqParameterSet sps) {
        Size size = new Size((sps.pic_width_in_mbs_minus1 + 1) << 4, (sps.pic_height_in_map_units_minus1 + 1) << 4);

        SampleEntry se = MP4Muxer.videoSampleEntry("avc1", size, "JCodec");

        avcC = new AvcCBox();
        se.add(avcC);
        track.addSampleEntry(se);
    }

    void addSP(SeqParameterSet sps, PictureParameterSet pps) {

    }

    private static ByteBuffer formPacket(NALUnit nu, ByteBuffer nextNALUnit) throws IOException {
        ByteBuffer out = ByteBuffer.allocate(nextNALUnit.remaining() + 5);
        out.putInt(nextNALUnit.remaining() + 1);
        nu.write(out);
        NIOUtils.write(out, nextNALUnit);
        out.flip();
        return out;
    }
}