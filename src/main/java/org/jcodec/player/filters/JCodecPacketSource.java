package org.jcodec.player.filters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.common.model.Packet;
import org.jcodec.common.model.RationalLarge;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.MP4Demuxer;
import org.jcodec.containers.mp4.MP4Demuxer.DemuxerTrack;
import org.jcodec.containers.mp4.boxes.AudioSampleEntry;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.boxes.VideoSampleEntry;

/**
 * This class is part of JCodec ( www.jcodec.org ) This software is distributed
 * under FreeBSD License
 * 
 * @author The JCodec project
 * 
 */
public class JCodecPacketSource {

    private MP4Demuxer demuxer;
    private List<Track> tracks;
    private FileChannel is;

    public JCodecPacketSource(File file) throws IOException {
        is = new FileInputStream(file).getChannel();
        demuxer = new MP4Demuxer(is);

        tracks = new ArrayList<Track>();
        for (DemuxerTrack demuxerTrack : demuxer.getTracks()) {
            if (demuxerTrack.getBox().isVideo() || demuxerTrack.getBox().isAudio())
                tracks.add(new Track(demuxerTrack));
        }
    }

    public List<? extends PacketSource> getTracks() {
        return tracks;
    }

    public PacketSource getVideo() {
        for (Track track : tracks) {
            if (track.track.getBox().isVideo())
                return track;
        }
        return null;
    }

    public List<? extends PacketSource> getAudio() {
        List<Track> result = new ArrayList<Track>();
        for (Track track : tracks) {
            if (track.track.getBox().isAudio())
                result.add(track);
        }
        return result;
    }

    public class Track implements PacketSource {
        private DemuxerTrack track;
        private static final int FRAMES_PER_PACKET = 2048;
        private int framesPerPkt;
        private boolean closed;

        public Track(DemuxerTrack track) {
            this.track = track;
            SampleEntry sampleEntry = track.getSampleEntries()[0];
            this.framesPerPkt = 1;
            if ((sampleEntry instanceof AudioSampleEntry) && ((AudioSampleEntry) sampleEntry).isPCM()) {
                framesPerPkt = FRAMES_PER_PACKET;
            }
        }

        public Packet getPacket(ByteBuffer buffer) {
            try {
                return track.getFrames(buffer, framesPerPkt);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public MediaInfo getMediaInfo() {
            RationalLarge duration = track.getDuration();
            if (track.getBox().isVideo()) {
                VideoSampleEntry se = (VideoSampleEntry) track.getSampleEntries()[0];
                return new MediaInfo.VideoInfo(se.getFourcc(), (int) duration.getDen(), duration.getNum(),
                        track.getFrameCount(), track.getName(), null, track.getBox().getPAR(), new Size(
                                (int) se.getWidth(), (int) se.getHeight()));
            } else if (track.getBox().isAudio()) {
                AudioSampleEntry se = (AudioSampleEntry) track.getSampleEntries()[0];
                return new MediaInfo.AudioInfo(se.getFourcc(), (int) duration.getDen(), duration.getNum(),
                        track.getFrameCount(), track.getName(), null, se.getFormat(), framesPerPkt, se.getLabels());
            }
            throw new RuntimeException("This shouldn't happen");
        }

        public boolean drySeek(RationalLarge second) {
            return track.canSeek(second.multiplyS(track.getTimescale()));
        }

        public void seek(RationalLarge second) {
            track.seek(second.multiplyS(track.getTimescale()));
        }

        public void close() throws IOException {
            this.closed = true;
            checkClose();
        }

        @Override
        public void gotoFrame(int frameNo) {
            track.gotoFrame(frameNo);
        }
    }

    private void checkClose() throws IOException {
        boolean closed = true;
        for (Track track : tracks) {
            closed &= track.closed;
        }
        if (closed)
            is.close();
    }
}