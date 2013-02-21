package org.jcodec.samples.h264embed;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Demuxer;
import org.jcodec.containers.mp4.MP4Demuxer.DemuxerTrack;
import org.jcodec.containers.mp4.MP4Muxer;
import org.jcodec.containers.mp4.MP4Muxer.CompressedTrack;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.jcodec.samples.transcode.TranscodeMain;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * JCodec sample code
 * 
 * This sample embeds text onto H.264 picture without full re-transcode cycle
 * 
 * @author Jay Codec
 * 
 */
public class H264EmbedMain {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("H264 Text Embed");
            System.out.println("Syntax: <in> <out>");
            return;
        }

        FileChannel sink = null;
        FileChannel source = null;
        try {
            source = new FileInputStream(new File(args[0])).getChannel();
            sink = new FileOutputStream(new File(args[1])).getChannel();

            MP4Demuxer demux = new MP4Demuxer(source);
            MP4Muxer muxer = new MP4Muxer(sink, Brand.MOV);

            EmbedTranscoder transcoder = new EmbedTranscoder();

            DemuxerTrack inTrack = demux.getVideoTrack();
            VideoSampleEntry ine = (VideoSampleEntry) inTrack.getSampleEntries()[0];

            CompressedTrack outTrack = muxer.addTrackForCompressed(TrackType.VIDEO, (int) inTrack.getTimescale());
            outTrack.addSampleEntry(ine);

            ByteBuffer _out = ByteBuffer.allocate(ine.getWidth() * ine.getHeight() * 6);

            MP4Packet inFrame;
            int totalFrames = (int) inTrack.getFrameCount();
            for (int i = 0; (inFrame = inTrack.getFrames(1)) != null; i++) {
                ByteBuffer data = inFrame.getData();
                TranscodeMain.decodeData(data);
                _out.clear();
                ByteBuffer result = transcoder.transcode(data, _out);
                outTrack.addFrame(new MP4Packet(inFrame, result));

                if (i % 100 == 0)
                    System.out.println((i * 100 / totalFrames) + "%");
            }
            muxer.writeHeader();
        } finally {
            if (sink != null)
                sink.close();
            if (source != null)
                source.close();
        }
    }
}
