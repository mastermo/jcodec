package org.jcodec.samples.mux;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.codecs.h264.MappedH264ES;
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
import org.jcodec.samples.transcode.TranscodeMain;

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

        ArrayList<ByteBuffer> spsList = new ArrayList<ByteBuffer>();
        ArrayList<ByteBuffer> ppsList = new ArrayList<ByteBuffer>();
        Packet frame = null;
        while ((frame = es.nextFrame()) != null) {
            ByteBuffer wrap = ByteBuffer.wrap(NIOUtils.toArray(frame.getData()));
            TranscodeMain.processFrame(wrap, spsList, ppsList);
            MP4Packet pkt = new MP4Packet(new Packet(frame, wrap), frame.getPts(), 0);
            System.out.println(pkt.getFrameNo());
            track.addFrame(pkt);
        }
        addSampleEntry(track, es.getSps(), es.getPps());
    }

    private static void addSampleEntry(CompressedTrack track, SeqParameterSet[] spss, PictureParameterSet[] ppss) {
        SeqParameterSet sps = spss[0];
        Size size = new Size((sps.pic_width_in_mbs_minus1 + 1) << 4, (sps.pic_height_in_map_units_minus1 + 1) << 4);

        SampleEntry se = MP4Muxer.videoSampleEntry("avc1", size, "JCodec");

        avcC = new AvcCBox(sps.profile_idc, 0, sps.level_idc, write(spss), write(ppss));
        se.add(avcC);
        track.addSampleEntry(se);
    }

    private static List<ByteBuffer> write(PictureParameterSet[] ppss) {
        List<ByteBuffer> result = new ArrayList<ByteBuffer>();
        for (PictureParameterSet pps : ppss) {
            ByteBuffer buf = ByteBuffer.allocate(1024);
            pps.write(buf);
            buf.flip();
            result.add(buf);
        }
        return result;
    }

    private static List<ByteBuffer> write(SeqParameterSet[] spss) {
        List<ByteBuffer> result = new ArrayList<ByteBuffer>();
        for (SeqParameterSet sps : spss) {
            ByteBuffer buf = ByteBuffer.allocate(1024);
            sps.write(buf);
            buf.flip();
            result.add(buf);
        }
        return result;
    }
}