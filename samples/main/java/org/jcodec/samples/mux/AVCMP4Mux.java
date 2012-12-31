package org.jcodec.samples.mux;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.common.ByteBufferUtil;
import org.jcodec.common.io.FileRAOutputStream;
import org.jcodec.common.io.RAOutputStream;
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
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Syntax: <in.264> <out.mp4>\n" + "\tWhere:\n"
                    + "\t-q\tLook for stream parameters only in the beginning of stream");
            return;
        }

        File in = new File(args[0]);
        File out = new File(args[1]);

        List<SeqParameterSet> spsList = new ArrayList<SeqParameterSet>();
        List<PictureParameterSet> ppsList = new ArrayList<PictureParameterSet>();

        RAOutputStream file = new FileRAOutputStream(out);
        MP4Muxer muxer = new MP4Muxer(file);
        CompressedTrack track = muxer.addTrackForCompressed(TrackType.VIDEO, 25);

        mux(track, in, spsList, ppsList);

        Size size = new Size((spsList.get(0).pic_width_in_mbs_minus1 + 1) << 4,
                (spsList.get(0).pic_height_in_map_units_minus1 + 1) << 4);

        SampleEntry se = MP4Muxer.videoSampleEntry("avc1", size, "JCodec");

        se.add(new AvcCBox(spsList, ppsList));
        track.addSampleEntry(se);

        muxer.writeHeader();

        file.close();
    }

    private static void mux(CompressedTrack track, File f, List<SeqParameterSet> spsList,
            List<PictureParameterSet> ppsList) throws IOException {
        MappedH264ES es = new MappedH264ES(ByteBufferUtil.map(f));

        ByteBuffer frame = null;
        int i = 0;
        do {
            frame = es.nextFrame();
            if (frame == null)
                continue;
            NALUnit nu = NALUnit.read(frame);
            if (nu.type == NALUnitType.IDR_SLICE || nu.type == NALUnitType.NON_IDR_SLICE) {

                track.addFrame(new MP4Packet(formPacket(nu, frame), i, 25, 1, i, nu.type == NALUnitType.IDR_SLICE,
                        null, i, 0));
                i++;
            } else if (nu.type == NALUnitType.SPS) {
                spsList.add(SeqParameterSet.read(frame));
            } else if (nu.type == NALUnitType.PPS) {
                ppsList.add(PictureParameterSet.read(frame));
            }
        } while (frame != null);
    }

    private static ByteBuffer formPacket(NALUnit nu, ByteBuffer nextNALUnit) throws IOException {
        ByteBuffer out = ByteBuffer.allocate(nextNALUnit.remaining() + 5);
        out.putInt(nextNALUnit.remaining() + 1);
        nu.write(out);
        ByteBufferUtil.write(out, nextNALUnit);
        out.flip();
        return out;
    }
}