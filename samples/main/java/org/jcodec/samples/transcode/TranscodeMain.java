package org.jcodec.samples.transcode;

import static java.lang.String.format;
import static org.jcodec.common.model.ColorSpace.RGB;
import static org.jcodec.common.model.Rational.HALF;
import static org.jcodec.common.model.Unit.SEC;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.EnumSet;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.annexb.H264Utils;
import org.jcodec.codecs.h264.annexb.MappedH264ES;
import org.jcodec.codecs.h264.io.model.NALUnit;
import org.jcodec.codecs.h264.io.model.NALUnitType;
import org.jcodec.codecs.h264.io.model.PictureParameterSet;
import org.jcodec.codecs.h264.io.model.SeqParameterSet;
import org.jcodec.codecs.h264.io.model.SliceHeader;
import org.jcodec.codecs.h264.mp4.AvcCBox;
import org.jcodec.codecs.prores.ProresDecoder;
import org.jcodec.codecs.prores.ProresEncoder;
import org.jcodec.codecs.prores.ProresEncoder.Profile;
import org.jcodec.codecs.y4m.Y4MDecoder;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Demuxer;
import org.jcodec.containers.mp4.TrackType;
import org.jcodec.containers.mp4.MP4Demuxer.DemuxerTrack;
import org.jcodec.containers.mp4.MP4Demuxer.FramesTrack;
import org.jcodec.containers.mp4.MP4DemuxerException;
import org.jcodec.containers.mp4.MP4Muxer;
import org.jcodec.containers.mp4.MP4Muxer.CompressedTrack;
import org.jcodec.containers.mp4.MP4Muxer.MuxerTrack;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.scale.AWTUtil;
import org.jcodec.scale.RgbToYuv420;
import org.jcodec.scale.RgbToYuv422;
import org.jcodec.scale.Transform;
import org.jcodec.scale.Yuv420pToYuv422p;
import org.jcodec.scale.Yuv422pToRgb;
import org.jcodec.scale.Yuv422pToYuv420p;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author Jay Codec
 * 
 */
public class TranscodeMain {

    private static final String APPLE_PRO_RES_422 = "Apple ProRes 422";

    public static void main(String[] args) throws Exception {

        if (args.length < 3) {
            System.out.println("Transcoder. Transcodes a prores movie into a set of png files\n"
                    + "Syntax: <type> <input file> <output file> [profile]\n"
                    + "\ttype: 'png2prores' or 'prores2png'\n" + "\tpng file name should be set c format string\n"
                    + "\tprofile: 'apch' (HQ) , 'apcn' (Standard) , 'apcs' (LT), 'apco' (Proxy).");

            return;
        }
        if ("prores2png".equals(args[0]))
            prores2png(args[1], args[2]);
        if ("mpeg2png".equals(args[0]))
            mpeg2png(args[1], args[2]);
        else if ("png2prores".equals(args[0]))
            png2prores(args[1], args[2], args.length > 3 ? args[3] : "apch");
        else if ("y4m2prores".equals(args[0]))
            y4m2prores(args[1], args[2]);
        else if ("png2avc".equals(args[0])) {
            png2avc(args[1], args[2]);
        } else if ("prores2avc".equals(args[0])) {
            prores2avc(args[1], args[2]);
        }
    }

    private static void prores2avc(String in, String out) throws IOException {
        FileChannel sink = null;
        FileChannel raw = null;
        FileChannel source = null;
        try {
            sink = new FileOutputStream(new File(out)).getChannel();
            raw = new FileOutputStream(new File("/Users/stan/Desktop/cool.264")).getChannel();
            source = new FileInputStream(new File(in)).getChannel();

            MP4Demuxer demux = new MP4Demuxer(source);
            MP4Muxer muxer = new MP4Muxer(sink, Brand.MOV);

            ProresDecoder decoder = new ProresDecoder();
            H264Encoder encoder = new H264Encoder();

            Transform transform = new Yuv422pToYuv420p(0, 2);

            DemuxerTrack inTrack = demux.getVideoTrack();
            CompressedTrack outTrack = muxer.addTrackForCompressed(TrackType.VIDEO, 25);

            VideoSampleEntry ine = (VideoSampleEntry) inTrack.getSampleEntries()[0];
            Picture target1 = Picture.create(ine.getWidth(), ine.getHeight(), ColorSpace.YUV422_10);
            Picture target2 = Picture.create(ine.getWidth(), ine.getHeight(), ColorSpace.YUV420);
            ByteBuffer _out = ByteBuffer.allocate(ine.getWidth() * ine.getHeight() * 6);

            ArrayList<ByteBuffer> spsList = new ArrayList<ByteBuffer>();
            ArrayList<ByteBuffer> ppsList = new ArrayList<ByteBuffer>();
            MP4Packet inFrame;
            int totalFrames = (int)inTrack.getFrameCount();
            for (int i = 0; (inFrame = inTrack.getFrames(1)) != null; i++) {
                Picture dec = decoder.decodeFrame(inFrame.getData(), target1.getData());
                transform.transform(dec, target2);
                _out.clear();
                ByteBuffer result = encoder.encodeFrame(_out, target2);
                raw.write(result.duplicate());
                spsList.clear();
                ppsList.clear();
                processFrame(result, spsList, ppsList);
                outTrack.addFrame(new MP4Packet(inFrame, result));
                if(i % 100 == 0)
                    System.out.println((i * 100 / totalFrames) + "%");
            }
            outTrack.addSampleEntry(createSampleEntry(spsList, ppsList));

            muxer.writeHeader();
        } finally {
            if (raw != null)
                raw.close();
            if (sink != null)
                sink.close();
            if (source != null)
                source.close();
        }
    }

    private static SampleEntry createSampleEntry(ArrayList<ByteBuffer> spsList, ArrayList<ByteBuffer> ppsList) {
        SeqParameterSet sps = SeqParameterSet.read(spsList.get(0).duplicate());
        AvcCBox avcC = new AvcCBox(sps.profile_idc, 0, sps.level_idc, spsList, ppsList);

        Size size = new Size((sps.pic_width_in_mbs_minus1 + 1) << 4, (sps.pic_height_in_map_units_minus1 + 1) << 4);

        SampleEntry se = MP4Muxer.videoSampleEntry("avc1", size, "JCodec");
        se.add(avcC);

        return se;
    }

    private static void processFrame(ByteBuffer _avcFrame, ArrayList<ByteBuffer> spsList, ArrayList<ByteBuffer> ppsList) {

        ByteBuffer dup = _avcFrame.duplicate();

        while (true) {
            ByteBuffer buf = H264Utils.nextNALUnit(dup);
            if (buf == null)
                break;
            buf.putInt(buf.remaining() - 4);
            NALUnit nu = NALUnit.read(buf);

            if (nu.type == NALUnitType.PPS) {
                ppsList.add(buf);
            } else if (nu.type == NALUnitType.SPS) {
                spsList.add(buf);
            }
        }
    }

    private static void png2avc(String pattern, String out) throws IOException {
        FileChannel sink = null;
        try {
            sink = new FileOutputStream(new File(out)).getChannel();
            H264Encoder encoder = new H264Encoder();
            RgbToYuv420 transform = new RgbToYuv420(0, 0);

            int i;
            for (i = 0; i < 10000; i++) {
                File nextImg = new File(String.format(pattern, i));
                if (!nextImg.exists())
                    continue;
                BufferedImage rgb = ImageIO.read(nextImg);
                Picture yuv = Picture.create(rgb.getWidth(), rgb.getHeight(), ColorSpace.YUV420);
                transform.transform(AWTUtil.fromBufferedImage(rgb), yuv);
                ByteBuffer buf = ByteBuffer.allocate(rgb.getWidth() * rgb.getHeight() * 3);

                ByteBuffer ff = encoder.encodeFrame(buf, yuv);
                sink.write(ff);
            }
            if (i == 1) {
                System.out.println("Image sequence not found");
                return;
            }
        } finally {
            if (sink != null)
                sink.close();
        }
    }

    static void y4m2prores(String input, String output) throws Exception {
        FileChannel y4m = new FileInputStream(input).getChannel();

        Y4MDecoder frames = new Y4MDecoder(y4m);

        Picture outPic = Picture.create(frames.getWidth(), frames.getHeight(), ColorSpace.YUV420);

        FileChannel sink = null;
        MP4Muxer muxer = null;
        try {
            sink = new FileOutputStream(new File(output)).getChannel();
            Rational fps = frames.getFps();
            if (fps == null) {
                System.out.println("Can't get fps from the input, assuming 24");
                fps = new Rational(24, 1);
            }
            muxer = new MP4Muxer(sink);
            ProresEncoder encoder = new ProresEncoder(Profile.HQ);

            Yuv420pToYuv422p color = new Yuv420pToYuv422p(2, 0);
            CompressedTrack videoTrack = muxer.addVideoTrack("apch", frames.getSize(), APPLE_PRO_RES_422, fps.getNum());
            videoTrack.setTgtChunkDuration(HALF, SEC);
            Picture picture = Picture.create(frames.getSize().getWidth(), frames.getSize().getHeight(),
                    ColorSpace.YUV422_10);
            Picture frame;
            int i = 0;
            ByteBuffer buf = ByteBuffer.allocate(frames.getSize().getWidth() * frames.getSize().getHeight() * 6);
            while ((frame = frames.nextFrame(outPic.getData())) != null) {
                color.transform(frame, picture);
                ByteBuffer ff = encoder.encodeFrame(buf, picture);
                videoTrack.addFrame(new MP4Packet(ff, i * fps.getDen(), fps.getNum(), fps.getDen(), i, true, null, i
                        * fps.getDen(), 0));
                i++;
            }
        } finally {
            if (muxer != null)
                muxer.writeHeader();
            if (sink != null)
                sink.close();
        }

    }

    private static void prores2png(String in, String out) throws IOException {
        File file = new File(in);
        if (!file.exists()) {
            System.out.println("Input file doesn't exist");
            return;
        }

        MP4Demuxer rawDemuxer = new MP4Demuxer(new FileInputStream(file).getChannel());
        FramesTrack videoTrack = (FramesTrack) rawDemuxer.getVideoTrack();
        if (videoTrack == null) {
            System.out.println("Video track not found");
            return;
        }
        Yuv422pToRgb transform = new Yuv422pToRgb(2, 0);

        ProresDecoder decoder = new ProresDecoder();
        BufferedImage bi = null;
        Picture rgb = null;
        int i = 0;
        Packet pkt;
        while ((pkt = videoTrack.getFrames(1)) != null) {
            Picture buf = Picture.create(1920, 1080, ColorSpace.YUV422_10);
            Picture pic = decoder.decodeFrame(pkt.getData(), buf.getData());
            if (bi == null)
                bi = new BufferedImage(pic.getWidth(), pic.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            if (rgb == null)
                rgb = Picture.create(pic.getWidth(), pic.getHeight(), RGB);
            transform.transform(pic, rgb);
            AWTUtil.toBufferedImage(rgb, bi);
            ImageIO.write(bi, "png", new File(format(out, i++)));
        }
    }

    private static void mpeg2png(String in, String out) throws IOException {
        File file = new File(in);
        if (!file.exists()) {
            System.out.println("Input file doesn't exist");
            return;
        }

        MP4Demuxer rawDemuxer = new MP4Demuxer(new FileInputStream(file).getChannel());
        FramesTrack videoTrack = (FramesTrack) rawDemuxer.getVideoTrack();
        if (videoTrack == null) {
            System.out.println("Video track not found");
            return;
        }
        Yuv422pToRgb transform = new Yuv422pToRgb(2, 0);

        ProresDecoder decoder = new ProresDecoder();
        BufferedImage bi = null;
        Picture rgb = null;
        int i = 0;
        Packet pkt;
        while ((pkt = videoTrack.getFrames(1)) != null) {
            Picture buf = Picture.create(1920, 1080, ColorSpace.YUV422_10);
            Picture pic = decoder.decodeFrame(pkt.getData(), buf.getData());
            if (bi == null)
                bi = new BufferedImage(pic.getWidth(), pic.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            if (rgb == null)
                rgb = Picture.create(pic.getWidth(), pic.getHeight(), RGB);
            transform.transform(pic, rgb);
            AWTUtil.toBufferedImage(rgb, bi);
            ImageIO.write(bi, "png", new File(format(out, i++)));
        }
    }

    private static void png2prores(String pattern, String out, String fourcc) throws IOException, MP4DemuxerException {

        Profile profile = getProfile(fourcc);
        if (profile == null) {
            System.out.println("Unsupported fourcc: " + fourcc);
            return;
        }

        FileChannel sink = null;
        try {
            sink = new FileOutputStream(new File(out)).getChannel();
            MP4Muxer muxer = new MP4Muxer(sink, Brand.MOV);
            ProresEncoder encoder = new ProresEncoder(profile);
            RgbToYuv422 transform = new RgbToYuv422(2, 0);

            CompressedTrack videoTrack = null;
            int i;
            for (i = 1;; i++) {
                File nextImg = new File(String.format(pattern, i));
                if (!nextImg.exists())
                    break;
                BufferedImage rgb = ImageIO.read(nextImg);

                if (videoTrack == null) {
                    videoTrack = muxer.addVideoTrack(profile.fourcc, new Size(rgb.getWidth(), rgb.getHeight()),
                            APPLE_PRO_RES_422, 24000);
                    videoTrack.setTgtChunkDuration(HALF, SEC);
                }
                Picture yuv = Picture.create(rgb.getWidth(), rgb.getHeight(), ColorSpace.YUV422);
                transform.transform(AWTUtil.fromBufferedImage(rgb), yuv);
                ByteBuffer buf = ByteBuffer.allocate(rgb.getWidth() * rgb.getHeight() * 3);

                ByteBuffer ff = encoder.encodeFrame(buf, yuv);
                videoTrack.addFrame(new MP4Packet(ff, i * 1001, 24000, 1001, i, true, null, i * 1001, 0));
            }
            if (i == 1) {
                System.out.println("Image sequence not found");
                return;
            }
            muxer.writeHeader();
        } finally {
            if (sink != null)
                sink.close();
        }
    }

    private static Profile getProfile(String fourcc) {
        for (Profile profile2 : EnumSet.allOf(Profile.class)) {
            if (fourcc.equals(profile2.fourcc))
                return profile2;
        }
        return null;
    }
}